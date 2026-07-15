package io.github.mikhailhal.sazanami.processor

import com.intellij.psi.PsiElement
import io.github.mikhailhal.sazanami.common.FunctionNode
import io.github.mikhailhal.sazanami.common.ModuleName
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * 依存グラフを構築するクラス
 *
 * KtFileを走査し、関数呼び出しとプロパティ参照を検出して逆方向依存グラフを構築する。
 *
 * ノードは2種類:
 *   - 関数 (KtNamedFunction)
 *   - プロパティ (KtProperty) — 初期化子・デリゲート式・アクセサ内の呼び出しは
 *     プロパティに帰属し、関数本体からのプロパティ参照はエッジになる。
 *     これにより Android ViewModel イディオム
 *     (val uiState = build(...).stateIn(...)) の経路を追跡できる (#27)
 *
 * エッジの張り方:
 *   1. owner = 式を囲む最も近い非ローカル宣言 (関数 or プロパティ)
 *   2. target = Analysis API で解決した呼び出し先関数 / 参照先プロパティ
 *   3. graph.addEdge(owner, target) で登録
 */
class GraphBuilder {
    private val graph = ReverseDependencyGraph()

    /**
     * 複数モジュールのKtFileを処理してグラフを構築
     *
     * @param moduleFiles モジュール名 → KtFileリストのマッピング
     * @return 構築された逆方向依存グラフ
     */
    fun build(moduleFiles: Map<ModuleName, List<KtFile>>): ReverseDependencyGraph {
        for ((moduleName, files) in moduleFiles) {
            for (file in files) {
                processFile(file, moduleName)
            }
        }
        return graph
    }

    /**
     * 単一のKtFileを処理
     */
    private fun processFile(file: KtFile, moduleName: ModuleName) {
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                processCallExpression(expression, moduleName)
                super.visitCallExpression(expression)
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                processNameReference(expression, moduleName)
                super.visitSimpleNameExpression(expression)
            }
        })
    }

    /**
     * 関数呼び出しを処理してグラフにエッジを追加
     */
    private fun processCallExpression(expression: KtCallExpression, moduleName: ModuleName) {
        val owner = resolveOwnerNode(expression, moduleName) ?: return

        // callee を解決: Analysis API で関数シンボルを取得
        analyze(expression) {
            val call = expression.resolveToCall()
            val functionSymbol = call?.singleFunctionCallOrNull()?.symbol

            if (functionSymbol != null) {
                val calleeFqn = functionSymbol.callableId?.asSingleFqName()?.asString()
                if (calleeFqn != null) {
                    // calleeのisTestとmoduleNameはここでは不明だが、検索キーとしてしか使わない
                    // 同一モジュール内の呼び出しと仮定
                    val calleeNode = FunctionNode.forLookup(calleeFqn, moduleName)
                    graph.addEdge(owner, calleeNode)
                }
            }
        }
    }

    /**
     * プロパティ参照を処理してグラフにエッジを追加 (#27)
     *
     * 例: fun testStream() { viewModel.stream } の `stream` 参照から
     *     testStream → stream のエッジを張る。
     * プロパティアクセスは構文上呼び出し式ではないが、意味的にはアクセサの実行なので
     * エッジとして扱う。読み書きは区別しない (setterの実行も影響経路のため)。
     */
    private fun processNameReference(expression: KtSimpleNameExpression, moduleName: ModuleName) {
        // 式の位置にいない名前 (import / package / 型参照) は解決コスト削減のためスキップ
        if (expression.getParentOfType<KtImportDirective>(strict = true) != null) return
        if (expression.getParentOfType<KtPackageDirective>(strict = true) != null) return
        if (expression.getParentOfType<KtTypeReference>(strict = true) != null) return

        val owner = resolveOwnerNode(expression, moduleName) ?: return

        analyze(expression) {
            val variableSymbol = expression.resolveToCall()
                ?.singleVariableAccessCall()
                ?.symbol

            // ローカル変数やパラメータは KaPropertySymbol ではないためここで除外される
            if (variableSymbol is KaPropertySymbol) {
                val propertyFqn = variableSymbol.callableId?.asSingleFqName()?.asString()
                if (propertyFqn != null) {
                    val propertyNode = FunctionNode.forLookup(propertyFqn, moduleName)
                    graph.addEdge(owner, propertyNode)
                }
            }
        }
    }

    /**
     * 式を囲む最も近い非ローカル宣言をグラフノードとして解決する
     *
     * PSIツリーを上方向に走査し、最初に見つかった FQN を持つ宣言を返す:
     *   - KtNamedFunction → 関数ノード (ローカル関数は FQN を持たないため素通りし、
     *     さらに外側の宣言へ帰属する)
     *   - KtProperty → プロパティノード (初期化子・デリゲート式内。ローカル変数は素通り)
     *
     * カスタム getter/setter (KtPropertyAccessor) はプロパティの子要素のため、
     * アクセサ本体内の式もこの走査で KtProperty に到達して帰属する。
     *
     * ラムダ (KtFunctionLiteral) は KtNamedFunction ではないため自然に素通りする。
     * これにより val config by lazy { ... } や格納ラムダ内の呼び出しも
     * 囲むプロパティに帰属する。
     *
     * どの宣言にも属さない場合 (トップレベル初期化コード等) は null を返しスキップする。
     */
    private fun resolveOwnerNode(expression: KtExpression, moduleName: ModuleName): FunctionNode? {
        var current: PsiElement? = expression.parent
        while (current != null) {
            when (current) {
                is KtNamedFunction -> {
                    val fqn = current.fqName?.asString()
                    if (fqn != null) {
                        return FunctionNode(fqn, moduleName, hasTestAnnotation(current))
                    }
                    // ローカル関数: FQNを持たないため、外側の宣言へ帰属を続ける
                }

                /**
                 * val uiState = buildUiState(repository)        // 初期化子 → 登るとKtProperty(uiState)
                 * val config by lazy { repository.loadConfig() } // デリゲート式 → KtProperty(config)
                 * val handler = { repository.processEvent() }    // 初期化子内のラムダ → KtProperty(handler)
                 *
                 * 上記のようなケースに該当
                 */
                is KtProperty -> {
                    val fqn = current.fqName?.asString()
                    if (fqn != null) {
                        return FunctionNode(fqn, moduleName, isTest = false)
                    }
                    // ローカル変数: FQNを持たないため、外側の宣言へ帰属を続ける
                }
            }
            current = current.parent
        }
        return null
    }

    /**
     * 関数に@Testアノテーションが付与されているかを判定
     *
     * 対応アノテーション:
     * - org.junit.Test (JUnit 4)
     * - org.junit.jupiter.api.Test (JUnit 5)
     * - kotlin.test.Test (kotlin-test)
     */
    private fun hasTestAnnotation(function: KtNamedFunction): Boolean {
        return function.annotationEntries.any { annotation ->
            val shortName = annotation.shortName?.asString()
            shortName == "Test"
        }
    }
}

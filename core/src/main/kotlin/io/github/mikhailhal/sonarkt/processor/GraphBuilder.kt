package io.github.mikhailhal.sonarkt.processor

import io.github.mikhailhal.sonarkt.common.FunctionNode
import io.github.mikhailhal.sonarkt.common.ModuleName
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * 依存グラフを構築するクラス
 *
 * KtFileを走査し、関数呼び出しを検出して逆方向依存グラフを構築する。
 * 各KtCallExpressionに対して:
 *   1. caller = 呼び出しを含む関数 (親のKtNamedFunction)
 *   2. callee = resolveToCall()で解決した関数のFQN
 *   3. graph.addEdge(caller, callee) で登録
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
        })
    }

    /**
     * 関数呼び出しを処理してグラフにエッジを追加
     */
    private fun processCallExpression(expression: KtCallExpression, moduleName: ModuleName) {
        // 1. caller を特定: この呼び出しを含む関数
        val callerFunction = expression.getParentOfType<KtNamedFunction>(strict = true) ?: // トップレベルの呼び出し（関数外）はスキップ
        return

        val callerFqn = callerFunction.fqName?.asString() ?: return
        val callerIsTest = hasTestAnnotation(callerFunction)
        val callerNode = FunctionNode(callerFqn, moduleName, callerIsTest)

        // 2. callee を解決: Analysis API で関数シンボルを取得
        analyze(expression) {
            val call = expression.resolveToCall()
            val functionSymbol = call?.singleFunctionCallOrNull()?.symbol

            if (functionSymbol != null) {
                val calleeFqn = functionSymbol.callableId?.asSingleFqName()?.asString()
                if (calleeFqn != null) {
                    // calleeのisTestとmoduleNameはここでは不明だが、検索キーとしてしか使わない
                    // 同一モジュール内の呼び出しと仮定
                    val calleeNode = FunctionNode.forLookup(calleeFqn, moduleName)
                    graph.addEdge(callerNode, calleeNode)
                }
            }
        }
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

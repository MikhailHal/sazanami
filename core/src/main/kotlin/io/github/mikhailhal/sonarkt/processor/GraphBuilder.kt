package io.github.mikhailhal.sonarkt.processor

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
     * 複数のKtFileを処理してグラフを構築
     */
    fun build(files: List<KtFile>): ReverseDependencyGraph {
        for (file in files) {
            processFile(file)
        }
        return graph
    }

    /**
     * 単一のKtFileを処理
     */
    private fun processFile(file: KtFile) {
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                processCallExpression(expression)
                super.visitCallExpression(expression)
            }
        })
    }

    /**
     * 関数呼び出しを処理してグラフにエッジを追加
     */
    private fun processCallExpression(expression: KtCallExpression) {
        // 1. caller を特定: この呼び出しを含む関数
        val callerFunction = expression.getParentOfType<KtNamedFunction>(strict = true)
        if (callerFunction == null) {
            // トップレベルの呼び出し（関数外）はスキップ
            return
        }

        val callerFqn = callerFunction.fqName?.asString() ?: return

        // 2. callee を解決: Analysis API で関数シンボルを取得
        analyze(expression) {
            val call = expression.resolveToCall()
            val functionSymbol = call?.singleFunctionCallOrNull()?.symbol

            if (functionSymbol != null) {
                val calleeFqn = functionSymbol.callableId?.asSingleFqName()?.asString()
                if (calleeFqn != null) {
                    graph.addEdge(callerFqn, calleeFqn)
                }
            }
        }
    }
}

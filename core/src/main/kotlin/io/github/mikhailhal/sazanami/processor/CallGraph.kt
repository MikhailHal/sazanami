package io.github.mikhailhal.sazanami.processor

import io.github.mikhailhal.sazanami.common.FunctionNode

/**
 * コールグラフ
 *
 * ノードは関数およびプロパティ、エッジは caller → callee の呼び出し/参照関係。
 *
 * 内部表現は逆向きの隣接リスト (callee → callers)。
 * 「ある関数を変更したとき、影響を受ける関数は何か」を効率的に調べるため、
 * 概念上のエッジとは逆向きで保持している。
 *
 * 例:
 *   Calculator.add が testAdd と helperB から呼ばれている場合:
 *   edges[Calculator.add] = {testAdd, helperB}
 *
 * FunctionNodeのequals/hashCodeはfqn + moduleNameで判定されるため、
 * マルチモジュール環境でも同じFQNを持つ異なるモジュールの関数を区別できる。
 */
class CallGraph {
    private val edges: MutableMap<FunctionNode, MutableSet<FunctionNode>> = mutableMapOf()

    /**
     * caller が callee を呼んでいることを登録
     * 内部的には callee → caller の向きで保存される
     */
    fun addEdge(caller: FunctionNode, callee: FunctionNode) {
        edges.getOrPut(callee) { mutableSetOf() }.add(caller)
    }

    /**
     * callee を呼んでいる関数（caller）の一覧を取得
     */
    fun getCallers(callee: FunctionNode): Set<FunctionNode> {
        return edges[callee] ?: emptySet()
    }

    /**
     * FQNのみでcalleeを検索し、呼んでいる関数（caller）の一覧を取得
     *
     * クロスモジュール呼び出しでは、CallGraphBuilderがcalleeのモジュール名を
     * 正確に特定できないため、FQNのみで検索する必要がある。
     *
     * 例: moduleB.AppService.execute が moduleA.CoreService.process を呼ぶ場合
     *   - CallGraphBuilder: calleeを (CoreService.process, moduleB) として登録
     *   - ChangedFunctionCollector: (CoreService.process, moduleA) として検出
     *   - FQNのみで検索することで、モジュール名の不一致を回避
     *   - モジュール間にてFQNが重複した場合：
     *     - 現時点ではどちらも検出する仕様となっている
     *     - 一方で、本ケースは低確率のため対応を保留とする
     *
     * @param fqn 検索する関数の完全修飾名
     * @return calleeを呼んでいる全caller（複数モジュールにまたがる可能性あり）
     */
    fun getCallersByFqn(fqn: String): Set<FunctionNode> {
        val result = mutableSetOf<FunctionNode>()
        for ((callee, callers) in edges) {
            if (callee.fqn == fqn) {
                result.addAll(callers)
            }
        }
        return result
    }

    /**
     * グラフの全エントリを取得（デバッグ用）
     */
    fun getAllEdges(): Map<FunctionNode, Set<FunctionNode>> {
        return edges.mapValues { it.value.toSet() }
    }

    /**
     * グラフの統計情報
     */
    fun stats(): String {
        val totalCallees = edges.size
        val totalEdges = edges.values.sumOf { it.size }
        return "Callees: $totalCallees, Total edges: $totalEdges"
    }
}

package io.github.mikhailhal.sonarkt.processor

import io.github.mikhailhal.sonarkt.common.FunctionFqn
import io.github.mikhailhal.sonarkt.common.FunctionNode

/**
 * 逆方向依存グラフ
 *
 * 通常の依存グラフは caller → callee だが、
 * このグラフは「ある関数を変更したとき、影響を受ける関数は何か」を
 * 効率的に調べるため、callee → callers の逆向きで保持する。
 *
 * 例:
 *   Calculator.add が testAdd と helperB から呼ばれている場合:
 *   edges[Calculator.add] = {testAdd, helperB}
 *
 * FunctionNodeのequals/hashCodeはfqnのみで判定されるため、
 * 検索時はFunctionNode.forLookup(fqn)で検索用ノードを作成できる。
 */
class ReverseDependencyGraph {
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
     * FQN文字列で検索可能（FunctionNode.forLookupを内部で使用）
     */
    fun getCallers(callee: FunctionFqn): Set<FunctionNode> {
        val lookupKey = FunctionNode.forLookup(callee)
        return edges[lookupKey] ?: emptySet()
    }

    /**
     * callee を呼んでいる関数（caller）の一覧を取得
     */
    fun getCallers(callee: FunctionNode): Set<FunctionNode> {
        return edges[callee] ?: emptySet()
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

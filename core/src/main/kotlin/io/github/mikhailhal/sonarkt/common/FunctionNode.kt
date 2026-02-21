package io.github.mikhailhal.sonarkt.common

/**
 * 関数ノード
 *
 * グラフ内の関数を表すドメインモデル。
 * FQNで同一性を判定するため、equals/hashCodeはfqnのみを使用。
 *
 * @property fqn 関数の完全修飾名
 * @property isTest @Testアノテーションが付与されているか
 */
data class FunctionNode(
    val fqn: FunctionFqn,
    val isTest: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FunctionNode) return false
        return fqn == other.fqn
    }

    override fun hashCode(): Int = fqn.hashCode()

    companion object {
        /**
         * 検索用のダミーノードを作成
         * isTestの値は検索には影響しない
         */
        fun forLookup(fqn: FunctionFqn): FunctionNode = FunctionNode(fqn, isTest = false)
    }
}

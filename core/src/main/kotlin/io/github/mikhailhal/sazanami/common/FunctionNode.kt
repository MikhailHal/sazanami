package io.github.mikhailhal.sazanami.common

import java.util.Objects

/**
 * 関数ノード
 *
 * グラフ内の関数を表すドメインモデル。
 * マルチモジュール環境では同じFQNが複数モジュールに存在しうるため、
 * fqn + moduleNameで同一性を判定する。
 *
 * @property fqn 関数の完全修飾名
 * @property moduleName 関数が属するモジュール名
 * @property isTest @Testアノテーションが付与されているか
 */
data class FunctionNode(
    val fqn: FunctionFqn,
    val moduleName: ModuleName,
    val isTest: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FunctionNode) return false
        return fqn == other.fqn && moduleName == other.moduleName
    }

    override fun hashCode(): Int = Objects.hash(fqn, moduleName)

    companion object {
        /**
         * 検索用のダミーノードを作成
         * isTestの値は検索には影響しない
         */
        fun forLookup(fqn: FunctionFqn, moduleName: ModuleName): FunctionNode =
            FunctionNode(fqn, moduleName, isTest = false)
    }
}

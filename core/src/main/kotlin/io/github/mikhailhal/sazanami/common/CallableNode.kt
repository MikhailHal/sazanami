package io.github.mikhailhal.sazanami.common

import java.util.Objects

/**
 * コールグラフのノード種別
 */
enum class NodeType {
    FUNCTION,
    PROPERTY
}

/**
 * callable ノード
 *
 * グラフ内の callable 宣言 (関数・プロパティ) を表すドメインモデル。
 * プロパティは初期化子・デリゲート・アクセサ内の呼び出しの帰属先として
 * ノードになる (#27)。
 *
 * 同一性は fqn + moduleName で判定する:
 * - マルチモジュール環境では同じFQNが複数モジュールに存在しうるため
 * - nodeType / isTest は同一性に含めない。グラフ検索はFQNキーで
 *   種別非依存に行う必要があるため (forLookup 参照)、これらはメタデータ扱い
 *
 * @property fqn callable の完全修飾名
 * @property moduleName callable が属するモジュール名
 * @property isTest @Testアノテーションが付与されているか (関数のみ意味を持つ)
 * @property nodeType ノード種別 (関数 / プロパティ)
 */
data class CallableNode(
    val fqn: CallableFqn,
    val moduleName: ModuleName,
    val isTest: Boolean,
    val nodeType: NodeType = NodeType.FUNCTION
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallableNode) return false
        return fqn == other.fqn && moduleName == other.moduleName
    }

    override fun hashCode(): Int = Objects.hash(fqn, moduleName)

    companion object {
        /**
         * 検索用のダミーノードを作成
         * isTest / nodeType の値は検索には影響しない
         */
        fun forLookup(
            fqn: CallableFqn,
            moduleName: ModuleName,
            nodeType: NodeType = NodeType.FUNCTION
        ): CallableNode =
            CallableNode(fqn, moduleName, isTest = false, nodeType = nodeType)
    }
}

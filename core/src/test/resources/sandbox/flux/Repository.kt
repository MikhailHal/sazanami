package io.github.mikhailhal.sazanami.flux

/**
 * 各ハンドラが呼ぶ末端の処理
 * 検出経路ごとに関数を分離している
 */
class Repository {
    // initブロックで購読する Store 経由
    fun refreshData(): String = "refreshed"

    // 外部から start() で購読する Store 経由
    fun startedRefresh(): String = "started-refresh"

    // マップ駆動ディスパッチの Store 経由
    fun mappedRefresh(): String = "mapped-refresh"

    // 外部 (DI/ライフサイクル) が購読開始する Store 経由
    fun externalRefresh(): String = "external-refresh"
}

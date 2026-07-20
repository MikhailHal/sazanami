package io.github.mikhailhal.sazanami.flux

/**
 * 配線パターン3: ハンドラをマップに登録するディスパッチ
 *
 * ハンドラはメソッド参照としてマップに格納される (#37 でエッジになる)。
 * ただしマップから取り出して呼ぶ側は動的なので、
 * 「登録」の経路が追えるかどうかが検出可否を決める。
 */
class MapWiredStore(private val repository: Repository) {
    var state: String = ""

    private val handlers: Map<String, () -> Unit> = mapOf(
        "refresh" to ::handleMappedRefresh
    )

    fun handle(key: String) {
        handlers[key]?.invoke()
    }

    private fun handleMappedRefresh() {
        state = repository.mappedRefresh()
    }
}

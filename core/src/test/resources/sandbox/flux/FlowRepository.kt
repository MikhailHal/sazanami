package io.github.mikhailhal.sazanami.flux

/**
 * stateIn/shareIn 経路の検出を測るための末端処理
 * 経路ごとに関数を分離している
 */
class FlowRepository {
    // map ラムダ経由 (stateIn)
    fun observe(): FlowLike<String> = FlowLike("data")

    // onEach ラムダ経由 (shareIn)
    fun observeEvents(): FlowLike<String> = FlowLike("event")

    fun onEventHandled(): String = "handled"

    // flatMapLatest のネストしたラムダ経由
    fun observeTrigger(): FlowLike<String> = FlowLike("trigger")

    fun observeNested(): FlowLike<String> = FlowLike("nested")

    // combine のラムダ経由
    fun merge(a: String, b: String): String = a + b
}

package io.github.mikhailhal.sazanami.flux

import kotlin.test.Test

/**
 * 各 Store を dispatch 経由で駆動するテスト
 * 実際の Flux/MVI のテストと同じく、テストは dispatch しか呼ばない
 */
class StoreTest {

    @Test
    fun testInitWired() {
        val dispatcher = Dispatcher()
        InitWiredStore(dispatcher, Repository())
        dispatcher.dispatch(Action.Refresh)
    }

    @Test
    fun testStartWired() {
        val dispatcher = Dispatcher()
        val store = StartWiredStore(dispatcher, Repository())
        store.start()
        dispatcher.dispatch(Action.Refresh)
    }

    @Test
    fun testMapWired() {
        val store = MapWiredStore(Repository())
        store.handle("refresh")
    }

    @Test
    fun testStateFlowUiState() {
        val store = StateFlowStore(FlowRepository())
        store.uiState
    }

    @Test
    fun testStateFlowEvents() {
        val store = StateFlowStore(FlowRepository())
        store.events
    }

    @Test
    fun testStateFlowNested() {
        val store = StateFlowStore(FlowRepository())
        store.nested
    }

    @Test
    fun testStateFlowCombined() {
        val store = StateFlowStore(FlowRepository())
        store.combined
    }

    @Test
    fun testExternallyStarted() {
        // 実アプリと同じく、購読開始は DI/ライフサイクル側の責務なので
        // テストは構築して dispatch するだけ
        val dispatcher = Dispatcher()
        ExternallyStartedStore(dispatcher, Repository())
        dispatcher.dispatch(Action.Refresh)
    }
}

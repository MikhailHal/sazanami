package io.github.mikhailhal.sazanami.flux

/**
 * SharedFlow を模した最小のディスパッチャ (ライブラリ非依存)
 *
 * dispatch でアクションを投入し、collect で購読する。
 * 投入と受信は実行時のデータフローで繋がるため、
 * 静的なコールグラフには「呼び出し」として現れない。
 */
class Dispatcher {
    private val subscribers = mutableListOf<(Action) -> Unit>()

    fun collect(subscriber: (Action) -> Unit) {
        subscribers.add(subscriber)
    }

    fun dispatch(action: Action) {
        subscribers.forEach { it(action) }
    }
}

sealed interface Action {
    data object Refresh : Action
    data object Load : Action
}

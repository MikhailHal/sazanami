package io.github.mikhailhal.sazanami.flux

/**
 * Flow / StateFlow を模した最小の型 (ライブラリ非依存)
 *
 * stateIn / shareIn / map / onEach / flatMapLatest / combine を
 * 実際の kotlinx.coroutines と同じ形で書けるようにするためのスタブ。
 */
class FlowLike<T>(private val value: T) {
    fun <R> map(transform: (T) -> R): FlowLike<R> = FlowLike(transform(value))

    fun onEach(action: (T) -> Unit): FlowLike<T> {
        action(value)
        return this
    }

    fun <R> flatMapLatest(transform: (T) -> FlowLike<R>): FlowLike<R> = transform(value)

    fun stateIn(scope: Any): FlowLike<T> = this

    fun shareIn(scope: Any): FlowLike<T> = this

    fun get(): T = value
}

fun <A, B, R> combineLike(a: FlowLike<A>, b: FlowLike<B>, transform: (A, B) -> R): FlowLike<R> =
    FlowLike(transform(a.get(), b.get()))

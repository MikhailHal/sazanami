package io.github.mikhailhal.sazanami.flux

/**
 * stateIn / shareIn を使う ViewModel/Store の形
 *
 * Flux の dispatch とは異なり、プロパティ初期化子のチェーン内で
 * プロジェクト関数を呼ぶ。検出経路は「初期化子内の呼び出しがプロパティに帰属するか」で決まる。
 */
class StateFlowStore(private val repository: FlowRepository) {

    // stateIn: チェーン内の map ラムダでプロジェクト関数を呼ぶ
    val uiState: FlowLike<String> = repository.observe()
        .map { it.toUiModel() }
        .stateIn(this)

    // shareIn: onEach で副作用ハンドラを呼ぶ (Flux と state flow の中間形)
    val events: FlowLike<String> = repository.observeEvents()
        .onEach { handleEvent() }
        .shareIn(this)

    // flatMapLatest: ネストしたラムダ内でプロジェクト関数を呼ぶ
    val nested: FlowLike<String> = repository.observeTrigger()
        .flatMapLatest { repository.observeNested() }
        .stateIn(this)

    // combine: 複数ソースを束ねるラムダ内でプロジェクト関数を呼ぶ
    val combined: FlowLike<String> = combineLike(
        repository.observe(),
        repository.observeEvents()
    ) { a, b -> mergeSources(a, b) }
        .stateIn(this)

    private fun handleEvent() {
        repository.onEventHandled()
    }

    private fun mergeSources(a: String, b: String): String = repository.merge(a, b)
}

private fun String.toUiModel(): String = "ui:$this"

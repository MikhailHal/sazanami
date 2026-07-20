package io.github.mikhailhal.sazanami.flux

/**
 * 配線パターン2: 外部から start() を呼んで購読する Store
 *
 * collect のラムダは start() に帰属する。
 * テストが start() を呼んでいれば handleRefresh ← start ← テスト が繋がるが、
 * DI コンテナやライフサイクルが start() を呼ぶ構成ではテストからの経路が消える。
 */
class StartWiredStore(private val dispatcher: Dispatcher, private val repository: Repository) {
    var state: String = ""

    fun start() {
        dispatcher.collect { action ->
            when (action) {
                is Action.Refresh -> handleStartedRefresh()
                is Action.Load -> Unit
            }
        }
    }

    private fun handleStartedRefresh() {
        state = repository.startedRefresh()
    }
}

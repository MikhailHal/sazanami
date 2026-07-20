package io.github.mikhailhal.sazanami.flux

/**
 * 配線パターン4: 購読の開始を外部 (DI コンテナ / ライフサイクル) が担う Store
 *
 * テストは Store を構築して dispatch するだけで、start() を呼ばない。
 * start() を呼ぶのはプロダクションコード側の AppInitializer であり、
 * テストからの呼び出し経路が存在しないため、
 * handleExternalRefresh への到達経路は静的には存在しない。
 */
class ExternallyStartedStore(private val dispatcher: Dispatcher, private val repository: Repository) {
    var state: String = ""

    fun start() {
        dispatcher.collect { action ->
            when (action) {
                is Action.Refresh -> handleExternalRefresh()
                is Action.Load -> Unit
            }
        }
    }

    private fun handleExternalRefresh() {
        state = repository.externalRefresh()
    }
}

/**
 * DI コンテナやライフサイクルを模した起動役
 * (テストからは呼ばれない)
 */
class AppInitializer(private val store: ExternallyStartedStore) {
    fun initialize() {
        store.start()
    }
}

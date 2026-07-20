package io.github.mikhailhal.sazanami.flux

/**
 * 配線パターン1: init ブロック内で collect する Store
 *
 * dispatch → collect はデータフローなのでエッジにならないが、
 * collect のラムダは init に帰属するため
 * handleRefresh ← <init> ← Store を構築するテスト の経路が繋がるはず (#28 の構築セマンティクス)
 */
class InitWiredStore(dispatcher: Dispatcher, private val repository: Repository) {
    var state: String = ""

    init {
        dispatcher.collect { action ->
            when (action) {
                is Action.Refresh -> handleRefresh()
                is Action.Load -> Unit
            }
        }
    }

    private fun handleRefresh() {
        state = repository.refreshData()
    }
}

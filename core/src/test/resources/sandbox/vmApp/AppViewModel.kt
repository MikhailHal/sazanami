package io.github.mikhailhal.sazanami.vmapp

import io.github.mikhailhal.sazanami.vmcore.Repository

/**
 * Android の ViewModel イディオムを模したクラス
 * 呼び出しが関数本体の外側 (プロパティ初期化子・デリゲート・格納ラムダ・init) に
 * 置かれるパターンを網羅する
 */
class AppViewModel(private val repository: Repository) {
    // プロパティ初期化子内の呼び出し (Android の uiState イディオム)
    val uiState: String = buildUiState(repository)

    // by lazy デリゲート内の呼び出し
    val config: String by lazy { repository.loadConfig() }

    // プロパティに格納されたラムダ内の呼び出し
    val handler: () -> Int = { repository.processEvent() }

    // init ブロック内の呼び出し
    var warm: Int = 0
    init {
        warm = repository.warmUp()
    }

    // 通常の関数本体内の呼び出し (現行仕様で検出可能なコントロール)
    fun refresh(): String = repository.reload()

    // stateIn/shareIn イディオムを模したチェーン付きプロパティ初期化子
    val stream: String = buildStream(repository).stateInLike()
}

private fun buildUiState(repository: Repository): String = repository.load()

private fun buildStream(repository: Repository): String = repository.loadStream()

// stateIn/shareIn 風のチェーンを作るためのライブラリ非依存な拡張
private fun <T> T.stateInLike(): T = this

package io.github.mikhailhal.sazanami.vmcore

/**
 * vmCore モジュールのリポジトリ
 * vmApp モジュールの AppViewModel から様々な経路で呼び出される
 *
 * 各関数は検出経路ごとに分離されている:
 * - load: プロパティ初期化子経由 (#27)
 * - loadConfig: by lazy デリゲート経由 (#28)
 * - processEvent: 格納ラムダ経由 (#28)
 * - warmUp: init ブロック経由 (#28)
 * - reload: 通常の関数呼び出し経由 (現行仕様で検出可能なコントロール)
 */
class Repository {
    fun load(): String = "data"
    fun loadConfig(): String = "config"
    fun processEvent(): Int = 1
    fun warmUp(): Int = 0
    fun reload(): String = "reloaded"
    // loadStream: チェーン付きプロパティ初期化子経由 (#27)
    fun loadStream(): String = "stream"
    // loadTitle: カスタムgetter経由 (#27)
    fun loadTitle(): String = "title"
}

package io.github.mikhailhal.sazanami.modulea

/**
 * モジュールAのコアサービス
 * モジュールBから呼び出される
 */
class CoreService {
    fun process(input: String): String = "processed: $input"
}

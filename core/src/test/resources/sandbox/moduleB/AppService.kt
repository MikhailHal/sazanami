package io.github.mikhailhal.sazanami.moduleb

import io.github.mikhailhal.sazanami.modulea.CoreService

/**
 * モジュールBのアプリサービス
 * モジュールAのCoreServiceを使用
 */
class AppService {
    private val coreService = CoreService()

    fun execute(input: String): String {
        return coreService.process(input)
    }
}

package io.github.mikhailhal.sonarkt.moduleb

import io.github.mikhailhal.sonarkt.modulea.CoreService

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

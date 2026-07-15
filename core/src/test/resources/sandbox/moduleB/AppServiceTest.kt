package io.github.mikhailhal.sazanami.moduleb

import kotlin.test.Test

/**
 * AppService のテスト
 * CoreService.process を間接的に呼び出す
 */
class AppServiceTest {
    @Test
    fun testExecute() {
        val appService = AppService()
        appService.execute("test")
    }
}

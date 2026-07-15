package io.github.mikhailhal.sazanami.vmapp

import io.github.mikhailhal.sazanami.vmcore.Repository
import kotlin.test.Test

/**
 * AppViewModel のテスト
 * 各テストは AppViewModel の異なる経路を通って vmCore の Repository に到達する
 */
class AppViewModelTest {
    @Test
    fun testUiState() {
        val viewModel = AppViewModel(Repository())
        viewModel.uiState
    }

    @Test
    fun testConfig() {
        val viewModel = AppViewModel(Repository())
        viewModel.config
    }

    @Test
    fun testHandler() {
        val viewModel = AppViewModel(Repository())
        viewModel.handler()
    }

    @Test
    fun testWarm() {
        AppViewModel(Repository())
    }

    @Test
    fun testRefresh() {
        val viewModel = AppViewModel(Repository())
        viewModel.refresh()
    }

    @Test
    fun testStream() {
        val viewModel = AppViewModel(Repository())
        viewModel.stream
    }
}

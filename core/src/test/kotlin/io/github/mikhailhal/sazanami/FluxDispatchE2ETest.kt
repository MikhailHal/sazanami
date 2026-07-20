package io.github.mikhailhal.sazanami

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sazanami.collector.ChangedFunctionCollector
import io.github.mikhailhal.sazanami.common.ModuleName
import io.github.mikhailhal.sazanami.emitter.AffectedTestEmitter
import io.github.mikhailhal.sazanami.processor.AffectedTestResolver
import io.github.mikhailhal.sazanami.processor.CallGraphBuilder
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UDF (Flux/MVI) のディスパッチ配線に対する検出可否を記録するテスト (#38)
 *
 * dispatch → collect はデータフローであり静的な呼び出しではないため、
 * ハンドラへの経路は「collect のラムダがどこに帰属するか」だけで決まる。
 * 各配線パターンの実際の挙動を、期待値としてここに固定する。
 */
class FluxDispatchE2ETest {

    private val fluxModule: ModuleName = "flux"
    private val pathMapping = mapOf(fluxModule to "src/test/resources/sandbox/flux")
    private val projectRoot = Paths.get("").toAbsolutePath()

    @Test
    fun `init-wired store is covered via constructor semantics`() {
        // collect のラムダが init に帰属するため、
        // handleRefresh ← <init> ← Store を構築する全テスト の経路が繋がる
        withFluxFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 9,
                before = "    fun refreshData(): String = \"refreshed\"",
                after = "    fun refreshData(): String = \"refreshed\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.flux.StoreTest.testInitWired", output)
        }
    }

    @Test
    fun `start-wired store is covered when the test calls start`() {
        // collect のラムダは start() に帰属する。
        // テストが start() を呼んでいるため経路が繋がる
        // (DI/ライフサイクルが start() を呼ぶ構成では繋がらない)
        withFluxFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 12,
                before = "    fun startedRefresh(): String = \"started-refresh\"",
                after = "    fun startedRefresh(): String = \"started-refresh\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.flux.StoreTest.testStartWired", output)
        }
    }

    @Test
    fun `map-wired store is covered via the handler reference in the property initializer`() {
        // ハンドラはメソッド参照でマップに登録される (#37)。
        // handleMappedRefresh ← handlers プロパティ ← <init> ← 構築するテスト で繋がる
        withFluxFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 15,
                before = "    fun mappedRefresh(): String = \"mapped-refresh\"",
                after = "    fun mappedRefresh(): String = \"mapped-refresh\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.flux.StoreTest.testMapWired", output)
        }
    }

    // === stateIn / shareIn 系 (Flux の dispatch より実務で多い形) ===

    @Test
    fun `stateIn chain with map lambda is covered`() {
        // val uiState = repository.observe().map { it.toUiModel() }.stateIn(...)
        withFluxFixture { moduleFiles ->
            val output = runPipeline(flowRepositoryDiff(9, "    fun observe(): FlowLike<String> = FlowLike(\"data\")"), moduleFiles)

            // uiState と combined の2つが observe() を使っている
            assertEquals(
                """
                io.github.mikhailhal.sazanami.flux.StoreTest.testStateFlowCombined
                io.github.mikhailhal.sazanami.flux.StoreTest.testStateFlowUiState
                """.trimIndent(),
                output
            )
        }
    }

    @Test
    fun `shareIn chain with onEach handler is covered`() {
        // val events = repository.observeEvents().onEach { handleEvent() }.shareIn(...)
        withFluxFixture { moduleFiles ->
            val output = runPipeline(flowRepositoryDiff(14, "    fun onEventHandled(): String = \"handled\""), moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.flux.StoreTest.testStateFlowEvents", output)
        }
    }

    @Test
    fun `flatMapLatest nested lambda is covered`() {
        // val nested = repository.observeTrigger().flatMapLatest { repository.observeNested() }.stateIn(...)
        withFluxFixture { moduleFiles ->
            val output = runPipeline(flowRepositoryDiff(19, "    fun observeNested(): FlowLike<String> = FlowLike(\"nested\")"), moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.flux.StoreTest.testStateFlowNested", output)
        }
    }

    @Test
    fun `combine lambda is covered`() {
        // val combined = combineLike(a, b) { a, b -> mergeSources(a, b) }.stateIn(...)
        withFluxFixture { moduleFiles ->
            val output = runPipeline(flowRepositoryDiff(22, "    fun merge(a: String, b: String): String = a + b"), moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.flux.StoreTest.testStateFlowCombined", output)
        }
    }

    @Test
    fun `externally started store is NOT covered - known limitation`() {
        // 購読開始が DI/ライフサイクル側にある構成では、テストから start() への
        // 呼び出し経路が存在しないため、ハンドラの変更を検出できない。
        // 実アプリで最も起こりやすい取りこぼしパターンとして固定する (#38)
        withFluxFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 18,
                before = "    fun externalRefresh(): String = \"external-refresh\"",
                after = "    fun externalRefresh(): String = \"external-refresh\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("", output)
        }
    }

    // === Helper functions ===

    private fun flowRepositoryDiff(line: Int, text: String): String = """
        diff --git a/src/test/resources/sandbox/flux/FlowRepository.kt b/src/test/resources/sandbox/flux/FlowRepository.kt
        --- a/src/test/resources/sandbox/flux/FlowRepository.kt
        +++ b/src/test/resources/sandbox/flux/FlowRepository.kt
        @@ -$line +$line @@
        -$text
        +$text // modified
    """.trimIndent()

    private fun repositoryDiff(line: Int, before: String, after: String): String = """
        diff --git a/src/test/resources/sandbox/flux/Repository.kt b/src/test/resources/sandbox/flux/Repository.kt
        --- a/src/test/resources/sandbox/flux/Repository.kt
        +++ b/src/test/resources/sandbox/flux/Repository.kt
        @@ -$line +$line @@
        -$before
        +$after
    """.trimIndent()

    private fun runPipeline(diff: String, moduleFiles: Map<ModuleName, List<KtFile>>): String {
        val allKtFiles = moduleFiles.values.flatten()
        val changedFunctions = ChangedFunctionCollector().collect(diff, allKtFiles, pathMapping, projectRoot)

        val graph = CallGraphBuilder().build(moduleFiles)
        val affectedTests = AffectedTestResolver(graph).findAffectedTests(changedFunctions)

        return AffectedTestEmitter.emit(affectedTests)
    }

    private fun withFluxFixture(block: (Map<ModuleName, List<KtFile>>) -> Unit) {
        val projectDisposable = Disposer.newDisposable("FluxDispatchE2ETest")

        try {
            val session = buildStandaloneAnalysisAPISession(projectDisposable) {
                buildKtModuleProvider {
                    platform = JvmPlatforms.defaultJvmPlatform

                    addModule(buildKtSourceModule {
                        moduleName = fluxModule
                        this.platform = JvmPlatforms.defaultJvmPlatform
                        addSourceRoot(Paths.get("src/test/resources/sandbox/flux"))
                    })
                }
            }

            val moduleFiles = session.modulesWithFiles
                .mapKeys { (kaModule, _) -> kaModule.name }
                .mapValues { (_, files) -> files.filterIsInstance<KtFile>() }

            block(moduleFiles)
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }
}

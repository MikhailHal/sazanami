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
import kotlin.test.assertTrue

/**
 * Android の ViewModel イディオムを模した E2E テスト (#30)
 *
 * 呼び出しが関数本体の外側に置かれるパターンの検出を検証する:
 * - プロパティ初期化子 (#27)
 * - by lazy デリゲート / 格納ラムダ / init ブロック (#28)
 *
 * fixture 構成:
 *   vmApp (vmCore に依存)
 *     AppViewModel: val uiState = buildUiState(repository) などのイディオム
 *     AppViewModelTest: 各経路を通るテスト
 *   vmCore
 *     Repository: 変更対象の関数群 (経路ごとに分離)
 */
class ViewModelIdiomE2ETest {

    private val vmCore: ModuleName = "vmCore"
    private val vmApp: ModuleName = "vmApp"
    private val pathMapping = mapOf(
        vmCore to "src/test/resources/sandbox/vmCore",
        vmApp to "src/test/resources/sandbox/vmApp"
    )
    private val projectRoot = Paths.get("").toAbsolutePath()

    @Test
    fun `changing reload detects testRefresh via ordinary function call`() {
        // コントロール: 関数本体内の呼び出し連鎖は現行仕様で検出できる
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 19,
                before = "    fun reload(): String = \"reloaded\"",
                after = "    fun reload(): String = \"reloaded\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testRefresh", output)
        }
    }

    @Test
    fun `changing load detects testUiState via property initializer`() {
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 15,
                before = "    fun load(): String = \"data\"",
                after = "    fun load(): String = \"data\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testUiState", output)
        }
    }

    @Test
    fun `changing loadStream detects testStream via chained property initializer`() {
        // stateIn/shareIn イディオムの形: val stream = buildStream(repository).stateInLike()
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 21,
                before = "    fun loadStream(): String = \"stream\"",
                after = "    fun loadStream(): String = \"stream\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testStream", output)
        }
    }

    @Test
    fun `changing loadConfig detects testConfig via lazy delegate`() {
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 16,
                before = "    fun loadConfig(): String = \"config\"",
                after = "    fun loadConfig(): String = \"config\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testConfig", output)
        }
    }

    @Test
    fun `changing processEvent detects testHandler via stored lambda`() {
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 17,
                before = "    fun processEvent(): Int = 1",
                after = "    fun processEvent(): Int = 1 // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testHandler", output)
        }
    }

    @Test
    fun `changing loadTitle detects testTitle via custom getter`() {
        // カスタム getter 本体はアクセサ(KtPropertyAccessor)の中だが、
        // PSI走査でその親のプロパティ(title)に帰属することを担保する
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 23,
                before = "    fun loadTitle(): String = \"title\"",
                after = "    fun loadTitle(): String = \"title\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testTitle", output)
        }
    }

    @Test
    fun `changing warmUp detects all constructing tests via init block`() {
        // init ブロックはインスタンス構築時に必ず実行されるため、
        // 影響集合は「AppViewModel を構築する全テスト」になる (#28)
        // warmUp ← <init>(initブロック帰属) ← AppViewModel(...) を呼ぶ全テスト
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 18,
                before = "    fun warmUp(): Int = 0",
                after = "    fun warmUp(): Int = 0 // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals(
                """
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testConfig
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testHandler
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testMapAll
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testRefLoader
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testRefresh
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testStream
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testTitle
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testUiState
                io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testWarm
                """.trimIndent(),
                output
            )
        }
    }

    @Test
    fun `changing loadRef detects testRefLoader via stored callable reference`() {
        // val refLoader: () -> String = repository::loadRef (#37)
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 25,
                before = "    fun loadRef(): String = \"ref\"",
                after = "    fun loadRef(): String = \"ref\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testRefLoader", output)
        }
    }

    @Test
    fun `changing transform detects testMapAll via argument-position callable reference`() {
        // fun mapAll() = listOf("a", "b").map(repository::transform) (#37)
        withViewModelFixture { moduleFiles ->
            val diff = repositoryDiff(
                line = 27,
                before = "    fun transform(input: String): String = input + \"!\"",
                after = "    fun transform(input: String): String = input + \"!\" // modified"
            )

            val output = runPipeline(diff, moduleFiles)

            assertEquals("io.github.mikhailhal.sazanami.vmapp.AppViewModelTest.testMapAll", output)
        }
    }

    @Test
    fun `constructor and property references produce call-graph edges`() {
        // ::Repository と repository::label はcollectorで駆動できない
        // (コンストラクタ/プロパティ自体の変更検出は別問題) ため、エッジの存在を直接検証する (#37)
        withViewModelFixture { moduleFiles ->
            val graph = CallGraphBuilder().build(moduleFiles)

            val initCallers = graph.getCallersByFqn("io.github.mikhailhal.sazanami.vmcore.Repository.<init>")
            assertTrue(
                initCallers.any { it.fqn == "io.github.mikhailhal.sazanami.vmapp.AppViewModel.factory" },
                "factory プロパティの ::Repository 参照がエッジになっていない: $initCallers"
            )

            val labelCallers = graph.getCallersByFqn("io.github.mikhailhal.sazanami.vmcore.Repository.label")
            assertTrue(
                labelCallers.any { it.fqn == "io.github.mikhailhal.sazanami.vmapp.AppViewModel.labelRef" },
                "labelRef プロパティの repository::label 参照がエッジになっていない: $labelCallers"
            )
        }
    }

    // === Helper functions ===

    /**
     * Repository.kt の指定行を変更する diff を生成
     */
    private fun repositoryDiff(line: Int, before: String, after: String): String = """
        diff --git a/src/test/resources/sandbox/vmCore/Repository.kt b/src/test/resources/sandbox/vmCore/Repository.kt
        --- a/src/test/resources/sandbox/vmCore/Repository.kt
        +++ b/src/test/resources/sandbox/vmCore/Repository.kt
        @@ -$line +$line @@
        -$before
        +$after
    """.trimIndent()

    /**
     * 全パイプラインを実行
     */
    private fun runPipeline(diff: String, moduleFiles: Map<ModuleName, List<KtFile>>): String {
        val allKtFiles = moduleFiles.values.flatten()
        val changedFunctions = ChangedFunctionCollector().collect(diff, allKtFiles, pathMapping, projectRoot)

        val graph = CallGraphBuilder().build(moduleFiles)
        val affectedTests = AffectedTestResolver(graph).findAffectedTests(changedFunctions)

        return AffectedTestEmitter.emit(affectedTests)
    }

    /**
     * vmCore / vmApp の2モジュール構成で Analysis API セッションを構築
     * vmApp が vmCore に依存する
     */
    private fun withViewModelFixture(block: (Map<ModuleName, List<KtFile>>) -> Unit) {
        val projectDisposable = Disposer.newDisposable("ViewModelIdiomE2ETest")

        try {
            val session = buildStandaloneAnalysisAPISession(projectDisposable) {
                buildKtModuleProvider {
                    platform = JvmPlatforms.defaultJvmPlatform

                    val core = addModule(buildKtSourceModule {
                        moduleName = vmCore
                        this.platform = JvmPlatforms.defaultJvmPlatform
                        addSourceRoot(Paths.get("src/test/resources/sandbox/vmCore"))
                    })

                    addModule(buildKtSourceModule {
                        moduleName = vmApp
                        this.platform = JvmPlatforms.defaultJvmPlatform
                        addSourceRoot(Paths.get("src/test/resources/sandbox/vmApp"))
                        addRegularDependency(core)
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

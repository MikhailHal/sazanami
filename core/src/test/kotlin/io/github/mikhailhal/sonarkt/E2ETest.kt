package io.github.mikhailhal.sonarkt

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sonarkt.collector.ChangedFunctionCollector
import io.github.mikhailhal.sonarkt.common.ModuleName
import io.github.mikhailhal.sonarkt.emitter.AffectedTestEmitter
import io.github.mikhailhal.sonarkt.processor.AffectedTestResolver
import io.github.mikhailhal.sonarkt.processor.GraphBuilder
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-End テスト
 *
 * 全パイプラインを通して、git diffから影響テスト一覧が
 * 正しく出力されることを検証
 *
 * Pipeline:
 *   git diff → ChangedFunctionCollector → GraphBuilder
 *            → AffectedTestResolver → AffectedTestEmitter → output
 */
class E2ETest {

    // テスト用のモジュール名とマッピング
    private val testModule: ModuleName = "test"
    private val modulePathMapping = mapOf(testModule to "src/test/resources/sandbox")
    private val projectRoot = Paths.get("").toAbsolutePath()

    @Test
    fun `changing Calculator_add detects testAdd and testHelper`() {
        withSandboxFiles { ktFiles ->
            // Calculator.add を変更したdiff
            val diff = """
                diff --git a/src/test/resources/sandbox/Calculator.kt b/src/test/resources/sandbox/Calculator.kt
                --- a/src/test/resources/sandbox/Calculator.kt
                +++ b/src/test/resources/sandbox/Calculator.kt
                @@ -4 +4 @@
                -    fun add(a: Int, b: Int): Int = a + b
                +    fun add(a: Int, b: Int): Int = a + b // modified
            """.trimIndent()

            val output = runPipeline(diff, ktFiles)

            // testAdd: Calculator.add を直接呼ぶ
            // testHelper: helperB → Calculator.add の間接呼び出し
            assertEquals(
                """
                io.github.mikhailhal.sonarkt.CalculatorTest.testAdd
                io.github.mikhailhal.sonarkt.CalculatorTest.testHelper
                """.trimIndent(),
                output
            )
        }
    }

    @Test
    fun `changing helperB detects only testHelper`() {
        withSandboxFiles { ktFiles ->
            // helperB を変更したdiff
            val diff = """
                diff --git a/src/test/resources/sandbox/Helper.kt b/src/test/resources/sandbox/Helper.kt
                --- a/src/test/resources/sandbox/Helper.kt
                +++ b/src/test/resources/sandbox/Helper.kt
                @@ -8 +8 @@
                -    val calc = Calculator()
                +    val calc = Calculator() // modified
            """.trimIndent()

            val output = runPipeline(diff, ktFiles)

            // testHelper のみが helperB を呼ぶ
            assertEquals("io.github.mikhailhal.sonarkt.CalculatorTest.testHelper", output)
        }
    }

    @Test
    fun `changing Calculator_multiply detects nothing`() {
        withSandboxFiles { ktFiles ->
            // Calculator.multiply を変更（誰からも呼ばれていない）
            val diff = """
                diff --git a/src/test/resources/sandbox/Calculator.kt b/src/test/resources/sandbox/Calculator.kt
                --- a/src/test/resources/sandbox/Calculator.kt
                +++ b/src/test/resources/sandbox/Calculator.kt
                @@ -5 +5 @@
                -    fun multiply(a: Int, b: Int): Int = a * b
                +    fun multiply(a: Int, b: Int): Int = a * b // modified
            """.trimIndent()

            val output = runPipeline(diff, ktFiles)

            // multiply は誰からも呼ばれていないので影響テストなし
            assertTrue(output.isEmpty())
        }
    }

    @Test
    fun `empty diff returns empty output`() {
        withSandboxFiles { ktFiles ->
            val output = runPipeline("", ktFiles)
            assertTrue(output.isEmpty())
        }
    }

    @Test
    fun `PR diff format (origin-main---HEAD) works the same`() {
        withSandboxFiles { ktFiles ->
            // origin/main...HEAD 形式でも diff フォーマットは同じ
            // 変わるのは含まれるコミット範囲だけ
            val diff = """
                diff --git a/src/test/resources/sandbox/Calculator.kt b/src/test/resources/sandbox/Calculator.kt
                --- a/src/test/resources/sandbox/Calculator.kt
                +++ b/src/test/resources/sandbox/Calculator.kt
                @@ -4 +4 @@
                -    fun add(a: Int, b: Int): Int = a + b
                +    fun add(a: Int, b: Int): Int = a + b // PR change
            """.trimIndent()

            val output = runPipeline(diff, ktFiles)

            assertEquals(
                """
                io.github.mikhailhal.sonarkt.CalculatorTest.testAdd
                io.github.mikhailhal.sonarkt.CalculatorTest.testHelper
                """.trimIndent(),
                output
            )
        }
    }

    @Test
    fun `multiple file changes in single PR diff`() {
        withSandboxFiles { ktFiles ->
            // PR で複数ファイルを変更した場合
            val diff = """
                diff --git a/src/test/resources/sandbox/Calculator.kt b/src/test/resources/sandbox/Calculator.kt
                --- a/src/test/resources/sandbox/Calculator.kt
                +++ b/src/test/resources/sandbox/Calculator.kt
                @@ -4 +4 @@
                -    fun add(a: Int, b: Int): Int = a + b
                +    fun add(a: Int, b: Int): Int = a + b // change 1
                diff --git a/src/test/resources/sandbox/Helper.kt b/src/test/resources/sandbox/Helper.kt
                --- a/src/test/resources/sandbox/Helper.kt
                +++ b/src/test/resources/sandbox/Helper.kt
                @@ -8 +8 @@
                -    val calc = Calculator()
                +    val calc = Calculator() // change 2
            """.trimIndent()

            val output = runPipeline(diff, ktFiles)

            // 両方の変更による影響テスト
            assertEquals(
                """
                io.github.mikhailhal.sonarkt.CalculatorTest.testAdd
                io.github.mikhailhal.sonarkt.CalculatorTest.testHelper
                """.trimIndent(),
                output
            )
        }
    }

    // === Cross-module support tests ===

    @Test
    fun `changing function in moduleA detects test in moduleB that calls it`() {
        withCrossModuleFiles { moduleFiles, pathMapping ->
            // moduleA の CoreService.process を変更
            val diff = """
                diff --git a/src/test/resources/sandbox/moduleA/CoreService.kt b/src/test/resources/sandbox/moduleA/CoreService.kt
                --- a/src/test/resources/sandbox/moduleA/CoreService.kt
                +++ b/src/test/resources/sandbox/moduleA/CoreService.kt
                @@ -8 +8 @@
                -    fun process(input: String): String = "processed: ${'$'}input"
                +    fun process(input: String): String = "processed: ${'$'}input" // modified
            """.trimIndent()

            val output = runCrossModulePipeline(diff, moduleFiles, pathMapping)

            // moduleB の AppServiceTest.testExecute が影響を受ける
            // AppServiceTest → AppService.execute → CoreService.process
            assertEquals("io.github.mikhailhal.sonarkt.moduleb.AppServiceTest.testExecute", output)
        }
    }

    // === Interface support tests ===

    @Test
    fun `changing implementation method detects tests calling interface method`() {
        withSandboxFiles { ktFiles ->
            // CalculatorImpl.compute を変更したdiff
            // テストは ICalculator.compute 経由で呼び出している
            val diff = """
                diff --git a/src/test/resources/sandbox/CalculatorImpl.kt b/src/test/resources/sandbox/CalculatorImpl.kt
                --- a/src/test/resources/sandbox/CalculatorImpl.kt
                +++ b/src/test/resources/sandbox/CalculatorImpl.kt
                @@ -8 +8 @@
                -    override fun compute(a: Int, b: Int): Int = a + b
                +    override fun compute(a: Int, b: Int): Int = a + b // modified
            """.trimIndent()

            val output = runPipeline(diff, ktFiles)

            // CalculatorImpl.compute の変更で、ICalculator.compute 経由のテストが検出される
            assertEquals("io.github.mikhailhal.sonarkt.InterfaceTest.testCompute", output)
        }
    }

    @Test
    fun `changing interface method itself detects calling tests`() {
        withSandboxFiles { ktFiles ->
            // ICalculator.compute を直接変更したdiff（署名の変更など）
            val diff = """
                diff --git a/src/test/resources/sandbox/ICalculator.kt b/src/test/resources/sandbox/ICalculator.kt
                --- a/src/test/resources/sandbox/ICalculator.kt
                +++ b/src/test/resources/sandbox/ICalculator.kt
                @@ -8 +8 @@
                -    fun compute(a: Int, b: Int): Int
                +    fun compute(a: Int, b: Int): Int // modified signature comment
            """.trimIndent()

            val output = runPipeline(diff, ktFiles)

            // ICalculator.compute を直接変更した場合もテストが検出される
            assertEquals("io.github.mikhailhal.sonarkt.InterfaceTest.testCompute", output)
        }
    }

    // === Helper functions ===

    /**
     * 全パイプラインを実行
     */
    private fun runPipeline(diff: String, moduleFiles: Map<ModuleName, List<KtFile>>): String {
        // 1. 変更された関数を収集
        val allKtFiles = moduleFiles.values.flatten()
        val changedFunctions = ChangedFunctionCollector().collect(diff, allKtFiles, modulePathMapping, projectRoot)

        // 2. 依存グラフを構築
        val graph = GraphBuilder().build(moduleFiles)

        // 3. 影響テストを解決
        val affectedTests = AffectedTestResolver(graph).findAffectedTests(changedFunctions)

        // 4. 出力形式に変換
        return AffectedTestEmitter.emit(affectedTests)
    }

    /**
     * sandboxファイルでAnalysis APIセッションを構築して処理を実行
     */
    private fun withSandboxFiles(block: (Map<ModuleName, List<KtFile>>) -> Unit) {
        val projectDisposable = Disposer.newDisposable("E2ETest")

        try {
            val session = buildStandaloneAnalysisAPISession(projectDisposable) {
                buildKtModuleProvider {
                    platform = JvmPlatforms.defaultJvmPlatform

                    addModule(buildKtSourceModule {
                        moduleName = testModule
                        platform = JvmPlatforms.defaultJvmPlatform
                        addSourceRoot(Paths.get("src/test/resources/sandbox"))
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

    // === Cross-module helpers ===

    /**
     * クロスモジュール用パイプラインを実行
     */
    private fun runCrossModulePipeline(
        diff: String,
        moduleFiles: Map<ModuleName, List<KtFile>>,
        pathMapping: Map<ModuleName, String>
    ): String {
        val allKtFiles = moduleFiles.values.flatten()
        val changedFunctions = ChangedFunctionCollector().collect(diff, allKtFiles, pathMapping, projectRoot)

        val graph = GraphBuilder().build(moduleFiles)
        val affectedTests = AffectedTestResolver(graph).findAffectedTests(changedFunctions)

        return AffectedTestEmitter.emit(affectedTests)
    }

    /**
     * クロスモジュールファイルでAnalysis APIセッションを構築
     * moduleB が moduleA に依存する構成
     */
    private fun withCrossModuleFiles(
        block: (Map<ModuleName, List<KtFile>>, Map<ModuleName, String>) -> Unit
    ) {
        val projectDisposable = Disposer.newDisposable("E2ETest-CrossModule")

        val moduleA = "moduleA"
        val moduleB = "moduleB"
        val pathMapping = mapOf(
            moduleA to "src/test/resources/sandbox/moduleA",
            moduleB to "src/test/resources/sandbox/moduleB"
        )

        try {
            val session = buildStandaloneAnalysisAPISession(projectDisposable) {
                buildKtModuleProvider {
                    platform = JvmPlatforms.defaultJvmPlatform

                    // モジュールをマップに保持
                    val builtModules = mutableMapOf<ModuleName, KaModule>()

                    // moduleA を先に構築（依存先）
                    val modA = addModule(buildKtSourceModule {
                        moduleName = moduleA
                        this.platform = JvmPlatforms.defaultJvmPlatform
                        addSourceRoot(Paths.get("src/test/resources/sandbox/moduleA"))
                    })
                    builtModules[moduleA] = modA

                    // moduleB を構築（moduleA に依存）
                    val modB = addModule(buildKtSourceModule {
                        moduleName = moduleB
                        this.platform = JvmPlatforms.defaultJvmPlatform
                        addSourceRoot(Paths.get("src/test/resources/sandbox/moduleB"))
                        addRegularDependency(modA)
                    })
                    builtModules[moduleB] = modB
                }
            }

            val moduleFiles = session.modulesWithFiles
                .mapKeys { (kaModule, _) -> kaModule.name }
                .mapValues { (_, files) -> files.filterIsInstance<KtFile>() }

            block(moduleFiles, pathMapping)
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }
}

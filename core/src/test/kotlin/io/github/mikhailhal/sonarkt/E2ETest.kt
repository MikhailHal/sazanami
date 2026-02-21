package io.github.mikhailhal.sonarkt

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sonarkt.collector.ChangedFunctionCollector
import io.github.mikhailhal.sonarkt.common.ModuleName
import io.github.mikhailhal.sonarkt.emitter.AffectedTestEmitter
import io.github.mikhailhal.sonarkt.processor.AffectedTestResolver
import io.github.mikhailhal.sonarkt.processor.GraphBuilder
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
}

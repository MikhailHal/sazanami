package io.github.mikhailhal.sonarkt.collector

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ChangedFunctionCollector統合テスト
 *
 * sandbox内のファイルを使って、diffから変更された関数を
 * 正しく特定できることを検証
 */
class ChangedFunctionCollectorTest {

    // テスト用のモジュール名とマッピング
    private val testModule = "test"
    private val modulePathMapping = mapOf(testModule to "src/test/resources/sandbox")
    private val projectRoot = Paths.get("").toAbsolutePath()

    // === collect ===

    @Test
    fun `collects changed function when diff overlaps function range`() {
        val projectDisposable = Disposer.newDisposable("ChangedFunctionCollectorTest")

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

            val ktFiles = session.modulesWithFiles
                .flatMap { it.value }
                .filterIsInstance<KtFile>()

            // Calculator.kt の4行目(add関数)を変更したdiff
            val diffOutput = """
                diff --git a/src/test/resources/sandbox/Calculator.kt b/src/test/resources/sandbox/Calculator.kt
                --- a/src/test/resources/sandbox/Calculator.kt
                +++ b/src/test/resources/sandbox/Calculator.kt
                @@ -4 +4 @@
                -    fun add(a: Int, b: Int): Int = a + b
                +    fun add(a: Int, b: Int): Int = a + b // modified
            """.trimIndent()

            val collector = ChangedFunctionCollector()
            val changed = collector.collect(diffOutput, ktFiles, modulePathMapping, projectRoot)

            assertEquals(
                setOf(ChangedFunction("io.github.mikhailhal.sonarkt.Calculator.add", testModule)),
                changed
            )
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    @Test
    fun `collects multiple changed functions`() {
        val projectDisposable = Disposer.newDisposable("ChangedFunctionCollectorTest")

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

            val ktFiles = session.modulesWithFiles
                .flatMap { it.value }
                .filterIsInstance<KtFile>()

            // Calculator.kt の4行目と5行目を変更
            val diffOutput = """
                diff --git a/src/test/resources/sandbox/Calculator.kt b/src/test/resources/sandbox/Calculator.kt
                --- a/src/test/resources/sandbox/Calculator.kt
                +++ b/src/test/resources/sandbox/Calculator.kt
                @@ -4,2 +4,2 @@
                -    fun add(a: Int, b: Int): Int = a + b
                -    fun multiply(a: Int, b: Int): Int = a * b
                +    fun add(a: Int, b: Int): Int = a + b // modified
                +    fun multiply(a: Int, b: Int): Int = a * b // modified
            """.trimIndent()

            val collector = ChangedFunctionCollector()
            val changed = collector.collect(diffOutput, ktFiles, modulePathMapping, projectRoot)

            assertEquals(
                setOf(
                    ChangedFunction("io.github.mikhailhal.sonarkt.Calculator.add", testModule),
                    ChangedFunction("io.github.mikhailhal.sonarkt.Calculator.multiply", testModule)
                ),
                changed
            )
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    @Test
    fun `collects top-level function`() {
        val projectDisposable = Disposer.newDisposable("ChangedFunctionCollectorTest")

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

            val ktFiles = session.modulesWithFiles
                .flatMap { it.value }
                .filterIsInstance<KtFile>()

            // Helper.kt の7-10行目(helperB関数)を変更
            val diffOutput = """
                diff --git a/src/test/resources/sandbox/Helper.kt b/src/test/resources/sandbox/Helper.kt
                --- a/src/test/resources/sandbox/Helper.kt
                +++ b/src/test/resources/sandbox/Helper.kt
                @@ -8 +8 @@
                -    val calc = Calculator()
                +    val calc = Calculator() // modified
            """.trimIndent()

            val collector = ChangedFunctionCollector()
            val changed = collector.collect(diffOutput, ktFiles, modulePathMapping, projectRoot)

            assertEquals(setOf(ChangedFunction("io.github.mikhailhal.sonarkt.helperB", testModule)), changed)
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    // === Edge cases ===

    @Test
    fun `empty diff returns empty set`() {
        val projectDisposable = Disposer.newDisposable("ChangedFunctionCollectorTest")

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

            val ktFiles = session.modulesWithFiles
                .flatMap { it.value }
                .filterIsInstance<KtFile>()

            val collector = ChangedFunctionCollector()
            val changed = collector.collect("", ktFiles, modulePathMapping, projectRoot)

            assertTrue(changed.isEmpty())
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    @Test
    fun `empty ktFiles returns empty set`() {
        val diffOutput = """
            diff --git a/src/test/resources/sandbox/Calculator.kt b/src/test/resources/sandbox/Calculator.kt
            --- a/src/test/resources/sandbox/Calculator.kt
            +++ b/src/test/resources/sandbox/Calculator.kt
            @@ -4 +4 @@
            -    fun add(a: Int, b: Int): Int = a + b
            +    fun add(a: Int, b: Int): Int = a + b // modified
        """.trimIndent()

        val collector = ChangedFunctionCollector()
        val changed = collector.collect(diffOutput, emptyList(), modulePathMapping, projectRoot)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun `diff for non-matching file returns empty set`() {
        val projectDisposable = Disposer.newDisposable("ChangedFunctionCollectorTest")

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

            val ktFiles = session.modulesWithFiles
                .flatMap { it.value }
                .filterIsInstance<KtFile>()

            // 存在しないファイルのdiff
            val diffOutput = """
                diff --git a/src/main/kotlin/NonExistent.kt b/src/main/kotlin/NonExistent.kt
                --- a/src/main/kotlin/NonExistent.kt
                +++ b/src/main/kotlin/NonExistent.kt
                @@ -1 +1 @@
                -old
                +new
            """.trimIndent()

            val collector = ChangedFunctionCollector()
            val changed = collector.collect(diffOutput, ktFiles, modulePathMapping, projectRoot)

            assertTrue(changed.isEmpty())
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }
}

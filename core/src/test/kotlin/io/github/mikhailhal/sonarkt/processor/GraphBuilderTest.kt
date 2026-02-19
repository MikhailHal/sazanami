package io.github.mikhailhal.sonarkt.processor

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * GraphBuilder統合テスト
 *
 * sandbox内のCalculator/Helper/CalculatorTestを使って
 * 依存グラフが正しく構築されることを検証
 */
class GraphBuilderTest {

    @Test
    fun `builds graph from sandbox files`() {
        val projectDisposable = Disposer.newDisposable("GraphBuilderTest")

        try {
            val session = buildStandaloneAnalysisAPISession(projectDisposable) {
                buildKtModuleProvider {
                    platform = JvmPlatforms.defaultJvmPlatform

                    addModule(buildKtSourceModule {
                        moduleName = "test"
                        platform = JvmPlatforms.defaultJvmPlatform
                        // sandboxのみを対象にする
                        addSourceRoot(Paths.get("src/test/resources/sandbox"))
                    })
                }
            }

            val ktFiles = session.modulesWithFiles
                .flatMap { it.value }
                .filterIsInstance<KtFile>()

            val graphBuilder = GraphBuilder()
            val graph = graphBuilder.build(ktFiles)

            // Calculator.add は CalculatorTest.testAdd と helperB から呼ばれる
            val addCallers = graph.getCallers("io.github.mikhailhal.sonarkt.Calculator.add")
            assertTrue(addCallers.contains("io.github.mikhailhal.sonarkt.CalculatorTest.testAdd"))
            assertTrue(addCallers.contains("io.github.mikhailhal.sonarkt.helperB"))

            // helperB は CalculatorTest.testHelper から呼ばれる
            val helperBCallers = graph.getCallers("io.github.mikhailhal.sonarkt.helperB")
            assertTrue(helperBCallers.contains("io.github.mikhailhal.sonarkt.CalculatorTest.testHelper"))

        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    @Test
    fun `empty file list returns empty graph`() {
        val graphBuilder = GraphBuilder()
        val graph = graphBuilder.build(emptyList())

        assertTrue(graph.getAllEdges().isEmpty())
    }
}

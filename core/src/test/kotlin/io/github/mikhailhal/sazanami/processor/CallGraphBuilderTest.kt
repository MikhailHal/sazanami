package io.github.mikhailhal.sazanami.processor

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sazanami.common.CallableNode
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * CallGraphBuilder統合テスト
 *
 * sandbox内のCalculator/Helper/CalculatorTestを使って
 * 依存グラフが正しく構築されることを検証
 */
class CallGraphBuilderTest {

    private val testModule = "test"

    // Helper to create lookup nodes
    private fun lookup(fqn: String) = CallableNode.forLookup(fqn, testModule)

    @Test
    fun `builds graph from sandbox files`() {
        val projectDisposable = Disposer.newDisposable("CallGraphBuilderTest")

        try {
            val session = buildStandaloneAnalysisAPISession(projectDisposable) {
                buildKtModuleProvider {
                    platform = JvmPlatforms.defaultJvmPlatform

                    addModule(buildKtSourceModule {
                        moduleName = testModule
                        platform = JvmPlatforms.defaultJvmPlatform
                        // sandboxのみを対象にする
                        addSourceRoot(Paths.get("src/test/resources/sandbox"))
                    })
                }
            }

            val ktFiles = session.modulesWithFiles
                .flatMap { it.value }
                .filterIsInstance<KtFile>()

            val graphBuilder = CallGraphBuilder()
            val graph = graphBuilder.build(mapOf(testModule to ktFiles))

            // Calculator.add は CalculatorTest.testAdd と helperB から呼ばれる
            val addCallers = graph.getCallers(lookup("io.github.mikhailhal.sazanami.Calculator.add"))
            assertTrue(addCallers.any { it.fqn == "io.github.mikhailhal.sazanami.CalculatorTest.testAdd" })
            assertTrue(addCallers.any { it.fqn == "io.github.mikhailhal.sazanami.helperB" })

            // helperB は CalculatorTest.testHelper から呼ばれる
            val helperBCallers = graph.getCallers(lookup("io.github.mikhailhal.sazanami.helperB"))
            assertTrue(helperBCallers.any { it.fqn == "io.github.mikhailhal.sazanami.CalculatorTest.testHelper" })

            // @Test annotation detection: testAdd should have isTest = true
            val testAddCaller = addCallers.find { it.fqn == "io.github.mikhailhal.sazanami.CalculatorTest.testAdd" }
            assertTrue(testAddCaller?.isTest == true, "testAdd should have @Test annotation")

            // helperB should have isTest = false (no @Test annotation)
            val helperBCaller = addCallers.find { it.fqn == "io.github.mikhailhal.sazanami.helperB" }
            assertTrue(helperBCaller?.isTest == false, "helperB should not have @Test annotation")

        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    @Test
    fun `empty file list returns empty graph`() {
        val graphBuilder = CallGraphBuilder()
        val graph = graphBuilder.build(emptyMap())

        assertTrue(graph.getAllEdges().isEmpty())
    }
}

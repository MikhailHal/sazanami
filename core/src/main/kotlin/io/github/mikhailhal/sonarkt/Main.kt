package io.github.mikhailhal.sonarkt

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sonarkt.collector.ChangedFunctionCollector
import io.github.mikhailhal.sonarkt.emitter.AffectedTestEmitter
import io.github.mikhailhal.sonarkt.processor.AffectedTestResolver
import io.github.mikhailhal.sonarkt.processor.GraphBuilder
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * sonar-kt CLI エントリーポイント
 *
 * Usage: git diff --unified=0 | sonar-kt --project <path>
 */
fun main(args: Array<String>) {
    val projectPath = parseArgs(args)
    if (projectPath == null) {
        printUsage()
        exitProcess(1)
    }

    val diff = readDiffFromStdin()
    if (diff.isEmpty()) {
        // 差分なし = 影響テストなし
        return
    }

    val projectDisposable = Disposer.newDisposable("sonar-kt")

    try {
        val ktFiles = loadKtFiles(projectPath, projectDisposable)
        val output = runPipeline(diff, ktFiles)

        if (output.isNotEmpty()) {
            println(output)
        }
    } finally {
        Disposer.dispose(projectDisposable)
    }

    // Analysis APIのバックグラウンドスレッドが残るため明示的に終了
    exitProcess(0)
}

/**
 * 引数をパースして --project の値を返す
 */
private fun parseArgs(args: Array<String>): String? {
    val projectIndex = args.indexOf("--project")
    if (projectIndex == -1 || projectIndex + 1 >= args.size) {
        return null
    }
    return args[projectIndex + 1]
}

/**
 * 標準入力からdiffを読み込む
 */
private fun readDiffFromStdin(): String {
    return generateSequence(::readLine).joinToString("\n")
}

/**
 * プロジェクトからKtFileを読み込む
 */
private fun loadKtFiles(projectPath: String, disposable: com.intellij.openapi.Disposable): List<KtFile> {
    val session = buildStandaloneAnalysisAPISession(disposable) {
        buildKtModuleProvider {
            platform = JvmPlatforms.defaultJvmPlatform

            addModule(buildKtSourceModule {
                moduleName = "main"
                platform = JvmPlatforms.defaultJvmPlatform
                addSourceRoot(Paths.get(projectPath))
            })
        }
    }

    return session.modulesWithFiles
        .flatMap { it.value }
        .filterIsInstance<KtFile>()
}

/**
 * パイプライン実行
 */
private fun runPipeline(diff: String, ktFiles: List<KtFile>): String {
    val changedFunctions = ChangedFunctionCollector().collect(diff, ktFiles)
    val graph = GraphBuilder().build(ktFiles)
    val affectedTests = AffectedTestResolver(graph).findAffectedTests(changedFunctions)
    return AffectedTestEmitter.emit(affectedTests)
}

private fun printUsage() {
    System.err.println("sonar-kt - Kotlin Affected Test Selector")
    System.err.println()
    System.err.println("Usage: git diff --unified=0 | sonar-kt --project <path>")
    System.err.println()
    System.err.println("Options:")
    System.err.println("  --project <path>  Path to Kotlin source directory (required)")
}

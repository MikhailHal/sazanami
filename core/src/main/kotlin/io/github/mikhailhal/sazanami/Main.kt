package io.github.mikhailhal.sazanami

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sazanami.collector.ChangedFunctionCollector
import io.github.mikhailhal.sazanami.common.ModuleName
import io.github.mikhailhal.sazanami.emitter.AffectedTestEmitter
import io.github.mikhailhal.sazanami.processor.AffectedTestResolver
import io.github.mikhailhal.sazanami.processor.GraphBuilder
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import kotlin.system.exitProcess

// シングルモジュール用のデフォルトモジュール名
private const val DEFAULT_MODULE: ModuleName = "main"

/**
 * sazanami CLI エントリーポイント
 *
 * Usage: git diff --unified=0 | sazanami --project <path>
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

    val projectDisposable = Disposer.newDisposable("sazanami")

    try {
        val projectRoot = Paths.get(projectPath).toAbsolutePath()
        val moduleFiles = loadKtFiles(projectPath, projectDisposable)
        val modulePathMapping = mapOf(DEFAULT_MODULE to projectPath)
        val output = runPipeline(diff, moduleFiles, modulePathMapping, projectRoot)

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
private fun loadKtFiles(
    projectPath: String,
    disposable: com.intellij.openapi.Disposable
): Map<ModuleName, List<KtFile>> {
    val session = buildStandaloneAnalysisAPISession(disposable) {
        buildKtModuleProvider {
            platform = JvmPlatforms.defaultJvmPlatform

            addModule(buildKtSourceModule {
                moduleName = DEFAULT_MODULE
                platform = JvmPlatforms.defaultJvmPlatform
                addSourceRoot(Paths.get(projectPath))
            })
        }
    }

    return session.modulesWithFiles
        .mapKeys { (kaModule, _) -> kaModule.name }
        .mapValues { (_, files) -> files.filterIsInstance<KtFile>() }
}

/**
 * パイプライン実行
 */
private fun runPipeline(
    diff: String,
    moduleFiles: Map<ModuleName, List<KtFile>>,
    modulePathMapping: Map<ModuleName, String>,
    projectRoot: java.nio.file.Path
): String {
    val allKtFiles = moduleFiles.values.flatten()
    val changedFunctions = ChangedFunctionCollector().collect(diff, allKtFiles, modulePathMapping, projectRoot)
    val graph = GraphBuilder().build(moduleFiles)
    val affectedTests = AffectedTestResolver(graph).findAffectedTests(changedFunctions)
    return AffectedTestEmitter.emit(affectedTests)
}

private fun printUsage() {
    System.err.println("sazanami - Kotlin Affected Test Selector")
    System.err.println()
    System.err.println("Usage: git diff --unified=0 | sazanami --project <path>")
    System.err.println()
    System.err.println("Options:")
    System.err.println("  --project <path>  Path to Kotlin source directory (required)")
}

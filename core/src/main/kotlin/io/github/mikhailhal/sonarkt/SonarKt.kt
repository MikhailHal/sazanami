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
import java.nio.file.Path

/**
 * sonar-kt のメインAPI
 *
 * マルチモジュールプロジェクト対応版:
 * ```
 * val affected = SonarKt.findAffectedTests(
 *     diff = "git diff output...",
 *     moduleSourceRoots = mapOf(
 *         ":core" to listOf(Path.of("core/src/main/kotlin"), Path.of("core/src/test/kotlin")),
 *         ":plugin" to listOf(Path.of("plugin/src/main/kotlin"), Path.of("plugin/src/test/kotlin"))
 *     ),
 *     modulePathMapping = mapOf(":core" to "core", ":plugin" to "plugin")
 * )
 * ```
 */
object SonarKt {

    /**
     * 変更されたコードに影響を受けるテストのFQN一覧を返す（マルチモジュール対応）
     *
     * @param diff git diff --unified=0 の出力
     * @param moduleSourceRoots モジュール名 → ソースルートリストのマッピング
     * @param modulePathMapping モジュール名 → モジュールルートパスのマッピング（diff解析用）
     * @param projectRoot プロジェクトルートの絶対パス
     * @return 影響テストのFQN集合
     */
    fun findAffectedTests(
        diff: String,
        moduleSourceRoots: Map<ModuleName, List<Path>>,
        modulePathMapping: Map<ModuleName, String>,
        projectRoot: Path
    ): Set<String> {
        if (diff.isEmpty() || moduleSourceRoots.isEmpty()) {
            return emptySet()
        }

        val projectDisposable = Disposer.newDisposable("sonar-kt")

        try {
            val moduleFiles = extractKtFilesViaSession(moduleSourceRoots, projectDisposable)
            if (moduleFiles.values.all { it.isEmpty() }) {
                return emptySet()
            }

            // ChangedFunctionCollectorは全ファイルをフラットに受け取る
            val allKtFiles = moduleFiles.values.flatten()
            val changedFunctions = ChangedFunctionCollector().collect(diff, allKtFiles, modulePathMapping, projectRoot)

            val graph = GraphBuilder().build(moduleFiles)
            return AffectedTestResolver(graph).findAffectedTests(changedFunctions)
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    /**
     * 影響テストを改行区切りの文字列で返す（マルチモジュール対応）
     */
    fun findAffectedTestsAsString(
        diff: String,
        moduleSourceRoots: Map<ModuleName, List<Path>>,
        modulePathMapping: Map<ModuleName, String>,
        projectRoot: Path
    ): String {
        val affected = findAffectedTests(diff, moduleSourceRoots, modulePathMapping, projectRoot)
        return AffectedTestEmitter.emit(affected)
    }

    /**
     * 各モジュールからKtFileを抽出
     *
     * @return モジュール名 → KtFileリストのマッピング
     */
    private fun extractKtFilesViaSession(
        moduleSourceRoots: Map<ModuleName, List<Path>>,
        disposable: com.intellij.openapi.Disposable
    ): Map<ModuleName, List<KtFile>> {
        val session = buildStandaloneAnalysisAPISession(disposable) {
            buildKtModuleProvider {
                platform = JvmPlatforms.defaultJvmPlatform

                for ((moduleName, sourceRoots) in moduleSourceRoots) {
                    addModule(buildKtSourceModule {
                        this.moduleName = moduleName
                        platform = JvmPlatforms.defaultJvmPlatform
                        sourceRoots.forEach { addSourceRoot(it) }
                    })
                }
            }
        }

        // KaSourceModule.name でモジュール名を取得し、KtFileをグループ化
        return session.modulesWithFiles
            .mapKeys { (kaModule, _) -> kaModule.name }
            .mapValues { (_, files) -> files.filterIsInstance<KtFile>() }
    }
}

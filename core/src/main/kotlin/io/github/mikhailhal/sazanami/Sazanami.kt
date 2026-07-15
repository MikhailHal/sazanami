package io.github.mikhailhal.sazanami

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sazanami.collector.ChangedFunctionCollector
import io.github.mikhailhal.sazanami.common.ModuleName
import io.github.mikhailhal.sazanami.emitter.AffectedTestEmitter
import io.github.mikhailhal.sazanami.processor.AffectedTestResolver
import io.github.mikhailhal.sazanami.processor.GraphBuilder
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

/**
 * sazanami のメインAPI
 *
 * マルチモジュールプロジェクト対応版:
 * ```
 * val affected = Sazanami.findAffectedTests(
 *     diff = "git diff output...",
 *     moduleSourceRoots = mapOf(
 *         ":core" to listOf(Path.of("core/src/main/kotlin"), Path.of("core/src/test/kotlin")),
 *         ":plugin" to listOf(Path.of("plugin/src/main/kotlin"), Path.of("plugin/src/test/kotlin"))
 *     ),
 *     modulePathMapping = mapOf(":core" to "core", ":plugin" to "plugin"),
 *     moduleDependencies = mapOf(":plugin" to setOf(":core"))
 * )
 * ```
 */
object Sazanami {

    /**
     * 変更されたコードに影響を受けるテストのFQN一覧を返す（マルチモジュール対応）
     *
     * @param diff git diff --unified=0 の出力
     * @param moduleSourceRoots モジュール名 → ソースルートリストのマッピング
     * @param modulePathMapping モジュール名 → モジュールルートパスのマッピング（diff解析用）
     * @param projectRoot プロジェクトルートの絶対パス
     * @param moduleDependencies モジュール名 → 依存モジュール名セットのマッピング（クロスモジュール解決用）
     * @return 影響テストのFQN集合
     */
    fun findAffectedTests(
        diff: String,
        moduleSourceRoots: Map<ModuleName, List<Path>>,
        modulePathMapping: Map<ModuleName, String>,
        projectRoot: Path,
        moduleDependencies: Map<ModuleName, Set<ModuleName>> = emptyMap()
    ): Set<String> {
        if (diff.isEmpty() || moduleSourceRoots.isEmpty()) {
            return emptySet()
        }

        val projectDisposable = Disposer.newDisposable("sazanami")

        try {
            val moduleFiles = extractKtFilesViaSession(moduleSourceRoots, moduleDependencies, projectDisposable)
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
        projectRoot: Path,
        moduleDependencies: Map<ModuleName, Set<ModuleName>> = emptyMap()
    ): String {
        val affected = findAffectedTests(diff, moduleSourceRoots, modulePathMapping, projectRoot, moduleDependencies)
        return AffectedTestEmitter.emit(affected)
    }

    /**
     * 各モジュールからKtFileを抽出
     *
     * モジュール間の依存関係を設定することで、クロスモジュールのシンボル解決が可能になる。
     * モジュールはトポロジカル順序（依存先 → 依存元）で構築される。
     *
     * @param moduleSourceRoots モジュール名 → ソースルートリストのマッピング
     * @param moduleDependencies モジュール名 → 依存モジュール名セットのマッピング
     * @param disposable セッションのライフサイクル管理用Disposable
     * @return モジュール名 → KtFileリストのマッピング
     */
    private fun extractKtFilesViaSession(
        moduleSourceRoots: Map<ModuleName, List<Path>>,
        moduleDependencies: Map<ModuleName, Set<ModuleName>>,
        disposable: com.intellij.openapi.Disposable
    ): Map<ModuleName, List<KtFile>> {
        // トポロジカルソート: 依存先が先に来るように並べる
        val sortedModules = topologicalSort(moduleSourceRoots.keys, moduleDependencies)
        val session = buildStandaloneAnalysisAPISession(disposable) {
            buildKtModuleProvider {
                platform = JvmPlatforms.defaultJvmPlatform

                // 作成済みモジュールを保持
                val builtModules = mutableMapOf<ModuleName, KaSourceModule>()

                for (moduleName in sortedModules) {
                    val sourceRoots = moduleSourceRoots[moduleName] ?: continue
                    val deps = moduleDependencies[moduleName] ?: emptySet()

                    val kaSourceModule = buildKtSourceModule {
                        // 現在のモジュールからみたコンテキストを設定する
                        this.moduleName = moduleName
                        this.platform = JvmPlatforms.defaultJvmPlatform
                        sourceRoots.forEach { addSourceRoot(it) }

                        // 依存モジュールを追加（すでに構築済みのはず）
                        for (depName in deps) {
                            val depModule = builtModules[depName]
                            if (depModule is KaModule) {
                                addRegularDependency(depModule)
                            }
                        }
                    }
                    addModule(kaSourceModule)
                    builtModules[moduleName] = kaSourceModule
                }
            }
        }

        // KaSourceModule.name でモジュール名を取得し、KtFileをグループ化
        return session.modulesWithFiles
            .mapKeys { (kaModule, _) -> kaModule.name }
            .mapValues { (_, files) -> files.filterIsInstance<KtFile>() }
    }

    /**
     * モジュールをトポロジカルソート
     *
     * 全モジュール間の依存関係をDAGとして表現。
     * モジュールA,B,C,Dがあり、
     * A->C->D
     * ⇩
     * B
     * となっていると仮定した場合トポロジカルソート結果は
     * [D, C, B, A]
     * となる。
     *
     * @param modules 全モジュール名
     * @param dependencies モジュール名 → 依存モジュール名セット
     * @return ソート済みモジュール名リスト
     */
    private fun topologicalSort(
        modules: Set<ModuleName>,
        dependencies: Map<ModuleName, Set<ModuleName>>
    ): List<ModuleName> {
        val result = mutableListOf<ModuleName>()
        val visited = mutableSetOf<ModuleName>()
        val visiting = mutableSetOf<ModuleName>()

        fun visit(module: ModuleName) {
            if (module in visited) return
            if (module in visiting) {
                // 循環依存の場合は無視
                return
            }

            visiting.add(module)

            // 依存先を先に処理
            dependencies[module]?.forEach { dep ->
                if (dep in modules) {
                    visit(dep)
                }
            }

            visiting.remove(module)
            visited.add(module)
            result.add(module)
        }

        modules.forEach { visit(it) }
        return result
    }
}

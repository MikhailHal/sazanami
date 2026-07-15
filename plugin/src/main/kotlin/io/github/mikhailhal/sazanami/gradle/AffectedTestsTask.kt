package io.github.mikhailhal.sazanami.gradle

import io.github.mikhailhal.sazanami.Sazanami
import io.github.mikhailhal.sazanami.common.ModuleName
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path

/**
 * 変更されたコードに影響を受けるテストを検出するタスク
 *
 * Usage: ./gradlew affectedTests
 *
 * マルチモジュールプロジェクトにも対応。
 * 各サブプロジェクトを自動検出してソースルートを収集する。
 */
abstract class AffectedTestsTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val baseBranch: Property<String>

    @TaskAction
    fun run() {
        val base = BaseBranchResolver.resolve(baseBranch.orNull)
        logger.lifecycle("Comparing against: $base")

        val diff = getGitDiff(base)
        if (diff.isEmpty()) {
            logger.lifecycle("No changes detected")
            return
        }

        val (moduleSourceRoots, modulePathMapping) = collectModuleInfo()
        if (moduleSourceRoots.isEmpty()) {
            logger.warn("No Kotlin source roots found")
            return
        }

        val moduleDependencies = collectModuleDependencies()
        logger.lifecycle("Detected modules: ${moduleSourceRoots.keys.joinToString(", ")}")

        val projectRoot = project.rootProject.projectDir.toPath()
        val output = Sazanami.findAffectedTestsAsString(
            diff,
            moduleSourceRoots,
            modulePathMapping,
            projectRoot,
            moduleDependencies
        )

        if (output.isNotEmpty()) {
            logger.lifecycle("Affected tests:")
            output.lines().forEach { logger.lifecycle("  $it") }
        } else {
            logger.lifecycle("No affected tests detected")
        }
    }

    private fun getGitDiff(baseBranch: String): String {
        return try {
            val process = ProcessBuilder("git", "diff", "--unified=0", "$baseBranch...HEAD")
                .directory(project.rootProject.projectDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                output
            } else {
                logger.warn("git diff failed with exit code $exitCode: $output")
                ""
            }
        } catch (e: Exception) {
            logger.warn("Failed to run git diff: ${e.message}")
            ""
        }
    }

    /**
     * マルチモジュール情報を収集
     *
     * @return Pair of (moduleSourceRoots, modulePathMapping)
     */
    private fun collectModuleInfo(): Pair<Map<ModuleName, List<Path>>, Map<ModuleName, String>> {
        val moduleSourceRoots = mutableMapOf<ModuleName, List<Path>>()
        val modulePathMapping = mutableMapOf<ModuleName, String>()
        val rootDir = project.rootProject.projectDir

        // ルートプロジェクトと全サブプロジェクトを処理
        val allProjects = listOf(project.rootProject) + project.rootProject.subprojects

        for (proj in allProjects) {
            val moduleName: ModuleName = proj.path.ifEmpty { ":" }
            val relativePath = rootDir.toPath().relativize(proj.projectDir.toPath()).toString()

            val sourceRoots = collectSourceRootsForProject(proj.projectDir)
            if (sourceRoots.isNotEmpty()) {
                moduleSourceRoots[moduleName] = sourceRoots
                modulePathMapping[moduleName] = relativePath.ifEmpty { "." }
            }
        }

        return Pair(moduleSourceRoots, modulePathMapping)
    }

    /**
     * 単一プロジェクトのソースルートを収集
     */
    private fun collectSourceRootsForProject(projectDir: java.io.File): List<Path> {
        val roots = mutableListOf<Path>()

        val standardPaths = listOf(
            "src/main/kotlin",
            "src/test/kotlin",
            "src/main/java",
            "src/test/java"
        )
        standardPaths.forEach { path ->
            val dir = projectDir.resolve(path)
            if (dir.exists()) {
                roots.add(dir.toPath())
            }
        }

        return roots
    }

    /**
     * モジュール間の依存関係を収集
     *
     * @return モジュール名 → 依存モジュール名セットのマッピング
     */
    private fun collectModuleDependencies(): Map<ModuleName, Set<ModuleName>> {
        val dependencies = mutableMapOf<ModuleName, MutableSet<ModuleName>>()

        val allProjects = listOf(project.rootProject) + project.rootProject.subprojects

        for (proj in allProjects) {
            val moduleName: ModuleName = proj.path.ifEmpty { ":" }

            // 解決可能なconfigurationからProjectDependencyを収集
            proj.configurations
                .filter { it.isCanBeResolved }
                .forEach { config ->
                    config.allDependencies
                        .filterIsInstance<ProjectDependency>()
                        .forEach { dep ->
                            val depPath = dep.path
                            dependencies.getOrPut(moduleName) { mutableSetOf() }.add(depPath)
                        }
                }
        }

        return dependencies
    }
}

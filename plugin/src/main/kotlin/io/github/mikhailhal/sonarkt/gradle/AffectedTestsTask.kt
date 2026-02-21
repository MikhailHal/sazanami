package io.github.mikhailhal.sonarkt.gradle

import io.github.mikhailhal.sonarkt.SonarKt
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path

/**
 * 変更されたコードに影響を受けるテストを検出するタスク
 *
 * Usage: ./gradlew affectedTests
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

        val sourceRoots = collectSourceRoots()
        if (sourceRoots.isEmpty()) {
            logger.warn("No Kotlin source roots found")
            return
        }

        val output = SonarKt.findAffectedTestsAsString(diff, sourceRoots)

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
                .directory(project.projectDir)
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

    private fun collectSourceRoots(): List<Path> {
        val roots = mutableListOf<Path>()

        val standardPaths = listOf(
            "src/main/kotlin",
            "src/test/kotlin",
            "src/main/java",
            "src/test/java"
        )
        standardPaths.forEach { path ->
            val dir = project.projectDir.resolve(path)
            if (dir.exists()) {
                roots.add(dir.toPath())
            }
        }

        return roots
    }
}

package io.github.mikhailhal.sonarkt.gradle

import io.github.mikhailhal.sonarkt.SonarKt
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path

/**
 * 変更されたコードに影響を受けるテストを検出するタスク
 *
 * Usage: ./gradlew affectedTests
 */
abstract class AffectedTestsTask : DefaultTask() {

    @TaskAction
    fun run() {
        val diff = getGitDiff()
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

    private fun getGitDiff(): String {
        return try {
            val process = ProcessBuilder("git", "diff", "--unified=0", "HEAD")
                .directory(project.projectDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                output
            } else {
                logger.warn("git diff failed with exit code $exitCode")
                ""
            }
        } catch (e: Exception) {
            logger.warn("Failed to run git diff: ${e.message}")
            ""
        }
    }

    private fun collectSourceRoots(): List<Path> {
        val roots = mutableListOf<Path>()

        // フォールバック: 標準的なパスを探す
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

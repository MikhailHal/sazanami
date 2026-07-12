package io.github.mikhailhal.sazanami.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * sazanami Gradle Plugin
 *
 * Usage:
 * ```
 * plugins {
 *     id("io.github.mikhailhal.sazanami")
 * }
 *
 * sazanami {
 *     baseBranch.set("origin/develop")  // optional
 * }
 * ```
 *
 * Tasks:
 * - affectedTests: 変更されたコードに影響を受けるテストを検出
 */
class SazanamiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sazanami", SazanamiExtension::class.java)

        project.tasks.register("affectedTests", AffectedTestsTask::class.java) { task ->
            task.group = "verification"
            task.description = "Detect tests affected by code changes"
            task.baseBranch.set(extension.baseBranch)
        }
    }
}

package io.github.mikhailhal.sonarkt.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * sonar-kt Gradle Plugin
 *
 * Usage:
 * ```
 * plugins {
 *     id("io.github.mikhailhal.sonarkt")
 * }
 *
 * sonarKt {
 *     baseBranch.set("origin/develop")  // optional
 * }
 * ```
 *
 * Tasks:
 * - affectedTests: 変更されたコードに影響を受けるテストを検出
 */
class SonarKtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sonarKt", SonarKtExtension::class.java)

        project.tasks.register("affectedTests", AffectedTestsTask::class.java) { task ->
            task.group = "verification"
            task.description = "Detect tests affected by code changes"
            task.baseBranch.set(extension.baseBranch)
        }
    }
}

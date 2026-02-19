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
 * ```
 *
 * Tasks:
 * - affectedTests: 変更されたコードに影響を受けるテストを検出
 */
class SonarKtPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("affectedTests", AffectedTestsTask::class.java) {
            it.group = "verification"
            it.description = "Detect tests affected by code changes"
        }
    }
}

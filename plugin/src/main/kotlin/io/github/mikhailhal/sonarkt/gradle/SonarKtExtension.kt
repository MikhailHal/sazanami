package io.github.mikhailhal.sonarkt.gradle

import org.gradle.api.provider.Property

/**
 * sonar-kt プラグインの設定
 *
 * Usage:
 * ```
 * sonarKt {
 *     baseBranch.set("origin/main")
 * }
 * ```
 */
interface SonarKtExtension {
    /**
     * 比較対象のベースブランチ
     * デフォルト: CI環境では自動検出、それ以外は "origin/main"
     */
    val baseBranch: Property<String>
}

package io.github.mikhailhal.sazanami.gradle

import org.gradle.api.provider.Property

/**
 * sazanami プラグインの設定
 *
 * Usage:
 * ```
 * sazanami {
 *     baseBranch.set("origin/main")
 * }
 * ```
 */
interface SazanamiExtension {
    /**
     * 比較対象のベースブランチ
     * デフォルト: CI環境では自動検出、それ以外は "origin/main"
     */
    val baseBranch: Property<String>
}

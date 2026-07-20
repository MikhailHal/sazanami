package io.github.mikhailhal.sazanami.common

import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * 関数に@Testアノテーションが付与されているかを判定
 *
 * 対応アノテーション:
 * - org.junit.Test (JUnit 4)
 * - org.junit.jupiter.api.Test (JUnit 5)
 * - kotlin.test.Test (kotlin-test)
 */
fun KtNamedFunction.hasTestAnnotation(): Boolean {
    return annotationEntries.any { annotation ->
        annotation.shortName?.asString() == "Test"
    }
}

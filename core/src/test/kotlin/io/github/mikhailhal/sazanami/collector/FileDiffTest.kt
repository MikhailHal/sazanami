package io.github.mikhailhal.sazanami.collector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileDiffTest {

    // === containsLine ===

    @Test
    fun `containsLine returns true for line in range`() {
        val diff = FileDiff("Foo.kt", listOf(LineRange(5, 10)))
        assertTrue(diff.containsLine(7))
    }

    @Test
    fun `containsLine returns false for line outside range`() {
        val diff = FileDiff("Foo.kt", listOf(LineRange(5, 10)))
        assertFalse(diff.containsLine(15))
    }

    @Test
    fun `containsLine with multiple ranges`() {
        val diff = FileDiff("Foo.kt", listOf(LineRange(5, 10), LineRange(20, 25)))
        assertTrue(diff.containsLine(7))
        assertTrue(diff.containsLine(22))
        assertFalse(diff.containsLine(15))
    }

    // === overlapsWithRange ===

    @Test
    fun `overlapsWithRange returns true for overlapping range`() {
        val diff = FileDiff("Foo.kt", listOf(LineRange(5, 10)))
        assertTrue(diff.overlapsWithRange(LineRange(8, 15)))
    }

    @Test
    fun `overlapsWithRange returns false for non-overlapping range`() {
        val diff = FileDiff("Foo.kt", listOf(LineRange(5, 10)))
        assertFalse(diff.overlapsWithRange(LineRange(15, 20)))
    }

    @Test
    fun `overlapsWithRange with multiple ranges`() {
        val diff = FileDiff("Foo.kt", listOf(LineRange(5, 10), LineRange(20, 25)))
        assertTrue(diff.overlapsWithRange(LineRange(8, 12)))
        assertTrue(diff.overlapsWithRange(LineRange(18, 22)))
        assertFalse(diff.overlapsWithRange(LineRange(12, 18)))
    }

    // === isKotlinFile ===

    @Test
    fun `isKotlinFile returns true for kt files`() {
        assertTrue(FileDiff("Foo.kt", emptyList()).isKotlinFile())
        assertTrue(FileDiff("src/main/Foo.kt", emptyList()).isKotlinFile())
    }

    @Test
    fun `isKotlinFile returns true for kts files`() {
        assertTrue(FileDiff("build.gradle.kts", emptyList()).isKotlinFile())
    }

    @Test
    fun `isKotlinFile returns false for non-Kotlin files`() {
        assertFalse(FileDiff("Foo.java", emptyList()).isKotlinFile())
        assertFalse(FileDiff("README.md", emptyList()).isKotlinFile())
    }
}

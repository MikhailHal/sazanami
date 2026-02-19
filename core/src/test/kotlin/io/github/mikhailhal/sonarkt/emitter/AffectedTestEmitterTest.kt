package io.github.mikhailhal.sonarkt.emitter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class AffectedTestEmitterTest {

    // === emit ===

    @Test
    fun `emit returns sorted newline-separated FQNs`() {
        val tests = setOf(
            "com.example.BTest.test1",
            "com.example.ATest.test2",
            "com.example.ATest.test1"
        )

        val result = AffectedTestEmitter.emit(tests)

        assertEquals(
            """
            com.example.ATest.test1
            com.example.ATest.test2
            com.example.BTest.test1
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `emit with empty set returns empty string`() {
        val result = AffectedTestEmitter.emit(emptySet())
        assertEquals("", result)
    }

    @Test
    fun `emit with single test`() {
        val result = AffectedTestEmitter.emit(setOf("com.example.Test.test1"))
        assertEquals("com.example.Test.test1", result)
    }

    // === emitToFile ===

    @Test
    fun `emitToFile writes to file`() {
        val tempFile = File.createTempFile("affected-tests", ".txt")
        try {
            val tests = setOf("com.example.Test.test1", "com.example.Test.test2")

            AffectedTestEmitter.emitToFile(tests, tempFile)

            val content = tempFile.readText()
            assertEquals(
                """
                com.example.Test.test1
                com.example.Test.test2
                """.trimIndent(),
                content
            )
        } finally {
            tempFile.delete()
        }
    }
}

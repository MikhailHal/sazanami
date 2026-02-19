package io.github.mikhailhal.sonarkt.collector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitDiffParserTest {

    // === Basic parsing ===

    @Test
    fun `parse single file with single hunk`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -10,2 +10,3 @@ fun existing()
            +    added line
        """.trimIndent()

        val result = GitDiffParser.parse(diff)

        assertEquals(1, result.size)
        val fileDiff = result["src/Foo.kt"]!!
        assertEquals("src/Foo.kt", fileDiff.filePath)
        assertEquals(1, fileDiff.changedLineRanges.size)
        assertEquals(LineRange(10, 12), fileDiff.changedLineRanges[0])
    }

    @Test
    fun `parse multiple files`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -5 +5 @@ class Foo
            +    changed
            diff --git a/src/Bar.kt b/src/Bar.kt
            --- a/src/Bar.kt
            +++ b/src/Bar.kt
            @@ -10,3 +10,4 @@ class Bar
            +    added
        """.trimIndent()

        val result = GitDiffParser.parse(diff)

        assertEquals(2, result.size)
        assertTrue(result.containsKey("src/Foo.kt"))
        assertTrue(result.containsKey("src/Bar.kt"))
    }

    @Test
    fun `parse multiple hunks in same file`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -5 +5 @@ fun first()
            +    change1
            @@ -20,2 +20,3 @@ fun second()
            +    change2
        """.trimIndent()

        val result = GitDiffParser.parse(diff)

        assertEquals(1, result.size)
        val fileDiff = result["src/Foo.kt"]!!
        assertEquals(2, fileDiff.changedLineRanges.size)
        assertEquals(LineRange(5, 5), fileDiff.changedLineRanges[0])
        assertEquals(LineRange(20, 22), fileDiff.changedLineRanges[1])
    }

    // === Hunk header format ===

    @Test
    fun `hunk with count omitted means count is 1`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -10 +10 @@ fun foo()
            +    single line change
        """.trimIndent()

        val result = GitDiffParser.parse(diff)
        val fileDiff = result["src/Foo.kt"]!!

        // +10 means line 10 with count 1, so range is 10-10
        assertEquals(LineRange(10, 10), fileDiff.changedLineRanges[0])
    }

    @Test
    fun `hunk with explicit count`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -10,2 +10,5 @@ fun foo()
            +    line 1
            +    line 2
            +    line 3
        """.trimIndent()

        val result = GitDiffParser.parse(diff)
        val fileDiff = result["src/Foo.kt"]!!

        // +10,5 means starting at line 10, 5 lines total: 10-14
        assertEquals(LineRange(10, 14), fileDiff.changedLineRanges[0])
    }

    @Test
    fun `delete-only hunk is skipped`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -10,3 +10,0 @@ fun foo()
            -    deleted line 1
            -    deleted line 2
            -    deleted line 3
        """.trimIndent()

        val result = GitDiffParser.parse(diff)

        // File with only deletions should not appear (no ranges to report)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed add and delete hunks`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -5,2 +5,0 @@ fun first()
            -    deleted
            @@ -10 +8 @@ fun second()
            +    added
        """.trimIndent()

        val result = GitDiffParser.parse(diff)
        val fileDiff = result["src/Foo.kt"]!!

        // Only the add hunk should be recorded
        assertEquals(1, fileDiff.changedLineRanges.size)
        assertEquals(LineRange(8, 8), fileDiff.changedLineRanges[0])
    }

    // === Edge cases ===

    @Test
    fun `empty diff returns empty map`() {
        val result = GitDiffParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `diff with no hunks returns empty map`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
        """.trimIndent()

        val result = GitDiffParser.parse(diff)
        assertTrue(result.isEmpty())
    }

    // === parseKotlinFiles ===

    @Test
    fun `parseKotlinFiles with empty diff returns empty map`() {
        val result = GitDiffParser.parseKotlinFiles("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseKotlinFiles filters to kt files only`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -5 +5 @@ class Foo
            +    changed
            diff --git a/src/Bar.java b/src/Bar.java
            --- a/src/Bar.java
            +++ b/src/Bar.java
            @@ -10 +10 @@ class Bar
            +    changed
            diff --git a/build.gradle.kts b/build.gradle.kts
            --- a/build.gradle.kts
            +++ b/build.gradle.kts
            @@ -1 +1 @@ plugins
            +    changed
        """.trimIndent()

        val result = GitDiffParser.parseKotlinFiles(diff)

        assertEquals(2, result.size)
        assertTrue(result.containsKey("src/Foo.kt"))
        assertTrue(result.containsKey("build.gradle.kts"))
        // Java file should be excluded
        assertTrue(!result.containsKey("src/Bar.java"))
    }
}

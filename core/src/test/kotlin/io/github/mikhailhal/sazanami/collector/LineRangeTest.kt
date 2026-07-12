package io.github.mikhailhal.sazanami.collector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class LineRangeTest {

    // === Construction ===

    @Test
    fun `basic construction`() {
        val range = LineRange(1, 10)
        assertEquals(1, range.startLine)
        assertEquals(10, range.endLine)
    }

    @Test
    fun `single line range`() {
        val range = LineRange(5, 5)
        assertEquals(5, range.startLine)
        assertEquals(5, range.endLine)
    }

    @Test
    fun `startLine must be at least 1`() {
        assertFailsWith<IllegalArgumentException> {
            LineRange(0, 5)
        }
    }

    @Test
    fun `endLine must be greater than or equal to startLine`() {
        assertFailsWith<IllegalArgumentException> {
            LineRange(10, 5)
        }
    }

    // === contains ===

    @Test
    fun `contains - line within range`() {
        val range = LineRange(5, 10)
        assertTrue(range.contains(5))  // start
        assertTrue(range.contains(7))  // middle
        assertTrue(range.contains(10)) // end
    }

    @Test
    fun `contains - line outside range`() {
        val range = LineRange(5, 10)
        assertFalse(range.contains(4))  // before
        assertFalse(range.contains(11)) // after
    }

    // === overlaps ===

    @Test
    fun `overlaps - identical ranges`() {
        val range1 = LineRange(5, 10)
        val range2 = LineRange(5, 10)
        assertTrue(range1.overlaps(range2))
    }

    @Test
    fun `overlaps - partial overlap at start`() {
        val range1 = LineRange(5, 10)
        val range2 = LineRange(3, 7)
        assertTrue(range1.overlaps(range2))
        assertTrue(range2.overlaps(range1))
    }

    @Test
    fun `overlaps - partial overlap at end`() {
        val range1 = LineRange(5, 10)
        val range2 = LineRange(8, 15)
        assertTrue(range1.overlaps(range2))
        assertTrue(range2.overlaps(range1))
    }

    @Test
    fun `overlaps - one contains the other`() {
        val outer = LineRange(1, 20)
        val inner = LineRange(5, 10)
        assertTrue(outer.overlaps(inner))
        assertTrue(inner.overlaps(outer))
    }

    @Test
    fun `overlaps - adjacent ranges do not overlap`() {
        val range1 = LineRange(1, 5)
        val range2 = LineRange(6, 10)
        assertFalse(range1.overlaps(range2))
        assertFalse(range2.overlaps(range1))
    }

    @Test
    fun `overlaps - touching at single point`() {
        val range1 = LineRange(1, 5)
        val range2 = LineRange(5, 10)
        assertTrue(range1.overlaps(range2))
        assertTrue(range2.overlaps(range1))
    }

    @Test
    fun `overlaps - completely disjoint`() {
        val range1 = LineRange(1, 5)
        val range2 = LineRange(10, 15)
        assertFalse(range1.overlaps(range2))
        assertFalse(range2.overlaps(range1))
    }

    // === containsAny ===

    @Test
    fun `containsAny - some lines match`() {
        val range = LineRange(5, 10)
        assertTrue(range.containsAny(setOf(1, 2, 7, 20)))
    }

    @Test
    fun `containsAny - no lines match`() {
        val range = LineRange(5, 10)
        assertFalse(range.containsAny(setOf(1, 2, 11, 20)))
    }

    @Test
    fun `containsAny - empty set`() {
        val range = LineRange(5, 10)
        assertFalse(range.containsAny(emptySet()))
    }

    // === toString ===

    @Test
    fun `toString format`() {
        assertEquals("L1-10", LineRange(1, 10).toString())
        assertEquals("L5-5", LineRange(5, 5).toString())
    }
}

package io.github.mikhailhal.sonarkt.processor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReverseDependencyGraphTest {

    // === addEdge / getCallers ===

    @Test
    fun `addEdge stores caller for callee`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge("testAdd", "Calculator.add")

        val callers = graph.getCallers("Calculator.add")
        assertEquals(setOf("testAdd"), callers)
    }

    @Test
    fun `multiple callers for same callee`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge("testAdd", "Calculator.add")
        graph.addEdge("helperB", "Calculator.add")

        val callers = graph.getCallers("Calculator.add")
        assertEquals(setOf("testAdd", "helperB"), callers)
    }

    @Test
    fun `same caller calling multiple callees`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge("main", "foo")
        graph.addEdge("main", "bar")

        assertEquals(setOf("main"), graph.getCallers("foo"))
        assertEquals(setOf("main"), graph.getCallers("bar"))
    }

    @Test
    fun `getCallers returns empty set for unknown callee`() {
        val graph = ReverseDependencyGraph()

        val callers = graph.getCallers("unknown.function")
        assertTrue(callers.isEmpty())
    }

    @Test
    fun `duplicate addEdge is idempotent`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge("testAdd", "Calculator.add")
        graph.addEdge("testAdd", "Calculator.add")

        val callers = graph.getCallers("Calculator.add")
        assertEquals(1, callers.size)
        assertEquals(setOf("testAdd"), callers)
    }

    // === getAllEdges ===

    @Test
    fun `getAllEdges returns all edges`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge("testAdd", "Calculator.add")
        graph.addEdge("helperB", "Calculator.add")
        graph.addEdge("testHelper", "helperB")

        val allEdges = graph.getAllEdges()

        assertEquals(2, allEdges.size)
        assertEquals(setOf("testAdd", "helperB"), allEdges["Calculator.add"])
        assertEquals(setOf("testHelper"), allEdges["helperB"])
    }

    @Test
    fun `getAllEdges returns empty map for empty graph`() {
        val graph = ReverseDependencyGraph()

        val allEdges = graph.getAllEdges()
        assertTrue(allEdges.isEmpty())
    }

    // === stats ===

    @Test
    fun `stats returns correct counts`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge("testAdd", "Calculator.add")
        graph.addEdge("helperB", "Calculator.add")
        graph.addEdge("testHelper", "helperB")

        val stats = graph.stats()

        // 2 callees (Calculator.add, helperB), 3 total edges
        assertEquals("Callees: 2, Total edges: 3", stats)
    }

    @Test
    fun `stats for empty graph`() {
        val graph = ReverseDependencyGraph()

        val stats = graph.stats()
        assertEquals("Callees: 0, Total edges: 0", stats)
    }
}

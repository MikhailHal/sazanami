package io.github.mikhailhal.sonarkt.processor

import io.github.mikhailhal.sonarkt.common.FunctionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReverseDependencyGraphTest {

    // Helper to create test nodes
    private fun node(fqn: String, isTest: Boolean = false) = FunctionNode(fqn, isTest)
    private fun testNode(fqn: String) = FunctionNode(fqn, isTest = true)

    // === addEdge / getCallers ===

    @Test
    fun `addEdge stores caller for callee`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge(testNode("testAdd"), node("Calculator.add"))

        val callers = graph.getCallers("Calculator.add")
        assertEquals(1, callers.size)
        assertTrue(callers.any { it.fqn == "testAdd" })
    }

    @Test
    fun `multiple callers for same callee`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge(testNode("testAdd"), node("Calculator.add"))
        graph.addEdge(node("helperB"), node("Calculator.add"))

        val callers = graph.getCallers("Calculator.add")
        assertEquals(2, callers.size)
        assertTrue(callers.any { it.fqn == "testAdd" })
        assertTrue(callers.any { it.fqn == "helperB" })
    }

    @Test
    fun `same caller calling multiple callees`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge(node("main"), node("foo"))
        graph.addEdge(node("main"), node("bar"))

        assertEquals(1, graph.getCallers("foo").size)
        assertEquals(1, graph.getCallers("bar").size)
        assertTrue(graph.getCallers("foo").any { it.fqn == "main" })
        assertTrue(graph.getCallers("bar").any { it.fqn == "main" })
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

        graph.addEdge(testNode("testAdd"), node("Calculator.add"))
        graph.addEdge(testNode("testAdd"), node("Calculator.add"))

        val callers = graph.getCallers("Calculator.add")
        assertEquals(1, callers.size)
    }

    // === getAllEdges ===

    @Test
    fun `getAllEdges returns all edges`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge(testNode("testAdd"), node("Calculator.add"))
        graph.addEdge(node("helperB"), node("Calculator.add"))
        graph.addEdge(testNode("testHelper"), node("helperB"))

        val allEdges = graph.getAllEdges()

        assertEquals(2, allEdges.size)
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

        graph.addEdge(testNode("testAdd"), node("Calculator.add"))
        graph.addEdge(node("helperB"), node("Calculator.add"))
        graph.addEdge(testNode("testHelper"), node("helperB"))

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

    // === FunctionNode isTest property ===

    @Test
    fun `caller isTest is preserved in getCallers result`() {
        val graph = ReverseDependencyGraph()

        graph.addEdge(testNode("CalculatorTest.testAdd"), node("Calculator.add"))
        graph.addEdge(node("Helper.helperB"), node("Calculator.add"))

        val callers = graph.getCallers("Calculator.add")

        val testCaller = callers.find { it.fqn == "CalculatorTest.testAdd" }
        val helperCaller = callers.find { it.fqn == "Helper.helperB" }

        assertEquals(true, testCaller?.isTest)
        assertEquals(false, helperCaller?.isTest)
    }
}

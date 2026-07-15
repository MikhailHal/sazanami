package io.github.mikhailhal.sazanami.processor

import io.github.mikhailhal.sazanami.collector.ChangedFunction
import io.github.mikhailhal.sazanami.common.FunctionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AffectedTestResolverTest {

    // テスト用のデフォルトモジュール名
    private val testModule = ":test"

    // Helper to create nodes
    private fun node(fqn: String, isTest: Boolean = false) = FunctionNode(fqn, testModule, isTest)
    private fun testNode(fqn: String) = FunctionNode(fqn, testModule, isTest = true)
    private fun changed(fqn: String) = ChangedFunction(fqn, testModule)

    // === Basic detection ===

    @Test
    fun `direct caller is test function`() {
        val graph = CallGraph()
        graph.addEdge(testNode("com.example.CalculatorTest.testAdd"), node("com.example.Calculator.add"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.Calculator.add")))

        assertEquals(setOf("com.example.CalculatorTest.testAdd"), affected)
    }

    @Test
    fun `transitive detection through helper`() {
        // testHelper -> helperB -> Calculator.add
        val graph = CallGraph()
        graph.addEdge(node("com.example.helperB"), node("com.example.Calculator.add"))
        graph.addEdge(testNode("com.example.CalculatorTest.testHelper"), node("com.example.helperB"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.Calculator.add")))

        assertEquals(setOf("com.example.CalculatorTest.testHelper"), affected)
    }

    @Test
    fun `multiple tests affected`() {
        val graph = CallGraph()
        graph.addEdge(testNode("com.example.CalculatorTest.testAdd"), node("com.example.Calculator.add"))
        graph.addEdge(testNode("com.example.CalculatorTest.testAddNegative"), node("com.example.Calculator.add"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.Calculator.add")))

        assertEquals(
            setOf(
                "com.example.CalculatorTest.testAdd",
                "com.example.CalculatorTest.testAddNegative"
            ),
            affected
        )
    }

    @Test
    fun `multiple changed functions`() {
        val graph = CallGraph()
        graph.addEdge(testNode("com.example.CalculatorTest.testAdd"), node("com.example.Calculator.add"))
        graph.addEdge(testNode("com.example.CalculatorTest.testMultiply"), node("com.example.Calculator.multiply"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(
            setOf(changed("com.example.Calculator.add"), changed("com.example.Calculator.multiply"))
        )

        assertEquals(
            setOf(
                "com.example.CalculatorTest.testAdd",
                "com.example.CalculatorTest.testMultiply"
            ),
            affected
        )
    }

    @Test
    fun `continues traversal after finding test function`() {
        // testA -> testB -> changed
        // Both should be detected because testA might call testB with different args
        val graph = CallGraph()
        graph.addEdge(testNode("com.example.SomeTest.testB"), node("com.example.changed"))
        graph.addEdge(testNode("com.example.SomeTest.testA"), testNode("com.example.SomeTest.testB"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.changed")))

        assertEquals(
            setOf(
                "com.example.SomeTest.testA",
                "com.example.SomeTest.testB"
            ),
            affected
        )
    }

    // === Edge cases ===

    @Test
    fun `empty changed functions returns empty set`() {
        val graph = CallGraph()
        graph.addEdge(testNode("com.example.CalculatorTest.testAdd"), node("com.example.Calculator.add"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(emptySet())

        assertTrue(affected.isEmpty())
    }

    @Test
    fun `changed function with no callers returns empty set`() {
        val graph = CallGraph()

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.unused")))

        assertTrue(affected.isEmpty())
    }

    @Test
    fun `cycle in graph does not cause infinite loop`() {
        // a -> b -> c -> a (cycle)
        val graph = CallGraph()
        graph.addEdge(node("b"), node("a"))
        graph.addEdge(node("c"), node("b"))
        graph.addEdge(node("a"), node("c"))
        graph.addEdge(testNode("com.example.SomeTest.testA"), node("a"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("a")))

        // Should complete without hanging
        assertEquals(setOf("com.example.SomeTest.testA"), affected)
    }

    // === @Test annotation detection ===

    @Test
    fun `function with isTest true is detected`() {
        val graph = CallGraph()
        graph.addEdge(testNode("com.example.Helper.testSomething"), node("com.example.foo"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.foo")))

        assertEquals(setOf("com.example.Helper.testSomething"), affected)
    }

    @Test
    fun `function with isTest false is not in result`() {
        val graph = CallGraph()
        graph.addEdge(node("com.example.Helper.doSomething"), node("com.example.foo"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.foo")))

        assertTrue(affected.isEmpty())
    }

    @Test
    fun `mixed test and non-test callers`() {
        val graph = CallGraph()
        graph.addEdge(testNode("com.example.CalculatorTest.testAdd"), node("com.example.Calculator.add"))
        graph.addEdge(node("com.example.Helper.helper"), node("com.example.Calculator.add"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.Calculator.add")))

        // Only the test function should be in result
        assertEquals(setOf("com.example.CalculatorTest.testAdd"), affected)
    }

    @Test
    fun `helper function leads to test`() {
        // testAdd -> helper -> Calculator.add
        val graph = CallGraph()
        graph.addEdge(node("com.example.Helper.helper"), node("com.example.Calculator.add"))
        graph.addEdge(testNode("com.example.CalculatorTest.testAdd"), node("com.example.Helper.helper"))

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf(changed("com.example.Calculator.add")))

        assertEquals(setOf("com.example.CalculatorTest.testAdd"), affected)
    }
}

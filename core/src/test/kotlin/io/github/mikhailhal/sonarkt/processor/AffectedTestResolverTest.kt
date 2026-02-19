package io.github.mikhailhal.sonarkt.processor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AffectedTestResolverTest {

    // === Basic detection ===

    @Test
    fun `direct caller is test function`() {
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.CalculatorTest.testAdd", "com.example.Calculator.add")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.Calculator.add"))

        assertEquals(setOf("com.example.CalculatorTest.testAdd"), affected)
    }

    @Test
    fun `transitive detection through helper`() {
        // testHelper -> helperB -> Calculator.add
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.helperB", "com.example.Calculator.add")
        graph.addEdge("com.example.CalculatorTest.testHelper", "com.example.helperB")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.Calculator.add"))

        assertEquals(setOf("com.example.CalculatorTest.testHelper"), affected)
    }

    @Test
    fun `multiple tests affected`() {
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.CalculatorTest.testAdd", "com.example.Calculator.add")
        graph.addEdge("com.example.CalculatorTest.testAddNegative", "com.example.Calculator.add")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.Calculator.add"))

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
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.CalculatorTest.testAdd", "com.example.Calculator.add")
        graph.addEdge("com.example.CalculatorTest.testMultiply", "com.example.Calculator.multiply")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(
            setOf("com.example.Calculator.add", "com.example.Calculator.multiply")
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
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.SomeTest.testB", "com.example.changed")
        graph.addEdge("com.example.SomeTest.testA", "com.example.SomeTest.testB")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.changed"))

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
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.CalculatorTest.testAdd", "com.example.Calculator.add")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(emptySet())

        assertTrue(affected.isEmpty())
    }

    @Test
    fun `changed function with no callers returns empty set`() {
        val graph = ReverseDependencyGraph()

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.unused"))

        assertTrue(affected.isEmpty())
    }

    @Test
    fun `cycle in graph does not cause infinite loop`() {
        // a -> b -> c -> a (cycle)
        val graph = ReverseDependencyGraph()
        graph.addEdge("b", "a")
        graph.addEdge("c", "b")
        graph.addEdge("a", "c")
        graph.addEdge("com.example.SomeTest.testA", "a")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("a"))

        // Should complete without hanging
        assertEquals(setOf("com.example.SomeTest.testA"), affected)
    }

    // === isTestFunction detection ===

    @Test
    fun `function name starting with test is detected`() {
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.Helper.testSomething", "com.example.foo")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.foo"))

        assertEquals(setOf("com.example.Helper.testSomething"), affected)
    }

    @Test
    fun `class ending with Test is detected`() {
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.CalculatorTest.add", "com.example.foo")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.foo"))

        assertEquals(setOf("com.example.CalculatorTest.add"), affected)
    }

    @Test
    fun `class ending with Tests is detected`() {
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.CalculatorTests.add", "com.example.foo")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.foo"))

        assertEquals(setOf("com.example.CalculatorTests.add"), affected)
    }

    @Test
    fun `class ending with Spec is detected`() {
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.CalculatorSpec.add", "com.example.foo")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.foo"))

        assertEquals(setOf("com.example.CalculatorSpec.add"), affected)
    }

    @Test
    fun `non-test function is not in result`() {
        val graph = ReverseDependencyGraph()
        graph.addEdge("com.example.Helper.doSomething", "com.example.foo")

        val resolver = AffectedTestResolver(graph)
        val affected = resolver.findAffectedTests(setOf("com.example.foo"))

        assertTrue(affected.isEmpty())
    }
}

package io.github.mikhailhal.sazanami

import kotlin.test.Test

/**
 * テストコードを模擬
 */
class CalculatorTest {
    @Test
    fun testAdd() {
        val calc = Calculator()
        calc.add(1, 2)
    }

    @Test
    fun testHelper() {
        helperB()
    }
}

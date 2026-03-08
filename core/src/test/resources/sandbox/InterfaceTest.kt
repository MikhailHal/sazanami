package io.github.mikhailhal.sonarkt

import kotlin.test.Test

/**
 * インターフェース経由でテストするコード
 * ICalculator 型で宣言し、compute() を呼び出す
 */
class InterfaceTest {
    @Test
    fun testCompute() {
        val calc: ICalculator = CalculatorImpl()
        calc.compute(1, 2)
    }
}

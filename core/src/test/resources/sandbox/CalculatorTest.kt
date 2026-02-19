package io.github.mikhailhal.sonarkt

/**
 * テストコードを模擬
 * 本来は src/test に置くが、PoCのため src/main に配置
 */
class CalculatorTest {
    // @Test (実際のアノテーションは省略)
    fun testAdd() {
        val calc = Calculator()
        calc.add(1, 2)
    }

    // @Test
    fun testHelper() {
        helperB()
    }
}

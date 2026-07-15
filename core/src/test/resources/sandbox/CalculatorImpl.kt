package io.github.mikhailhal.sazanami

/**
 * ICalculator の実装クラス
 * この実装が変更された場合、インターフェース経由で呼び出すテストも影響を受ける
 */
class CalculatorImpl : ICalculator {
    override fun compute(a: Int, b: Int): Int = a + b
}

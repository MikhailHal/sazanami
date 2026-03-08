package io.github.mikhailhal.sonarkt

/**
 * Calculator インターフェース
 * テストコードはこのインターフェース経由で呼び出す
 */
interface ICalculator {
    fun compute(a: Int, b: Int): Int
}

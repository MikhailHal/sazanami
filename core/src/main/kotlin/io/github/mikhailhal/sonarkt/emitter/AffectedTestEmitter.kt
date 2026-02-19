package io.github.mikhailhal.sonarkt.emitter

import io.github.mikhailhal.sonarkt.common.FunctionFqn
import java.io.File

/**
 * 影響テスト一覧を外部出力するEmitter
 *
 * 出力形式: Plain Text (1行1FQN)
 * ```
 * io.github.mikhailhal.sonarkt.CalculatorTest.testAdd
 * io.github.mikhailhal.sonarkt.CalculatorTest.testHelper
 * ```
 *
 * この形式を選んだ理由:
 * - 最も汎用的（grep, xargs, シェルスクリプトで扱いやすい）
 * - Gradle/Maven/JUnit 等の各ツールへの変換が容易
 * - 人間が読んでも理解しやすい
 */
object AffectedTestEmitter {

    /**
     * 影響テスト一覧を文字列として出力
     */
    fun emit(tests: Set<FunctionFqn>): String {
        return tests.sorted().joinToString("\n")
    }

    /**
     * 影響テスト一覧をファイルに出力
     *
     * @param tests 影響テストのFQN集合
     * @param outputFile 出力先ファイル
     */
    fun emitToFile(tests: Set<FunctionFqn>, outputFile: File) {
        val content = emit(tests)
        outputFile.writeText(content)
    }

    /**
     * 影響テスト一覧を標準出力に出力
     */
    fun emitToStdout(tests: Set<FunctionFqn>) {
        println(emit(tests))
    }
}

package io.github.mikhailhal.sonarkt.processor

import io.github.mikhailhal.sonarkt.common.FunctionFqn

/**
 * 変更された関数から影響を受けるテストを特定する
 *
 * アルゴリズム: 逆方向BFS
 * 1. 変更された関数(callee)をキューに入れる
 * 2. キューからcalleeを取り出し、calleeを呼んでいる関数(callers)を取得
 * 3. callerがテスト関数（@Test付き）なら結果に追加
 * 4. callerがテスト関数でなければキューに追加して更に遡る
 * 5. seenで重複訪問を防止
 *
 * 設計方針: 保守的アプローチ（安全側に倒す）
 *
 * テスト関数を見つけても探索を止めず、推移的に全ての呼び出し元を辿る。
 *
 * 例: testA → testB → C（変更対象）の依存関係がある場合
 *   - C が変更されると testB は再実行対象（直接呼んでいるため）
 *   - testA も再実行対象に含める（testB 経由で C に依存しているため）
 *
 * 「testB が通れば testA は大丈夫では？」という最適化は行わない。
 * 理由: testA が testB を「testB のテストとは異なる引数」で呼んでいる可能性がある。
 *
 * 具体例:
 *   ```
 *   fun divide(a: Int, b: Int): Int {
 *       if (b == 0) throw IllegalArgumentException()  // ← 変更: 以前は 0 を返していた
 *       return a / b
 *   }
 *   fun calculate(x: Int) = divide(100, x)
 *   fun process() = calculate(0)  // ← 0 を渡している
 *
 *   @Test fun testCalculate() { calculate(2) }  // x=2 でテスト → パスする
 *   @Test fun testProcess() { process() }       // x=0 → divide(100, 0) → 例外で落ちる
 *   ```
 *
 * この例では divide の変更により:
 *   - testCalculate は x=2 でテストしているのでパスする
 *   - testProcess は x=0 で呼んでいるため、testCalculate が通っても落ちる
 *
 * テストが全パスをカバーしている保証がない限り、この最適化は安全ではない。
 * よって、推移的に全ての呼び出し元を影響範囲に含める保守的アプローチを採用している。
 */
class AffectedTestResolver(
    private val graph: ReverseDependencyGraph
) {
    /**
     * 変更された関数から影響を受けるテストを特定
     *
     * @param changedFunctions 変更された関数のFQN集合
     * @return 影響を受けるテスト関数のFQN集合
     */
    fun findAffectedTests(changedFunctions: Set<FunctionFqn>): Set<FunctionFqn> {
        val affectedTests = mutableSetOf<FunctionFqn>()
        val seen = mutableSetOf<FunctionFqn>()
        val queue = ArrayDeque(changedFunctions)

        while (queue.isNotEmpty()) {
            val callee = queue.removeFirst()

            /**
             * 閲覧済み関数の場合はスキップ。
             * 例えば、a()とb()からc()を呼んでいる場合、a()でc()を確認済みであればb()の時は確認する必要がない。
             */
            if (callee in seen) continue
            seen.add(callee)

            // callee(関数本体)を呼んでいる全関数を取得
            val callers = graph.getCallers(callee)
            for (caller in callers) {
                // @Testアノテーションが付与されている関数のみを影響テストとして追加
                if (caller.isTest) {
                    affectedTests.add(caller.fqn)
                }

                /**
                 * テスト関数であっても探索を継続する
                 * 理由: @Test付きテスト関数が他のテストから呼ばれるケース
                 * 例:
                 *   @Test fun testA() { ... }
                 *   @Test fun testB() { testA() }  // testBがtestAを呼ぶ
                 *
                 * testA が変更された場合、testB も影響を受ける。
                 * よって、テスト関数でも探索を止めずに継続する。
                 */
                queue.add(caller.fqn)
            }
        }
        return affectedTests
    }
}

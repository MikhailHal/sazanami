# Low-Level-Design

## Data Models

```kotlin
// 関数の識別子 (FQN)
// 例: "io.github.sazanami.Calculator.add"
typealias FunctionFqn = String

// 逆方向依存グラフ
// Key: 呼ばれる関数 (callee)
// Value: 呼んでいる関数たち (callers)
class ReverseDependencyGraph {
    private val edges: MutableMap<FunctionFqn, MutableSet<FunctionFqn>>

    // callee を呼んでいる caller を登録
    fun addEdge(caller: FunctionFqn, callee: FunctionFqn)

    // callee を呼んでいる関数一覧を取得
    fun getCallers(callee: FunctionFqn): Set<FunctionFqn>
}
```

## Graph Building (Phase 1)

**目的**: プロジェクト全体を解析し、逆方向依存グラフを構築する

### 解析対象: main + test の両方

グラフは **main コード (src/main) と test コード (src/test) の両方** から構築する。

```
src/
├── main/kotlin/          ← 解析対象
│   ├── Calculator.kt
│   └── Helper.kt
└── test/kotlin/          ← 解析対象
    └── CalculatorTest.kt
```

**理由**:
- **callee** (呼ばれる側): 主に main のプロダクションコード
- **caller** (呼ぶ側): main のコード **または** test のコード

test コードも解析しないと「どのテスト関数がどの関数を呼んでいるか」が分からない。

### 処理フロー

```
1. main と test の全KtFileを走査
2. 各ファイルでKtCallExpressionを見つける
3. caller = この呼び出しを含む関数 (親のKtNamedFunctionを探す)
4. callee = resolveToCall()で解決した関数のFQN
5. graph.addEdge(caller, callee) で登録
```

### 構築されるデータ例

```kotlin
// main/Calculator.kt
class Calculator {
    fun add(a: Int, b: Int) = a + b
}

// main/Helper.kt
fun helperB() {
    Calculator().add(1, 2)  // helperB → Calculator.add
}

// test/CalculatorTest.kt
class CalculatorTest {
    @Test
    fun testAdd() {
        Calculator().add(1, 2)  // testAdd → Calculator.add
    }
    @Test
    fun testHelper() {
        helperB()  // testHelper → helperB
    }
}
```

↓ 構築される逆依存グラフ:

```kotlin
edges = {
    "Calculator.<init>" → ["testAdd", "helperB"],  // テストと通常関数が混在
    "Calculator.add"    → ["testAdd", "helperB"],
    "helperB"           → ["testHelper"],
}
```

### ポイント

- グラフはプロジェクト全体で **一度だけ** 構築
- 変更があるたびに再構築するのではなく、キャッシュして再利用
- キーは callee、バリューは callers の集合
- callers には **テスト関数と通常関数の両方** が含まれる

## Affected Test Resolution (Phase 2)

**目的**: 変更された関数から、影響を受けるテストを特定する

**アルゴリズム**: 逆方向BFS + seen

```kotlin
fun findAffectedTests(
    changedFunctions: Set<FunctionFqn>,
    graph: ReverseDependencyGraph
): Set<FunctionFqn> {
    val affected = mutableSetOf<FunctionFqn>()
    val seen = mutableSetOf<FunctionFqn>()
    val queue = ArrayDeque(changedFunctions)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current in seen) continue
        seen.add(current)

        // currentを呼んでいる関数を取得
        val callers = graph.getCallers(current)
        for (caller in callers) {
            if (isTestFunction(caller)) {
                affected.add(caller)
            }
            queue.add(caller)  // 更に遡って探索
        }
    }
    return affected
}
```

### 探索例

```
変更された関数: [Calculator.add]

Step 1: Calculator.add の呼び出し元を探す
  → [testAdd, helperB] を発見
  → testAdd はテスト関数 → affected に追加
  → helperB はテストじゃない → queue に追加して更に遡る
  → seen: {Calculator.add}

Step 2: helperB の呼び出し元を探す
  → [testHelper] を発見
  → testHelper はテスト関数 → affected に追加
  → seen: {Calculator.add, helperB}

Step 3: testAdd, testHelper の呼び出し元を探す
  → [] (テスト関数は通常呼ばれない = ルート)
  → 探索終了

結果: affected = {testAdd, testHelper}
```

### なぜテスト関数まで遡るのか？

通常の関数（helperB など）は **単体で実行できない**。
テスト関数（@Test がついた関数）だけが JUnit などのテストランナーで実行可能。

```
Calculator.add が変更された
  ↓
helperB の動作が変わる可能性がある
  ↓
helperB を検証したいが、helperB は単体実行できない
  ↓
helperB を呼んでいる testHelper を実行して検証する
```

### なぜBFS?

- 全ての影響経路を漏れなく探索
- `seen` で循環参照や重複訪問を防止
- DFSでも可能だが、BFSは「影響の近さ」順に探索できる

## Kotlin Analysis API Usage

**セッション初期化**:
```kotlin
val session = buildStandaloneAnalysisAPISession(disposable) {
    buildKtModuleProvider {
        platform = JvmPlatforms.defaultJvmPlatform
        addModule(buildKtSourceModule {
            moduleName = "module-name"
            addSourceRoot(Paths.get("/path/to/sources"))
        })
    }
}
```

**関数呼び出しの解決**:
```kotlin
// KtCallExpression (構文上の呼び出し) から FQN を取得
analyze(expression) {
    val callInfo = expression.resolveToCall()
    val symbol = callInfo?.singleFunctionCallOrNull()?.symbol
    val fqn = symbol?.callableId?.asSingleFqName()  // "io.github.sazanami.Calculator.add"
}
```

**PSI走査 (Visitor Pattern)**:
```kotlin
file.accept(object : KtTreeVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        // ここで各関数呼び出しを処理
        super.visitCallExpression(expression)  // 子ノードも走査
    }
})
```

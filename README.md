<p align="center">
  <h1 align="center">sazanami</h1>
  <p align="center">
    <strong>Affected Test Selection for Kotlin</strong>
  </p>
  <p align="center">
    <a href="https://plugins.gradle.org/plugin/io.github.mikhailhal.sazanami"><img src="https://img.shields.io/gradle-plugin-portal/v/io.github.mikhailhal.sazanami?style=flat-square&logo=gradle&label=Gradle%20Plugin" alt="Gradle Plugin Portal"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square" alt="License"></a>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.1.20-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
    <a href="#"><img src="https://img.shields.io/github/actions/workflow/status/MikhailHal/sazanami/ci.yml?style=flat-square&logo=github" alt="CI"></a>
    <!-- <a href="https://github.com/MikhailHal/sazanami/actions/workflows/check-ka-api.yml"><img src="https://img.shields.io/github/actions/workflow/status/MikhailHal/sazanami/check-ka-api.yml?style=flat-square&logo=kotlin&logoColor=white&label=KA%20API" alt="Kotlin Analysis API Status"></a> -->
  </p>
<br>

**sazanami** analyzes your code changes and identifies which tests are affected, enabling faster feedback loops by running only the tests that matter.

## Features

- **Static Analysis** — Uses Kotlin Analysis API to build accurate call graphs
- **Git Integration** — Automatically detects changes from git diff
- **Gradle Plugin** — Simple integration with `./gradlew affectedTests`
- **CLI Support** — Standalone command-line interface for CI pipelines

## Installation

### Gradle Plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.mikhailhal.sazanami") version "0.3.0"
}
```

### CLI

```bash
./gradlew :core:installDist

git diff --unified=0 | ./core/build/install/core/bin/core --project /path/to/your/project
```

## Usage

### Gradle

Run the task to see affected tests:

```bash
./gradlew affectedTests
```

Output:
```
Affected tests:
  com.example.UserServiceTest.testCreateUser
  com.example.UserRepositoryTest.testSave
```

### CLI

Pipe git diff output to the CLI:

```bash
git diff --unified=0 HEAD~1 | ./core/build/install/core/bin/core --project .
```

## How It Works

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  git diff   │ ──▶ │ Changed Function │ ──▶ │  Call Graph     │
│             │     │    Collector     │     │    Builder      │
└─────────────┘     └──────────────────┘     └─────────────────┘
                                                      │
                                                      ▼
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Affected   │ ◀── │ Affected Test    │ ◀── │  Reverse        │
│   Tests     │     │    Resolver      │     │   Traversal     │
└─────────────┘     └──────────────────┘     └─────────────────┘
```

1. **Parse Diff** — Extract changed line ranges from `git diff --unified=0`
2. **Identify Changed Functions** — Map line changes to function FQNs using PSI
3. **Build Call Graph** — Analyze all source files to build caller→callee relationships
4. **Reverse Traverse** — Find all test functions that transitively call changed functions

## Kotlin Analysis API: KaSymbol Cheat Sheet

sazanami が扱う `KaSymbol` 階層の地図 (Analysis API 2.1.20 で検証済み)。
「コード上のどの構文が、どのシンボル型に解決されるか」の対応表:

```
KaSymbol                                  ← 全シンボルの根
└── KaDeclarationSymbol                   ← 宣言されたもの全般
    ├── KaClassifierSymbol                ← 「型」を宣言する側
    │   └── KaClassSymbol                 ── class Repository の宣言そのもの / 型位置の Repository
    └── KaCallableSymbol                  ← 呼べる・参照できるもの (sazanami の主戦場)
        │    共通API: callableId          → FQN取得 (コンストラクタ/ローカルは null)
        │             allOverriddenSymbols → オーバーライド元を遡る (Collector が使用)
        │
        ├── KaFunctionSymbol
        │   ├── KaNamedFunctionSymbol     ── repository.load() の load / repository::loadRef の loadRef
        │   ├── KaConstructorSymbol       ── Repository() / ::Repository
        │   │                                (callableId が null → containingClassId から <init> FQN を組む)
        │   ├── KaAnonymousFunctionSymbol ── { it + 1 } (ラムダそれ自体)
        │   └── KaPropertyAccessorSymbol
        │       ├── KaPropertyGetterSymbol ── val title get() = ... の get()
        │       └── KaPropertySetterSymbol ── var name set(v) {...} の set()
        │
        └── KaVariableSymbol
            ├── KaPropertySymbol          ── val label = "ready" / viewModel.uiState / repository::label
            ├── KaLocalVariableSymbol     ── fun f() { val x = 1; x } の x
            └── KaParameterSymbol
                └── KaValueParameterSymbol ── fun transform(input: String) の input
```

sazanami の解決入口と、返ってくるシンボル型の対応:

| 解決入口 | 対象の構文 | 返ってくる型 |
|---|---|---|
| `resolveToCall().singleFunctionCallOrNull().symbol` | `load()` / `Repository()` | `KaNamedFunctionSymbol` / `KaConstructorSymbol` |
| `resolveToCall().singleVariableAccessCall().symbol` | `viewModel.uiState` (参照) | `KaVariableSymbol` のいずれか |
| `mainReference.resolveToSymbol()` | `::loadRef` / `::Repository` / `::label` | 上記すべての可能性 |
| `symbol.allOverriddenSymbols` | 実装メソッド → インターフェース | `KaCallableSymbol` |

## Requirements

- **JDK 21** or later
- **Kotlin 2.1.20** or later
- **Gradle 8.0** or later (for plugin)

## License

```
Copyright 2025 sazanami contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

See [LICENSE](LICENSE) for the full text.

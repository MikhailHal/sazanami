# High-Level-Design
## Overview
変更された関数から影響を受けるテストを特定し、必要なテストのみを実行することでCI時間を短縮する。

## Motivations
* 現状: 多くのプロジェクトで全テスト実行 → CI遅い
* 大企業は内製ツール持ってるがOSSで公開されてない
* Gradle Enterprise は有料
* Kotlin/Android 特化のOSSがない

## Goals / Non-Goals
### Goals
* git diffから変更された関数を検出
* 関数単位の依存グラフ（逆向き）を構築
* 影響を受けるテストのリストを出力
* Gradle タスクとして実行可能

### 追跡対象（v1スコープ）
| 対象 | 対応 | 理由 |
|------|------|------|
| 通常の関数呼び出し | ✅ | 基本 |
| インターフェース経由 | ✅ | 実務で多用、全実装クラスを影響範囲に含める（保守的） |
| ラムダ内の呼び出し | ✅ | Kotlinの基本パターン |
| カスタムgetter/setter | ✅ | 関数と同等のロジックを持つ |

### Non-Goals（v1対象外）
| 対象 | 理由 |
|------|------|
| 関数参照 `::func` | 使用頻度が低め、v2で検討 |
| Delegated property (`by lazy` 等) | 複雑、v2で検討 |
| リフレクション経由 | 静的解析の限界 |
| テスト実行自体 | 影響テストのリスト出力まで |

## Architecture

### Flow
```
収集 → 加工 → 出力
```

### Overall Components
```
┌─────────────────┐
│   Collector     │  収集
│  (Git Diff)     │
└────────┬────────┘
         │ 変更関数リスト
         ▼
┌─────────────────┐
│  Processor      │  加工
│                 │
│  ┌───────────┐  │
│  │ Graph     │  │  Phase 1: 依存グラフ構築
│  │ Builder   │  │  (Kotlin Analysis API)
│  └─────┬─────┘  │
│        │        │
│  ┌─────▼─────┐  │
│  │ Affected  │  │  Phase 2: 影響テスト特定
│  │ Resolver  │  │  (逆向きBFS + seen)
│  └───────────┘  │
└────────┬────────┘
         │ 影響テスト集合
         ▼
┌─────────────────┐
│   Emitter       │  出力
│  (txt/json)     │
└─────────────────┘
```

### Cache Strategy
```
初回ビルド:
  全ソース解析 → グラフ構築 → .sazanami/graph.bin 保存

差分ビルド:
  変更ファイルのみ再解析 → グラフ更新 → キャッシュ更新

CI実行時:
  キャッシュ読み込み → 探索のみ（高速）
```

### Gradle Integration
```kotlin
// build.gradle.kts
plugins {
    id("io.github.xxx.sazanami")
}

sazanami {
    // 出力形式
    outputFormat = "txt" // or "json"
    
    // 対象ソースセット
    targetSourceSets = listOf("main", "test")
    
    // キャッシュ場所
    cacheDir = file(".sazanami")
}
```

## Core Components

| Component | Input | Output | Responsibility |
|---------------|------|------|------|
| Collector | git diff | `Set<MethodSignature>` | 変更された関数を特定 |
| GraphBuilder | ソースコード | `DependencyGraph` | 全関数の依存グラフ構築 |
| AffectedResolver | `Set<MethodSignature>` + `DependencyGraph` | `Set<MethodSignature>` | 逆向き探索で影響テスト特定 |
| Emitter | `Set<MethodSignature>` | ファイル出力 | 結果を指定形式で出力 |

## Gradle Tasks

| タスク | 説明 |
|--------|------|
| `sazanamiBuildGraph` | 依存グラフ構築・キャッシュ保存 |
| `affectedTests` | 影響テスト出力 |

## Output Example
```txt
# affected_tests.txt
com.example.UserViewModelTest#testLoadUser
com.example.UserViewModelTest#testUpdateUser
com.example.UserScreenTest#testRender
```
```json
// affected_tests.json
{
  "affectedTests": [
    {
      "className": "com.example.UserViewModelTest",
      "methodName": "testLoadUser"
    }
  ]
}
```
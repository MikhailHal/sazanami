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
    id("io.github.mikhailhal.sazanami") version "0.1.0"
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

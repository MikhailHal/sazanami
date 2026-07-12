plugins {
    application
}

// Kotlin 2.1.20 - detektと同様の構成
// 参照: https://github.com/detekt/detekt/blob/main/gradle/libs.versions.toml
val kotlinVersion = "2.1.20"

application {
    mainClass.set("io.github.mikhailhal.sazanami.MainKt")
}

repositories {
    mavenCentral()
    // Kotlin Analysis API用 (KT-56203が解決されるまで必要)
    maven("https://redirector.kotlinlang.org/maven/intellij-dependencies")
}

dependencies {
    // Kotlin Compiler (PSIクラスを含む)
    implementation("org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion")

    // Caffeine - Analysis APIのキャッシュに必要
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")

    // Kotlin Analysis API - detektの構成に基づく
    // -for-ide アーティファクトはシャドウ(全依存関係を内包)されているため isTransitive = false が必要
    implementation("org.jetbrains.kotlin:analysis-api-for-ide:$kotlinVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-k2-for-ide:$kotlinVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-platform-interface-for-ide:$kotlinVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-impl-base-for-ide:$kotlinVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-standalone-for-ide:$kotlinVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlin:low-level-api-fir-for-ide:$kotlinVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlin:symbol-light-classes-for-ide:$kotlinVersion") { isTransitive = false }

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

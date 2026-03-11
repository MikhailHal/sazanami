plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.mikhailhal"
version = "0.2.0"

repositories {
    maven("https://redirector.kotlinlang.org/maven/intellij-dependencies")
}

dependencies {
    compileOnly(gradleApi())
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://github.com/MikhailHal/sonar-kt"
    vcsUrl = "https://github.com/MikhailHal/sonar-kt"

    plugins {
        create("sonarKt") {
            id = "io.github.mikhailhal.sonarkt"
            implementationClass = "io.github.mikhailhal.sonarkt.gradle.SonarKtPlugin"
            displayName = "sonar-kt"
            description = "Affected test selection for Kotlin - run only tests that matter"
            tags = listOf("kotlin", "testing", "affected-tests", "test-selection")
        }
    }
}

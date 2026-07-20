plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "io.github.mikhailhal"
version = "0.2.2"

repositories {
    maven("https://redirector.kotlinlang.org/maven/intellij-dependencies")
}

dependencies {
    compileOnly(gradleApi())
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://github.com/MikhailHal/sazanami"
    vcsUrl = "https://github.com/MikhailHal/sazanami"

    plugins {
        create("sazanami") {
            id = "io.github.mikhailhal.sazanami"
            implementationClass = "io.github.mikhailhal.sazanami.gradle.SazanamiPlugin"
            displayName = "sazanami"
            description = "Affected test selection for Kotlin - run only tests that matter"
            tags = listOf("kotlin", "testing", "affected-tests", "test-selection")
        }
    }
}

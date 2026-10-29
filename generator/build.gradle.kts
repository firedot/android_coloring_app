import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: must never depend on android.*
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

tasks.test {
    // ./gradlew :generator:test -PrecordGoldens=true rewrites the golden JSON files.
    systemProperty("recordGoldens", providers.gradleProperty("recordGoldens").getOrElse("false"))
    maxHeapSize = "1g"
}

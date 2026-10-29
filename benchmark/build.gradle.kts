// Macrobenchmarks for :app: stroke latency and frame time. Run on a real device:
// ./gradlew :benchmark:connectedBenchmarkAndroidTest
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.fireworm.colourfun.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // System tracing with Perfetto needs API 29.
        minSdk = 29
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    // Only the benchmark variant makes sense: it targets the app's benchmark build.
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

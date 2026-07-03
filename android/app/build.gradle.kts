plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aibook.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aibook.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:reader"))

    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    debugImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.readium.kotlin-toolkit:readium-shared:3.1.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.1.0") {
        exclude(group = "com.mcxiaoke.koi", module = "core")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

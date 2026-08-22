plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

val releaseSigningProperties = Properties().apply {
    val file = rootProject.file("release-signing.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}
android {
    namespace = "com.lovelyreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lovelyreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 82
        versionName = "0.8.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile", "lovely-reader-release.jks"))
            storePassword = releaseSigningProperties.getProperty("storePassword", "")
            keyAlias = releaseSigningProperties.getProperty("keyAlias", "lovely-reader")
            keyPassword = releaseSigningProperties.getProperty("keyPassword", "")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            // The diagnostic build upgrades the installed release without clearing app data.
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerExecutionStrategy.set(org.jetbrains.kotlin.gradle.tasks.KotlinCompilerExecutionStrategy.IN_PROCESS)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-cast:1.4.1")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")

    implementation("org.json:json:20231013")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val compressSplashImages by tasks.registering(Exec::class) {
    val inputDir = rootProject.rootDir.parentFile.resolve("图片素材")
    val outputDir = file("src/main/assets/splash")

    inputs.dir(inputDir)
    outputs.dir(outputDir)

    doFirst {
        outputDir.mkdirs()
    }

    commandLine(
        "py",
        rootProject.rootDir.parentFile.resolve("scripts/compress_splash_images.py").absolutePath,
        inputDir.absolutePath,
        outputDir.absolutePath
    )

    onlyIf {
        !outputDir.exists() || outputDir.listFiles()?.isEmpty() != false
    }
}

tasks.configureEach {
    if (name == "mergeDebugAssets" || name == "mergeReleaseAssets" ||
        name.startsWith("merge") && name.contains("Resources")
    ) {
        dependsOn(compressSplashImages)
    }
}

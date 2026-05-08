@file:OptIn(ExperimentalEncodingApi::class)

import buildlogic.gitHash
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

plugins {
    id("com.android.application")
    id("com.mikepenz.aboutlibraries.plugin.android")
    kotlin("android")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("logging.level", "WARN")
}

android {
    namespace = "com.github.zly2006.zhihu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.zly2006.zhplus"
        minSdk = 27
        targetSdk = 35
        versionCode = property("app.versionCode").toString().toIntOrNull() ?: 1
        versionName = property("app.versionName").toString()

        testInstrumentationRunner = "com.github.zly2006.zhihu.ZhihuInstrumentedTestRunner"
    }

    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
            buildConfigField("boolean", "IS_LITE", "false")
        }
        create("lite") {
            dimension = "version"
            buildConfigField("boolean", "IS_LITE", "true")
            applicationIdSuffix = ".lite"
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "zh")
    }

    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("generated/androidTestSecrets"))

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // ========== 👇 修改点 1/2：signingConfigs 固定签名配置 ==========
    signingConfigs {
        // 新增一个固定的 release 签名配置，使用我们生成的 zhihu.jks
        create("release") {
            storeFile = file("zhihu.jks")
            storePassword = "android"     // 与工作流中生成的一致
            keyAlias = "zhihu"            // 与工作流中生成的一致
            keyPassword = "android"       // 与工作流中生成的一致
        }

        // 保留原作者通过环境变量动态注入签名的能力（不影响我们）
        if (System.getenv("signingKey") != null) {
            register("env") {
                storeFile = file("zhihu.jks").apply {
                    writeBytes(Base64.decode(System.getenv("signingKey")))
                }
                storePassword = System.getenv("keyStorePassword")
                keyAlias = System.getenv("keyAlias")
                keyPassword = System.getenv("keyPassword")
            }
        }
    }
    // ========== 修改结束 ==========

    buildTypes {
        val gitHash = gitHash(rootProject.projectDir)
        debug {
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        }
        // ========== 👇 修改点 2/2：release 构建类型固定使用 release 签名 ==========
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            // 直接使用我们上面定义的 release 签名配置
            signingConfig = signingConfigs.getByName("release")
        }
        // ========== 修改结束 ==========
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes +=
                listOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/**/LICENSE",
                    "META-INF/**/LICENSE.txt",
                    "META-INF/proguard/*",
                    "**.kotlin_module",
                    "kotlin-tooling-metadata.json",
                    "DebugProbesKt.bin",
                )
        }
    }

    androidComponents {
        beforeVariants(selector().all()) { variantBuilder ->
            val flavorName = variantBuilder.flavorName
            if (variantBuilder.buildType == "release") {
                val minify =
                    when (flavorName) {
                        "lite" -> true
                        else -> false
                    }
                variantBuilder.isMinifyEnabled = minify
                variantBuilder.shrinkResources = minify
            }
        }
    }
}

// ... 后面依赖部分完全不变，无需修改 ...

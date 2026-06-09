plugins {
    alias(libs.plugins.agp.lib)
    `maven-publish`
}

android {
    namespace = "io.github.kulipai.luahook.ext.layout"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("proguard-rules.pro")
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
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.github.kulipai"
                artifactId = "luahook-ext-layout"
                version = project.version.toString()
                from(components["release"])
            }
        }
    }
}

dependencies {
    // Core luahook dependencies
    api(project(":luahook-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    
    // UI dependencies moved from core
    implementation(libs.material)
    implementation(libs.androidx.preference.ktx)

    // Coil dependencies moved from core
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)
}

plugins {
    alias(libs.plugins.agp.lib)
//    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "io.github.kulipai.luahook.ext.dexkit"
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
                artifactId = "luahook-ext-dexkit"
                version = project.version.toString()
                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    
    // Core luahook dependency
    api(project(":luahook-core"))
    
    // DexKit & XpHelper dependencies
    implementation(libs.xphelper)
    implementation(libs.dexkit)
}

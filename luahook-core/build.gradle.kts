plugins {
    alias(libs.plugins.agp.lib)
//    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "io.github.kulipai.luahook.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
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
                artifactId = "luahook-core"
                version = project.version.toString()
                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    
    // Kotlin & reflection
    implementation(libs.kotlin.reflect)
    
    // OkHttp dependency for LuaHttp
    implementation(libs.okhttp)
    
    // Preference components
    implementation(libs.androidx.preference.ktx)

    
    // AndroLua engine dependency
    api(project(":androlua"))
    
    // Xposed API (compileOnly)
    compileOnly("io.github.libxposed:api:101.0.1")
    compileOnly(fileTree(mapOf("dir" to "compileOnly", "include" to listOf("*.jar"))))
    
    // Fallback compileOnly for traditional Xposed API
    compileOnly(libs.androidx.annotation)
}

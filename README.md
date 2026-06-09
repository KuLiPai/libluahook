# libluahook

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![LibXposed](https://img.shields.io/badge/LibXposed-101.0.1+-purple.svg)](https://github.com/libxposed/api)

`libluahook` is an extremely lightweight, modular, and high-performance Lua execution and Xposed hooking runtime engine designed for Android Xposed modules. It empowers developers to load dynamic, variable hook logic using pure Lua scripts, keeping compiled code changes separate from runtime lifecycles.

It is fully decoupled, carrying **zero rigid storage path dependencies** and **zero heavy UI/image-loader library wrappers** by default. The absolute core has zero dependencies on Google Material or Coil. Integrators are free to load scripts from any customized source and pull only the specific extensions they need.

---

## 🏗️ Architecture & Modules

`libluahook` is structured in a highly decoupled, extension-based multi-module design to allow developers maximum integration freedom:

```
libluahook/ (Root Project)
├── libs/                                # Transitive runtime dependencies
│   └── luajpp_nocglib.jar               # Core LuaJ + DexMaker dynamic compile engine
├── androlua/                            # Module: Minimal core Lua engine (no Sora editor UI/textmate)
├── luahook-core/                        # Module: Clean minimal Xposed hook runtime (Reflection-based, no Material/Coil)
├── luahook-ext-layout/                  # Module (Extension): Android XML Layout trees & custom list/recycler adapters (Material & Coil)
├── luahook-ext-dexkit/                  # Module (Extension): DexKit and XpHelper scanner hooks
└── luahook-ext-native/                  # Module (Extension): Native Dobby hook & JNI cpp memory hooks
```

---

## 🛠️ Getting Started

### 1. Repository Resolution
Prioritize your Maven local repository if testing packages locally, and add JCenter/JitPack as fallbacks:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal() // Prioritize locally published modules
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Declare Dependencies
In your module's `app/build.gradle.kts`:

```kotlin
dependencies {
    // 1. Add the absolute core Lua hook environment (implicitly carries androlua release 1.0.0-alpha01)
    implementation("com.github.kulipai:luahook-core:1.0.0-alpha01")
    
    // 2. Add modern LibXposed compiler API
    compileOnly("io.github.libxposed:api:101.0.1")

    // 3. (Optional) Add Layout & Custom Adapter support (pulls Material and Coil)
    implementation("com.github.kulipai:luahook-ext-layout:1.0.0-alpha01")

    // 4. (Optional) Add DexKit scanner support
    implementation("com.github.kulipai:luahook-ext-dexkit:1.0.0-alpha01")

    // 5. (Optional) Add Native JNI Dobby inline hook support
    implementation("com.github.kulipai:luahook-ext-native:1.0.0-alpha01")
}
```

---

## 🚀 Usage Guide

Initialize the engine once inside your module's entry point, and execute your scripts on-demand. The engine accepts standard Xposed framework objects and automatically performs type wrapping internally.

### Option A: Modern LibXposed (`XposedModule` / API 101.0.1+)
```kotlin
package com.example.myxposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.kulipai.luahook.hook.entry.LuaHookEngine
import io.github.kulipai.luahook.ext.layout.registerLayout
import io.github.kulipai.luahook.ext.dexkit.registerDexKit
import io.github.kulipai.luahook.ext.nativelib.registerNative

class MyNewHook : XposedModule() {

    override fun onPackageReady(lpparam: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(lpparam)
        
        // 1. Initialize the engine once with your XposedModule instance and lpparam
        LuaHookEngine.init(
            xposedModule = this,
            param = lpparam
        )
        
        // 2. Load and run your script content on-demand
        val scriptText = """
            log("Running in process: " .. LPParam.processName)
            -- Expose hooks here!
        """.trimIndent()
        
        val globals = LuaHookEngine.run(scriptText, "[MY_SCRIPT]")
        
        // 3. (Optional) Expose your extension APIs to the global Lua script runner
        globals.registerLayout()  // 👈 Exposes loadlayout() and adapters to your Lua runtime
        globals.registerDexKit()
        globals.registerNative()
    }
}
```

### Option B: Legacy Xposed (`IXposedHookLoadPackage`)
```kotlin
package com.example.myxposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kulipai.luahook.hook.entry.LuaHookEngine
import io.github.kulipai.luahook.ext.layout.registerLayout

class MyLegacyHook : IXposedHookZygoteInit, IXposedHookLoadPackage {
    
    private lateinit var suparam: IXposedHookZygoteInit.StartupParam

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        this.suparam = startupParam
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. Initialize the engine once with legacy parameters
        LuaHookEngine.init(
            xposedModule = this,
            param = lpparam,
            suparam = suparam
        )
        
        // 2. Run your Lua code
        val scriptText = "log('Hello from legacy Xposed Lua!')"
        val globals = LuaHookEngine.run(scriptText, "[LEGACY_SCRIPT]")
        
        // 3. Register layout features
        globals.registerLayout()
    }
}
```

---

## 🛠️ Local Development & Compilation

To build the library project locally and publish `.aar` archives directly to your host's local Maven repository (`~/.m2/repository`):

```bash
# Clone the repository
git clone https://github.com/kulipai/libluahook.git
cd libluahook

# Build and publish to mavenLocal
./gradlew publishToMavenLocal
```

---

## 📄 License

```
Copyright 2026 KuLiPai

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

# libluahook

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![LibXposed](https://img.shields.io/badge/LibXposed-101.0.1+-purple.svg)](https://github.com/libxposed/api)

`libluahook` 是一个为 Android Xposed 模块设计的**极简、轻量、高能**的 Lua 脚本执行和 Xposed Hook 运行时框架。它让开发者可以使用纯 Lua 脚本动态编写可变 hook 逻辑，将代码实现与 hook 声明生命周期彻底解耦。

框架经过高度提纯，**不带任何本地存储路径读取依赖**，**核心层也不依赖任何重的 UI 或图片加载库**。纯净底座模块（`luahook-core`）对 Google Material 以及 Coil 库的依赖为零。集成模块的开发者只需在有需要时按需引入特定的附加扩展依赖即可。

---

## 🏗️ 架构与模块划分

`libluahook` 采用高度解耦的插件式多模块设计，赋予集成模块最大的自由度：

```
libluahook/ (Root Project)
├── libs/                                # 内部传递运行期依赖
│   └── luajpp_nocglib.jar               # 核心 LuaJ + DexMaker 动态编译引擎
├── androlua/                            # 模块: 精简版核心 Lua 引擎（剥离了 Sora 编辑器 UI 和 TextMate 语法高亮）
├── luahook-core/                        # 模块: 纯净底座 Xposed Hook 运行时（完全基于反射，不依赖 Material/Coil）
├── luahook-ext-layout/                  # 模块 (扩展): Android XML 布局解析及自定义列表/网格适配器（依赖 Material & Coil）
├── luahook-ext-dexkit/                  # 模块 (扩展): 引入 DexKit & XpHelper 扫描器支持
└── luahook-ext-native/                  # 模块 (扩展): 引入 JNI 动态加载和 Dobby Native 内存 hook 支持
```

---

## 🛠️ 快速开始

### 1. 配置仓库解析源
优先将您的本地 Maven 仓库 (`mavenLocal()`) 放入首位以支持本地包测试：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal() // 👈 优先解析本地发布的 maven 库
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. 引入 Gradle 依赖
在您的模块 `app/build.gradle.kts` 中声明依赖：

```kotlin
dependencies {
    // 1. 引入绝对纯净的核心底座 (已传递 api 依赖 ':androlua')
    implementation("com.github.kulipai.libluahook:luahook-core:1.0.0-alpha01")
    
    // 2. 引入现代的 LibXposed 编译器 API (compileOnly)
    compileOnly("io.github.libxposed:api:101.0.1")

    // 3. (可选) 引入 XML 布局及各类列表适配器支持 (引入 Material & Coil 依赖)
    implementation("com.github.kulipai.libluahook:luahook-ext-layout:1.0.0-alpha01")

    // 4. (可选) 引入 DexKit 扫描器扩展支持
    implementation("com.github.kulipai.libluahook:luahook-ext-dexkit:1.0.0-alpha01")

    // 5. (可选) 引入 Native Dobby JNI 内存 hook 扩展支持
    implementation("com.github.kulipai.libluahook:luahook-ext-native:1.0.0-alpha01")
}
```

---

## 🚀 集成与调用指南

在新模块的入口中调用 `LuaHookEngine.init()` 仅需初始化一次，之后即可随时调用 `run()` 批量加载并运行您的 Lua 代码。引擎内部会自动进行入参对象的类型检查与多版本兼容性处理。

### 选项 A: 现代 LibXposed 入口 (`XposedModule` / API 101.0.1+)
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
        
        // 1. 初始化引擎。只需传入您的 XposedModule entry 'this'，和框架传入的 lpparam
        LuaHookEngine.init(
            xposedModule = this,
            param = lpparam
        )
        
        // 2. 从您的 Assets、Preferences 或是其他安全加密存储源读取并直接运行您的 Lua 代码
        val scriptText = """
            log("Running in process: " .. LPParam.processName)
            -- 在此编写或加载您的 Lua Hook 业务逻辑！
        """.trimIndent()
        
        val globals = LuaHookEngine.run(scriptText, "[MY_SCRIPT]")
        
        // 3. (可选) 注册您需要的布局扩展、扫描器或 Native C++ Dobby 等注入函数扩展
        globals.registerLayout()  // 👈 向 Lua 运行期暴露出原生的 loadlayout() 和图片加载适配器支持
        globals.registerDexKit()
        globals.registerNative()
    }
}
```

### 选项 B: 传统 Xposed 入口 (`IXposedHookLoadPackage`)
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
        // 1. 传统 Xposed 环境下一键初始化
        LuaHookEngine.init(
            xposedModule = this,
            param = lpparam,
            suparam = suparam
        )
        
        // 2. 运行您的 Lua 代码
        val scriptText = "log('Hello from legacy Xposed Lua!')"
        val globals = LuaHookEngine.run(scriptText, "[LEGACY_SCRIPT]")
        
        // 3. 注册布局库支持
        globals.registerLayout()
    }
}
```

---

## 🛠️ 本地开发与重新编译

如果您打算修改框架源码并重新编译 `.aar` 依赖发布至您本机的本地 Maven 仓库 (`~/.m2/repository`)：

```bash
# 克隆代码仓库
git clone https://github.com/kulipai/libluahook.git
cd libluahook

# 执行本地编译并发布至 mavenLocal
./gradlew publishToMavenLocal
```

---

## 📄 开源许可证

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

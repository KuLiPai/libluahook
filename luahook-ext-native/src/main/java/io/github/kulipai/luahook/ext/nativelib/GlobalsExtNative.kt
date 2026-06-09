package io.github.kulipai.luahook.ext.nativelib

import org.luaj.Globals
import io.github.kulipai.luahook.hook.api.NativeLib

/**
 * 扩展方法：为 Lua Globals 运行环境注册 native 指针及 Dobby Hook 库。
 * 允许 Lua 脚本在包含此依赖的模块中直接使用原生 C++ 层的内联 Hook、内存读写以及符号解析。
 */
fun Globals.registerNative() {
    this["native"] = NativeLib().toLuaTable()
}

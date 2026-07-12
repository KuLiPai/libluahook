package io.github.kulipai.luahook.ext.dexkit

import org.luaj.Globals
import org.luaj.lib.jse.CoerceJavaToLua

/**
 * 扩展方法：为 Lua Globals 运行环境注册 XpHelper, DexFinder, DexKitBridge。
 * 允许 Lua 脚本在包含此依赖的模块中直接使用 DexKit 的扫描匹配逻辑。
 */
fun Globals.registerDexKit() {
    this["XpHelper"] = CoerceJavaToLua.coerce(top.sacz.xphelper.XpHelper::class.java)
    this["DexFinder"] = CoerceJavaToLua.coerce(top.sacz.xphelper.dexkit.DexFinder::class.java)
    this["DexKitBridge"] = CoerceJavaToLua.coerce(org.luckypray.dexkit.DexKitBridge::class.java)

    this["DexKit"] = object : org.luaj.lib.TwoArgFunction() {
        override fun call(bridgeOrFinder: org.luaj.LuaValue, classLoader: org.luaj.LuaValue): org.luaj.LuaValue {
            if (bridgeOrFinder.isnil()) throw IllegalArgumentException("DexKit requires a DexKitBridge or DexFinder instance as the first argument.")
            val obj = bridgeOrFinder.checkuserdata()
            val dexKitBridge = if (obj is org.luckypray.dexkit.DexKitBridge) {
                obj
            } else {
                val m = obj.javaClass.methods.firstOrNull { it.name.contains("bridge", ignoreCase = true) }
                if (m != null) m.invoke(obj) as org.luckypray.dexkit.DexKitBridge
                else throw IllegalArgumentException("Expected DexKitBridge, but got ${obj.javaClass.name}")
            }
            val cl = if (classLoader.isnil()) Thread.currentThread().contextClassLoader else classLoader.checkuserdata(ClassLoader::class.java) as ClassLoader
            return CoerceJavaToLua.coerce(LuaDexKit(dexKitBridge, cl))
        }
    }
}

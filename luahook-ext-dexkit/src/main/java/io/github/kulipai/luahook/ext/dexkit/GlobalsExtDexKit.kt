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
}

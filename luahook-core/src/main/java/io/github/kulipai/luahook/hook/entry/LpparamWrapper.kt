package io.github.kulipai.luahook.hook.entry

import android.content.pm.ApplicationInfo
import io.github.kulipai.luahook.hook.api.HookLib
import io.github.kulipai.luahook.hook.api.LuaActivity
import io.github.kulipai.luahook.hook.api.LuaImport
import io.github.kulipai.luahook.hook.api.LuaUtil
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.libxposed.api.XposedModuleInterface
import org.luaj.Globals
import org.luaj.LoadState
import org.luaj.compiler.LuaC
import org.luaj.lib.Bit32Lib
import org.luaj.lib.CoroutineLib
import org.luaj.lib.DebugLib
import org.luaj.lib.PackageLib
import org.luaj.lib.StringLib
import org.luaj.lib.TableLib
import org.luaj.lib.Utf8Lib
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JseBaseLib
import org.luaj.lib.jse.JseIoLib
import org.luaj.lib.jse.JseMathLib
import org.luaj.lib.jse.JseOsLib
import org.luaj.lib.jse.JsePlatform
import org.luaj.lib.jse.LuajavaLib

/**
 * 统一抽象化api100和低于100的hook的部分接口
 */

interface LPParam {
    val packageName: String
    val classLoader: ClassLoader
    val appInfo: ApplicationInfo
    val isFirstApplication: Boolean
    val processName: String
}

class LoadPackageParamWrapper(val origin: LoadPackageParam) : LPParam {
    override val packageName: String get() = origin.packageName
    override val classLoader: ClassLoader get() = origin.classLoader
    override val appInfo: ApplicationInfo get() = origin.appInfo
    override val processName: String get() = origin.processName
    override val isFirstApplication: Boolean get() = origin.isFirstApplication
}

class ModuleInterfaceParamWrapper(val origin: XposedModuleInterface.PackageReadyParam) : LPParam {
    override val packageName: String get() = origin.packageName
    override val classLoader: ClassLoader get() = origin.classLoader
    override val appInfo: ApplicationInfo get() = origin.applicationInfo
    override val processName: String get() = origin.applicationInfo.processName
    override val isFirstApplication: Boolean get() = origin.isFirstPackage
}

/**
 * A Lua environment bound to a script name.
 *
 * The name is used as the prefix for everything that environment reports: `log()`
 * output and hook error messages (see [HookLib]). Keeping it on the environment
 * itself means it never has to be passed twice.
 */
class LuaEnv(val scriptName: String) : Globals()

/**
 * Equivalent to [JsePlatform.standardGlobals], but builds a [LuaEnv] so the
 * environment carries its script name.
 *
 * The library list and install order mirror `standardGlobals()`. In particular
 * `LuaC.install` / `LoadState.install` are what give the environment a Lua
 * compiler; without them `Globals.load()` returns a broken chunk and every
 * script fails with "attempt to index (a nil value)".
 */
private fun standardLuaEnv(scriptName: String): LuaEnv {
    val globals = LuaEnv(scriptName)
    globals.load(JseBaseLib())
    globals.load(PackageLib())
    globals.load(Bit32Lib())
    globals.load(TableLib())
    globals.load(StringLib())
    globals.load(CoroutineLib())
    globals.load(JseMathLib())
    globals.load(JseIoLib())
    globals.load(JseOsLib())
    globals.load(LuajavaLib())
    globals.load(DebugLib())
    globals.load(Utf8Lib())
    LuaC.install(globals)
    LoadState.install(globals)
    return globals
}

// 创建一个Hook的lua环境
fun createGlobals(
    context: Any?,
    lpparam: LPParam,
    suparam: IXposedHookZygoteInit.StartupParam,
    scriptName: String = "",
): LuaEnv {
    val globals = standardLuaEnv(scriptName)

    // 加载Lua模块
    globals["this"] = CoerceJavaToLua.coerce(context)
    globals["suparam"] = CoerceJavaToLua.coerce(suparam)
    LuaActivity(null).registerTo(globals)
    // 脚本名以环境自身携带的那份为准，保证日志前缀与异常包装用的是同一个值
    HookLib(lpparam, globals.scriptName).registerTo(globals)

    LuaImport(lpparam.classLoader, context?.let { it::class.java.classLoader }).registerTo(
        globals
    )
    LuaUtil.loadBasicLib(globals)

//    LuaProject(projectName).registerTo(globals)

    return globals
}
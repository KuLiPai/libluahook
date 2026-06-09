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
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform

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

// 创建一个Hook的lua环境
fun createGlobals(
    context: Any?,
    lpparam: LPParam,
    suparam: IXposedHookZygoteInit.StartupParam,
    scriptName: String = "",
): Globals {
    val globals: Globals = JsePlatform.standardGlobals()

    // 加载Lua模块
    globals["this"] = CoerceJavaToLua.coerce(context)
    globals["suparam"] = CoerceJavaToLua.coerce(suparam)
    LuaActivity(null).registerTo(globals)
    HookLib(lpparam, scriptName).registerTo(globals)

    LuaImport(lpparam.classLoader, context?.let { it::class.java.classLoader }).registerTo(
        globals
    )
    LuaUtil.loadBasicLib(globals)

//    LuaProject(projectName).registerTo(globals)

    return globals
}
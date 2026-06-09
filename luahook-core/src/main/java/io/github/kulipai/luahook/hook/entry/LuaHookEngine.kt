package io.github.kulipai.luahook.hook.entry

import android.content.pm.ApplicationInfo
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.libxposed.api.XposedModuleInterface
import io.github.kulipai.luahook.hook.api.LuaUtil
import org.luaj.Globals
import org.luaj.LuaValue

object LuaHookEngine {

    private var xposedModule: Any? = null
    private var lpParam: LPParam? = null
    private var startupParam: IXposedHookZygoteInit.StartupParam? = null

    /**
     * Initializes the Lua Xposed engine with target package parameters.
     * Call this inside your module's lifecycle hook (e.g. handleLoadPackage or onPackageReady).
     * 
     * @param xposedModule The XposedModule or legacy hook entry point instance (usually `this`).
     * @param param The package loading parameter. Can be either [XposedModuleInterface.PackageReadyParam] or [XC_LoadPackage.LoadPackageParam].
     * @param suparam Zygote startup parameter (optional, will be automatically generated if not provided).
     * @param moduleSourceDir The module's own APK file path (optional, used to auto-generate suparam if needed).
     */
    @JvmStatic
    @JvmOverloads
    fun init(
        xposedModule: Any,
        param: Any,
        suparam: IXposedHookZygoteInit.StartupParam? = null,
        moduleSourceDir: String? = null
    ) {
        this.xposedModule = xposedModule

        // Dynamically detect type and wrap it
        this.lpParam = when (param) {
            is XposedModuleInterface.PackageReadyParam -> {
                ModuleInterfaceParamWrapper(param)
            }

            is XC_LoadPackage.LoadPackageParam -> {
                LoadPackageParamWrapper(param)
            }

            is LPParam -> {
                param
            }

            else -> {
                throw IllegalArgumentException("Unsupported param type: ${param::class.java.name}. Expected XC_LoadPackage.LoadPackageParam or XposedModuleInterface.PackageReadyParam")
            }
        }

        // Auto-generate or set the startup parameter
        this.startupParam = suparam ?: run {
            val sourceDir = moduleSourceDir ?: run {
                // Safe fallback: try to retrieve module sourceDir using moduleApplicationInfo if context is XposedModule
                try {
                    val method = xposedModule::class.java.getMethod("getModuleApplicationInfo")
                    val moduleAppInfo = method.invoke(xposedModule) as ApplicationInfo
                    moduleAppInfo.sourceDir
                } catch (e: Exception) {
                    this.lpParam?.classLoader?.toString() ?: ""
                }
            }
            createDefaultStartupParam(sourceDir)
        }
    }

    /**
     * Run a Lua script in the pre-initialized Xposed hook environment.
     * 
     * @param scriptContent The Lua script content to run.
     * @param scriptName Identifier name of the script for error reports (optional).
     * @return The generated Lua Globals environment.
     */
    @JvmStatic
    @JvmOverloads
    fun load(
        scriptContent: String,
        context: Any? = null,
        scriptName: String = "[LUA]"

    ): Globals {
        val lp = this.lpParam
            ?: throw IllegalStateException("LuaHookEngine must be initialized by calling init() before run()")
        val sp = this.startupParam
            ?: throw IllegalStateException("LuaHookEngine must be initialized by calling init() before run()")

        val globals = createGlobals(context, lp, sp, scriptName)

        try {
            val chunk: LuaValue = globals.load(scriptContent)
            chunk.call()
        } catch (e: Exception) {
            val err = LuaUtil.simplifyLuaError(e.toString())
            throw RuntimeException("LuaHook error in script $scriptName: $err", e)
        }

        return globals
    }

    private fun createDefaultStartupParam(modulePath: String): IXposedHookZygoteInit.StartupParam {
        val clazz = IXposedHookZygoteInit.StartupParam::class.java
        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()

        val fieldModulePath = clazz.getDeclaredField("modulePath")
        fieldModulePath.isAccessible = true
        fieldModulePath.set(instance, modulePath)

        return instance
    }
}

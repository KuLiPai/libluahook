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
     * Create a Lua environment in the pre-initialized Xposed hook environment,
     * without running any script yet.
     *
     * Use this when you need to inject your own globals (or extension APIs such as
     * `registerLayout()`, `registerDexKit()`, `registerNative()`) before the script
     * runs. Hand the returned environment to [run] when you are done.
     *
     * @param context An arbitrary object exposed to Lua as `this` (optional).
     * @param scriptName Name of the script, used to prefix its log output and error
     *   reports for the whole lifetime of this environment (optional).
     * @return The generated Lua environment.
     */
    @JvmStatic
    @JvmOverloads
    fun load(
        context: Any? = null,
        scriptName: String = "[LUA]"
    ): Globals {
        val lp = this.lpParam
            ?: throw IllegalStateException("LuaHookEngine must be initialized by calling init() before load()")
        val sp = this.startupParam
            ?: throw IllegalStateException("LuaHookEngine must be initialized by calling init() before load()")

        return createGlobals(context, lp, sp, scriptName)
    }

    /**
     * Run a Lua script in a previously created environment.
     *
     * The script name comes from the environment itself (see [load]), so it does not
     * need to be passed again. Any globals injected after [load] are visible to the
     * script. Repeated calls run each script in the same shared environment.
     *
     * @param globals The environment returned by [load].
     * @param scriptContent The Lua script content to run.
     * @return The same environment, for chaining.
     */
    @JvmStatic
    fun run(globals: Globals, scriptContent: String): Globals {
        // [load] always returns a LuaEnv, so this only falls back for environments
        // built by hand outside the engine.
        val scriptName = (globals as? LuaEnv)?.scriptName ?: "[LUA]"
        try {
            val chunk: LuaValue = globals.load(scriptContent)
            if (chunk.isnil()) {
                throw IllegalStateException(
                    "Failed to compile script $scriptName: the environment has no Lua compiler"
                )
            }
            chunk.call()
        } catch (e: Exception) {
            val err = LuaUtil.simplifyLuaError(e.toString())
            throw RuntimeException("LuaHook error in script $scriptName: $err", e)
        }

        return globals
    }

    /**
     * Create a Lua environment and immediately run a script in it.
     *
     * Shortcut for callers that do not need to inject custom globals in between.
     * For the injectable form, use [load] followed by [run].
     *
     * @param scriptContent The Lua script content to run.
     * @param context An arbitrary object exposed to Lua as `this` (optional).
     * @param scriptName Name of the script, used to prefix its log output and error
     *   reports (optional).
     * @return The generated Lua environment.
     */
    @JvmStatic
    @JvmOverloads
    fun loadAndRun(
        scriptContent: String,
        context: Any? = null,
        scriptName: String = "[LUA]"
    ): Globals = run(load(context, scriptName), scriptContent)

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

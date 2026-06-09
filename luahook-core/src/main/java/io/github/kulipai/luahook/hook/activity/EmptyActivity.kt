package io.github.kulipai.luahook.hook.activity

import android.app.Activity
import android.os.Bundle
import io.github.kulipai.luahook.hook.api.LuaActivity
import io.github.kulipai.luahook.hook.api.LuaUtil
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luaj.Globals
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform

/**
 * EmptyActivity是一个注入到宿主应用内实现的一个页面
 * 加载通过intent Extra的script传入的lua代码
 */
class EmptyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.getStringExtra("script")
        if (data != null) {
            try {
                createGlobals().load(data).call()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createGlobals(): Globals {
        val globals: Globals = JsePlatform.standardGlobals()

        //加载Lua模块
        globals["XposedHelpers"] = CoerceJavaToLua.coerce(XposedHelpers::class.java)
        globals["XposedBridge"] = CoerceJavaToLua.coerce(XposedBridge::class.java)
        globals["this"] = CoerceJavaToLua.coerce(this)
        globals["activity"] = CoerceJavaToLua.coerce(this)
        LuaActivity(this).registerTo(globals)

        LuaUtil.loadBasicLib(globals)
        return globals
    }
}
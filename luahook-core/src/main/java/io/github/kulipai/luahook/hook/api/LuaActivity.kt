package io.github.kulipai.luahook.hook.api

import android.content.Context
import android.content.Intent
import io.github.kulipai.luahook.hook.activity.EmptyActivity
import org.luaj.Globals
import org.luaj.LuaValue
import org.luaj.lib.TwoArgFunction
import org.luaj.lib.jse.CoerceJavaToLua

/**
 * 加载lua布局树loadlayout
 * 和注入界面在宿主中
 */

class LuaActivity(val context: Context?) {
    fun registerTo(env: LuaValue) {

        //注入界面
        env["injectActivity"] = object : TwoArgFunction() {
            override fun call(
                context: LuaValue?,
                data: LuaValue? //
            ): LuaValue? {
                val activity = context?.touserdata(Context::class.java)

                val intent = Intent(activity, EmptyActivity::class.java)
                intent.putExtra("script", data?.tojstring())
                activity?.startActivity(intent)

                return NIL
            }

        }
    }
}
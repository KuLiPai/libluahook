package io.github.kulipai.luahook.ext.layout

import android.content.Context
import io.github.kulipai.luahook.hook.api.loadlayout
import org.luaj.Globals
import org.luaj.lib.jse.CoerceJavaToLua

/**
 * Extension to register Android layouts and adapters dynamically in Lua globals.
 */
fun Globals.registerLayout(context: Context? = null) {
    val actualContext = context ?: try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    } catch (e: Exception) {
        null
    }
    this["loadlayout"] = CoerceJavaToLua.coerce(loadlayout(actualContext, this))
}

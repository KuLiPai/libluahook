package io.github.kulipai.luahook.ext.layout

import android.content.Context
import io.github.kulipai.luahook.hook.api.loadlayout
import io.github.kulipai.luahook.hook.api.ThemedFactory
import org.luaj.LuaError
import org.luaj.Globals
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua

/**
 * Extension to register Android layouts and adapters dynamically in Lua globals.
 */
@JvmOverloads
fun Globals.registerLayout(context: Context? = null, themeResId: Int = 0) {
    val actualContext = context ?: try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    } catch (e: Exception) {
        null
    }
    val loader = loadlayout(actualContext, this)
    loader.setContext(actualContext, themeResId)

    this["loadlayout"] = CoerceJavaToLua.coerce(loader)

    // The Application context is often registered before the first Activity is
    // created. These helpers let a script switch to the Activity's themed context
    // once it is available, without rebuilding the Lua environment.
    this["setLayoutContext"] = SetLayoutContext(loader)
    this["layoutContext"] = LayoutContext(loader)
    this["themed"] = ThemedFactory(loader)
}

private class SetLayoutContext(private val loader: loadlayout) : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs {
        val context = args.arg1().touserdata(Context::class.java)
            ?: throw LuaError("setLayoutContext: first argument must be an Android Context")
        val themeResId = loader.resolveTheme(args.arg(2), context)
        loader.setContext(context, themeResId)
        return loader.contextLua()
    }
}

private class LayoutContext(private val loader: loadlayout) : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs {
        val themeResId = loader.resolveTheme(args.arg1())
        return loader.contextLua(themeResId)
    }
}

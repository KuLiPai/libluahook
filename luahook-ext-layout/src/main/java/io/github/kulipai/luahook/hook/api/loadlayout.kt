package io.github.kulipai.luahook.hook.api

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import io.github.kulipai.luahook.core.androlua.LuaLayout
import io.github.kulipai.luahook.core.log.e
import com.nekolaska.ktx.argAt
import com.nekolaska.ktx.firstArg
import com.nekolaska.ktx.secondArg
import com.nekolaska.ktx.toLuaInstance
import org.luaj.Globals
import org.luaj.LuaError
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction

class loadlayout(var mContext: Context?, val globals: Globals) : VarArgFunction() {

    private var themeResId: Int = 0

    /**
     * Changes the context used by subsequent layout loads and view factories.
     * A theme resource is optional because the supplied context may already be themed.
     */
    @JvmOverloads
    fun setContext(context: Context?, themeResId: Int = 0) {
        mContext = context
        this.themeResId = themeResId
    }

    fun context(themeOverride: Int = 0): Context {
        val context = mContext ?: resolveApplicationContext()
            ?: throw LuaError("loadlayout: context is null; call setLayoutContext(activity, theme) first")
        val effectiveTheme = if (themeOverride != 0) themeOverride else themeResId
        return if (effectiveTheme != 0) {
            ContextThemeWrapper(context, effectiveTheme)
        } else {
            context
        }
    }

    /**
     * Resolves either a numeric style ID or a style name against the current
     * context. Name resolution is useful when a library R constant belongs to a
     * different resource package than the hooked application.
     */
    fun resolveTheme(value: LuaValue, lookupContext: Context? = null): Int {
        if (value.isnil()) return 0
        if (value.isnumber()) return value.toint()
        if (!value.isstring()) {
            throw LuaError("theme must be a style resource ID or name")
        }

        val rawName = value.tojstring().removePrefix("@style/")
        val packageName = rawName.substringBefore(':', "")
        val styleName = rawName.substringAfter(':', rawName)
        val resourceContext = lookupContext ?: mContext ?: resolveApplicationContext()
            ?: throw LuaError("loadlayout: context is null; call setLayoutContext(activity, theme) first")
        val lookupPackages = if (packageName.isNotEmpty()) {
            listOf(packageName)
        } else {
            listOf(resourceContext.packageName, "com.google.android.material").distinct()
        }
        val styleNames = listOf(styleName, styleName.replace('.', '_')).distinct()
        var resolvedId = 0
        for (lookupPackage in lookupPackages) {
            for (candidateName in styleNames) {
                resolvedId = resourceContext.resources.getIdentifier(
                    candidateName,
                    "style",
                    lookupPackage
                )
                if (resolvedId != 0) {
                    break
                }
            }
            if (resolvedId != 0) break
        }
        if (resolvedId == 0) {
            throw LuaError(
                "theme style '$rawName' was not found in resources for ${lookupPackages.joinToString()}"
            )
        }
        return resolvedId
    }

    fun contextLua(themeOverride: Int = 0): LuaValue = context(themeOverride).toLuaInstance()

    fun themedClass(viewClass: LuaValue, themeOverride: Int = 0): LuaValue =
        ThemedClass(viewClass, this, themeOverride).value

    override fun invoke(args: Varargs): Varargs {
        if (mContext == null) {
            mContext = resolveApplicationContext()
        }
        if (mContext == null) {
            return ("loadlayout: context is null").e().let { NIL }
        }
        val context = try {
            context()
        } catch (e: LuaError) {
            return e.message?.e().let { NIL }
        }
        return when (args.narg()) {

            // use cast instead of LuaContext.getContext
            1 -> LuaLayout(context, globals).load(args.firstArg(), globals)
            2 -> LuaLayout(context, globals).load(args.firstArg(), args.secondArg().checktable())
            3 -> LuaLayout(context, globals).load(
                args.firstArg(),
                args.secondArg().checktable(),
                args.argAt(3)
            )

            else -> ("loadlayout: invalid arguments").e().let { NIL }
        }
    }

    private fun resolveApplicationContext(): Context? = try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    } catch (_: Exception) {
        null
    }
}

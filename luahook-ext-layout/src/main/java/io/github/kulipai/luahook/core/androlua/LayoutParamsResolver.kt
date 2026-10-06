package io.github.kulipai.luahook.core.androlua

import com.nekolaska.ktx.toLuaValue
import org.luaj.LuaValue
import org.luaj.LuaValue.NIL

/** Resolves the nested LayoutParams class without colliding with View.getLayoutParams(). */
internal fun LuaValue.layoutParamsClass(): LuaValue {
    if (!isuserdata()) {
        return get("LayoutParams")
    }

    val viewClass = runCatching {
        touserdata(Class::class.java)
    }.getOrNull() ?: return NIL

    var current: Class<*>? = viewClass
    while (current != null) {
        current.declaredClasses.firstOrNull { it.simpleName == "LayoutParams" }?.let {
            return it.toLuaValue()
        }
        current = current.superclass
    }
    return NIL
}

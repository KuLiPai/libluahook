package io.github.kulipai.luahook.hook.api

import com.nekolaska.ktx.toLuaInstance
import io.github.kulipai.luahook.core.androlua.layoutParamsClass
import org.luaj.LuaError
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction

/**
 * A Lua callable that injects a themed Context into an Android view constructor.
 * It is intentionally a table with an __call metamethod so it can be passed to
 * loadlayout in exactly the same position as a Java class.
 */
class ThemedClass(
    private val viewClass: LuaValue,
    private val layout: loadlayout,
    private val themeResId: Int
) {
    val value: LuaValue = LuaTable().apply {
        // Keep LayoutParams available even when a Lua table lookup does not
        // traverse the userdata __index metamethod in a custom Lua runtime.
        rawset("LayoutParams", viewClass.layoutParamsClass())
        setmetatable(LuaTable().apply {
            // Delegate nested classes such as LayoutParams to the Java class so
            // themed ViewGroups keep the same loadlayout child-parameter behavior.
            rawset("__index", viewClass)
            rawset("__call", object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    if (args.narg() == 0) {
                        throw LuaError("themed: missing callable view class")
                    }

                    // Lua's __call receives the factory table as argument 1. When
                    // loadlayout calls a factory it also supplies its own Context;
                    // discard that value because the themed Context is injected here.
                    var firstConstructorArg = 2
                    if (args.narg() >= 2 && args.arg(2).isuserdata(android.content.Context::class.java)) {
                        firstConstructorArg = 3
                    }

                    val constructorArgs = ArrayList<LuaValue>(args.narg())
                    constructorArgs.add(layout.contextLua(themeResId))
                    for (index in firstConstructorArg..args.narg()) {
                        constructorArgs.add(args.arg(index))
                    }
                    return viewClass.invoke(LuaValue.varargsOf(constructorArgs.toTypedArray()))
                }
            })
        })
    }
}

/** Creates a themed factory for a Java view class. */
class ThemedFactory(private val layout: loadlayout) : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs {
        if (args.narg() < 1 || args.arg(1).isnil()) {
            throw LuaError("themed: expected a Java view class")
        }
        val themeResId = layout.resolveTheme(args.arg(2))
        return ThemedClass(args.arg(1), layout, themeResId).value
    }
}

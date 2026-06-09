package io.github.kulipai.luahook.hook.api

import android.content.Context
import io.github.kulipai.luahook.core.androlua.LuaLayout
import io.github.kulipai.luahook.core.log.e
import com.nekolaska.ktx.argAt
import com.nekolaska.ktx.firstArg
import com.nekolaska.ktx.secondArg
import org.luaj.Globals
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction

class loadlayout(var mContext: Context?, val globals: Globals) : VarArgFunction() {

    override fun invoke(args: Varargs): Varargs {
        if(mContext == null) {
            mContext = try {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as? Context
            } catch (e: Exception) {
                null
            }
        }
        if (mContext == null) {
            return ("loadlayout: context is null").e().let { NIL }
        }
        mContext = mContext!!
        return when (args.narg()) {

            // use cast instead of LuaContext.getContext
            1 -> LuaLayout(mContext!!).load(args.firstArg(), globals)
            2 -> LuaLayout(mContext!!).load(args.firstArg(), args.secondArg().checktable())
            3 -> LuaLayout(mContext!!).load(
                args.firstArg(),
                args.secondArg().checktable(),
                args.argAt(3)
            )

            else -> ("loadlayout: invalid arguments").e().let { NIL }
        }
    }
}

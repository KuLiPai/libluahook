package io.github.kulipai.luahook.hook.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction

/**
 * Task封装，协程中执行代码
 */

object LuaTask {
    fun registerTo(env: LuaValue) {

        env["Task"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val func = args.checkfunction(1)
                val time = if (args.narg() == 2) args.checknumber(2).tolong() else 0L
                CoroutineScope(Dispatchers.Main).launch {

                    delay(time)
                    func.invoke()
                }
                return NIL
            }
        }
    }
}

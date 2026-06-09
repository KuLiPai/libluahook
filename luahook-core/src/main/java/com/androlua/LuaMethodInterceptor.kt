package com.androlua

import dx.proxy.MethodInterceptor
import dx.proxy.MethodProxy
import org.luaj.LuaError
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.CoerceLuaToJava

/**
 * Created by nirenr on 2019/3/1.
 */
class LuaMethodInterceptor(private val obj: LuaValue) : MethodInterceptor {
    @Throws(Exception::class)
    override fun intercept(`object`: Any?, args: Array<Any?>, methodProxy: MethodProxy): Any? {
        var args = args
        val method = methodProxy.getOriginalMethod()
        val methodName = method.getName()
        val func: LuaValue
        if (obj.isfunction()) {
            func = obj
        } else {
            func = obj.get(methodName)
        }
        val retType = method.getReturnType()

        if (func.isnil()) {
            if (retType == Boolean::class.javaPrimitiveType || retType == Boolean::class.java) return false
            else if (retType.isPrimitive() || Number::class.java.isAssignableFrom(retType)) return 0
            else return null
        }
        val na = arrayOfNulls<Any>(args.size + 1)
        System.arraycopy(args, 0, na, 1, args.size)
        na[0] = SuperCall(`object`, methodProxy)
        args = na
        var ret: Any? = null
        try {
            // Checks if returned type is void. if it is returns null.
            if (retType == Void::class.java || retType == Void.TYPE) {
                func.jcall(*args)
            } else {
                ret = func.jcall(*args)
            }
        } catch (e: LuaError) {
//            LuaActivity.logError(methodName, e);
        }
        if (ret == null) if (retType == Boolean::class.javaPrimitiveType || retType == Boolean::class.java) return false
        else if (retType.isPrimitive() || Number::class.java.isAssignableFrom(retType)) return 0
        return ret
    }

    private class SuperCall(private val mObject: Any?, private val mMethodProxy: MethodProxy) :
        VarArgFunction() {
        override fun invoke(vargs: Varargs): Varargs? {
            val n = vargs.narg()
            val args = arrayOfNulls<Any>(n)
            val argsType = mMethodProxy.argsType
            for (i in 0..<n) {
                args[i] = CoerceLuaToJava.coerce(vargs.arg(i + 1), argsType[i])
            }
            return CoerceJavaToLua.coerce(mMethodProxy.invokeSuper(mObject, args))
        }

        override fun tostring(): LuaValue? {
            return valueOf(toString())
        }

        override fun toString(): String {
            return "SuperCall{" +
                "Object=" + mObject +
                ", Method=" + mMethodProxy +
                '}'
        }
    }
}

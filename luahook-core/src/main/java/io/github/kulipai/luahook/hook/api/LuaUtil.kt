package io.github.kulipai.luahook.hook.api

import io.github.kulipai.luahook.core.log.d
import org.luaj.Globals
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.OneArgFunction
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua

/**
 * 提供一些基础功能，通用功能，同时注册全部library库
 */

object LuaUtil {
    fun shell(_G: Globals) {


        _G["shell"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val cmd = args.checkjstring(1)
                return try {
                    val process = Runtime.getRuntime().exec(cmd)
                    val stdout = process.inputStream.bufferedReader().use { it.readText() }
                    val stderr = process.errorStream.bufferedReader().use { it.readText() }
                    process.waitFor()
                    if (process.exitValue() == 0) {
                        varargsOf(
                            CoerceJavaToLua.coerce(stdout),
                            CoerceJavaToLua.coerce(true)
                        )
                    } else {
                        varargsOf(
                            CoerceJavaToLua.coerce(stderr),
                            CoerceJavaToLua.coerce(false)
                        )
                    }
                } catch (e: Exception) {
                    varargsOf(
                        CoerceJavaToLua.coerce(e.message ?: ""),
                        CoerceJavaToLua.coerce(false)
                    )
                }
            }
        }
    }

    fun loadBasicLib(_G: Globals) {
        LuaHttp.registerTo(_G)
        LuaFile.registerTo(_G)
        LuaJson.registerTo(_G)
        LuaTask.registerTo(_G)
        LuaResource.registerTo(_G)
        LuaSharedPreferences.registerTo(_G)
        LuaDrawableLoader().registerTo(_G)

        // 打印堆栈
        // 这个不能写在java层会栈溢出
        _G.load(
            """
            function printStackTrace()
               import "java.lang.Throwable"
               stackTrace = Throwable().stackTrace
               for _,v in stackTrace do
                  print("at "..tostring(v))
               end
            end
        """.trimIndent()
        ).call()


        _G["pcall"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (args.narg() < 1 || !args.arg1().isfunction()) {
                    return varargsOf(
                        FALSE,
                        valueOf("first argument must be a function")
                    )
                }

                val func = args.arg1().checkfunction()
                val funcArgs = args.subargs(2) // Get all arguments after the function

                try {
                    // Execute the function with provided arguments
                    val result = func.invoke(funcArgs)

                    // Return true followed by any results from the function
                    return varargsOf(TRUE, result)
                } catch (e: Exception) {
                    // Catch any Lua or Java exceptions
                    val err = simplifyLuaError(e.toString())

                    // Return false followed by the error message
                    return varargsOf(FALSE, valueOf(err))
                }
            }
        }


        _G["print"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val stringBuilder = StringBuilder()
                for (i in 1..args.narg()) {
                    val value = args.arg(i)
                    stringBuilder.append(valueToString(value))
                    if (i < args.narg()) {
                        stringBuilder.append("\t")
                    }
                }
                (stringBuilder.toString()).d()
                return NONE
            }

            private fun valueToString(value: LuaValue): String {
                return when {
                    value.isnil() -> "nil"
                    value.isboolean() -> value.checkboolean().toString()
                    value.isnumber() -> value.checkdouble().toString()
                    value.isstring() -> value.checkjstring()
                    value.istable() -> tableToString(value.checktable())
                    value.isfunction() -> "function: ${value.tojstring()}"
                    value.isuserdata() -> {
                        // 处理 Java 对象，尝试调用 toString()
                        val userdata = value.checkuserdata()
                        userdata?.toString() ?: "userdata: null"
                    }

                    else -> value.tojstring() // 其他类型使用 LuaValue 默认的 toString
                }
            }

            private fun tableToString(table: LuaTable): String {
                val tableStringBuilder = StringBuilder()
                tableStringBuilder.append("{")
                var first = true

                var k: LuaValue = NIL
                while (true) {
                    val n: Varargs = table.next(k)
                    k = n.arg1() // 获取当前键
                    if (k.isnil()) { // 如果键是 nil，表示遍历结束
                        break
                    }
                    val v: LuaValue = n.arg(2) // 获取当前值

                    if (!first) {
                        tableStringBuilder.append(", ")
                    }
                    first = false

                    if (k.isstring()) {
                        tableStringBuilder.append(k.checkjstring())
                    } else if (k.isnumber()) {
                        tableStringBuilder.append("[${k.checkint()}]") // 对数字键使用方括号
                    } else {
                        tableStringBuilder.append(k.tojstring()) // 其他类型键
                    }
                    tableStringBuilder.append(" = ")
                    tableStringBuilder.append(valueToString(v)) // 递归处理值
                }
                tableStringBuilder.append("}")
                return tableStringBuilder.toString()
            }
        }

        //解析Pair,返回table
        _G["unPair"] = object : OneArgFunction() {
            override fun call(pairValue: LuaValue): LuaValue {
                if (pairValue.isuserdata()) {
                    val userdata = pairValue.checkuserdata()
                    if (userdata is Pair<*, *>) {
                        val pair = userdata
                        val table = LuaTable()
                        table.set(1, CoerceJavaToLua.coerce(pair.first))
                        table.set(2, CoerceJavaToLua.coerce(pair.second))
                        return table
                    }
                }
                return NIL
            }
        }

        _G["getAppLanguage"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val locale = java.util.Locale.getDefault()
                return LuaValue.valueOf(locale.toLanguageTag())
            }
        }

    }


    fun simplifyLuaError(raw: String): String {
        val lines = raw.lines()

        // 1. 优先提取第一条真正的错误信息（不是 traceback）
        val primaryErrorLine = lines.firstOrNull { it.trim().matches(Regex(""".*:\d+ .+""")) }

        if (primaryErrorLine != null) {
            val match = Regex(""".*:(\d+) (.+)""").find(primaryErrorLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line $lineNum: $msg"
            }
        }

        // 2. 其次从 traceback 提取（防止所有匹配失败）
        val fallbackLine = lines.find { it.trim().matches(Regex(""".*:\d+: .*""")) }
        if (fallbackLine != null) {
            val match = Regex(""".*:(\d+): (.+)""").find(fallbackLine)
            if (match != null) {
                val (lineNum, msg) = match.destructured
                return "line $lineNum: $msg"
            }
        }

        return raw.lines().firstOrNull()?.take(100) ?: "Unknown error"
    }
}
package io.github.kulipai.luahook.hook.api

import android.content.Context
import androidx.core.content.edit
import de.robv.android.xposed.XSharedPreferences
import org.luaj.LuaError
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction

/**
 * 封装 Android 原生的 SharedPreferences 给 Lua 使用
 */

object LuaSharedPreferences {

    fun registerTo(globals: LuaValue) {
        val sp = LuaTable()

        // 设置值
        sp["set"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 需要 Context, name, key, value (至少4个参数)
                if (args.narg() < 4) {
                    throw LuaError("Usage: sp.set(context, name, key, value)")
                }

                // 从参数中获取 Context
                val context = args.checkuserdata(1, Context::class.java) as Context
                val name = args.checkjstring(2) // 参数索引后移
                val key = args.checkjstring(3) // 参数索引后移
                // value 是第四个参数，不需要单独取，直接通过 args.arg(4) 检查类型和值


                val prefs =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE) // 使用传入的 Context
                prefs.edit {

                    when {
                        args.isstring(4) -> {
                            putString(key, args.checkjstring(4))
                        }

                        args.isnumber(4) -> {
                            val num = args.checkdouble(4)
                            if (num % 1 == 0.0 && num >= Int.MIN_VALUE && num <= Int.MAX_VALUE) {
                                putInt(key, num.toInt())
                            } else {
                                putFloat(key, num.toFloat())
                            }
                        }

                        args.arg(4).type() == TBOOLEAN -> {
                            putBoolean(key, args.checkboolean(4))
                        } // 参数索引后移
                        else -> {
                            return FALSE
                        }
                    }

                }
                return TRUE
            }
        }

        // 获取值
        sp["get"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 需要 Context, name, key, defaultValue (至少4个参数)
                if (args.narg() < 4) {
                    throw LuaError("Usage: sp.get(context, name, key, defaultValue)")
                }

                // 从参数中获取 Context
                val context = args.checkuserdata(1, Context::class.java) as Context
                val name = args.checkjstring(2) // 参数索引后移
                val key = args.checkjstring(3) // 参数索引后移
                // defaultValue 是第四个参数，args.arg(4)F

                val prefs =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE) // 使用传入的 Context


                // 如果键不存在，直接返回默认值 (第四个参数)
                if (!prefs.contains(key)) {
                    return args.arg(4)
                }


                // 根据默认值的类型来获取 SharedPreferences 中的值
                return when {
                    // 如果默认值是字符串，按字符串获取
                    args.isstring(4) -> valueOf(
                        prefs.getString(
                            key,
                            args.checkjstring(4) // 使用传入的默认值
                        )!!
                    )

                    // 如果默认值是布尔，按布尔获取
                    args.arg(4).type() == TBOOLEAN -> valueOf(
                        prefs.getBoolean(
                            key,
                            args.checkboolean(4) // 使用传入的默认值
                        )
                    )

                    // 如果默认值是数字，尝试按 Int 或 Float 获取
                    args.isnumber(4) -> {
                        val defaultValue = args.checkdouble(4) // 使用传入的默认值
                        if (defaultValue % 1 == 0.0 && defaultValue >= Int.MIN_VALUE && defaultValue <= Int.MAX_VALUE) {
                            valueOf(prefs.getInt(key, defaultValue.toInt()).toDouble())
                        } else {
                            valueOf(prefs.getFloat(key, defaultValue.toFloat()).toDouble())
                        }
                    }

                    // 对于其他类型的默认值，直接返回默认值（例如 Lua nil, table 等）
                    else -> args.arg(4)
                }
            }
        }

        // 检查键是否存在
        sp["contains"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 需要 Context, name, key (至少3个参数)
                if (args.narg() < 3) {
                    throw LuaError("Usage: sp.contains(context, name, key)")
                }

                // 从参数中获取 Context
                val context = args.checkuserdata(1, Context::class.java) as Context
                val name = args.checkjstring(2) // 参数索引后移
                val key = args.checkjstring(3) // 参数索引后移
                val prefs =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE) // 使用传入的 Context

                return valueOf(prefs.contains(key))
            }
        }

        // 删除键
        sp["remove"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 需要 Context, name, key (至少3个参数)
                if (args.narg() < 3) {
                    throw LuaError("Usage: sp.remove(context, name, key)")
                }

                // 从参数中获取 Context
                val context = args.checkuserdata(1, Context::class.java) as Context
                val name = args.checkjstring(2) // 参数索引后移
                val key = args.checkjstring(3) // 参数索引后移
                val prefs =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE) // 使用传入的 Context

                prefs.edit { remove(key) }
                return TRUE
            }
        }

        // 获取所有键值对
        sp["getAll"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 需要 Context, name (至少2个参数)
                if (args.narg() < 2) {
                    throw LuaError("Usage: sp.getAll(context, name)")
                }

                // 从参数中获取 Context
                val context = args.checkuserdata(1, Context::class.java) as Context
                val name = args.checkjstring(2) // 参数索引后移
                val prefs =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE) // 使用传入的 Context
                val all = prefs.all
                val table = LuaTable()

                // 遍历并转换为 LuaValue
                all.forEach { (k, v) ->
                    when (v) {
                        is String -> table[valueOf(k)] = valueOf(v)
                        is Int -> table[valueOf(k)] =
                            valueOf(v.toDouble()) // Lua numbers are doubles
                        is Float -> table[valueOf(k)] =
                            valueOf(v.toDouble()) // Lua numbers are doubles
                        is Boolean -> table[valueOf(k)] = valueOf(v)
                        else -> table[valueOf(k)] =
                            valueOf(v.toString()) // Fallback to string
                    }
                }

                return table
            }
        }

        // 清除所有键值对
        sp["clear"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 需要 Context, name (至少2个参数)
                if (args.narg() < 2) {
                    throw LuaError("Usage: sp.clear(context, name)")
                }

                // 从参数中获取 Context
                val context = args.checkuserdata(1, Context::class.java) as Context
                val name = args.checkjstring(2) // 参数索引后移
                val prefs =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE) // 使用传入的 Context

                prefs.edit { clear() }
                return TRUE
            }
        }

        // 将封装好的 sp 表注册到 Lua 全局变量 globals 中
        globals["sp"] = sp


        // XSharedPreferences 部分不需要 Context，所以函数签名不变
        val xsp = LuaTable()

        // 获取指定包名和名称的 XSharedPreferences
        // 这个 helper 函数仍然放在这里，它不依赖于类构造函数中的 Context
        val getXPrefs = { packageName: String, name: String ->
            val prefs = XSharedPreferences(packageName, name)
            // 确保可以访问
            prefs.makeWorldReadable()
            prefs
        }

        // 获取值 (参数不变，因为不需要 Context)
        xsp["get"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 4) {
                    throw LuaError("Usage: xsp.get(packageName, name, key, defaultValue)")
                }

                val packageName = args.checkjstring(1)
                val name = args.checkjstring(2)
                val key = args.checkjstring(3)
                val prefs = getXPrefs(packageName, name) // 使用 helper 函数获取 prefs

                if (!prefs.contains(key)) {
                    return args.arg(4) // 返回默认值
                }

                return when {
                    args.isstring(4) -> valueOf(
                        prefs.getString(
                            key,
                            args.checkjstring(4)
                        )!!
                    )

                    args.arg(4).type() == TBOOLEAN -> valueOf(
                        prefs.getBoolean(
                            key,
                            args.checkboolean(4)
                        )
                    )

                    args.isnumber(4) -> {
                        val defaultValue = args.checkdouble(4)
                        if (defaultValue % 1 == 0.0 && defaultValue >= Int.MIN_VALUE && defaultValue <= Int.MAX_VALUE) {
                            valueOf(prefs.getInt(key, defaultValue.toInt()).toDouble())
                        } else {
                            valueOf(prefs.getFloat(key, defaultValue.toFloat()).toDouble())
                        }
                    }

                    else -> args.arg(4) // 返回默认值
                }
            }
        }
        // 检查键是否存在 (参数不变)
        xsp["contains"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 3) {
                    throw LuaError("Usage: xsp.contains(packageName, name, key)")
                }

                val packageName = args.checkjstring(1)
                val name = args.checkjstring(2)
                val key = args.checkjstring(3)
                val prefs = getXPrefs(packageName, name) // 使用 helper 函数获取 prefs

                return valueOf(prefs.contains(key))
            }
        }

        // 获取所有键值对 (参数不变)
        xsp["getAll"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 2) {
                    throw LuaError("Usage: xsp.getAll(packageName, name)")
                }

                val packageName = args.checkjstring(1)
                val name = args.checkjstring(2)
                val prefs = getXPrefs(packageName, name) // 使用 helper 函数获取 prefs
                val all = prefs.all
                val table = LuaTable()

                all.forEach { (k, v) ->
                    when (v) {
                        is String -> table[valueOf(k)] = valueOf(v)
                        is Int -> table[valueOf(k)] = valueOf(v.toDouble())
                        is Float -> table[valueOf(k)] = valueOf(v.toDouble())
                        is Boolean -> table[valueOf(k)] = valueOf(v)
                        else -> table[valueOf(k)] = valueOf(v.toString())
                    }
                }

                return table
            }
        }

        // 重新加载 (参数不变)
        xsp["reload"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 2) {
                    throw LuaError("Usage: xsp.reload(packageName, name)")
                }

                val packageName = args.checkjstring(1)
                val name = args.checkjstring(2)
                val prefs = getXPrefs(packageName, name) // 使用 helper 函数获取 prefs

                prefs.reload()
                return NIL
            }
        }

        // 将封装好的 xsp 表注册到 Lua 全局变量 globals 中
        globals["xsp"] = xsp

    }
}
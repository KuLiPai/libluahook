package io.github.kulipai.luahook.hook.api

import org.json.JSONObject
import org.json.JSONArray
import org.luaj.*
import org.luaj.lib.*
import kotlin.collections.iterator

/**
 * json处理封装
 */

object LuaJson {
    fun registerTo(env: LuaValue): LuaValue {
        val json = LuaTable()

        json.set("encode", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = try {
                val jsonStr = if (arg.istable()) {
                    if (isArray(arg.checktable())) {
                        JSONArray(toList(arg.checktable())).toString()
                    } else {
                        JSONObject(toMap(arg.checktable())).toString()
                    }
                } else {
                    JSONObject().put("value", arg.tojstring()).toString()
                }
                valueOf(jsonStr)
            } catch (_: Exception) {
                NIL
            }
        })

        json.set("decode", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = try {
                val str = arg.checkjstring().trim()
                if (str.startsWith("[")) {
                    toLuaValue(JSONArray(str))
                } else {
                    toLuaValue(JSONObject(str))
                }
            } catch (_: Exception) {
                NIL
            }
        })

        env.set("json", json)
        env.get("package").get("loaded").set("json", json)
        return json
    }

    private fun isArray(table: LuaTable): Boolean {
        var i = 1
        while (true) {
            val value = table.rawget(i)
            if (value.isnil()) return table.keys().any { !it.isint() }.not()
            i++
        }
    }

    private fun toMap(table: LuaTable): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = table.keys()
        for (k in keys) {
            val key = k.tojstring()
            val v = table.get(k)
            map[key] = fromLuaValue(v)
        }
        return map
    }

    private fun toList(table: LuaTable): List<Any?> {
        val list = mutableListOf<Any?>()
        var i = 1
        while (true) {
            val v = table.rawget(i)
            if (v.isnil()) break
            list.add(fromLuaValue(v))
            i++
        }
        return list
    }

    private fun fromLuaValue(v: LuaValue): Any? = when {
        v.istable() -> {
            if (isArray(v.checktable())) toList(v.checktable())
            else toMap(v.checktable())
        }
        v.isboolean() -> v.toboolean()
        // 检查值是否真的是数字类型，而不是一个字符串
        v.type() == LuaValue.TNUMBER -> v.todouble()
        v.isstring() -> v.tojstring()
        else -> null
    }

    private fun toLuaValue(json: JSONObject): LuaTable {
        val table = LuaTable()
        for (key in json.keys()) {
            table.set(key, wrapJsonValue(json.get(key)))
        }
        return table
    }

    private fun toLuaValue(json: JSONArray): LuaTable {
        val table = LuaTable()
        for (i in 0 until json.length()) {
            table.set(i + 1, wrapJsonValue(json.get(i)))
        }
        return table
    }

    private fun wrapJsonValue(value: Any?): LuaValue = when (value) {
        is JSONObject -> toLuaValue(value)
        is JSONArray -> toLuaValue(value)
        is Boolean -> LuaValue.valueOf(value)
        is Number -> LuaValue.valueOf(value.toDouble())
        is String -> LuaValue.valueOf(value)
        null -> LuaValue.NIL
        else -> LuaValue.valueOf(value.toString())
    }
}
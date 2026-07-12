package io.github.kulipai.luahook.ext.dexkit

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Field
import org.luaj.LuaTable

class LuaDexKit(private val bridge: DexKitBridge, private val classLoader: ClassLoader? = null) {

    fun findClass(): LuaClassFinder {
        return LuaClassFinder(bridge, classLoader)
    }

    fun findMethod(): LuaMethodFinder {
        return LuaMethodFinder(bridge, classLoader)
    }

    fun findField(): LuaFieldFinder {
        return LuaFieldFinder(bridge, classLoader)
    }
}

class LuaClassFinder(private val bridge: DexKitBridge, private val classLoader: ClassLoader?) {
    private val findClassReq = FindClass.create()
    private val matcher = ClassMatcher.create()

    fun pkg(vararg pkgs: String): LuaClassFinder {
        findClassReq.searchPackages(*pkgs)
        return this
    }

    fun usingStrings(vararg strs: String): LuaClassFinder {
        matcher.usingStrings(*strs)
        return this
    }



    fun interfaces(vararg intfs: String): LuaClassFinder {
        for (i in intfs) {
            matcher.addInterface(org.luckypray.dexkit.query.matchers.ClassMatcher.create().className(i))
        }
        return this
    }

    fun name(name: String): LuaClassFinder {
        matcher.className(name)
        return this
    }
    
    fun superClass(superClz: String): LuaClassFinder {
        matcher.superClass(superClz)
        return this
    }

    private fun loadClass(className: String): Class<*>? {
        if (classLoader == null) return null
        return try {
            classLoader.loadClass(className)
        } catch (e: Exception) {
            null
        }
    }

    fun single(): Class<*>? {
        findClassReq.matcher(matcher)
        val result = bridge.findClass(findClassReq)
        if (result.size == 1) {
            return loadClass(result.first().name)
        }
        return null
    }

    fun first(): Class<*>? {
        findClassReq.matcher(matcher)
        val result = bridge.findClass(findClassReq)
        val first = result.firstOrNull() ?: return null
        return loadClass(first.name)
    }

    fun all(): LuaTable {
        findClassReq.matcher(matcher)
        val result = bridge.findClass(findClassReq)
        val table = LuaTable()
        var i = 1
        for (res in result) {
            val clz = loadClass(res.name)
            if (clz != null) {
                table.set(i++, org.luaj.lib.jse.CoerceJavaToLua.coerce(clz))
            }
        }
        return table
    }
}

class LuaMethodFinder(private val bridge: DexKitBridge, private val classLoader: ClassLoader?) {
    private val findMethodReq = FindMethod.create()
    private val matcher = MethodMatcher.create()

    fun declareClass(className: String): LuaMethodFinder {
        matcher.declaredClass(org.luckypray.dexkit.query.matchers.ClassMatcher.create().className(className))
        return this
    }

    fun name(name: String): LuaMethodFinder {
        matcher.name(name)
        return this
    }

    fun returnType(type: String): LuaMethodFinder {
        matcher.returnType(type)
        return this
    }

    fun paramTypes(vararg types: String): LuaMethodFinder {
        matcher.paramTypes(*types)
        return this
    }

    fun usingStrings(vararg strs: String): LuaMethodFinder {
        matcher.usingStrings(*strs)
        return this
    }

    fun usingNumbers(vararg nums: Number): LuaMethodFinder {
        matcher.usingNumbers(*nums)
        return this
    }

    fun single(): Method? {
        findMethodReq.matcher(matcher)
        val result = bridge.findMethod(findMethodReq)
        if (result.size == 1) {
            val res = result.first()
            return res.getMethodInstance(classLoader ?: return null)
        }
        return null
    }

    fun first(): Method? {
        findMethodReq.matcher(matcher)
        val result = bridge.findMethod(findMethodReq)
        val first = result.firstOrNull() ?: return null
        return first.getMethodInstance(classLoader ?: return null)
    }

    fun all(): LuaTable {
        findMethodReq.matcher(matcher)
        val result = bridge.findMethod(findMethodReq)
        val table = LuaTable()
        var i = 1
        for (res in result) {
            val m = res.getMethodInstance(classLoader ?: continue)
            if (m != null) {
                table.set(i++, org.luaj.lib.jse.CoerceJavaToLua.coerce(m))
            }
        }
        return table
    }
}

class LuaFieldFinder(private val bridge: DexKitBridge, private val classLoader: ClassLoader?) {
    private val findFieldReq = org.luckypray.dexkit.query.FindField.create()
    private val matcher = org.luckypray.dexkit.query.matchers.FieldMatcher.create()

    fun declareClass(className: String): LuaFieldFinder {
        matcher.declaredClass(org.luckypray.dexkit.query.matchers.ClassMatcher.create().className(className))
        return this
    }

    fun name(name: String): LuaFieldFinder {
        matcher.name(name)
        return this
    }

    fun type(type: String): LuaFieldFinder {
        matcher.type(org.luckypray.dexkit.query.matchers.ClassMatcher.create().className(type))
        return this
    }

    fun single(): Field? {
        findFieldReq.matcher(matcher)
        val result = bridge.findField(findFieldReq)
        if (result.size == 1) {
            val res = result.first()
            return res.getFieldInstance(classLoader ?: return null)
        }
        return null
    }

    fun first(): Field? {
        findFieldReq.matcher(matcher)
        val result = bridge.findField(findFieldReq)
        val first = result.firstOrNull() ?: return null
        return first.getFieldInstance(classLoader ?: return null)
    }

    fun all(): LuaTable {
        findFieldReq.matcher(matcher)
        val result = bridge.findField(findFieldReq)
        val table = LuaTable()
        var i = 1
        for (res in result) {
            val f = res.getFieldInstance(classLoader ?: continue)
            if (f != null) {
                table.set(i++, org.luaj.lib.jse.CoerceJavaToLua.coerce(f))
            }
        }
        return table
    }
}

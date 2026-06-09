package io.github.kulipai.luahook.hook.api

import android.os.Build
import androidx.annotation.RequiresApi
import io.github.kulipai.luahook.hook.entry.LPParam
import io.github.kulipai.luahook.core.log.d
import io.github.kulipai.luahook.core.log.e
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luaj.LuaFunction
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.LuaValue.NONE
import org.luaj.Varargs
import org.luaj.lib.OneArgFunction
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.CoerceLuaToJava
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * 封装给lua用于hook的所有工具函数类等等
 */

class HookLib(private val lpparam: LPParam, private val scriptName: String = "") {

    fun registerTo(globals: LuaValue) {
        globals["XposedHelpers"] = CoerceJavaToLua.coerce(XposedHelpers::class.java)
        globals["XposedBridge"] = CoerceJavaToLua.coerce(XposedBridge::class.java)
        globals["lpparam"] = CoerceJavaToLua.coerce(lpparam)

        globals["getField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val targetObject = args.arg(1)
                val fieldName = args.checkjstring(2)

                if (targetObject.isuserdata(Any::class.java)) {
                    val target = targetObject.touserdata(Any::class.java)
                    val result = XposedHelpers.getObjectField(target, fieldName)
                    return CoerceJavaToLua.coerce(result)
                } else {
                    return NIL
                }
            }
        }

        globals["setField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val targetObject = args.arg(1)
                val fieldName = args.checkjstring(2)
                val fieldValue = fromLuaValue(args.arg(3))

                if (targetObject.isuserdata(Any::class.java)) {
                    val target = targetObject.touserdata(Any::class.java)
                    XposedHelpers.setObjectField(target, fieldName, fieldValue)
                }
                return NIL
            }
        }

        globals["getStaticField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val className = args.checkjstring(1)
                val fieldName = args.checkjstring(2)
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                val result = XposedHelpers.getStaticObjectField(clazz, fieldName)
                return CoerceJavaToLua.coerce(result)

            }
        }

        globals["setStaticField"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {

                val className = args.checkjstring(1)
                val fieldName = args.checkjstring(2)
                val fieldValue = fromLuaValue(args.arg(3))
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                XposedHelpers.setStaticObjectField(clazz, fieldName, fieldValue)
                return NIL

            }
        }

        globals["findClass"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // 确保 self 是字符串
                val className = args.checkjstring(1)
                val loader: ClassLoader = if (args.narg() == 1) {
                    lpparam.classLoader
                } else {
                    args.checkuserdata(2, ClassLoader::class.java) as ClassLoader
                }

                // 查找类
                val clazz = XposedHelpers.findClass(className, loader)
                return CoerceJavaToLua.coerce(clazz) // 返回 Java Class 对象
            }
        }

        globals["log"] = object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val message = arg.tojstring()
                "$scriptName:$message".d()
                XposedBridge.log("$scriptName:$message")
                return NIL
            }
        }

        globals["invoke"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val obj = args.arg(1)  // Lua 传入的第一个参数
                val methodName = args.checkjstring(2) // 方法名
                var isStatic = false

                // 获取 Java 对象
                val targetObject: Any? = when {
                    obj.isuserdata(XC_MethodHook.MethodHookParam::class.java) -> {
                        val param =
                            obj.touserdata(XC_MethodHook.MethodHookParam::class.java) as XC_MethodHook.MethodHookParam
                        param.thisObject // 获取 thisObject
                    }

                    obj.isuserdata(Class::class.java) -> {
                        isStatic = true
                        obj.touserdata(Class::class.java)
                    }

                    obj.isuserdata(Any::class.java) -> obj.touserdata(Any::class.java)
                    else -> throw IllegalArgumentException("invoke 需要 Java 对象")
                }

                // 收集参数
                val javaParams = mutableListOf<Any?>()
                for (i in 3..args.narg()) {
                    javaParams.add(fromLuaValue(args.arg(i)))
                }

                var result: Any
                if (isStatic) {
                    // 反射调用方法
                    result = XposedHelpers.callStaticMethod(
                        targetObject as Class<*>?,
                        methodName,
                        *javaParams.toTypedArray()
                    )

                } else {
                    // 反射调用方法
                    result = XposedHelpers.callMethod(
                        targetObject,
                        methodName,
                        *javaParams.toTypedArray()
                    )
                }

                return CoerceJavaToLua.coerce(result) // 把 Java 结果返回 Lua
            }
        }



        // Helper replaced by member function



        // 封装获取构造函数的 Lua 函数
        globals["getConstructor"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata(Class::class.java)) {
                    return error("Usage: getConstructor(class, argType1, argType2, ...)")
                }

                val clazz = args.arg(1).touserdata(Class::class.java) as Class<*>
                val paramTypes = mutableListOf<Class<*>>()

                for (i in 2..args.narg()) {

                    if (args.arg(i).isstring()) {
                        val typeName = args.checkjstring(i)
                        val type = parseType(typeName, lpparam.classLoader)
                        paramTypes.add(type as Class<*>)
                    } else if (args.arg(i).isuserdata()) {
                        // 使用安全的转换方法替代直接 as 转换
                        val classObj = safeToJavaClass(args.arg(i))
                        if (classObj != null) {
                            paramTypes.add(classObj)
                        } else {
                            "getConstructor: 无法将参数 $i 转换为 Class".e()
                            return error("getConstructor: 无法将参数 $i 转换为 Class")
                        }
                    } else {
                        "getConstructor: 参数 $i 类型不支持".e()
                        return error("getConstructor: 参数类型不支持")
                    }

                }


                val constructor = clazz.getConstructor(*paramTypes.toTypedArray())
                return CoerceJavaToLua.coerce(constructor)
            }
        }


        // 封装创建新实例的 Lua 函数
        globals["newInstance"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata(Constructor::class.java)) {
                    return error("Usage: newInstance(constructor, arg1, arg2, ...)")
                }

                val constructor = args.arg(1).touserdata(Constructor::class.java) as Constructor<*>
                val params = mutableListOf<Any?>()

                for (i in 2..args.narg()) {
                    params.add(fromLuaValue(args.arg(i)))
                }


                constructor.isAccessible = true // 允许访问非公共构造函数
                val instance = constructor.newInstance(*params.toTypedArray())
                return CoerceJavaToLua.coerce(instance)

            }
        }


        // 已弃用
        globals["new"] = object : VarArgFunction() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun invoke(args: Varargs): LuaValue {
                val classNameOrClass = args.arg(1)
                val clazz: Class<*> = when {
                    //字符串
                    classNameOrClass.isstring() -> {
                        val className = args.checkjstring(1)
                        Class.forName(className)
                    }
                    //类
                    classNameOrClass.isuserdata(Class::class.java) -> classNameOrClass.touserdata(
                        Class::class.java
                    ) as Class<*>

                    else -> {
                        throw IllegalArgumentException("First argument must be class name (String) or Class object")
                    }
                }

                val params = mutableListOf<Any?>()
                val paramTypes = mutableListOf<Class<*>>()
                for (i in 2..args.narg()) {
                    val luaValue = args.arg(i)
                    val value = fromLuaValue(luaValue)
                    params.add(value)
                    paramTypes.add(
                        value?.javaClass ?: Any::class.java
                    ) // Still using this for simplicity, see note below
                }

                val constructor = try {
                    clazz.getConstructor(*paramTypes.toTypedArray())
                } catch (e: NoSuchMethodException) {
                    // Attempt to find constructor with more flexible type matching
                    var foundConstructor: Constructor<*>? = null
                    for (ctor in clazz.constructors) {
                        if (ctor.parameterCount == params.size) {
                            val ctorParamTypes = ctor.parameterTypes
                            var match = true
                            for (i in params.indices) {
                                val param = params[i]
                                val ctorParamType = ctorParamTypes[i]
                                if (param != null && !ctorParamType.isAssignableFrom(param.javaClass)) {
                                    match = false
                                    break
                                } else if (param == null && ctorParamType.isPrimitive) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                foundConstructor = ctor
                                break
                            }
                        }
                    }
                    foundConstructor
                        ?: throw e // Re-throw the original NoSuchMethodException if no flexible match found
                }

                constructor.isAccessible = true // 允许访问非公共构造函数
                val instance = constructor.newInstance(*params.toTypedArray())
                return CoerceJavaToLua.coerce(instance)


            }
        }


        globals["callMethod"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (args.narg() < 1 || !args.arg(1).isuserdata()) {
                    throw IllegalArgumentException("First argument must be a Method object")
                }

                // Extract the Method object from the JavaInstance wrapper
                val methodArg = args.arg(1)
                val methodObj = methodArg.touserdata()
                val method: Method

                // Handle different ways the Method might be wrapped
                if (methodObj is Method) {
                    method = methodObj

                } else {
                    "callMethod param1 is not method".e()
                    return NIL
                }

                // Check if the method is static
                val isStatic = Modifier.isStatic(method.modifiers)

                // For static methods, we can call directly with all arguments
                // For instance methods, the second argument should be the instance

                val result: Any?

                if (isStatic) {
                    // Convert all Lua arguments to Java objects
                    val javaArgs = Array(args.narg() - 1) { i ->
                        fromLuaValue(args.arg(i + 2))
                    }

                    // Call the static method
                    result = method.invoke(null, *javaArgs)
                } else {
                    // Need at least 2 arguments for instance methods
                    if (args.narg() < 2) {
                        ("Instance method requires an object instance as second parameter").e()
                        return NIL
                    }

                    // Get the instance object
                    val instance = fromLuaValue(args.arg(2))

                    // Convert remaining Lua arguments to Java objects
                    val javaArgs = Array(args.narg() - 2) { i ->
                        fromLuaValue(args.arg(i + 3))
                    }

                    // Call the instance method
                    result = method.invoke(instance, *javaArgs)
                }

                // Convert the result back to Lua
                return CoerceJavaToLua.coerce(result)

            }
        }


        // 单独错误处理
        globals["hook"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val arg1 = args.arg(1)
                
                if (arg1.istable()) {
                    val table = arg1.checktable()
                    val clazz = table.get("class")
                    val method = table.get("method")
                    val loader = table.get("classloader").touserdata(ClassLoader::class.java) ?: lpparam.classLoader
                    
                    val paramTypes = mutableListOf<Class<*>>()
                    val params = table.get("params")
                    if (params.istable()) {
                         val t = params.checktable()
                         for(k in t.keys()) resolveParam(t.get(k), loader)?.let { paramTypes.add(it) }
                    }
                    
                    val callback = createMethodHook(table.get("before"), table.get("after"))
                    
                    if (clazz.isstring() && method.isstring()) {
                        XposedHelpers.findAndHookMethod(clazz.tojstring(), loader, method.tojstring(), *paramTypes.toTypedArray(), callback)
                    } else if (clazz.isuserdata(Class::class.java) && method.isstring()) {
                        XposedHelpers.findAndHookMethod(clazz.touserdata(Class::class.java), method.tojstring(), *paramTypes.toTypedArray(), callback)
                    } else if (method.isuserdata(Method::class.java)) {
                         XposedBridge.hookMethod(method.touserdata(Method::class.java), callback)
                    }
                    return NIL
                }
                
                if (arg1.isstring()) {
                    val className = arg1.tojstring()
                    val loader = args.optuserdata(2, lpparam.classLoader) as ClassLoader
                    val methodName = args.checkjstring(3)
                    
                    val paramTypes = mutableListOf<Class<*>>()
                    if (args.arg(4).istable()) {
                        val t = args.arg(4).checktable()
                        for(k in t.keys()) resolveParam(t.get(k), loader)?.let { paramTypes.add(it) }
                    } else {
                        for(i in 4 until args.narg() - 1) {
                            resolveParam(args.arg(i), loader)?.let { paramTypes.add(it) }
                        }
                    }
                    
                    val callback = createMethodHook(args.optfunction(args.narg() - 1, null), args.optfunction(args.narg(), null))
                    XposedHelpers.findAndHookMethod(className, loader, methodName, *paramTypes.toTypedArray(), callback)
                    return NIL
                }
                
                if (arg1.isuserdata(Method::class.java)) {
                    val method = arg1.touserdata(Method::class.java) as Method
                    XposedBridge.hookMethod(method, createMethodHook(args.optfunction(2, null), args.optfunction(3, null)))
                    return TRUE
                }
                
                return NIL
            }
        }


        // 单独错误处理
        globals["replace"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val arg1 = args.arg(1)
                
                if (arg1.istable()) {
                    val table = arg1.checktable()
                    val clazz = table.get("class")
                    val method = table.get("method")
                    val loader = table.get("classloader").touserdata(ClassLoader::class.java) ?: lpparam.classLoader
                    val replace = table.get("replace")
                    
                    val paramTypes = mutableListOf<Class<*>>()
                    val params = table.get("params")
                    if (params.istable()) {
                         val t = params.checktable()
                         for(k in t.keys()) resolveParam(t.get(k), loader)?.let { paramTypes.add(it) }
                    }
                    
                    val callback = createMethodReplacement(replace)

                    if (clazz.isstring() && method.isstring()) {
                        XposedHelpers.findAndHookMethod(clazz.tojstring(), loader, method.tojstring(), *paramTypes.toTypedArray(), callback)
                    } else if (clazz.isuserdata(Class::class.java) && method.isstring()) {
                        XposedHelpers.findAndHookMethod(clazz.touserdata(Class::class.java), method.tojstring(), *paramTypes.toTypedArray(), callback)
                    } else if (method.isuserdata(Method::class.java)) {
                         XposedBridge.hookMethod(method.touserdata(Method::class.java), callback)
                    }
                    return NIL
                }
                
                if (arg1.isstring()) {
                    val className = arg1.tojstring()
                    val loader = args.optuserdata(2, lpparam.classLoader) as ClassLoader
                    val methodName = args.checkjstring(3)
                    
                    val paramTypes = mutableListOf<Class<*>>()
                    if (args.arg(4).istable()) {
                        val t = args.arg(4).checktable()
                        for(k in t.keys()) resolveParam(t.get(k), loader)?.let { paramTypes.add(it) }
                    } else {
                        // Varargs param types: 4 to narg (excluding replace at narg)
                        for(i in 4 until args.narg()) {
                            resolveParam(args.arg(i), loader)?.let { paramTypes.add(it) }
                        }
                    }
                    
                    XposedHelpers.findAndHookMethod(className, loader, methodName, *paramTypes.toTypedArray(), createMethodReplacement(args.optfunction(args.narg(), null)))
                    return NIL
                }
                
                if (arg1.isuserdata(Method::class.java)) {
                    val method = arg1.touserdata(Method::class.java) as Method
                    XposedBridge.hookMethod(method, createMethodReplacement(args.optfunction(2, null)))
                    return TRUE
                }

                return NIL
            }
        }





        globals["hookAll"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val arg1 = args.arg(1)
                    var clazz: Class<*>? = null
                    var methodName: String? = null
                    var before: LuaValue? = null
                    var after: LuaValue? = null
                    
                    if (arg1.istable()) {
                        val table = arg1.checktable()
                        methodName = table.get("method").tojstring()
                        before = table.get("before")
                        after = table.get("after")
                        
                        val cls = table.get("class")
                        if (cls.isstring()) {
                            val loader = table.get("classloader").touserdata(ClassLoader::class.java) ?: lpparam.classLoader
                            clazz = XposedHelpers.findClass(cls.tojstring(), loader)
                        } else {
                            clazz = cls.touserdata(Class::class.java) as Class<*>
                        }
                    } else if (arg1.isstring()) {
                         val loader = args.optuserdata(2, lpparam.classLoader) as ClassLoader
                         clazz = XposedHelpers.findClass(arg1.tojstring(), loader)
                         methodName = args.arg(3).toString()
                         before = args.optfunction(4, null)
                         after = args.optfunction(5, null)
                    } else if (arg1.isuserdata(Class::class.java)) {
                        clazz = arg1.touserdata(Class::class.java) as Class<*>
                        methodName = args.arg(2).toString()
                        before = args.optfunction(3, null)
                        after = args.optfunction(4, null)
                    }
                    
                    if (clazz != null && methodName != null) {
                        XposedBridge.hookAllMethods(clazz, methodName, createMethodHook(before, after))
                        return TRUE
                    }
                } catch (e: Exception) {
                     val err = LuaUtil.simplifyLuaError(e.toString())
                    "[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err".e()
                }
                return FALSE
            }
        }


        globals["hookctor"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                try {
                    val arg1 = args.arg(1)
                    
                    if (arg1.istable()) {
                        val table = arg1.checktable()
                        val clazz = table.get("class")
                        val loader = table.get("classloader").touserdata(ClassLoader::class.java) ?: lpparam.classLoader
                        val callback = createMethodHook(table.get("before"), table.get("after"))
                        
                        val paramTypes = mutableListOf<Class<*>>()
                        val params = table.get("params")
                        if (params.istable()) {
                             val t = params.checktable()
                             for(k in t.keys()) resolveParam(t.get(k), loader)?.let { paramTypes.add(it) }
                        }
                        
                        if (clazz.isstring()) {
                            XposedHelpers.findAndHookConstructor(clazz.tojstring(), loader, *paramTypes.toTypedArray(), callback)
                        } else {
                            XposedHelpers.findAndHookConstructor(clazz.touserdata(Class::class.java), *paramTypes.toTypedArray(), callback)
                        }
                    } else if (arg1.isstring()) {
                        val className = arg1.tojstring()
                        val loader = args.optuserdata(2, lpparam.classLoader) as ClassLoader
                        val paramTypes = mutableListOf<Class<*>>()
                        
                        if (args.arg(3).istable()) {
                             val t = args.arg(3).checktable()
                             for(k in t.keys()) resolveParam(t.get(k), loader)?.let { paramTypes.add(it) }
                        } else {
                             for(i in 3 until args.narg() - 1) {
                                 resolveParam(args.arg(i), loader)?.let { paramTypes.add(it) }
                             }
                        }
                        
                        val callback = createMethodHook(args.optfunction(args.narg() - 1, null), args.optfunction(args.narg(), null))
                        XposedHelpers.findAndHookConstructor(className, loader, *paramTypes.toTypedArray(), callback)
                    } else if (arg1.isuserdata(Class::class.java)) {
                        val clazz = arg1.touserdata(Class::class.java) as Class<*>
                        val paramTypes = mutableListOf<Class<*>>()
                         if (args.arg(2).istable()) {
                             val t = args.arg(2).checktable()
                             for(k in t.keys()) resolveParam(t.get(k), lpparam.classLoader)?.let { paramTypes.add(it) }
                        } else {
                             for(i in 2 until args.narg() - 1) {
                                 resolveParam(args.arg(i), lpparam.classLoader)?.let { paramTypes.add(it) }
                             }
                        }
                        val callback = createMethodHook(args.optfunction(args.narg() - 1, null), args.optfunction(args.narg(), null))
                        XposedHelpers.findAndHookConstructor(clazz, *paramTypes.toTypedArray(), callback)
                    }
                    return NIL
                } catch (e: Exception) {
                     val err = LuaUtil.simplifyLuaError(e.toString())
                    "[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err".e()
                    return NIL
                }
            }
        }

        globals["hookcotr"] = globals["hookctor"]


        // 已弃用
        globals["createProxy"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // --- CORRECTED ARGUMENT PARSING ---

                // Argument 1: Interface Class (as Java Class userdata at index 1)
                val interfaceClass = args.checkuserdata(1, Class::class.java) as Class<*>
                if (!interfaceClass.isInterface) {
                    // Use argerror for better Lua-side error reporting
                    argerror(1, "Expected an interface class")
                }

                // Argument 2: Lua Table with method implementations (at index 2)
                val implementationTable = args.checktable(2)

                // Argument 3: Optional ClassLoader (as ClassLoader userdata at index 3)
                // Default to the interface's classloader if argument 3 is nil or absent.
                val classLoaderOrDefault = interfaceClass.classLoader // Define default value
                val classLoader = args.optuserdata(
                    3, // Index of the Lua argument
                    ClassLoader::class.java, // Expected Java type
                    classLoaderOrDefault // Default value if arg 3 is nil/absent
                ) as ClassLoader? // Cast the result (Object) to ClassLoader?
                    ?: classLoaderOrDefault // Use default if optuserdata returned null (which it shouldn't with a non-null default, but good practice)

                // Ensure we have a non-null loader (should always be true here)
                val finalClassLoader = classLoader ?: classLoaderOrDefault

                // --- END OF CORRECTIONS ---

                println("createProxy: Interface=${interfaceClass.name}, Loader=${finalClassLoader}")

                // Create the InvocationHandler
                val handler =
                    LuaInvocationHandler(implementationTable) // Assumes LuaInvocationHandler class is defined elsewhere

                try {
                    // Create the proxy instance using the specified class loader
                    val proxyInstance = Proxy.newProxyInstance(
                        finalClassLoader, // Use the resolved class loader
                        arrayOf(interfaceClass),
                        handler
                    )

                    // Return the proxy instance coerced to a LuaValue
                    return CoerceJavaToLua.coerce(proxyInstance)

                } catch (e: Exception) {
                    println("createProxy error: ${e.message}")
                    e.printStackTrace() // Log the full stack trace for debugging
                    // Consider returning a LuaError for better script handling
                    // throw LuaError("Failed to create proxy: ${e.message}")
                    return NIL
                }
            }
        }



        globals["printFields"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val target = args.arg(1)
                val separator = args.optjstring(2, " ")
                if (!target.isuserdata()) {
                    return NIL
                }
                
                val obj = target.touserdata()
                val isClass = obj is Class<*>
                val clazz = if (isClass) obj else obj.javaClass
                
                val sb = StringBuilder()
                var currentClass: Class<*>? = clazz
                while (currentClass != null && currentClass != Any::class.java) {
                    for (field in currentClass.declaredFields) {
                        // Skip if static but we are printing an instance (optional choice, 
                        // but usually printFields on instance wants instance fields. 
                        // However, user said "print all internal fields", usually implies instance fields for instance.
                        // If it's a static dump, we filter for static.
                        // XposedHelpers.getStaticObjectField handles static.
                        // XposedHelpers.getObjectField handles instance.
                        
                        val isStaticField = Modifier.isStatic(field.modifiers)
                        if (isClass && !isStaticField) continue // Printing class, only static fields
                        if (!isClass && isStaticField) continue // Printing instance, usually only instance fields desired, or maybe both?
                        // Let's stick to: isClass -> static only. Instance -> instance only (common behavior).
                        // If user strictly wants ALL, they can modify. But "instance's internal fields" usually means instance state.
                        
                        try {
                            val name = field.name
                            val value = if (isClass) {
                                XposedHelpers.getStaticObjectField(clazz, name)
                            } else {
                                XposedHelpers.getObjectField(obj, name)
                            }
                            sb.append(name).append("=").append(value).append(separator)
                        } catch (e: Exception) {
                            // Ignore inaccessible or error fields
                        }
                    }
                    currentClass = currentClass.superclass
                }
                
                val result = sb.toString()
                result.d()
                return NIL
            }
        }
    }


    private fun safeToJavaClass(luaValue: LuaValue): Class<*>? {
        if (!luaValue.isuserdata()) return null
        val userData = luaValue.touserdata()
        if (userData is Class<*>) return userData

        val wrapper = userData.javaClass
        // Fields
        val possibleFields = arrayOf("clazz", "class", "jclass", "classObject")
        for (name in possibleFields) {
            try {
                val f = wrapper.getDeclaredField(name).apply { isAccessible = true }
                val v = f.get(userData)
                if (v is Class<*>) return v
            } catch (_: Exception) {
            }
        }
        // Methods
        val possibleMethods = arrayOf("getClassObject", "toClass", "asClass", "getJavaClass")
        for (name in possibleMethods) {
            try {
                val m = wrapper.getMethod(name)
                val v = m.invoke(userData)
                if (v is Class<*>) return v
            } catch (_: Exception) {
            }
        }
        // Name
        try {
            val nameMethod = wrapper.getMethod("getName")
            val className = nameMethod.invoke(userData) as? String
            if (className != null) return Class.forName(className)
        } catch (_: Exception) {
        }

        "无法转换 ${wrapper.name} 为 Class 对象".d()
        return null
    }

    private fun resolveParam(param: LuaValue, classLoader: ClassLoader?): Class<*>? {
        return if (param.isstring()) {
            parseType(param.tojstring(), classLoader)
        } else {
            safeToJavaClass(param)
        }
    }

    private fun safeCall(func: LuaValue?, param: XC_MethodHook.MethodHookParam?) {
        func?.takeUnless { it.isnil() }?.let { f ->
            try {
                val luaParam = CoerceJavaToLua.coerce(param)
                f.call(luaParam)
            } catch (e: Exception) {
                val err = LuaUtil.simplifyLuaError(e.toString())
                "[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err".e()
            }
        }
    }

    private fun createMethodHook(before: LuaValue?, after: LuaValue?): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                safeCall(before, param)
            }

            override fun afterHookedMethod(param: MethodHookParam?) {
                safeCall(after, param)
            }
        }
    }

    private fun createMethodReplacement(replace: LuaValue?): XC_MethodReplacement {
        return object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                if (replace == null || replace.isnil()) return null
                try {
                    val luaParam = CoerceJavaToLua.coerce(param)
                    val result = replace.call(luaParam)
                    if (result != null && !result.isnil()) {
                        val returnType = (param?.method as? Method)?.returnType ?: Any::class.java
                        return CoerceLuaToJava.coerce(result, returnType)
                    }
                } catch (e: Exception) {
                    val err = LuaUtil.simplifyLuaError(e.toString())
                    "[Error] | Package: ${lpparam.packageName} | Script: $scriptName | Message: $err".e()
                }
                return null
            }
        }
    }

    companion object {
        // 将Lua值转换回Java类型
        @JvmStatic
        fun fromLuaValue(value: LuaValue?): Any? {
            return when {
                value == null || value.isnil() -> null
                value.isboolean() -> value.toboolean()
                value.isint() -> value.toint()
                value.islong() -> value.tolong()
                value.isnumber() -> value.todouble()
                value.isstring() -> value.tojstring()
                value.isuserdata() -> value.touserdata()
                else -> null
            }
        }

        @JvmStatic
        fun parseType(
            typeStr: String,
            classLoader: ClassLoader? = Thread.currentThread().contextClassLoader
        ): Class<*>? {
            val typeMap = mapOf(
                "int" to Int::class.javaPrimitiveType,
                "long" to Long::class.javaPrimitiveType,
                "boolean" to Boolean::class.javaPrimitiveType,
                "double" to Double::class.javaPrimitiveType,
                "float" to Float::class.javaPrimitiveType,
                "char" to Char::class.javaPrimitiveType,
                "byte" to Byte::class.javaPrimitiveType,
                "short" to Short::class.javaPrimitiveType,
                "String" to String::class.java
            )

            var baseType = typeStr.trim()
            var arrayDepth = 0

            // 计算数组维度
            while (baseType.endsWith("[]")) {
                arrayDepth++
                baseType = baseType.substring(0, baseType.length - 2).trim()
            }

            val baseClass = typeMap[baseType] ?: try {
                XposedHelpers.findClass(baseType, classLoader)
            } catch (_: ClassNotFoundException) {
                "Parameter error".d()
                return null
            }

            // 构建数组类型
            var resultClass = baseClass
            repeat(arrayDepth) {
                resultClass = java.lang.reflect.Array.newInstance(resultClass, 0).javaClass
            }

            return resultClass
        }
    }


    class LuaInvocationHandler(private val luaTable: LuaTable) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val methodName = method.name
            val luaFunction = luaTable.get(methodName)

            // Debugging log
            // println("Proxy invoked: Method=$methodName, LuaFunction found=${!luaFunction.isnil()}")

            return if (luaFunction.isfunction()) {

                // Convert Java arguments to LuaValue varargs
                val luaArgs = convertArgsToLuaValues(args)
                // Call the Lua function
                // Note: Lua functions typically don't receive 'self' implicitly when called from Java proxies.
                // We pass only the method arguments.
                val result = luaFunction.invoke(luaArgs) // Use invoke for Varargs

                // Convert the Lua return value back to the expected Java type
                CoerceLuaToJava.coerce(
                    result.arg1(),
                    method.returnType
                ) // result is Varargs, get first value
            } else {
                // Handle standard Object methods or missing implementations
                when (methodName) {
                    "toString" -> "LuaProxy<${proxy.javaClass.interfaces.firstOrNull()?.name ?: "UnknownInterface"}>@${
                        Integer.toHexString(
                            hashCode()
                        )
                    }"

                    "hashCode" -> luaTable.hashCode() // Or System.identityHashCode(proxy)? Or handler's hashcode? Be consistent.
                    "equals" -> proxy === args?.get(0) // Standard proxy equality check
                    else -> {
                        // No Lua function found for this method
                        println("Warning: No Lua implementation found for proxied method: $methodName")
                        // Return default value based on return type, or throw exception
                        if (method.returnType == Void.TYPE) {
                            null // Return null for void methods
                        } else if (method.returnType.isPrimitive) {
                            // Return default primitive values (0, false)
                            when (method.returnType) {
                                Boolean::class.javaPrimitiveType -> false
                                Char::class.javaPrimitiveType -> '\u0000'
                                Byte::class.javaPrimitiveType -> 0.toByte()
                                Short::class.javaPrimitiveType -> 0.toShort()
                                Int::class.javaPrimitiveType -> 0
                                Long::class.javaPrimitiveType -> 0L
                                Float::class.javaPrimitiveType -> 0.0f
                                Double::class.javaPrimitiveType -> 0.0
                                else -> throw UnsupportedOperationException("Unsupported primitive return type: ${method.returnType.name}")
                            }
                        } else {
                            // Return null for object return types
                            null
                            // Alternatively, throw an exception:
                            // throw UnsupportedOperationException("No Lua implementation for method: $methodName")
                        }
                    }
                }
            }
        }

        // Helper function to convert Java args array to LuaValue Varargs
        private fun convertArgsToLuaValues(javaArgs: Array<Any?>?): Varargs {
            if (javaArgs == null || javaArgs.isEmpty()) {
                return NONE
            }
            val luaArgs = javaArgs.map { CoerceJavaToLua.coerce(it) }.toTypedArray()
            // Important: Use varargsOf, not listOf, to create Varargs correctly
            return LuaValue.varargsOf(luaArgs)
        }


    }

}

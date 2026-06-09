package io.github.kulipai.luahook.hook.api
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import io.github.kulipai.luahook.core.log.d
import org.luaj.Globals
import org.luaj.LuaError
import org.luaj.LuaTable
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import java.lang.reflect.Modifier

/**
 * Lua资源访问桥接器
 * 提供一个Lua表，其中包含需要Context参数的资源访问函数。
 */

object LuaResource {

    // 创建并返回包含资源访问函数的Lua表
    fun createResourceAccessTable(): LuaTable {
        val resourceTable = LuaTable()

        // --- 定义需要 Context 参数的 Lua 函数 ---

        // 获取Drawable资源
        // Lua usage: resources.getDrawable(context, resourceName [, packageName])
        resourceTable.set("getDrawable", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string), 参数3: packageName (string, optional)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)
                    // 如果提供了包名，则使用它；否则使用 context 的包名
                    val packageName = if (args.narg() > 2 && !args.isnil(3)) args.checkjstring(3) else context.packageName

//                    "Lua::getDrawable - Context: ${context.packageName}, ResName: $resourceName, PkgName: $packageName".d()

                    val drawable = ResourceHelper.getDrawableByName(context, resourceName, packageName)
                    return if (drawable != null) CoerceJavaToLua.coerce(drawable) else NIL
                } catch (e: Exception) {
                    "Lua::getDrawable - Error".d()
                    throw LuaError(e)
                }
            }
        })

        // 获取资源ID
        // Lua usage: resources.getResourceId(context, resourceName, resourceType)
        resourceTable.set("getResourceId", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string), 参数3: resourceType (string)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)
                    val resourceType = args.checkjstring(3)

//                    "Lua::getResourceId - Context: ${context.packageName}, ResName: $resourceName, ResType: $resourceType".d()

                    val resId = ResourceHelper.getResourceId(context, resourceName, resourceType)
                    return valueOf(resId)
                } catch (e: Exception) {
                    "Lua::getResourceId - Error".d()
                    throw LuaError(e)
                }
            }
        })

        // 获取字符串资源
        // Lua usage: resources.getString(context, resourceName)
        resourceTable.set("getString", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)

//                    "Lua::getString - Context: ${context.packageName}, ResName: $resourceName".d()

                    val str = ResourceHelper.getStringByName(context, resourceName)
                    return if (str != null) valueOf(str) else NIL
                } catch (e: Exception) {
                    "Lua::getString - Error".d()
                    throw LuaError(e)
                }
            }
        })

        // 获取颜色资源
        // Lua usage: resources.getColor(context, resourceName)
        resourceTable.set("getColor", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceName (string)
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceName = args.checkjstring(2)

//                    "Lua::getColor - Context: ${context.packageName}, ResName: $resourceName".d()

                    // 注意：ResourceHelper.getColorByName 返回 Int
                    val colorInt = ResourceHelper.getColorByName(context, resourceName)
                    // 如果颜色是0，可能是没找到，也可能是颜色本身就是透明黑
                    // 这里直接返回值，由Lua端判断
                    return valueOf(colorInt)
                } catch (e: Exception) {
                    "Lua::getColor - Error".d()
                    throw LuaError(e)
                }
            }
        })

        // 获取R类常量
        // Lua usage: resources.getRConstants(context, resourceType)
        resourceTable.set("getRConstants", object : VarArgFunction() {
            override fun onInvoke(args: Varargs): Varargs {
                try {
                    // 参数1: Context, 参数2: resourceType (string, e.g., "drawable", "string", "id")
                    val context = args.checkuserdata(1, Context::class.java) as Context
                    val resourceType = args.checkjstring(2)

//                    "Lua::getRConstants - Context: ${context.packageName}, ResType: $resourceType".d()

                    val constantsMap = ResourceHelper.getRClassConstants(context, resourceType)
                    val luaTable = LuaTable()
                    constantsMap.forEach { (key, value) ->
                        luaTable.set(key, valueOf(value))
                    }
                    return luaTable
                } catch (e: Exception) {
                    "Lua::getRConstants - Error".d()
                    throw LuaError(e)
                    // 返回空表表示出错
                }
            }
        })

        return resourceTable
    }

    /**
     * 将资源访问表注册到 Lua 环境中
     * @param globals Lua 全局环境
     * @param tableName 在Lua中暴露的表名，例如 "resources"
     */
    fun registerTo(globals: Globals, tableName: String = "resources") {
        globals.set(tableName, createResourceAccessTable())
//        "LuaResourceBridge registered as '$tableName' in Lua globals.".d()
    }
}

/**
 * 资源访问辅助类 (保持不变，因为它已经接受Context作为参数)
 * 注意：已将日志从 .d() 改为更通用的打印或移除，因为Helper不应强依赖特定日志库
 */
object ResourceHelper {

    @SuppressLint("DiscouragedApi")
    fun getDrawableByName(context: Context, resourceName: String, packageName: String? = null): Drawable? {
        try {
            val targetPackageName = packageName ?: context.packageName
            val resources = if (targetPackageName != context.packageName) {
                // 尝试获取其他应用的资源，需要权限或在特定环境（如Xposed）下才能成功
                try {
                    context.packageManager.getResourcesForApplication(targetPackageName)
                } catch (e: Exception) {
                    ("ResourceHelper: Failed to get resources for package $targetPackageName").d()
                    throw LuaError(e)
                    // 获取外部包资源失败
                }
            } else {
                context.resources
            }

            val resId = resources.getIdentifier(resourceName, "drawable", targetPackageName)

            if (resId == 0) {
                ("ResourceHelper: Drawable resource ID not found for '$resourceName' in package '$targetPackageName'").d()
                return null
            }

            // 使用兼容性方法获取Drawable
            return ContextCompat.getDrawable(context, resId)
            /* // 如果不想依赖 androidx.core
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                resources.getDrawable(resId, context.theme)
            } else {
                @Suppress("DEPRECATION")
                resources.getDrawable(resId)
            }
            */
        } catch (e: Resources.NotFoundException) {
            ("ResourceHelper: Drawable resource not found for '$resourceName' (ID: ${context.resources.getIdentifier(resourceName, "drawable", packageName ?: context.packageName)})").d()
            throw LuaError(e)
        } catch (e: Exception) {
            println("ResourceHelper: Error getting drawable '$resourceName': ${e.javaClass.name}")
            throw LuaError(e)
        }
    }

    @SuppressLint("DiscouragedApi")
    fun getResourceId(context: Context, resourceName: String, resourceType: String): Int {
        return try {
            context.resources.getIdentifier(resourceName, resourceType, context.packageName)
        } catch (e: Exception) {
            ("ResourceHelper: Error getting resource ID for '$resourceName' (type: $resourceType)").d()
            throw LuaError(e)
        }
    }

    fun getStringByName(context: Context, resourceName: String): String? {
        return try {
            val resId = getResourceId(context, resourceName, "string")
            if (resId == 0) {
                ("ResourceHelper: String resource ID not found for '$resourceName'").d()
                return null
            }
            context.resources.getString(resId)
        } catch (e: Resources.NotFoundException) {
            ("ResourceHelper: String resource not found for '$resourceName' (ID: ${getResourceId(context, resourceName, "string")})").d()
            throw LuaError(e)
        } catch (e: Exception) {
            ("ResourceHelper: Error getting string '$resourceName'").d()
            throw LuaError(e)
        }
    }

    fun getColorByName(context: Context, resourceName: String): Int {
        return try {
            val resId = getResourceId(context, resourceName, "color")
            if (resId == 0) {
                ("ResourceHelper: Color resource ID not found for '$resourceName'").d()

                return 0 // Return 0 for failure (ambiguous with black color)
            }
            // 使用兼容性方法获取Color
            ContextCompat.getColor(context, resId)
            /* // 如果不想依赖 androidx.core
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                 context.resources.getColor(resId, context.theme)
            } else {
                 @Suppress("DEPRECATION")
                 context.resources.getColor(resId)
            }
            */
        } catch (e: Resources.NotFoundException) {
            ("ResourceHelper: Color resource not found for '$resourceName' (ID: ${getResourceId(context, resourceName, "color")})").d()
            throw LuaError(e)
        } catch (e: Exception) {
            ("ResourceHelper: Error getting color '$resourceName'").d()
            throw LuaError(e)
        }
    }

    fun getRClassConstants(context: Context, resourceType: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        try {
            val packageName = context.packageName
            // 加载内部类 R$resourceType (例如 com.example.app.R$drawable)
            val rClass = Class.forName("$packageName.R$$resourceType")
            rClass.declaredFields.forEach { field ->
                // 只获取 public static final int 类型的字段
                if (Modifier.isStatic(field.modifiers) &&
                    Modifier.isFinal(field.modifiers) &&
                    Modifier.isPublic(field.modifiers) && // R文件中的资源ID通常是public的
                    field.type == Int::class.javaPrimitiveType) { // 确保是 int 类型
                    try {
                        // 无需 setAccessible(true)，因为它们是 public
                        val name = field.name
                        val value = field.getInt(null) // 获取静态字段的值
                        result[name] = value
                    } catch (_: Exception) {
                        // 忽略获取单个常量失败的情况
                        ("ResourceHelper: Error getting R constant value for '${field.name}'").d()
                    }
                }
            }
        } catch (_: ClassNotFoundException) {
            ("ResourceHelper: R class not found for type '$resourceType' in package '${context.packageName}'").d()
            // 这可能是因为类型名称错误（如 "layout" 而不是 "layouts"），或者R文件结构不同
        } catch (e: Exception) {
            ("ResourceHelper: Error getting R class constants for type '$resourceType'").d()
            throw LuaError(e)
        }
        return result
    }
}

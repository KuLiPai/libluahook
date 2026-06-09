package com.androlua

import android.content.Context
import android.content.ContextWrapper
import dx.proxy.Enhancer
import dx.proxy.EnhancerInterface
import dx.proxy.MethodFilter
import dx.proxy.MethodInterceptor
import org.luaj.LuaValue
import java.io.File
import java.lang.reflect.Method

/**
 * Created by nirenr on 2018/12/19.
 * Fixed for Android 10+ SecurityException using ContextWrapper Hook by Gemini
 */
class LuaEnhancer(context: Context?, cls: Class<*>) {
    private val mEnhancer: Enhancer

    // =======================================================
    // 🎭 Context 欺骗器：拦截外部存储请求，重定向到内部存储
    // =======================================================
    private class ForceInternalContext(base: Context?) : ContextWrapper(base) {
        override fun getExternalFilesDir(type: String?): File? {
            // 拦截！无论请求什么，都返回内部私有目录
            return super.getDir("dexfiles", MODE_PRIVATE)
        }

        override fun getExternalCacheDir(): File? {
            // 拦截！
            return super.getDir("dexcache", MODE_PRIVATE)
        }

        // 有些老版本库可能直接调用这个
        override fun getFilesDir(): File? {
            return super.getDir("files", MODE_PRIVATE)
        }
    }

    // =======================================================
    constructor(cls: String) : this(Class.forName(cls))

    constructor(cls: Class<*>) : this(try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    } catch (e: Exception) {
        null
    }, cls) {
//        Log.d("LuaEnhancerDebug", "123")
    }

    constructor(context: Context?, cls: String) : this(context, Class.forName(cls))

    init {
//        if (context == null) throw NullPointerException("Context is null")
//        if (cls == null) throw NullPointerException("Class is null")

        // 1. 设置系统属性 (作为双重保险)
        try {
//            Log.d(TAG, "🛠️ $context");
            val dexDir = context!!.getDir("dexfiles", Context.MODE_PRIVATE)
            System.setProperty("dexmaker.dexcache", dexDir.getAbsolutePath())
            //            Log.d(TAG, "🛠️ [1/2] System Property 设置为: " + dexDir.getAbsolutePath());
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. 创建一个“被欺骗”的 Context
        val hookedContext: Context = ForceInternalContext(context)

        //        Log.d(TAG, "🛠️ [2/2] 启用 Context 劫持，强制重定向 SD 卡写入请求");

        // 3. 将这个“假” Context 传给 Enhancer
        // Enhancer 以为它是真的 Activity/Context，实际上它的所有路径请求都被我们篡改了
        mEnhancer = Enhancer(hookedContext)
        mEnhancer.setSuperclass(cls)
    }

    fun setInterceptor(obj: EnhancerInterface, interceptor: MethodInterceptor?) {
        obj.setMethodInterceptor_Enhancer(interceptor)
    }

    fun create(): Class<*>? {
        try {
            return mEnhancer.create()
        } catch (e: Exception) {
//            Log.e(TAG, "create() Error", e);
        }
        return null
    }

    fun create(filer: MethodFilter?): Class<*>? {
        try {
            mEnhancer.setMethodFilter(filer)
            return mEnhancer.create()
        } catch (e: Exception) {
//            Log.e(TAG, "create(Filter) Error", e);
        }
        return null
    }

    fun create(arg: LuaValue): Class<*>? {
        val filter = MethodFilter { method: Method?, name: String? -> !arg.get(name).isnil() }
        try {
            mEnhancer.setMethodFilter(filter)
            // 此时调用 create，内部的 DexMaker 会调用 hookedContext.getExternalFilesDir()
            // 然后被我们重定向到内部存储，从而绕过 SecurityException
            val cls = mEnhancer.create()
            setInterceptor(cls, LuaMethodInterceptor(arg))
            return cls
        } catch (e: Exception) {
//            Log.e(TAG, "❌ create(LuaValue) 崩溃", e);
            e.printStackTrace()
        }
        return null
    }

    companion object {
        private const val TAG = "LuaEnhancerDebug"
        fun setInterceptor(obj: Class<*>, interceptor: MethodInterceptor?) {
            try {
                val field = obj.getDeclaredField("methodInterceptor")
                field.setAccessible(true)
                field.set(obj, interceptor)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

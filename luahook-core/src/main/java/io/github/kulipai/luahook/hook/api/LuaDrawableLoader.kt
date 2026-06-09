package io.github.kulipai.luahook.hook.api

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.drawable.toDrawable
import io.github.kulipai.luahook.core.log.d
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.luaj.Globals
import org.luaj.LuaError
import org.luaj.LuaValue
import org.luaj.lib.ThreeArgFunction
import org.luaj.lib.TwoArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 加载网络/本地图片的Drawable
 */

class LuaDrawableLoader(val handler: Handler = Handler(Looper.getMainLooper())) {

    // 同步网络图像
    val loadDrawableSync: TwoArgFunction = object : TwoArgFunction() {
        override fun call(urlValue: LuaValue, cacheValue: LuaValue): LuaValue {
            val url = urlValue.checkjstring()
            val cache = cacheValue.optboolean(true)
//                "开始同步加载: $url, 缓存: $cache".d()
            val drawable = DrawableHelper.loadDrawableSync(url, cache)
            return if (drawable != null) CoerceJavaToLua.coerce(drawable) else NIL
        }
    }

    // 异步网络图像
    val loadDrawableAsync: ThreeArgFunction = object : ThreeArgFunction() {
        override fun call(urlValue: LuaValue, cacheValue: LuaValue, callback: LuaValue): LuaValue {
            val url = urlValue.checkjstring()
            val cache = cacheValue.optboolean(true)
//                "开始异步加载: $url, 缓存: $cache".d()
            DrawableHelper.loadDrawableAsync(url, cache) { drawable ->
                handler.post {
                    try {
                        if (!callback.isnil()) {
                            if (drawable != null) {
                                callback.call(CoerceJavaToLua.coerce(drawable))
                            } else {
                                callback.call(NIL)
                            }
                        }
                    } catch (e: Exception) {
                        "DrawableLoader err: ${e.javaClass.name}".d()
                        throw LuaError(e)
                    }
                }
            }
            return NIL

        }
    }

    // 本地文件图像
    val loadDrawableFromFile: TwoArgFunction = object : TwoArgFunction() {
        override fun call(pathValue: LuaValue, cacheValue: LuaValue): LuaValue {
            try {
                val path = pathValue.checkjstring()
                val cache = cacheValue.optboolean(true)
//                "开始加载本地文件: $path, 缓存: $cache".d()
                val drawable = DrawableHelper.loadDrawableFromFile(path, cache)
                return if (drawable != null) CoerceJavaToLua.coerce(drawable) else NIL
            } catch (e: Exception) {
                "loadDrawableFromFile err: ${e.javaClass.name}".d()
                throw LuaError(e)
            }
        }
    }

    // 清除缓存
    val clearDrawableCache: TwoArgFunction = object : TwoArgFunction() {
        override fun call(keyValue: LuaValue, allValue: LuaValue): LuaValue {
            try {
                val key = if (!keyValue.isnil()) keyValue.checkjstring() else null
                val clearAll = allValue.optboolean(false)
                return valueOf(DrawableHelper.clearCache(key, clearAll))
            } catch (e: Exception) {
                "clearDrawableCache err: ${e.javaClass.name}: ${e.message}".d()
                throw LuaError(e)
            }
        }
    }

    fun registerTo(globals: Globals) {
        globals.set("loadDrawableSync", loadDrawableSync)
        globals.set("loadDrawableAsync", loadDrawableAsync)
        globals.set("loadDrawableFromFile", loadDrawableFromFile)
        globals.set("clearDrawableCache", clearDrawableCache)
    }
}

object DrawableHelper {
    // 内存缓存
    private val memoryCache = android.util.LruCache<String, Drawable>(50)

    // 共享的OkHttpClient实例，适当配置超时和连接池
    private val okHttpClient by lazy {
        try {
//            "初始化OkHttpClient".d()
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .dispatcher(Dispatcher().apply {
                    maxRequestsPerHost = 10
                    maxRequests = 20
                })
                .build()
        } catch (e: Exception) {
            "OkHttpClient Initialization failed: ${e.javaClass.name}: ${e.message}".d()
//            throw LuaError(e)
            // 创建一个基本的OkHttpClient作为后备
            OkHttpClient()
        }
    }

    /**
     * 同步加载网络图片
     */
    fun loadDrawableSync(url: String, cache: Boolean = true): Drawable? {
//        "loadDrawableSync开始执行: $url".d()

        try {
            // 检查URL格式
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "URL Invalid format: $url".d()
                return null
            }

            // 检查缓存
            if (cache && memoryCache.get(url) != null) {
//                "使用缓存同步加载: $url".d()
                return memoryCache.get(url)
            }

//            "同步请求开始: $url".d()

            // 创建请求
            val request = try {
                Request.Builder().url(url).get().build()
            } catch (e: Exception) {
                "Failed to create request object: ${e.javaClass.name}: ${e.message}".d()

                return null
            }

            // 执行请求
            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                "Execution of synchronous request IO exception: ${e.javaClass.name}: ${e.message}".d()
                return null
            } catch (e: Exception) {
                "Exception during the execution of synchronous request: ${e.javaClass.name}: ${e.message}".d()
                return null
            }

//            "同步请求响应: code=${response.code}, message=${response.message}".d()

            if (!response.isSuccessful) {
                "Synchronous request failed, response code: ${response.code}".d()
                return null
            }

            // 处理响应体
            val responseBody = response.body

            // 读取字节数据
            val bytes = try {
                responseBody.bytes()
            } catch (e: Exception) {
                "Abnormal response data read: ${e.javaClass.name}: ${e.message}".d()
                return null
            }

//            "成功获取图片数据: ${bytes.size} 字节".d()

            if (bytes.isEmpty()) {
                "Synchronous loading failed: image data is empty.".d()
                return null
            }

            // 解码图片
            val bitmap = try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, options)
            } catch (e: Exception) {
                "Bitmap decoding exception: ${e.javaClass.name}: ${e.message}".d()
                return null
            }

            if (bitmap == null) {
                "Synchronous loading failed: Decoding Bitmap failed".d()
                return null
            }

//            "Bitmap解码成功: ${bitmap.width}x${bitmap.height}".d()

            // 创建Drawable
            val drawable = try {
                bitmap.toDrawable(Resources.getSystem())
            } catch (e: Exception) {
                "Exception creating BitmapDrawable: ${e.javaClass.name}: ${e.message}".d()
                bitmap.recycle()
                return null
            }

            // 缓存Drawable
            if (cache) {
                try {
                    memoryCache.put(url, drawable)
//                    "已缓存Drawable: $url".d()
                } catch (e: Exception) {
                    "Cache Drawable Exception: ${e.javaClass.name}: ${e.message}".d()
                    // 缓存失败不影响返回结果
                }
            }

//            "同步加载成功: $url".d()
            return drawable
        } catch (e: Throwable) {
            "Synchronous loading uncaught exceptions: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }

    /**
     * 异步加载网络图片
     */
    fun loadDrawableAsync(url: String, cache: Boolean = true, callback: (Drawable?) -> Unit) {
        try {
            // 检查URL格式
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "The URL format is invalid.: $url".d()
                callback(null)
                return
            }

            // 检查缓存
            if (cache && memoryCache.get(url) != null) {
//                "使用缓存异步加载: $url".d()
                callback(memoryCache.get(url))
                return
            }

//            "异步请求开始: $url".d()

            // 创建请求
            val request = try {
                Request.Builder().url(url).get().build()
            } catch (e: Exception) {
                "Failed to create asynchronous request object: ${e.javaClass.name}: ${e.message}".d()
                callback(null)
                return
            }

            // 执行异步请求
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    "Asynchronous request failed: ${e.javaClass.name}: ${e.message}".d()
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
//                        "异步请求响应: code=${response.code}, message=${response.message}".d()

                        if (!response.isSuccessful) {
                            "Abnormal asynchronous request response code: ${response.code}".d()
                            callback(null)
                            return
                        }

                        // 处理响应体
                        val responseBody = response.body

                        // 读取字节数据
                        val bytes = try {
                            responseBody.bytes()
                        } catch (e: Exception) {
                            "Error reading asynchronous response data: ${e.javaClass.name}: ${e.message}".d()
                            callback(null)
                            return
                        }

//                        "成功获取异步图片数据: ${bytes.size} 字节".d()

                        if (bytes.isEmpty()) {
                            "Asynchronous loading failed: image data is empty.".d()
                            callback(null)
                            return
                        }

                        // 解码图片
                        val bitmap = try {
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, options)
                        } catch (e: Exception) {
                            "Decoding asynchronous Bitmap exception: ${e.javaClass.name}: ${e.message}".d()
                            callback(null)
                            return
                        }

                        if (bitmap == null) {
                            "Asynchronous loading failed: Bitmap decoding failed".d()
                            callback(null)
                            return
                        }

//                        "异步Bitmap解码成功: ${bitmap.width}x${bitmap.height}".d()

                        // 创建Drawable
                        val drawable = try {
                            bitmap.toDrawable(Resources.getSystem())
                        } catch (e: Exception) {
                            "Error creating asynchronous BitmapDrawable: ${e.javaClass.name}: ${e.message}".d()
                            bitmap.recycle()
                            callback(null)
                            return
                        }

                        // 缓存Drawable
                        if (cache) {
                            try {
                                memoryCache.put(url, drawable)
//                                "已缓存异步Drawable: $url".d()
                            } catch (e: Exception) {
                                "Cache asynchronous Drawable exception: ${e.javaClass.name}: ${e.message}".d()
                                // 缓存失败不影响返回结果
                            }
                        }

//                        "异步加载成功: $url".d()
                        callback(drawable)
                    } catch (e: Throwable) {
                        "Asynchronous loading response handling exception: ${e.javaClass.name}: ${e.message}".d()
                        callback(null)
                    }
                }
            })
        } catch (e: Throwable) {
            "Uncaught exception in asynchronous loading: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }

    /**
     * 从本地文件加载图片
     */
    fun loadDrawableFromFile(path: String, cache: Boolean = true): Drawable? {
        try {
            // 检查缓存
            if (cache && memoryCache.get(path) != null) {
//                "使用缓存加载本地图片: $path".d()
                return memoryCache.get(path)
            }

            val file = File(path)
            if (!file.exists()) {
                "The file does not exist.: $path".d()
                return null
            }

//            "开始解码本地文件: $path, 文件大小: ${file.length()} 字节".d()

            // 解码文件
            val bitmap = try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(path, options)
            } catch (e: Exception) {
                "Error decoding local file: ${e.javaClass.name}: ${e.message}".d()
                return null
            }

            if (bitmap == null) {
                "Local image decoding failed: $path".d()
                return null
            }

//            "本地Bitmap解码成功: ${bitmap.width}x${bitmap.height}".d()

            // 创建Drawable
            val drawable = try {
                bitmap.toDrawable(Resources.getSystem())
            } catch (e: Exception) {
                "Exception creating local BitmapDrawable: ${e.javaClass.name}: ${e.message}".d()
                bitmap.recycle()
                return null
            }

            // 缓存Drawable
            if (cache) {
                try {
                    memoryCache.put(path, drawable)
//                    "已缓存本地Drawable: $path".d()
                } catch (e: Exception) {
                    "Exception creating local BitmapDrawable: ${e.javaClass.name}: ${e.message}".d()
                    // 缓存失败不影响返回结果
                }
            }

//            "本地图片加载成功: $path".d()
            return drawable
        } catch (e: Throwable) {
            "Local loading unhandled exception: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }

    /**
     * 清除缓存
     * @param key 特定的缓存键，如果为null且clearAll为true则清除所有缓存
     * @param clearAll 是否清除所有缓存
     * @return 返回是否成功清除缓存
     */
    fun clearCache(key: String? = null, clearAll: Boolean = false): Boolean {
        return try {
            if (clearAll || key == null) {
                val size = memoryCache.size()
                memoryCache.evictAll()
                "Clear all caches, a total of $size items.".d()
                true
            } else if (memoryCache.get(key) != null) {
                memoryCache.remove(key)
                "Clear cache: $key".d()
                true
            } else {
                "Cache does not exist: $key".d()
                false
            }
        } catch (e: Exception) {
            "Failed to clear cache: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }
}
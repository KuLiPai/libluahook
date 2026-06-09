package io.github.kulipai.luahook.hook.api

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.collections.iterator

/**
 * http封装
 */

object LuaHttp{

    private val client = OkHttpClient()  // 确保 OkHttpClient 实例已定义
    fun registerTo(env: LuaValue): LuaValue {
        val http = LuaTable()



        http["get"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val hasTimeout: Boolean = args.isnumber(args.narg())
                val callback =
                    if (hasTimeout) args.checkfunction(args.narg() - 1) else args.checkfunction(args.narg())
                val headers =
                    if (args.narg() > (if (hasTimeout) 3 else 2) && args.istable(2)) args.checktable(
                        2
                    ) else null
                val cookie =
                    if (args.narg() > (if (hasTimeout) 4 else 3)) args.optjstring(3, null) else null
                val timeout =
                    if (hasTimeout) args.checklong(args.narg()) else 10000L // Default 10s

                val client = this@LuaHttp.client.newBuilder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                    .build()

                val requestBuilder = Request.Builder().url(url)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)
                        if (key.isnil()) break
                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(valueOf(-1), valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body.bytes()
                        callback.call(valueOf(response.code), valueOf(body))
                    }
                })

                return NIL
            }
        }

        http["post"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val postData = args.checkjstring(2)
                val hasTimeout: Boolean = args.isnumber(args.narg())
                val callback =
                    if (hasTimeout) args.checkfunction(args.narg() - 1) else args.checkfunction(args.narg())
                val headers =
                    if (args.narg() > (if (hasTimeout) 4 else 3) && args.istable(3)) args.checktable(
                        3
                    ) else null
                val cookie =
                    if (args.narg() > (if (hasTimeout) 5 else 4)) args.optjstring(4, null) else null
                val timeout =
                    if (hasTimeout) args.checklong(args.narg()) else 10000L // Default 10s

                val client = this@LuaHttp.client.newBuilder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                    .build()

                var contentType = "application/x-www-form-urlencoded"
                headers?.let {
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)
                        if (key.isnil()) break
                        if (key.tojstring().equals("Content-Type", ignoreCase = true)) {
                            contentType = value.tojstring()
                            break
                        }
                    }
                }

                val requestBody = postData.toRequestBody(contentType.toMediaTypeOrNull())
                val requestBuilder = Request.Builder().url(url).post(requestBody)

                headers?.let {
                    val headerMap = mutableMapOf<String, String>()
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)
                        if (key.isnil()) break
                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(valueOf(-1), valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body.bytes()
                        callback.call(valueOf(response.code), valueOf(body))
                    }
                })

                return NIL
            }
        }




        // 新增 postJson 方法，专门用于发送 JSON 数据
        http["postJson"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val jsonData = args.checkjstring(2)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 3 && args.istable(3)) args.checktable(3) else null
                val cookie = if (args.narg() > 4) args.optjstring(4, null) else null

                // 创建 JSON 类型的请求体
                val requestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())

                val requestBuilder = Request.Builder().url(url).post(requestBody)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()

                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)

                        if (key.isnil()) break

                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(valueOf(-1), valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body.bytes()
                        callback.call(valueOf(response.code), valueOf(body))
                    }
                })

                return NIL
            }
        }


        // 下载文件到指定路径
        http["download"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val savePath = args.checkjstring(2)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 3 && args.istable(3)) args.checktable(3) else null
                val cookie = if (args.narg() > 4) args.optjstring(4, null) else null

                val requestBuilder = Request.Builder().url(url)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)
                        if (key.isnil()) break
                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(valueOf(-1), valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            if (!response.isSuccessful) {
                                callback.call(
                                    valueOf(response.code),
                                    valueOf("Download failed: ${response.message}")
                                )
                                return
                            }

                            val body = response.body

                            val file = File(savePath)
                            // 创建父目录（如果不存在）
                            file.parentFile?.mkdirs()

                            val inputStream = body.byteStream()
                            val outputStream = FileOutputStream(file)
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            var downloadedBytes: Long = 0
                            val contentLength = body.contentLength()

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                // 每下载约10%回调一次进度
                                if (contentLength > 0 && downloadedBytes % (contentLength / 10) < 4096) {
                                    val progress = (downloadedBytes * 100 / contentLength).toInt()
                                    callback.call(valueOf(0), valueOf(progress))
                                }
                            }

                            outputStream.close()
                            inputStream.close()

                            callback.call(
                                valueOf(response.code),
                                valueOf(savePath)
                            )
                        } catch (e: Exception) {
                            callback.call(
                                valueOf(-1),
                                valueOf("Download exception: ${e.message}")
                            )
                        }
                    }
                })

                return NIL
            }
        }

// HEAD 请求，只获取响应头信息
        http["head"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 2 && args.istable(2)) args.checktable(2) else null
                val cookie = if (args.narg() > 3) args.optjstring(3, null) else null

                val requestBuilder = Request.Builder().url(url).head()

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)
                        if (key.isnil()) break
                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(valueOf(-1), valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // 将响应头转换为 Lua 表
                        val headersTable = LuaTable()
                        response.headers.forEach { (name, value) ->
                            headersTable[valueOf(name)] = valueOf(value)
                        }

                        callback.call(valueOf(response.code), headersTable)
                    }
                })

                return NIL
            }
        }

// PUT 请求，通常用于更新资源
        http["put"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val putData = args.checkjstring(2)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 3 && args.istable(3)) args.checktable(3) else null
                val cookie = if (args.narg() > 4) args.optjstring(4, null) else null

                // 默认内容类型
                var contentType = "application/json"

                // 检查headers中是否指定了Content-Type
                headers?.let {
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)

                        if (key.isnil()) break

                        if (key.tojstring().equals("Content-Type", ignoreCase = true)) {
                            contentType = value.tojstring()
                            break
                        }
                    }
                }

                // 创建RequestBody
                val requestBody = putData.toRequestBody(contentType.toMediaTypeOrNull())
                val requestBuilder = Request.Builder().url(url).put(requestBody)

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)
                        if (key.isnil()) break
                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(valueOf(-1), valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body.bytes()
                        callback.call(valueOf(response.code), valueOf(body))
                    }
                })

                return NIL
            }
        }

// DELETE 请求，用于删除资源
        http["delete"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 2 && args.istable(2)) args.checktable(2) else null
                val cookie = if (args.narg() > 3) args.optjstring(3, null) else null

                val requestBuilder = Request.Builder().url(url).delete()

                // 添加请求头
                headers?.let {
                    val headerMap = mutableMapOf<String, String>()
                    var key: LuaValue = NIL
                    while (true) {
                        val nextPair = headers.next(key)
                        key = nextPair.arg1()
                        val value = nextPair.arg(2)
                        if (key.isnil()) break
                        headerMap[key.tojstring()] = value.tojstring()
                    }
                    for ((k, v) in headerMap) {
                        requestBuilder.addHeader(k, v)
                    }
                }

                // 添加 Cookie
                cookie?.let {
                    requestBuilder.addHeader("Cookie", it)
                }

                val request = requestBuilder.build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.call(valueOf(-1), valueOf(e.message ?: ""))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body.bytes()
                        callback.call(valueOf(response.code), valueOf(body))
                    }
                })

                return NIL
            }
        }

// 上传文件
        http["upload"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val url = args.checkjstring(1)
                val filePath = args.checkjstring(2)
                val paramName = args.optjstring(3, "file")
                val callback = args.checkfunction(args.narg())
                val headers = if (args.narg() > 4 && args.istable(4)) args.checktable(4) else null
                val cookie = if (args.narg() > 5) args.optjstring(5, null) else null

                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        callback.call(
                            valueOf(-1),
                            valueOf("文件不存在或不是文件")
                        )
                        return NIL
                    }

                    // 获取文件 MIME 类型
                    val mimeType = getMimeType(file.name)

                    // 创建 MultipartBody
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            paramName,
                            file.name,
                            file.asRequestBody(mimeType.toMediaTypeOrNull())
                        )
                        .build()

                    val requestBuilder = Request.Builder().url(url).post(requestBody)

                    // 添加请求头
                    headers?.let {
                        val headerMap = mutableMapOf<String, String>()
                        var key: LuaValue = NIL
                        while (true) {
                            val nextPair = headers.next(key)
                            key = nextPair.arg1()
                            val value = nextPair.arg(2)
                            if (key.isnil()) break
                            headerMap[key.tojstring()] = value.tojstring()
                        }
                        for ((k, v) in headerMap) {
                            requestBuilder.addHeader(k, v)
                        }
                    }

                    // 添加 Cookie
                    cookie?.let {
                        requestBuilder.addHeader("Cookie", it)
                    }

                    val request = requestBuilder.build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            callback.call(valueOf(-1), valueOf(e.message ?: ""))
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val body = response.body.bytes()
                            callback.call(valueOf(response.code), valueOf(body))
                        }
                    })
                } catch (e: Exception) {
                    callback.call(valueOf(-1), valueOf("上传异常: ${e.message}"))
                }

                return NIL
            }

            // 根据文件扩展名获取 MIME 类型
            private fun getMimeType(fileName: String): String {
                return when {
                    fileName.endsWith(".jpg", true) || fileName.endsWith(
                        ".jpeg",
                        true
                    ) -> "image/jpeg"

                    fileName.endsWith(".png", true) -> "image/png"
                    fileName.endsWith(".gif", true) -> "image/gif"
                    fileName.endsWith(".txt", true) -> "text/plain"
                    fileName.endsWith(".html", true) || fileName.endsWith(
                        ".htm",
                        true
                    ) -> "text/html"

                    fileName.endsWith(".pdf", true) -> "application/pdf"
                    fileName.endsWith(".doc", true) || fileName.endsWith(
                        ".docx",
                        true
                    ) -> "application/msword"

                    fileName.endsWith(".xls", true) || fileName.endsWith(
                        ".xlsx",
                        true
                    ) -> "application/vnd.ms-excel"

                    fileName.endsWith(".zip", true) -> "application/zip"
                    fileName.endsWith(".mp3", true) -> "audio/mpeg"
                    fileName.endsWith(".mp4", true) -> "video/mp4"
                    else -> "application/octet-stream" // 默认二进制流
                }
            }
        }

        env.set("http", http)
        return http
    }
}
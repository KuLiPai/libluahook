package io.github.kulipai.luahook.core.file

import android.annotation.SuppressLint
import android.content.Context
import io.github.kulipai.luahook.core.log.d
import org.json.JSONObject
import java.io.File

/**
 * /data/local/tmp/LuaHook/ 文件管理系统 (纯标准 Java/Kotlin I/O 实现)
 */
object WorkspaceFileManager {

    const val DIR: String = "/data/local/tmp/LuaHook"
    const val Project: String = "/Project"
    const val AppConf: String = "/AppConf"
    const val AppScript: String = "/AppScript"
    const val Plugin: String = "/Plugin"

    fun read(relativePath: String): String {
        return try {
            val fullPath = DIR + relativePath
            val file = File(fullPath)
            if (file.exists() && file.canRead()) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    fun write(file: String, content: String): Boolean {
        return try {
            val path = "$DIR$file"
            val targetFile = File(path)
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(content)
            targetFile.setReadable(true, false)
            targetFile.setWritable(true, false)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun writeMap(file: String, data: MutableMap<String, Any?>): Boolean {
        val jsonObject = JSONObject(data)
        val jsonString = jsonObject.toString()
        return write(file, jsonString)
    }

    fun readMap(file: String): MutableMap<String, Any?> {
        return try {
            val jsonString = read(file)
            if (jsonString.isEmpty()) {
                return mutableMapOf()
            }
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, Any?>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.get(key)
            }
            map
        } catch (e: Exception) {
            ("Error decoding arbitrary object from $file: ${e.message}").d()
            mutableMapOf()
        }
    }

    fun writeStringList(path: String, list: List<String>) {
        write(path, list.joinToString(","))
    }

    fun readStringList(path: String): MutableList<String> {
        val serialized = read(path)
        return if (serialized.isNotEmpty()) {
            serialized.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }

    fun rm(file: String): Boolean {
        return try {
            val path = "$DIR$file"
            val targetFile = File(path)
            if (targetFile.exists()) {
                targetFile.deleteRecursively()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun init(context: Context) {
        ensureDirectoryExists(DIR)
        ensureDirectoryExists(DIR + Project)
        ensureDirectoryExists(DIR + AppConf)
        ensureDirectoryExists(DIR + AppScript)
        ensureDirectoryExists(DIR + Plugin)
    }

    fun directoryExists(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.isDirectory
    }

    fun ensureDirectoryExists(path: String): Boolean {
        val file = File(path)
        if (file.exists() && file.isDirectory) {
            return true
        }
        val created = file.mkdirs()
        if (created) {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        }
        return created
    }

    fun ensureReadable(path: String) {
        val file = File(path)
        if (file.exists()) {
            file.setReadable(true, false)
            file.setWritable(true, false)
        }
    }

    data class FileParameters(
        val name: String? = null,
        val descript: String? = null,
        val packageName: String? = null,
        val author: String? = null,
        val otherParams: Map<String, String> = emptyMap()
    )

    fun parseParameters(fileContent: String): FileParameters? {
        try {
            val lines = fileContent.split("\n")
            val parsedParams = mutableMapOf<String, String>()

            for (line in lines) {
                val trimmedLine = line.trim()

                if (trimmedLine.startsWith("--")) {
                    val content = trimmedLine.substring(2).trim()
                    val colonIndex = content.indexOf(":")

                    if (colonIndex != -1) {
                        val key = content.substring(0, colonIndex).trim()
                        val value = content.substring(colonIndex + 1).trim()
                        parsedParams[key] = value
                    } else {
                        break
                    }
                } else if (trimmedLine.isEmpty()) {
                    continue
                } else {
                    break
                }
            }

            return FileParameters(
                name = parsedParams["name"],
                descript = parsedParams["descript"],
                packageName = parsedParams["package"],
                author = parsedParams["author"],
                otherParams = parsedParams.filterKeys {
                    it != "name" && it != "descript" && it != "package" && it != "author"
                }
            )
        } catch (_: Exception) {
        }
        return null
    }
}
package io.github.kulipai.luahook.hook.api

import io.github.kulipai.luahook.core.file.WorkspaceFileManager
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.lib.OneArgFunction
import org.luaj.lib.TwoArgFunction
import java.io.File

/**
 * file操作类，读写等各种文件封装
 */

object LuaFile {

    private val modName = "file"

    fun registerTo(env: LuaValue): LuaValue {
        val file = LuaTable()

        file["isFile"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).isFile)
        }

        file["isDir"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).isDirectory)
        }

        file["isExists"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).exists())
        }

        file["read"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue = try {
                val content = File(path.checkjstring()).readText()
                valueOf(content)
            } catch (_: Exception) {
                NIL
            }
        }

        file["readBytes"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue = try {
                val bytes = File(path.checkjstring()).readBytes()
                valueOf(String(bytes)) // 可改为 base64 或返回 userdata
            } catch (_: Exception) {
                NIL
            }
        }

        file["write"] = object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                val f = File(path.checkjstring())
                f.parentFile?.mkdirs()
                f.writeText(content.checkjstring())
                WorkspaceFileManager.ensureReadable(f.absolutePath)
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["writeBytes"] = object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                val f = File(path.checkjstring())
                f.parentFile?.mkdirs()
                f.writeBytes(content.checkstring().c)
                WorkspaceFileManager.ensureReadable(f.absolutePath)
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["append"] = object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                val f = File(path.checkjstring())
                f.parentFile?.mkdirs()
                f.appendText(content.checkjstring())
                WorkspaceFileManager.ensureReadable(f.absolutePath)
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["appendBytes"] = object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                val f = File(path.checkjstring())
                f.parentFile?.mkdirs()
                f.appendBytes(content.checkstring().c)
                WorkspaceFileManager.ensureReadable(f.absolutePath)
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["copy"] = object : TwoArgFunction() {
            override fun call(from: LuaValue, to: LuaValue): LuaValue = try {
                val fromFile = File(from.checkjstring())
                val toFile = File(to.checkjstring())
                toFile.parentFile?.mkdirs()
                fromFile.copyTo(toFile, overwrite = true)
                WorkspaceFileManager.ensureReadable(toFile.absolutePath)
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["move"] = object : TwoArgFunction() {
            override fun call(from: LuaValue, to: LuaValue): LuaValue = try {
                val fromFile = File(from.checkjstring())
                val toFile = File(to.checkjstring())
                toFile.parentFile?.mkdirs()
                if (!fromFile.renameTo(toFile)) {
                    fromFile.copyTo(toFile, overwrite = true)
                    fromFile.delete()
                }
                WorkspaceFileManager.ensureReadable(toFile.absolutePath)
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["rename"] = object : TwoArgFunction() {
            override fun call(path: LuaValue, newName: LuaValue): LuaValue {
                val f = File(path.checkjstring())
                val dest = File(f.parentFile, newName.checkjstring())
                val success = f.renameTo(dest)
                if (success) {
                    WorkspaceFileManager.ensureReadable(dest.absolutePath)
                }
                return valueOf(success)
            }
        }

        file["delete"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).delete())
        }

        file["getName"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).name)
        }

        file["getSize"] = object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).length().toInt())
        }

        env.set(modName, file)
        return file
    }
}

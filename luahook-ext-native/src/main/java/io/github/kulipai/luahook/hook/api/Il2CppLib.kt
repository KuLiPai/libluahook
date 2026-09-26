package io.github.kulipai.luahook.hook.api

import org.luaj.LuaTable
import org.luaj.LuaUserdata
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction

/** JNI 与 LuaJ 之间使用的轻量值容器。 */
class Il2CppNativeValue(
    val kind: Int,
    val bits: Long,
    val text: String?,
    val classHandle: Long
)

/**
 * Unity IL2CPP Lua API。
 *
 * Lua 侧入口：
 * - il2cpp.getClass(assembly, namespace, className)
 * - il2cpp.asTable(listAddress)
 */
class Il2CppLib(private val nativeLib: NativeLib) {
    private val loadError: Throwable?
    private val isLoaded: Boolean
    @Volatile
    private var initialized = false

    init {
        var error: Throwable? = null
        isLoaded = try {
            System.loadLibrary("luahook")
            true
        } catch (e: Throwable) {
            error = e
            false
        }
        loadError = error
        if (error != null) {
            try {
                android.util.Log.e("LuaHookNative", "Failed to load libluahook for il2cpp", error)
            } catch (_: Throwable) {
                error.printStackTrace()
            }
        }
    }

    private external fun nativeInit(): Boolean
    private external fun nativeGetClass(
        assemblyName: String,
        namespaceName: String,
        className: String
    ): Long

    private external fun nativeFindObjects(classHandle: Long): LongArray?
    private external fun nativeNewObject(classHandle: Long): Long
    private external fun nativeGetField(
        classHandle: Long,
        objectAddress: Long,
        fieldName: String
    ): Il2CppNativeValue?

    private external fun nativeSetField(
        classHandle: Long,
        objectAddress: Long,
        fieldName: String,
        valueKind: Int,
        valueBits: Long,
        valueText: String?
    ): Boolean

    private external fun nativeInvokeMethod(
        classHandle: Long,
        objectAddress: Long,
        methodName: String,
        argumentKinds: IntArray,
        argumentBits: LongArray,
        argumentTexts: Array<String?>
    ): Il2CppNativeValue?

    private external fun nativeAsList(objectAddress: Long): Array<Il2CppNativeValue?>?

    fun toLuaTable(): LuaTable {
        val table = LuaTable()

        table["init"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) {
                    return LuaValue.error(
                        "il2cpp unavailable: libluahook failed to load: " +
                            (loadError?.message ?: loadError?.javaClass?.name ?: "unknown error")
                    )
                }
                initialized = try {
                    nativeInit()
                } catch (error: Throwable) {
                    try {
                        android.util.Log.e("LuaHookNative", "Il2CppLib.nativeInit failed", error)
                    } catch (_: Throwable) {
                        error.printStackTrace()
                    }
                    return LuaValue.error("il2cpp nativeInit failed: " + error.message)
                }
                return LuaValue.valueOf(initialized)
            }
        }

        table["getClass"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) {
                    return LuaValue.error(
                        "il2cpp unavailable: libluahook failed to load: " +
                            (loadError?.message ?: loadError?.javaClass?.name ?: "unknown error")
                    )
                }
                if (!initialized) {
                    return LuaValue.error("call il2cpp.init() after Unity has loaded libil2cpp.so")
                }
                val handle = nativeGetClass(
                    args.checkjstring(1),
                    args.checkjstring(2),
                    args.checkjstring(3)
                )
                return if (handle == 0L) NIL else Il2CppClassValue(handle)
            }
        }

        table["asTable"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return NIL
                if (!initialized) return LuaValue.error("call il2cpp.init() before il2cpp.asTable()")
                val address = addressOf(args.arg(1))
                if (address == 0L) return NIL
                val values = nativeAsList(address) ?: return NIL
                val result = LuaTable()
                values.forEachIndexed { index, value ->
                    result[index + 1] = toLuaValue(value)
                }
                return result
            }
        }

        return table
    }

    private inner class Il2CppClassValue(private val classHandle: Long) : LuaUserdata(classHandle) {
        override fun get(key: LuaValue): LuaValue {
            val name = key.tojstring()
            return when (name) {
                "_addr" -> NativeLib.LuaPointer(classHandle, nativeLib)
                "_findObject" -> {
                    val address = nativeFindObjects(classHandle)?.firstOrNull() ?: 0L
                    if (address == 0L) NIL else Il2CppObjectValue(address, classHandle)
                }

                "_findObjects" -> {
                    val result = LuaTable()
                    val addresses = nativeFindObjects(classHandle) ?: LongArray(0)
                    addresses.forEachIndexed { index, address ->
                        result[index + 1] = Il2CppObjectValue(address, classHandle)
                    }
                    result
                }

                "_newObject" -> {
                    val address = nativeNewObject(classHandle)
                    if (address == 0L) NIL else Il2CppObjectValue(address, classHandle)
                }

                else -> {
                    val field = nativeGetField(classHandle, 0L, name)
                    if (field != null) {
                        toLuaValue(field)
                    } else {
                        methodFunction(classHandle, 0L, name, this)
                    }
                }
            }
        }

        override fun set(key: LuaValue, value: LuaValue) {
            val encoded = encodeValue(value)
            if (!nativeSetField(
                    classHandle,
                    0L,
                    key.tojstring(),
                    encoded.kind,
                    encoded.bits,
                    encoded.text
                )
            ) {
                LuaValue.error("IL2CPP static field not found or cannot be written: " + key.tojstring())
            }
        }

        override fun tojstring(): String {
            return "Il2CppClass(0x" + java.lang.Long.toHexString(classHandle).uppercase() + ")"
        }

        override fun tostring(): LuaValue = valueOf(tojstring())
    }

    private inner class Il2CppObjectValue(
        val address: Long,
        private val classHandle: Long
    ) : LuaUserdata(address) {
        override fun get(key: LuaValue): LuaValue {
            val name = key.tojstring()
            if (name == "_addr") return NativeLib.LuaPointer(address, nativeLib)

            val field = nativeGetField(classHandle, address, name)
            return if (field != null) {
                toLuaValue(field)
            } else {
                methodFunction(classHandle, address, name, this)
            }
        }

        override fun set(key: LuaValue, value: LuaValue) {
            val encoded = encodeValue(value)
            if (!nativeSetField(
                    classHandle,
                    address,
                    key.tojstring(),
                    encoded.kind,
                    encoded.bits,
                    encoded.text
                )
            ) {
                LuaValue.error("IL2CPP instance field not found or cannot be written: " + key.tojstring())
            }
        }

        override fun tojstring(): String {
            return "Il2CppObject(0x" + java.lang.Long.toHexString(address).uppercase() + ")"
        }

        override fun tostring(): LuaValue = valueOf(tojstring())
    }

    private fun methodFunction(
        classHandle: Long,
        objectAddress: Long,
        methodName: String,
        boundValue: LuaValue
    ): VarArgFunction = object : VarArgFunction() {
        override fun invoke(args: Varargs): LuaValue {
            val encoded = encodeArguments(args, boundValue)
            val result = nativeInvokeMethod(
                classHandle,
                objectAddress,
                methodName,
                encoded.kinds,
                encoded.bits,
                encoded.texts
            )
            return toLuaValue(result)
        }
    }

    private data class EncodedValue(val kind: Int, val bits: Long, val text: String?)
    private data class EncodedArguments(
        val kinds: IntArray,
        val bits: LongArray,
        val texts: Array<String?>
    )

    private fun encodeArguments(args: Varargs, boundValue: LuaValue): EncodedArguments {
        val start = if (args.narg() > 0 && args.arg(1) === boundValue) 2 else 1
        val count = (args.narg() - start + 1).coerceAtLeast(0)
        val kinds = IntArray(count)
        val bits = LongArray(count)
        val texts = arrayOfNulls<String>(count)

        for (index in 0 until count) {
            val value = encodeValue(args.arg(start + index))
            kinds[index] = value.kind
            bits[index] = value.bits
            texts[index] = value.text
        }
        return EncodedArguments(kinds, bits, texts)
    }

    private fun encodeValue(value: LuaValue): EncodedValue {
        return when {
            value.isnil() -> EncodedValue(KIND_NIL, 0L, null)
            value.isboolean() -> EncodedValue(KIND_BOOLEAN, if (value.toboolean()) 1L else 0L, null)
            value is Il2CppObjectValue -> EncodedValue(KIND_OBJECT, value.address, null)
            value is NativeLib.LuaPointer -> EncodedValue(KIND_OBJECT, value.address, null)
            value.isnumber() && value.isinttype() -> EncodedValue(KIND_INTEGER, value.tolong(), null)
            value.isnumber() -> EncodedValue(
                KIND_NUMBER,
                java.lang.Double.doubleToRawLongBits(value.todouble()),
                null
            )

            value.isstring() -> EncodedValue(KIND_STRING, 0L, value.tojstring())
            value.isuserdata() && value.touserdata() is Long -> EncodedValue(
                KIND_OBJECT,
                value.touserdata() as Long,
                null
            )

            else -> EncodedValue(KIND_NIL, 0L, null)
        }
    }

    private fun toLuaValue(value: Il2CppNativeValue?): LuaValue {
        if (value == null) return LuaValue.NIL
        return when (value.kind) {
            KIND_BOOLEAN -> LuaValue.valueOf(value.bits != 0L)
            KIND_INTEGER -> LuaValue.valueOf(value.bits)
            KIND_NUMBER -> LuaValue.valueOf(java.lang.Double.longBitsToDouble(value.bits))
            KIND_STRING -> value.text?.let(LuaValue::valueOf) ?: LuaValue.NIL
            KIND_OBJECT -> if (value.bits == 0L) {
                LuaValue.NIL
            } else if (value.classHandle == 0L) {
                NativeLib.LuaPointer(value.bits, nativeLib)
            } else {
                Il2CppObjectValue(value.bits, value.classHandle)
            }

            else -> LuaValue.NIL
        }
    }

    private fun addressOf(value: LuaValue): Long {
        return when (value) {
            is Il2CppObjectValue -> value.address
            is NativeLib.LuaPointer -> value.address
            else -> NativeLib.LuaPointer.unwrap(value)
        }
    }

    companion object {
        private const val KIND_NIL = 0
        private const val KIND_BOOLEAN = 1
        private const val KIND_INTEGER = 2
        private const val KIND_NUMBER = 3
        private const val KIND_STRING = 4
        private const val KIND_OBJECT = 5
    }
}

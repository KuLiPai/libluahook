package io.github.kulipai.luahook.hook.api

import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.LuaUserdata
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.ZeroArgFunction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

class NativeLib {
    private val isLoaded: Boolean = try {
        System.loadLibrary("luahook")
        true
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    }

    // --- JNI 接口 ---
    external fun registerGenericHook(addr: Long, retType: Int, argc: Int): Int
    external fun mallocString(str: String): Long
    external fun malloc(size: Int): Long
    external fun free(ptr: Long)
    external fun moduleBase(name: String): Long
    external fun getModuleBase(module_name: String, module_field: String): Long
    external fun resolveSymbol(module: String, name: String): Long
    external fun readPoint(ptr: Long, offsets: LongArray): Long
    external fun safeRead(ptr: Long, size: Int): ByteArray?
    external fun safeWrite(ptr: Long, data: ByteArray): Boolean
    external fun invoke(
        addr: Long,
        gprs: LongArray,
        fprs: DoubleArray,
        stack: LongArray,
        retType: Int
    ): Long

    data class HookConfig(
        val onEnter: LuaValue?,
        val onLeave: LuaValue?,
        val retType: Int,
        val argc: Int
    )

    companion object {
        const val RET_INT = 0
        const val RET_FLOAT = 1
        const val RET_DOUBLE = 3
        const val RET_VOID = 4
        val hookCallbacks = ConcurrentHashMap<Int, HookConfig>()
    }

    // --- 回调入口 ---
    fun onNativeEnter(index: Int, regs: LongArray): LongArray? {
        val cfg = hookCallbacks[index] ?: return null
        val onEnter = cfg.onEnter ?: return null

        val ctx = LuaTable()

        val gprCount = if (isProcess64Bit()) 8 else 4
        val fprCount = 8
        val maxStack = regs.size - gprCount - fprCount
        val stackCount = cfg.argc.coerceAtLeast(0).coerceAtMost(maxStack.coerceAtLeast(0))

        val rawTable = LuaTable()
        val fprTable = LuaTable()
        val stackTable = LuaTable()

        for (i in 0 until gprCount) {
            rawTable[i] = LuaPointer(regs[i], this)
            ctx[i] = rawTable[i]
        }
        for (i in 0 until fprCount) {
            fprTable[i] = LuaPointer(regs[gprCount + i], this)
        }
        for (i in 0 until stackCount) {
            stackTable[i] = LuaPointer(regs[gprCount + fprCount + i], this)
        }

        ctx["raw"] = rawTable
        ctx["fpr"] = fprTable
        ctx["stack"] = stackTable

        try {
            onEnter.call(ctx)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val newRegs = LongArray(regs.size)
        var changed = false
        for (i in 0 until gprCount) {
            val v = LuaPointer.unwrap(ctx[i])
            newRegs[i] = v
            if (v != regs[i]) changed = true
        }
        for (i in 0 until fprCount) {
            val v = LuaPointer.unwrap(fprTable[i])
            val idx = gprCount + i
            newRegs[idx] = v
            if (v != regs[idx]) changed = true
        }
        for (i in 0 until stackCount) {
            val v = LuaPointer.unwrap(stackTable[i])
            val idx = gprCount + fprCount + i
            newRegs[idx] = v
            if (v != regs[idx]) changed = true
        }

        return if (changed) newRegs else null
    }

    fun onNativeLeave(index: Int, retval: Long): Long {
        val cfg = hookCallbacks[index] ?: return retval
        val onLeave = cfg.onLeave ?: return retval

        val arg = when (cfg.retType) {
            RET_FLOAT -> LuaValue.valueOf(java.lang.Float.intBitsToFloat(retval.toInt()).toDouble())
            RET_DOUBLE -> LuaValue.valueOf(java.lang.Double.longBitsToDouble(retval))
            RET_VOID -> LuaValue.NIL
            else -> LuaPointer(retval, this)
        }

        try {
            val result = onLeave.call(arg)
            if (!result.isnil()) {
                return when (cfg.retType) {
                    RET_FLOAT -> java.lang.Float.floatToIntBits(result.todouble().toFloat())
                        .toLong()

                    RET_DOUBLE -> java.lang.Double.doubleToLongBits(result.todouble())
                    RET_VOID -> retval
                    else -> LuaPointer.unwrap(result)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return retval
    }

    // --- LuaPointer ---
    class LuaPointer(val address: Long, private val lib: NativeLib) : LuaUserdata(address) {

        // 重写 get 实现 "." 调用

        override fun get(key: LuaValue): LuaValue {
            val name = key.tojstring()

            // 每次访问 ptr.xxx 时，返回一个闭包，直接使用当前的 address
            // 这样 Lua 调用时就不需要传 self (即不需要冒号)
            return when (name) {
                // ptr.read_s32([offset])
                "read_s32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0) // arg1 就是 offset
                        val data = lib.safeRead(address + offset, 4) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.int.toDouble())
                    }
                }

                // ptr.read_u32([offset])
                "read_u32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 4) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf((bb.int.toLong() and 0xFFFFFFFFL).toDouble())
                    }
                }

                // ptr.read_s64([offset]) -> Pointer (avoid Lua number precision loss)
                "read_s64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 8) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return LuaPointer(bb.long, lib)
                    }
                }

                // ptr.read_u64([offset]) -> Pointer (avoid Lua number precision loss)
                "read_u64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 8) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return LuaPointer(bb.long, lib)
                    }
                }

                // ptr.read_ptr([offset]) -> Pointer
                "read_ptr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val size = lib.pointerSize()
                        val data = lib.safeRead(address + offset, size) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val v = if (size == 8) bb.long else (bb.int.toLong() and 0xFFFFFFFFL)
                        return LuaPointer(v, lib)
                    }
                }

                // ptr.read_cstr([max_len]) - 读取 C 风格字符串 (char*)
                "read_cstr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val maxLen = args.optint(1, 512)
                        val data = lib.safeRead(address, maxLen) ?: return NIL
                        var len = 0
                        while (len < data.size && data[len] != 0.toByte()) len++
                        return valueOf(String(data, 0, len))
                    }
                }

                "read_cstr_utf8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val maxLen = args.optint(1, 512)
                        val data = lib.safeRead(address, maxLen) ?: return NIL
                        var len = 0
                        while (len < data.size && data[len] != 0.toByte()) len++
                        return valueOf(String(data, 0, len, Charsets.UTF_8))
                    }
                }

                // ptr.read_u8([offset])
                "read_u8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 1) ?: return ZERO
                        return valueOf((data[0].toInt() and 0xFF).toDouble())
                    }
                }

                // ptr.read_s8([offset])
                "read_s8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 1) ?: return ZERO
                        return valueOf(data[0].toInt().toDouble())
                    }
                }

                // ptr.read_s16([offset])
                "read_s16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 2) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.short.toDouble())
                    }
                }

                // ptr.read_u16([offset])
                "read_u16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 2) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf((bb.short.toInt() and 0xFFFF).toDouble())
                    }
                }

                // ptr.read_f32([offset])
                "read_f32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 4) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.float.toDouble())
                    }
                }

                // ptr.read_f64([offset])
                "read_f64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.safeRead(address + offset, 8) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.double)
                    }
                }

                // ptr.read_byte_array(size, [offset]) - 读取原始字节数组
                "read_byte_array" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val size = args.checkint(1)
                        val offset = args.optint(2, 0)
                        val data = lib.safeRead(address + offset, size) ?: return NIL
                        val table = LuaTable()
                        for (i in data.indices) {
                            table[i + 1] = valueOf((data[i].toInt() and 0xFF).toDouble())
                        }
                        return table
                    }
                }


                // ptr.write_u8(val, [offset])
                "write_u8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        return valueOf(
                            lib.safeWrite(
                                address + offset,
                                byteArrayOf(v.toByte())
                            )
                        )
                    }
                }
                // ptr.write_s8(val, [offset])
                "write_s8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        return valueOf(
                            lib.safeWrite(
                                address + offset,
                                byteArrayOf(v.toByte())
                            )
                        )
                    }
                }

                // ptr.write_s16(val, [offset])
                "write_s16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                            .putShort(v.toShort())
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }
                // ptr.write_u16(val, [offset])
                "write_u16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1) and 0xFFFF
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                            .putShort(v.toShort())
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }

                // ptr.write_s32(val, [offset])
                "write_s32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }
                // ptr.write_u32(val, [offset])
                "write_u32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checklong(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt((v and 0xFFFFFFFFL).toInt())
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }

                // ptr.write_s64(val, [offset])
                "write_s64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = unwrap(args.arg(1))
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v)
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }
                // ptr.write_u64(val, [offset])
                "write_u64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = unwrap(args.arg(1))
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v)
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }
                // ptr.write_ptr(val, [offset])
                "write_ptr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = unwrap(args.arg(1))
                        val offset = args.optint(2, 0)
                        val size = lib.pointerSize()
                        val bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                        if (size == 8) bb.putLong(v) else bb.putInt((v and 0xFFFFFFFFL).toInt())
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }

                // ptr.write_f32(val, [offset])
                "write_f32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkdouble(1).toFloat()
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v)
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }

                // ptr.write_f64(val, [offset])
                "write_f64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkdouble(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(v)
                        return valueOf(lib.safeWrite(address + offset, bb.array()))
                    }
                }

                // ptr.write_byte_array(table, [offset]) - 写入字节数组
                "write_byte_array" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val table = args.checktable(1)
                        val offset = args.optint(2, 0)
                        val len = table.length()
                        val bytes = ByteArray(len)
                        for (i in 1..len) {
                            bytes[i - 1] = table[i].checkint().toByte()
                        }
                        return valueOf(lib.safeWrite(address + offset, bytes))
                    }
                }

                // ptr.write_cstr(str)
                "write_cstr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val s = args.checkjstring(1)
                        val bytes = s.toByteArray()
                        val withNull = bytes.copyOf(bytes.size + 1)
                        return valueOf(lib.safeWrite(address, withNull))
                    }
                }

                "write_cstr_utf8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val s = args.checkjstring(1)
                        val bytes = s.toByteArray(Charsets.UTF_8)
                        val withNull = bytes.copyOf(bytes.size + 1)
                        return valueOf(lib.safeWrite(address, withNull))
                    }
                }

                // ptr.is_null() - 检查指针是否为空
                "is_null" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(address == 0L)
                    }
                }

                // ptr.not_null() - 检查指针是否非空
                "not_null" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(address != 0L)
                    }
                }

                // ptr.to_hex() - 支持无参调用
                "to_hex" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(java.lang.Long.toHexString(address).uppercase())
                    }
                }

                // ptr.to_int() - 获取指针地址本身作为整数
                "to_int" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(address.toDouble())
                    }
                }

                // ptr.to_long() - 获取指针地址本身作为 long (返回 LuaPointer 以保持精度)
                "to_long" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return LuaPointer(address, lib)
                    }
                }

                // ptr.hexdump([size])
                "hexdump" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val size = args.optint(1, 256)
                        val data = lib.safeRead(address, size)
                            ?: return valueOf(
                                "Cannot read memory at 0x${
                                    java.lang.Long.toHexString(
                                        address
                                    )
                                }"
                            )

                        val sb = StringBuilder()
                        val hexPart = StringBuilder()
                        // 暂存当前行的字节，用于最后转 String
                        val lineBytes = java.io.ByteArrayOutputStream()

                        for (i in data.indices) {
                            val b = data[i].toInt() and 0xFF

                            // 拼接 Hex
                            hexPart.append(String.format("%02X ", b))

                            // 收集字节用于显示文本
                            lineBytes.write(data[i].toInt())

                            // 每 16 字节换一行，或者读到了最后
                            if ((i + 1) % 16 == 0 || i == data.size - 1) {
                                // 补齐 Hex 部分的空格 (如果最后一行不满16字节)
                                while (hexPart.length < 48) {
                                    hexPart.append("   ")
                                }

                                // 尝试用 UTF-8 解码这一行
                                val rawString = String(lineBytes.toByteArray(), Charsets.UTF_8)
                                val safeString = StringBuilder()

                                // 过滤控制字符，保留中文
                                for (char in rawString) {
                                    // 只要不是控制字符(如换行、退格)，或者是空格，都显示
                                    if (!Character.isISOControl(char) || char == ' ') {
                                        safeString.append(char)
                                    } else {
                                        safeString.append('.')
                                    }
                                }

                                // 输出
                                val offset = (i / 16) * 16
                                sb.append(
                                    String.format(
                                        "%04X  %s |%s|\n",
                                        offset,
                                        hexPart.toString(),
                                        safeString.toString()
                                    )
                                )

                                // 重置缓冲区
                                hexPart.setLength(0)
                                lineBytes.reset()
                            }
                        }

                        return valueOf("\n" + sb.toString().trim())
                    }
                }

                // ptr.add(offset)
                "add" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.checklong(1)
                        return LuaPointer(address + offset, lib)
                    }
                }

                // ptr.sub(offset)
                "sub" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.checklong(1)
                        return LuaPointer(address - offset, lib)
                    }
                }

                // ptr.set(value) - 直接设置指针值 (用于修改 args 中的参数)
                "set" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        // 返回新的 LuaPointer，调用者需要赋值回 args
                        val newVal = unwrap(args.arg(1))
                        return LuaPointer(newVal, lib)
                    }
                }

                // ptr.deref() - 解引用指针 (读取指针指向的地址)
                "deref" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val size = lib.pointerSize()
                        val data = lib.safeRead(address + offset, size) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val v = if (size == 8) bb.long else (bb.int.toLong() and 0xFFFFFFFFL)
                        return LuaPointer(v, lib)
                    }
                }

                // ptr.and(mask) - 位与操作
                "and" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val mask = unwrap(args.arg(1))
                        return LuaPointer(address and mask, lib)
                    }
                }

                // ptr.or(mask) - 位或操作
                "or" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val mask = unwrap(args.arg(1))
                        return LuaPointer(address or mask, lib)
                    }
                }

                // ptr.xor(mask) - 位异或操作
                "xor" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val mask = unwrap(args.arg(1))
                        return LuaPointer(address xor mask, lib)
                    }
                }

                // ptr.shl(bits) - 左移
                "shl" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val bits = args.checkint(1)
                        return LuaPointer(address shl bits, lib)
                    }
                }

                // ptr.shr(bits) - 右移
                "shr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val bits = args.checkint(1)
                        return LuaPointer(address shr bits, lib)
                    }
                }

                // ptr.equals(other) - 比较两个指针
                "equals" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val other = unwrap(args.arg(1))
                        return valueOf(address == other)
                    }
                }

                else -> super.get(key)
            }
        }

        override fun tostring(): LuaValue {
            return valueOf("Ptr(0x${java.lang.Long.toHexString(address).uppercase()})")
        }

        companion object {
            fun unwrap(v: LuaValue): Long {
                return when {
                    v is LuaPointer -> v.address
                    v.isuserdata() -> {
                        val obj = v.touserdata()
                        obj as? Long ?: 0L
                    }

                    v.isnumber() -> v.tolong()
                    v.isstring() -> try {
                        java.lang.Long.decode(v.tojstring())
                    } catch (_: Exception) {
                        0L
                    }

                    else -> 0L
                }
            }
        }
    }

    // --- Lua API 注册 ---
    fun toLuaTable(): LuaTable {
        val t = LuaTable()

        val memory = LuaTable()

        fun memProxy(name: String): VarArgFunction = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                if (addr == 0L) return if (name.startsWith("write")) FALSE else NIL
                if (name == "read_byte_array") {
                    val size = args.optint(2, 0)
                    if (size <= 0) return NIL
                }
                val fn = LuaPointer(addr, this@NativeLib).get(valueOf(name))
                val n = args.narg()
                val rest = Array(maxOf(0, n - 1)) { i -> args.arg(i + 2) }
                val vargs = LuaValue.varargsOf(rest)
                return fn.invoke(vargs).arg1()
            }
        }

        memory["read_byte_array"] = memProxy("read_byte_array")
        memory["read_u8"] = memProxy("read_u8")
        memory["read_s8"] = memProxy("read_s8")
        memory["read_u16"] = memProxy("read_u16")
        memory["read_s16"] = memProxy("read_s16")
        memory["read_u32"] = memProxy("read_u32")
        memory["read_s32"] = memProxy("read_s32")
        memory["read_u64"] = memProxy("read_u64")
        memory["read_s64"] = memProxy("read_s64")
        memory["read_f32"] = memProxy("read_f32")
        memory["read_f64"] = memProxy("read_f64")
        memory["read_ptr"] = memProxy("read_ptr")
        memory["read_cstr"] = memProxy("read_cstr")
        memory["read_cstr_utf8"] = memProxy("read_cstr_utf8")

        memory["read_lp_utf8"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val maxLen = args.optint(2, 512)
                val lenData = safeRead(addr, 1) ?: return NIL
                val len = lenData[0].toInt() and 0xFF
                if (len == 0) return valueOf("")
                val readLen = if (len > maxLen) maxLen else len
                val data = safeRead(addr + 1, readLen) ?: return NIL
                return valueOf(String(data, 0, data.size))
            }
        }

        memory["read_auto_utf8"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val lenData = safeRead(addr, 1) ?: return memory["read_cstr"].invoke(args).arg1()
                val len = lenData[0].toInt() and 0xFF
                if (len in 1..0x7F) {
                    val data =
                        safeRead(addr + 1, len) ?: return memory["read_cstr"].invoke(args).arg1()
                    val s = String(data, 0, data.size)
                    if (s.length == len) return valueOf(s)
                }
                return memory["read_cstr"].invoke(args).arg1()
            }
        }

        memory["write_byte_array"] = memProxy("write_byte_array")
        memory["write_u8"] = memProxy("write_u8")
        memory["write_s8"] = memProxy("write_s8")
        memory["write_u16"] = memProxy("write_u16")
        memory["write_s16"] = memProxy("write_s16")
        memory["write_u32"] = memProxy("write_u32")
        memory["write_s32"] = memProxy("write_s32")
        memory["write_u64"] = memProxy("write_u64")
        memory["write_s64"] = memProxy("write_s64")
        memory["write_ptr"] = memProxy("write_ptr")
        memory["write_f32"] = memProxy("write_f32")
        memory["write_f64"] = memProxy("write_f64")
        memory["write_cstr"] = memProxy("write_cstr")
        memory["write_cstr_utf8"] = memProxy("write_cstr_utf8")

        memory["alloc_utf8_string"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val s = args.checkjstring(1)
                return LuaPointer(mallocString(s), this@NativeLib)
            }
        }
        memory["alloc"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val size = args.checkint(1)
                if (size <= 0) return LuaPointer(0, this@NativeLib)
                return LuaPointer(malloc(size), this@NativeLib)
            }
        }
        memory["free"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (isLoaded) free(LuaPointer.unwrap(args.arg(1)))
                return NIL
            }
        }

        memory["write_lp_utf8"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val s = args.checkjstring(2)
                val bytes = s.toByteArray()
                val len = bytes.size
                val maxLen = args.optint(3, len)
                val writeLen = if (len > maxLen) maxLen else len
                val buf = ByteArray(writeLen + 1)
                buf[0] = (writeLen and 0xFF).toByte()
                System.arraycopy(bytes, 0, buf, 1, writeLen)
                return valueOf(safeWrite(addr, buf))
            }
        }

        t["memory"] = memory

        t["ptr"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                return LuaPointer(addr, this@NativeLib)
            }
        }

        t["module_base"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val base = moduleBase(args.checkjstring(1))
                return LuaPointer(base, this@NativeLib)
            }
        }

        t["getModuleBase"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return ZERO
                val name = args.checkjstring(1)
                val field = args.checkjstring(2)
                return valueOf(getModuleBase(name, field).toDouble())
            }
        }

        t["resolve_symbol"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return ZERO
                val module = args.checkjstring(1)
                val name = args.checkjstring(2)
                return valueOf(resolveSymbol(module, name).toDouble())
            }
        }

        t["readPoint"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return ZERO
                val ptr = LuaPointer.unwrap(args.arg(1))
                val offsetsTable = args.checktable(2)
                val count = offsetsTable.length()
                val offsets = LongArray(count)
                for (i in 0 until count) {
                    offsets[i] = offsetsTable.get(i + 1).tolong()
                }
                return valueOf(readPoint(ptr, offsets).toDouble())
            }
        }

        t["get_module_base"] = t["module_base"]

        t["new_function"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val retTypeStr = args.optjstring(2, "void")
                val argTypes = args.arg(3)
                
                val retType = when(retTypeStr.lowercase()) {
                    "int", "s32", "u32", "s64", "u64", "long", "ptr", "pointer" -> RET_INT
                    "float" -> RET_FLOAT
                    "double" -> RET_DOUBLE
                    "void" -> RET_VOID
                    else -> RET_INT
                }
                
                val argTypeList = ArrayList<String>()
                if (argTypes.istable()) {
                    val n = argTypes.length()
                    for(i in 0 until n) {
                        argTypeList.add(argTypes.get(i+1).tojstring())
                    }
                }
                
                return object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val gprs = LongArray(8)
                        val fprs = DoubleArray(8)
                        val stack = ArrayList<Long>()
                        var gprIdx = 0
                        var fprIdx = 0
                        
                        for(i in 0 until argTypeList.size) {
                            val type = argTypeList[i]
                            val luaVal = args.arg(i+1)
                            
                            when(type) {
                                "int", "long", "ptr", "pointer" -> {
                                    val v = LuaPointer.unwrap(luaVal)
                                    if(gprIdx < 8) {
                                        gprs[gprIdx++] = v
                                    } else {
                                        stack.add(v)
                                    }
                                }
                                "float", "double" -> {
                                    val v = luaVal.todouble()
                                    if(fprIdx < 8) {
                                        fprs[fprIdx++] = v
                                    } else {
                                        // Stack slots are 64-bit
                                        // If float, packed in low 32 bits?
                                        // If double, packed in 64 bits.
                                        if (type == "float") {
                                            val bits = java.lang.Float.floatToRawIntBits(v.toFloat()).toLong()
                                            stack.add(bits and 0xFFFFFFFFL)
                                        } else {
                                            val bits = java.lang.Double.doubleToRawLongBits(v)
                                            stack.add(bits)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Handle variable args if more args provided than types? 
                        // User might just provide "int" for everything in varargs context.
                        // For now strict mapping.
                        
                        val stackArray = LongArray(stack.size) { stack[it] }
                        
                        val retVal = invoke(addr, gprs, fprs, stackArray, retType)
                        
                        return when(retType) {
                            RET_VOID -> NIL
                            RET_INT -> LuaPointer(retVal, this@NativeLib)
                            RET_FLOAT -> valueOf(java.lang.Float.intBitsToFloat(retVal.toInt()).toDouble())
                            RET_DOUBLE -> valueOf(java.lang.Double.longBitsToDouble(retVal))
                            else -> LuaPointer(retVal, this@NativeLib)
                        }
                    }
                }
            }
        }

        t["hook"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return FALSE
                val addr = LuaPointer.unwrap(args.arg(1))
                if (addr == 0L) return FALSE
                val cbs = args.arg(2).checktable()

                val ret = cbs["ret"]
                val retType = when {
                    ret.isnumber() -> ret.toint()
                    ret.isstring() -> when (ret.tojstring().lowercase()) {
                        "int", "ptr", "pointer" -> RET_INT
                        "float" -> RET_FLOAT
                        "double" -> RET_DOUBLE
                        "void" -> RET_VOID
                        else -> RET_INT
                    }

                    else -> RET_INT
                }

                val argc = if (cbs["argc"].isnumber()) cbs["argc"].toint() else 0

                val id = registerGenericHook(addr, retType, argc)
                if (id >= 0) {
                    hookCallbacks[id] = HookConfig(
                        cbs["onEnter"].optfunction(null),
                        cbs["onLeave"].optfunction(null),
                        retType,
                        argc
                    )
                    return TRUE
                }
                return FALSE
            }
        }
        return t
    }

    private fun isProcess64Bit(): Boolean {
        return try {
            android.os.Process.is64Bit()
        } catch (_: Throwable) {
            // Fallback: best-effort when is64Bit() is unavailable
            android.os.Build.SUPPORTED_ABIS.any { it.contains("64") }
        }
    }

    private fun pointerSize(): Int = if (isProcess64Bit()) 8 else 4
}

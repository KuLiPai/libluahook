package io.github.kulipai.luahook.core.log

import android.util.Log

/**
 * 日志
 */

private val TAG = "LuaXposed"

fun Throwable.log(text: String = "Throwable"): Throwable {
    Log.e(TAG, text, this)
    return this
}

fun Any.d(text: String = "Debug") {
    Log.d(TAG, this.toString())
}

fun Any.e(text: String = "Error") {
    Log.e(TAG, this.toString())
}

fun printStackTrace() {
    val stackTrace = Throwable().stackTrace
    val stackTraceStr = stackTrace.joinToString("\n") { element ->
        "at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})"
    }
    ("StackTrace\n$stackTraceStr").d()
}

package dev.eastar.operaxwebextansion

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.experimental.and

object Log {
    var LOG = false
    var FILE_LOG: File? = null
    private const val LF = "\n"
    private const val MAX_LOG_LINE_BYTE_SIZE = 3600
    private const val PREFIX = "``"
    private const val PREFIX_MULTILINE: String = "$PREFIX▼"
    private val LOG_CLASS: String = javaClass.name
    private val ANDROID_CLASS = "^android\\..+|^com\\.android\\..+|^java\\..+".toRegex()

    fun e(vararg args: Any?) {
        if (!LOG) return
        val info: StackTraceElement? = getStack()
        val tag: String = getTag(info)
        val locator: String = getLocator(info)
        val msg: String = makeMessage(*args)
        println(android.util.Log.ERROR, tag, locator, msg)
    }

    fun flog(vararg args: Any?) {
        FILE_LOG ?: return
        runCatching {
            val info = getStack()
            val log: String = makeMessage(*args)
            val st = StringTokenizer(log, LF, false)

            val tag = "%-40s%-40d %-100s ``".format(Date().toString(), SystemClock.elapsedRealtime(), info.toString())
            if (st.hasMoreTokens()) {
                val token = st.nextToken()
                FILE_LOG!!.appendText(tag + token + LF)
            }

            val space = "%-40s%-40s %-100s ``".format("", "", "")
            while (st.hasMoreTokens()) {
                val token = st.nextToken()
                FILE_LOG!!.appendText(space + token + LF)
            }
        }
    }

    fun i(vararg args: Any?) {
        val info: StackTraceElement? = getStack()
        val tag: String = getTag(info)
        val locator: String = getLocator(info)
        val msg: String = makeMessage(*args)
        println(android.util.Log.INFO, tag, locator, msg)
    }

    private fun println(priority: Int, tag: String, locator: String, msg: String?) {
        val sa = ArrayList<String>(100)
        val st = StringTokenizer(msg, LF, false)
        while (st.hasMoreTokens()) {
            val byte_text = st.nextToken().toByteArray()
            var offset = 0
            val N = byte_text.size
            while (offset < N) {
                val count: Int = safeCut(byte_text, offset, MAX_LOG_LINE_BYTE_SIZE)
                sa.add(PREFIX + String(byte_text, offset, count))
                offset += count
            }
        }
        val DOTS = "...................................................................................."
        val sb = StringBuilder(DOTS)
        val last_tag = tag.substring(Math.max(tag.length + locator.length - DOTS.length, 0))
        sb.replace(0, last_tag.length, last_tag)
        sb.replace(sb.length - locator.length, sb.length, locator)
        val adj_tag = sb.toString()
        val N = sa.size
        if (N <= 0) android.util.Log.println(priority, adj_tag, PREFIX)
        if (N == 1) android.util.Log.println(priority, adj_tag, sa[0])
        var sum = android.util.Log.println(priority, adj_tag, PREFIX_MULTILINE)
        for (s in sa) sum += android.util.Log.println(priority, adj_tag, s)
    }

    private fun safeCut(byteArray: ByteArray, startOffset: Int, bufferSize: Int): Int {
        val byteLength = byteArray.size
        if (byteLength <= startOffset) throw ArrayIndexOutOfBoundsException("!!text_length <= start_byte_index")
        if (bufferSize <= 0) throw UnsupportedOperationException("!!must length > 0 ")
        if (byteArray[startOffset] and 0xc0.toByte() == 0x80.toByte()) throw java.lang.UnsupportedOperationException("!!start_byte_index must splited index")

        var position = startOffset + bufferSize
        if (byteLength <= position) return byteLength - startOffset

        while (startOffset <= position) {
            if (byteArray[position] and 0xc0.toByte() != 0x80.toByte()) break
            position--
        }
        if (position <= startOffset) throw UnsupportedOperationException("!!byte_length too small")
        return position - startOffset
    }

    private fun getStack(): StackTraceElement? = Exception().stackTrace.filterNot {
        it.className.startsWith(LOG_CLASS)
    }.filterNot {
        it.className.matches(ANDROID_CLASS)
    }.last {
        it.lineNumber > 0
    }

    private fun getLocator(info: StackTraceElement?): String = info?.run { "(%s:%d)".format(fileName, lineNumber) } ?: ""

    private fun getTag(info: StackTraceElement?): String = info?.run {
        var tag = methodName
        runCatching {
            val name = className
            tag = name.substring(name.lastIndexOf('.') + 1) + "." + methodName
        }
        return tag.replace("\\$".toRegex(), "_")
    } ?: ""

    private fun makeMessage(vararg args: Any?): String = args.map {
        when (it) {
            null -> "null"
            is JSONObject -> it.toString(2)
            is JSONArray -> it.toString(2)
            is CharSequence -> dump(it.toString())
            else -> it.toString()
        }
    }.joinToString()

    private fun dump(text: String): String? = StringBuilder().runCatching {
        val s = text[0]
        val e = text[text.length - 1]
        if (s == '[' && e == ']') {
            val ja = JSONArray(text).toString(2)
            append("\nJA\n")
            append(ja)
        } else if (s == '{' && e == '}') {
            val jo = JSONObject(text).toString(2)
            append("\nJO\n")
            append(jo)
        } else {
            append(text)
        }
        toString()
    }.getOrDefault(text)

}

package dev.eastar.operaxwebextansion

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.experimental.and

object OperaXLog {
    var LOG = false
    var FILE_LOG: File? = null
    private const val LF = "\n"
    private const val MAX_LOG_LINE_BYTE_SIZE = 3600
    private const val PREFIX = "``"

    internal fun e(vararg args: Any?) {
        if (!LOG) return
        val msg: String = makeMessage(*args)
        println(android.util.Log.ERROR, msg)
    }

    internal fun flog(vararg args: Any?) {
        FILE_LOG ?: return
        runCatching {
            val log: String = makeMessage(*args)
            val st = StringTokenizer(log, LF, false)

            val tag = "%-40s%-40d %-100s ``".format(Date().toString(), SystemClock.elapsedRealtime(), "")
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

    internal fun i(vararg args: Any?) {
        val msg: String = makeMessage(*args)
        println(android.util.Log.INFO, msg)
    }

    private fun println(priority: Int, msg: String?) {
        val sa = ArrayList<String>(100)
        val st = StringTokenizer(msg, LF, false)
        while (st.hasMoreTokens()) {
            val byteText = st.nextToken().toByteArray()
            var offset = 0
            while (offset < byteText.size) {
                val count: Int = safeCut(byteText, offset)
                sa.add(PREFIX + String(byteText, offset, count))
                offset += count
            }
        }
        when (sa.size) {
            0 -> android.util.Log.println(priority, "OPERA_X", PREFIX)
            else -> sa.forEach { android.util.Log.println(priority, "OPERA_X", it) }
        }
    }

    private fun safeCut(byteArray: ByteArray, startOffset: Int): Int {
        val byteLength = byteArray.size
        if (byteLength <= startOffset) throw ArrayIndexOutOfBoundsException("!!text_length <= start_byte_index")
        if (byteArray[startOffset] and 0xc0.toByte() == 0x80.toByte()) throw java.lang.UnsupportedOperationException("!!start_byte_index must splited index")

        var position = startOffset + MAX_LOG_LINE_BYTE_SIZE
        if (byteLength <= position) return byteLength - startOffset

        while (startOffset <= position) {
            if (byteArray[position] and 0xc0.toByte() != 0x80.toByte()) break
            position--
        }
        if (position <= startOffset) throw UnsupportedOperationException("!!byte_length too small")
        return position - startOffset
    }

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
            append(ja)
        } else if (s == '{' && e == '}') {
            val jo = JSONObject(text).toString(2)
            append(jo)
        } else {
            append(text)
        }
        toString()
    }.getOrDefault(text)

}

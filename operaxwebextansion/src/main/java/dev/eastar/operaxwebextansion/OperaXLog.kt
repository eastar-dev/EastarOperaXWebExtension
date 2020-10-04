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
    var _OUT_1 = false
    var _OUT_2 = false
    var _IN_1 = false
    var _IN_2 = false

    private const val LF = "\n"
    private const val MAX_LOG_LINE_BYTE_SIZE = 3600
    private const val PREFIX = "``"

    internal fun e(vararg args: Any?) {
        if (!LOG) return
        val msg: String = makeMessage(*args)
        println(android.util.Log.ERROR, msg)
    }

    internal fun i(vararg args: Any?) {
        val msg: String = makeMessage(*args)
        println(android.util.Log.INFO, msg)
    }

    internal fun w(vararg args: Any?) {
        val msg: String = makeMessage(*args)
        println(android.util.Log.WARN, msg)
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

    fun inLog(json: String) {
        if (!LOG) return
        flog(">>OPERA", json)
        val req = OperaXRequest.newInstance(json)
        val log = req.clazz.getAnnotation(NoLog::class.java) == null && req.method.getAnnotation(NoLog::class.java) == null
        if (log && _IN_1) e(">>OPERA", "${req.clazz}.${req.methodName}:${req.params.contentToString()}")
        if (log && _IN_2) e(">>OPERA", json)
    }

    fun outLog(script: String) {
        if (!LOG) return

        runCatching {
            "\\((.*)\\)\\((.*)\\);".toRegex().matchEntire(script)?.run {
                flog("<<OPERA", groupValues[1], groupValues[2])

                val jo = JSONObject(groupValues[2])
                val success = jo.getInt("resultCode") == 0
                val reqJson = jo.getString("request")
                val req = OperaXRequest.newInstance(reqJson)
                val log = req.clazz.getAnnotation(NoLog::class.java) == null && req.method.getAnnotation(NoLog::class.java) == null
                jo.remove("request")
                if (success) {
                    if (log && _OUT_1) i("<<OPERA", success, "${req.clazz}.${req.methodName}:${jo.getString("result")}")
                } else {
                    if (log && _OUT_1) w("<<OPERA", success, "${req.clazz}.${req.methodName}:${jo.getString("result")}")
                }

                if (success) {
                    if (log && _OUT_2) i("<<OPERA", groupValues[1], groupValues[2])
                } else {
                    w("<<OPERA", groupValues[1], groupValues[2])
                }
            }
        }
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NoLog

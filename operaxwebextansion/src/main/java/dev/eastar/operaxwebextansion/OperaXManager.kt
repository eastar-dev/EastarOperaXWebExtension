/*
 * Copyright 2019 copyright eastar Jeong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package dev.eastar.operaxwebextansion

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dev.eastar.operaxwebextansion.Log.LOG
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.experimental.and

/**
 * Opera Manager
 */
object OperaXManager {

    //RUNNING_EXTENSION/////////////////////////////////////////////////////////////////////////////////

    @SuppressLint("UseSparseArrays")
    private val RUNNING_EXTENSION = HashMap<Int, OperaX>()

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <R> execute(context: Context, json: String): R? = runCatching {
        if (LOG) {
            Log.e("OPERA>>", json)
            Log.flog("OPERA>>", json)
        }
        OperaXRequest.newInstance(json).invoke(context) as? R
    }.getOrNull()

    @JvmStatic
    fun execute(webView: WebView, json: String): Any? = runCatching {
        if (LOG) {
            Log.e("OPERA>>", json)
            Log.flog("OPERA>>", json)
        }
        val req = OperaXRequest.newInstance(json)
        val result = req.invoke(webView)
        if (req.returnType === Void.TYPE)
            return null

        var res = OperaXResponse.OK
        if (result == null)
            res = OperaXResponse.FAIL
        val script = res.getScript(req, result)
        sendJavascript(webView, script)
        return result
    }.onFailure {
        sendException(webView, OperaXRequest.errorInstance(json), it)
    }.getOrElse {
        it.javaClass.simpleName + " : " + it.message
    }

    fun sendException(webView: WebView, req: OperaXRequest, e: Throwable) {
        var th: Throwable = e
        while (th.cause != null) {
            th = th.cause!!
        }
        val resClassName = th.javaClass.simpleName
        val message = resClassName + " " + th.message
        val res = OperaXResponse.find(resClassName)
        val script = res.getScript(req, message)
        sendJavascript(webView, script)
    }

    fun sendJavascript(webView: WebView, script: String) {
        runCatching {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                webView.post { sendJavascript(webView, script) }
                return
            }
            webView.evaluateJavascript(script, null)
        }

        //log
        if (LOG) {
            runCatching {
                "\\((.*)\\)\\((.*)\\);".toRegex().matchEntire(script)?.run {
                    Log.i("<<OPERA", groupValues[1], groupValues[2])
                    Log.flog("<<OPERA", groupValues[1], groupValues[2])
                }
            }
        }
    }

    //RUNNING_EXTENSION/////////////////////////////////////////////////////////////////////////////////
    @JvmStatic
    fun addOperaXJavascriptInterface(webview: WebView, name: String) {
        Log.e("addOperaXJavascriptInterface", name)
        webview.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun execute(json: String): Any? = execute(webview, json)
        }, name)
    }

    @JvmStatic
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val ext = removeRunningExtension(requestCode) ?: return false

        ext.onActivityResult(requestCode, resultCode, data)
        return true
    }

    internal fun addRunningExtension(operaXItem: OperaX) {
        RUNNING_EXTENSION[operaXItem.mReq.requestCode] = operaXItem
    }

    private fun removeRunningExtension(requestCode: Int): OperaX? {
        return RUNNING_EXTENSION.remove(requestCode)
    }

//    /**
//     * JSONxxxxx element -> primitive type
//     */
//    @Throws(JSONException::class)
//    fun dewarp(element: Any?): Any? {
//        if (element == null) {
//            return null
//        } else if (element is Number) {
//            return element
//        } else if (element is String) {
//            val string = element.toString()
//            if (string.matches("\\d+|\\d*\\.\\d+|\\d+\\.\\d*".toRegex()))
//                Log.w("!JSON element : $element may be Number?")
//            return string
//        } else {
//            return element
//        }
//    }

}

object Log {
    var LOG = false
    private val FILE_LOG: File? = null
    private const val LF = "\n"
    private const val MAX_LOG_LINE_BYTE_SIZE = 3600
    private const val PREFIX = "``"
    private const val PREFIX_MULTILINE: String = "$PREFIX▼"
    private val LOG_CLASS: String = javaClass.name
    private val ANDROID_CLASS = "^android\\..+|^com\\.android\\..+|^java\\..+".toRegex()

    var MODE = eMODE.STUDIO

    enum class eMODE { STUDIO, SYSTEMOUT }

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
        val info: StackTraceElement? = getStack()
        flog(info, *args)
    }

    private fun flog(info: StackTraceElement?, vararg args: Any?) {
        FILE_LOG ?: return

        runCatching {
            val log: String = makeMessage(*args)
            val buf = BufferedWriter(OutputStreamWriter(FileOutputStream(FILE_LOG, true), "UTF-8"))
            val tag = String.format("%-80s %-100s ``", _DUMP_milliseconds(System.currentTimeMillis()), info.toString())
            val tagspace = String.format("%80s %100s ``", " ", " ")
            val st = StringTokenizer(log, LF, false)
            if (st.hasMoreTokens()) {
                val token = st.nextToken()
                buf.append(tag).append(token).append(LF)
            }
            while (st.hasMoreTokens()) {
                val token = st.nextToken()
                buf.append(tagspace).append(token).append(LF)
            }
            buf.close()
        }
    }

    private fun _DUMP_milliseconds(milliseconds: Long): String? = "<%s,%${Long.MAX_VALUE.toString().length}d>".format(SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(milliseconds)), SystemClock.elapsedRealtime())

    fun i(vararg args: Any?) {
        val info: StackTraceElement? = getStack()
        val tag: String = getTag(info)
        val locator: String = getLocator(info)
        val msg: String = makeMessage(*args)
        println(android.util.Log.INFO, tag, locator, msg)
    }

    private fun println(priority: Int, tag: String, locator: String, msg: String?): Int {
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
        if (MODE == eMODE.STUDIO) {
            val DOTS = "...................................................................................."
            val sb = StringBuilder(DOTS)
            val last_tag = tag.substring(Math.max(tag.length + locator.length - DOTS.length, 0))
            sb.replace(0, last_tag.length, last_tag)
            sb.replace(sb.length - locator.length, sb.length, locator)
            val adj_tag = sb.toString()
            val N = sa.size
            if (N <= 0) return android.util.Log.println(priority, adj_tag, PREFIX)
            if (N == 1) return android.util.Log.println(priority, adj_tag, sa[0])
            var sum = android.util.Log.println(priority, adj_tag, PREFIX_MULTILINE)
            for (s in sa) sum += android.util.Log.println(priority, adj_tag, s)
            return sum
        }
        if (MODE == eMODE.SYSTEMOUT) {
            val DOTS = "...................................................................................."
            val sb = StringBuilder(DOTS)
            val last_tag = tag.substring(Math.max(tag.length + locator.length - DOTS.length, 0))
            sb.replace(0, last_tag.length, last_tag)
            sb.replace(sb.length - locator.length, sb.length, locator)
            val adj_tag = sb.toString()
            val N = sa.size
            if (N <= 0) {
                println(adj_tag + PREFIX)
                return 0
            }
            if (N == 1) {
                println(adj_tag + sa[0])
                return 0
            }
            println(adj_tag + PREFIX_MULTILINE)
            for (s in sa) println(adj_tag + s)
            return 0
        }
        return 0
    }

    private fun safeCut(byte_text: ByteArray, byte_start_index: Int, byte_length: Int): Int {
        val text_length = byte_text.size
        if (text_length <= byte_start_index) throw ArrayIndexOutOfBoundsException("!!text_length <= start_byte_index")
        if (byte_length <= 0) throw UnsupportedOperationException("!!must length > 0 ")
        if (byte_text[byte_start_index] and 0xc0.toByte() == 0x80.toByte()) throw java.lang.UnsupportedOperationException("!!start_byte_index must splited index")
        var po = byte_start_index + byte_length
        if (text_length <= po) return text_length - byte_start_index
        while (byte_start_index <= po) {
            if (byte_text[po] and 0xc0.toByte() != 0x80.toByte()) break
            po--
        }
        if (po <= byte_start_index) throw UnsupportedOperationException("!!byte_length too small")
        return po - byte_start_index
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

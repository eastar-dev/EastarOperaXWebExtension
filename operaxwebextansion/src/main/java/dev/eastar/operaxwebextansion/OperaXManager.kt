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
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dev.eastar.operaxwebextansion.OperaXLog.LOG
import org.json.JSONObject
import java.util.*

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
        OperaXLog.inLog(json)
        OperaXRequest.newInstance(json).invoke(context) as? R
    }.getOrNull()

    @JvmStatic
    fun execute(webView: WebView, json: String): Any? = runCatching {
        OperaXLog.inLog(json)
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
    }.getOrElse {
        sendException(webView, OperaXRequest.errorInstance(json), it)
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
        OperaXLog.outLog(script)
    }

    //RUNNING_EXTENSION/////////////////////////////////////////////////////////////////////////////////
    @JvmStatic
    fun addOperaXJavascriptInterface(webview: WebView, name: String) {
        OperaXLog.e("addOperaXJavascriptInterface", name)
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
        RUNNING_EXTENSION[operaXItem.req.requestCode] = operaXItem
    }

    private fun removeRunningExtension(requestCode: Int): OperaX? {
        return RUNNING_EXTENSION.remove(requestCode)
    }
}

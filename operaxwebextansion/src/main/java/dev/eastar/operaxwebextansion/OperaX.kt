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
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package dev.eastar.operaxwebextansion


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

abstract class OperaX {
    companion object {
        private const val TAILFIX_CALLBACK = "C"
    }

    protected lateinit var mContext: Context//for extension
    protected lateinit var mActivity: AppCompatActivity//for extension
    protected lateinit var webView: WebView//for extension
    ///////////
    lateinit var mReq: OperaXRequest

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class OperaXInternal

    open fun initialize(webView: WebView, req: OperaXRequest): OperaX {
        this.webView = webView
        initialize(webView.context, req)
        return this
    }

    open fun initialize(context: Context, req: OperaXRequest): OperaX {
        mContext = context
        if (context is AppCompatActivity)
            mActivity = context
        this.mReq = req
        return this
    }

    //////////////////////////////////////
    override fun toString(): String {
        return javaClass.simpleName
    }

    //////////////////////////////////////
    protected fun startActivityForResult(intent: Intent, requestCode: Int) {
        setRequestCode(requestCode)
        mActivity.startActivityForResult(intent, requestCode)
    }

    protected fun setRequestCode(requestCode: Int) {
        mReq.requestCode = requestCode
        OperaXManager.addRunningExtension(this)
    }

    ///////////////////////////////////////////////////////
    //Req
    open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val callbackMethodName = mReq.methodName + TAILFIX_CALLBACK
//        Log.i(callbackMethodName, requestCode, resultCode, data)
        try {
            val method = javaClass.getDeclaredMethod(callbackMethodName, Int::class.java, Int::class.java, Intent::class.java)
            method.isAccessible = true
            method.invoke(this, requestCode, resultCode, data)
        } catch (e: NoSuchMethodException) {
            sendOnActivityResult(requestCode, resultCode, data)
        } catch (e: Exception) {
            sendException(e)
        }
    }

    @Deprecated("No more use replace to sendResult", ReplaceWith("OperaX.sendResult(result: Any?) or OperaX.sendResult(resultCode: Int, result: Any?)  "))
    protected fun sendJavascript(script: String) = OperaXManager.sendJavascript(webView, script)

    protected fun sendOK(result: Any?) = sendResult(OperaXResponse.OK, result)
    protected fun sendFail(result: Any?) = sendResult(OperaXResponse.FAIL, result)
    protected fun sendResult(resultCode: Int, result: Any?) = OperaXManager.sendJavascript(webView, OperaXResponse.getScript(mReq, resultCode, result))
    protected fun sendResult(resultCode: OperaXResponse, result: Any?) = OperaXManager.sendJavascript(webView, OperaXResponse.getScript(mReq, resultCode.ordinal, result))
    protected fun sendException(e: Exception) = OperaXManager.sendException(webView, mReq, e)
    @Suppress("UNUSED_PARAMETER")
    private fun sendOnActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val res = if (Activity.RESULT_OK == resultCode) OperaXResponse.OK else OperaXResponse.FAIL
        val script = res.getScript(mReq, data)
        OperaXManager.sendJavascript(webView, script)
    }
}

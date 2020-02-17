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
package dev.eastar.operaxwebextansion

import android.content.Context
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Method

class OperaXRequest {

    lateinit var json: String

    lateinit var clazz: Class<out OperaX>
    lateinit var method: Method
    var returnType: Class<*>? = null
    lateinit var methodName: String

    var params: Array<Any?> = emptyArray()

    var requestCode: Int = 0
    var callback: String = DEFAULT_CALLBACK

    override fun toString(): String {
        return "[$callback] ${method.returnType.simpleName} ${method.declaringClass.simpleName}::${method.name} ${params.contentToString()}"
    }

    fun invoke(context: Context): Any? {
        val extension = clazz.newInstance()
        extension.initialize(context, this)
        val method = method
        return method.invoke(extension, *params)
    }

    fun invoke(webView: WebView): Any? {
        val extension = clazz.newInstance()
        extension.initialize(webView, this)
        val method = method
        return method.invoke(extension, *params)
    }

    companion object {
        private const val CALLBACK = "callback"
        private const val CLAZZ = "clazz"
        private const val METHOD = "method"
        private const val PARAMS = "params"
        private const val REQUESTCODE = "requestcode"
        private const val DEFAULT_CALLBACK: String = "console.log"

        fun errorInstance(json: String): OperaXRequest {
            return OperaXRequest().apply {
                this.json = json
                this.callback = runCatching {
                    JSONObject(json).optString(CALLBACK, DEFAULT_CALLBACK)
                }.getOrDefault(DEFAULT_CALLBACK)
            }
        }

        @Throws(ClassNotFoundException::class, JSONException::class)
        fun newInstance(json: String): OperaXRequest {
            val req = OperaXRequest()
            req.json = json

            val jo = JSONObject(json)
            req.callback = jo.optString(CALLBACK, DEFAULT_CALLBACK)
            req.requestCode = jo.optInt(REQUESTCODE, -1)

            req.clazz = getClazz(jo.optString(CLAZZ))
            req.params = getParams(jo.optJSONArray(PARAMS))
            req.method = getMethod(req.clazz, jo.optString(METHOD), req.params)
            req.returnType = req.method.returnType
            req.methodName = req.method.name
            return req
        }

        @Suppress("UNCHECKED_CAST")
        private fun getClazz(clz: String): Class<out OperaX> {
            return runCatching {
                Class.forName(clz)
            }.recoverCatching {
                Class.forName("${OperaX::class.java.`package`!!.name}.$clz")
            }.recoverCatching {
                Class.forName("opera.ext.$clz")
            }.getOrThrow() as Class<out OperaX>
        }

        @Throws(JSONException::class)
        private fun getParams(param: JSONArray?): Array<Any?> {
            return arrayOfNulls<Any>(param?.length() ?: 0)
                    .mapIndexed { index, _ -> param?.opt(index) }
                    .toTypedArray()
        }

        @Throws(NoSuchMethodException::class)
        private fun getMethod(clz: Class<*>, name: String, parameter: Array<*>): Method {
            for (method in clz.methods) {
                if (name != method.name)
                    continue
                if (method.parameterTypes.size == parameter.size)
                    return method
            }
            throw NoSuchMethodException("${clz.name}.$name( ${getParameterTypes(parameter)} )")
        }

        private fun getParameterTypes(args: Array<*>): List<Class<*>> = args.map { it?.javaClass ?: Any::class.java }

    }
}
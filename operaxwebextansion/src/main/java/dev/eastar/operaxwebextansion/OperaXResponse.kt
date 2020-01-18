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

import android.content.Intent

import org.json.JSONException
import org.json.JSONObject

@Suppress("unused")
enum class OperaXResponse {
    OK,//정상적으로 호출되고 결과도 정상임
    FAIL,//정상적으로 호출되고 결과는 실패임
    IllegalAccessException,//extension method를 잘못입력한경우
    IllegalArgumentException,//Argument 가 잘못된경우
    InvocationTargetException,//함수 실행중 오류가 난경우
    NoSuchMethodException,//지정한 method가 없는경우
    ExtensionNotFoundException,//지정한 extension이 없는경우
    InstantiationException,//함수로부터 확장팩을 못찾는경우
    UnsupportedException,//호출 규격자체가 잘못된경우
    AnotherException;//기타 예외처리

    fun getScript(req: OperaXRequest, result: Any?): String {
        return getScript(req, ordinal, result)
    }

    companion object {
        fun getScript(req: OperaXRequest, resultCode: Int, result: Any?): String {
            return "(${req.callback})(${getResult(req, resultCode, result)});"
        }

        private fun getResult(req: OperaXRequest, resultCode: Int, result: Any?) = runCatching {
            val jo = JSONObject()
            var response = result
            if (result is Intent)
                response = intent2json(result)

            jo.put("resultCode", resultCode)

            if (resultCode == OK.ordinal)
                jo.put("result", response)
            else
                jo.put("message", response)

            kotlin.runCatching {
                jo.put("request", JSONObject(req.json))
            }.getOrElse {
                jo.put("request", req.toString())
            }
        }.getOrDefault(JSONObject())

        private fun intent2json(data: Intent?): JSONObject {
            val result = JSONObject()
            if (data != null && data.extras != null) {
                val bundle = data.extras
                val keys = bundle!!.keySet()
                for (key in keys) {
                    try {
                        val o = bundle.get(key)
                        result.put(key, o)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
            return result
        }

        fun find(name: String): OperaXResponse {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                AnotherException
            }
        }
    }
}

/*
web:
var callback_func = function(param) {
    alert(param);
};
------------------------------------------------------
asis:
callback_func

tobe:
callback_func("hello x")

or
tobe:
(callback_func)("hello x")
-------------------------------------------------------
asis:
function(param) {
    alert(param);
}

tobe:
var __opera_temp_callback_func = function(param) {
    alert(param);
};
__opera_temp_callback_func("hello x")

or
tobe:
javascript:(
    function(param) {
        alert(param);
    }
)("hello x")

or
tobe:
(
    function(param) {
        alert(param);
    }
)("hello x")
*/
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
@file:Suppress("DEPRECATION")

package dev.eastar.operaxwebextansion

import android.app.Application

import dalvik.system.DexFile
import java.util.concurrent.Executors

@Suppress("unused", "DEPRECATION")
fun Application.operaXDump() {
    Executors.newSingleThreadExecutor().execute {
        val parentClass = OperaX::class.java
        val packageName = parentClass.`package`?.name!!
        val thisClass = parentClass.name
        DexFile(packageCodePath).entries().toList()
                .filter { it.startsWith(packageName) }
                .filterNot { it.startsWith(thisClass) }
//                .map { it.also { Log.e(it) } }
                .map { Class.forName(it) }
                .filter { parentClass.isAssignableFrom(it) }
                .flatMap { clz ->
                    clz.methods
                            .filterNot { it.declaringClass == Any::class.java }
                            .filterNot { it.declaringClass == OperaX::class.java }
//                            .filterNot { it.name.contains("lambda$") }
//                            .filterNot { it.name.contains("access$") }
                            .filterNot { it.name.contains('$') }
                            .filter { it.getAnnotation(OperaX.OperaXInternal::class.java) == null }
                            .toList()
                }
                .forEach {
                    android.util.Log.w("DUMP", it.toString())
                }
    }
}

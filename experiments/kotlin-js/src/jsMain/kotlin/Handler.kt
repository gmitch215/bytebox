package com.example

import kotlin.js.Promise
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise

external class Response(body: String?) : JsAny

suspend fun compute(path: String): String {
	delay(1)
	val v = Promise.resolve(42.toJsNumber()).await()
	return "hello from kotlin js " + path + " " + v.toString()
}

@OptIn(ExperimentalJsExport::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@JsExport
fun handle(path: String): Promise<JsAny?> = GlobalScope.promise { Response(compute(path) + "\n") }

fun main() {}

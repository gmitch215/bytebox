package com.example

import kotlin.coroutines.*

object Program {
	fun run() {
		val ctx = object : CoroutineContext {
			override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R = initial
			override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = null
			override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = this
		}
		val done = object : Continuation<Unit> {
			override val context: CoroutineContext = ctx
			override fun resumeWith(result: Result<Unit>) {
				println("done=" + result.isSuccess)
			}
		}
		val block: suspend () -> Unit = { println("stdlib suspend d") }
		block.startCoroutine(done)
		println("after")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

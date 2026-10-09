package com.example

import kotlin.coroutines.*

object Program {
	fun run() {
		val done = object : Continuation<Unit> {
			override val context: CoroutineContext = EmptyCoroutineContext
			override fun resumeWith(result: Result<Unit>) {
				println("done=" + result.isSuccess)
			}
		}
		val block: suspend () -> Unit = { println("stdlib suspend b") }
		block.startCoroutine(done)
		println("after")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

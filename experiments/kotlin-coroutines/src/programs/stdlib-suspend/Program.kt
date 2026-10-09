package com.example

import kotlinx.coroutines.*
import kotlin.coroutines.*

object Program {
	fun run() {
		suspend fun answer(): Int = 42
		val block: suspend () -> Unit = { println("stdlib suspend=" + answer()) }
		block.startCoroutine(Continuation(EmptyCoroutineContext) { })
		println("after")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

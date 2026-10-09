package com.example

import kotlinx.coroutines.*
import kotlin.coroutines.*

object Program {
	fun run() {
		CoroutineScope(Dispatchers.Unconfined).launch {
			delay(10)
			println("delayed")
		}
		println("after")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

package com.example

import kotlinx.coroutines.*
import kotlin.coroutines.*

object Program {
	fun run() {
		CoroutineScope(Dispatchers.Unconfined).launch { println("in launch") }
		println("after")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

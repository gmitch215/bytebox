package com.example

import kotlinx.coroutines.*

object Program {
	fun run() {
		runBlocking {
			delay(10)
			println("delayed")
		}
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

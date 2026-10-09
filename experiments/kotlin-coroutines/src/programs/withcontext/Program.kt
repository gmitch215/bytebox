package com.example

import kotlinx.coroutines.*

object Program {
	fun run() {
		runBlocking {
			val v = withContext(Dispatchers.Default) { 21 * 2 }
			println("value=$v")
		}
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

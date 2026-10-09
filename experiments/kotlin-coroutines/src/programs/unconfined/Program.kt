package com.example

import kotlinx.coroutines.*

object Program {
	fun run() {
		runBlocking {
			launch(Dispatchers.Unconfined) { println("unconfined") }
		}
		println("done")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

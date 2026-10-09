package com.example

import kotlinx.coroutines.*

object Program {
	fun run() {
		runBlocking {
			launch { println("in launch") }
		}
		println("done")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

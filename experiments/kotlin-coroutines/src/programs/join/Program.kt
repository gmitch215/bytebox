package com.example

import kotlinx.coroutines.*

object Program {
	fun run() {
		runBlocking {
			val job = launch { println("in job") }
			job.join()
			println("joined")
		}
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

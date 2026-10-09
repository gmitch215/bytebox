package com.example

import kotlinx.coroutines.*

object Program {
	fun run() {
		println("before")
		runBlocking { }
		println("after")
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

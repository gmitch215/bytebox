package com.example

sealed class Shape {
	class Circle(val r: Int) : Shape()
	class Square(val s: Int) : Shape()
}

interface Greeter {
	fun greet(): String
}

class Delegating(g: Greeter) : Greeter by g

object Probe {
	private val lazyValue: String by lazy { "computed" }

	fun run() {
		step("lazy") { lazyValue }
		step("sequence-builder") { sequence { yield(1); yield(2); yield(3) }.toList() }
		step("class-simpleName") { Point::class.simpleName }
		step("class-java-name") { Point(1, 2)::class.java.name }
		step("runCatching") { runCatching { error("boom") }.exceptionOrNull()?.message }
		step("buildString") { buildString { append("a"); append(1); append('c') } }
		step("delegation") { Delegating(object : Greeter { override fun greet() = "hi" }).greet() }
		step("sealed-when") {
			listOf<Shape>(Shape.Circle(2), Shape.Square(3)).map {
				when (it) {
					is Shape.Circle -> "circle" + it.r
					is Shape.Square -> "square" + it.s
				}
			}
		}
		step("random-seeded") { kotlin.random.Random(42).nextInt(1000) }
		step("regex-named") { Regex("(?<y>\\d{4})-(?<m>\\d{2})").find("on 2026-10")?.groups?.get("m")?.value }
		step("string-split-trim") { " a, b ,c ".split(",").map { it.trim() } }
		step("charsets") { "hé".toByteArray(Charsets.UTF_8).size }
		step("exception-stack") { Throwable("x").stackTrace.size >= 0 }
		step("arrays") { intArrayOf(3, 1, 2).sortedArray().toList() }
		step("pair-triple") { Pair(1, "a").toString() + Triple(1, 2, 3).toString() }
		step("range-progression") { (1..10 step 3).toList() + (10 downTo 7).toList() }
		step("string-format-locale") { "%,d".format(1234567) }
		step("toInt-parse") { "42".toInt() + "3.5".toDouble() }
		step("measure-time") { kotlin.system.measureTimeMillis { } >= 0 }
		step("lambda-toString-nonnull") { ({ 1 }).toString().isNotEmpty() }
		step("use-closeable") { java.io.StringReader("abc").use { it.read().toChar() } }
	}

	private fun step(name: String, block: () -> Any?) {
		try {
			println("$name=" + block())
		} catch (t: Throwable) {
			println("$name!threw " + t.javaClass.name + ": " + t.message)
		}
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

package com.example

data class Point(val x: Int, val y: Int, val label: String = "p")

enum class Color(val rgb: Int) {
	RED(0xff0000),
	GREEN(0x00ff00),
	BLUE(0x0000ff)
}

class Holder {
	lateinit var name: String

	companion object {
		const val TAG = "holder"
		fun make(n: String): Holder = Holder().also { it.name = n }
	}
}

inline fun <T> twice(block: () -> T): List<T> = listOf(block(), block())

fun String.shout(): String = uppercase() + "!"

fun describe(v: Any?): String =
	when (v) {
		null -> "null"
		is Int -> if (v > 10) "big int" else "small int"
		is String -> "string of ${v.length}"
		else -> "other"
	}

object Transcript {
	fun run() {
		collections()
		sequences()
		templates()
		dataClass()
		lambdas()
		whens()
		nullSafety()
		lateinits()
		companion()
		enums()
		formats()
		regexes()
		extensions()
	}

	private fun collections() {
		val nums = listOf(5, 3, 8, 1, 9, 2)
		println("list.map=" + nums.map { it * 2 })
		println("list.filter=" + nums.filter { it % 2 == 1 })
		println("list.sortedBy=" + nums.sortedBy { -it })
		println("list.groupBy=" + nums.groupBy { it % 3 })
		val m = mapOf("a" to 1, "b" to 2, "c" to 3)
		println("map=" + m + " keys=" + m.keys + " values=" + m.values)
		println("map.mapValues=" + m.mapValues { it.value * 10 })
		val ml = mutableListOf<String>()
		ml.add("x")
		ml += "y"
		ml.addAll(listOf("z", "w"))
		println("mutableList=" + ml + " size=" + ml.size)
	}

	private fun sequences() {
		val s = sequenceOf(1, 2, 3, 4, 5, 6).map { it * it }.filter { it > 4 }
		println("sequence=" + s.toList())
		println("generateSequence=" + generateSequence(1) { it * 2 }.take(6).toList())
	}

	private fun templates() {
		val name = "kotlin"
		val n = 3
		println("template=hello $name, ${n + 1} items, ${name.length} chars")
	}

	private fun dataClass() {
		val p = Point(1, 2)
		val q = p.copy(y = 5)
		val (x, y, label) = q
		println("data.toString=$p")
		println("data.equals=" + (p == Point(1, 2)) + " ne=" + (p == q))
		println("data.hashCode=" + p.hashCode() + " " + q.hashCode())
		println("data.destructure=$x,$y,$label")
	}

	private fun lambdas() {
		val add = { a: Int, b: Int -> a + b }
		println("lambda=" + add(2, 3))
		println("inline=" + twice { "z" })
		val counter = run {
			var c = 0
			{ c += 1; c }
		}
		counter()
		println("closure=" + counter())
	}

	private fun whens() {
		println("when=" + listOf(null, 3, 30, "abc", 2.5).map { describe(it) })
	}

	private fun nullSafety() {
		val a: String? = null
		val b: String? = "bee"
		println("safecall=" + a?.length + " " + b?.length)
		println("elvis=" + (a ?: "default") + " " + (b ?: "default"))
	}

	private fun lateinits() {
		val h = Holder()
		h.name = "late"
		println("lateinit=" + h.name)
	}

	private fun companion() {
		println("companion=" + Holder.TAG + " " + Holder.make("made").name)
	}

	private fun enums() {
		println("enum.values=" + Color.values().map { it.name + "=" + it.rgb })
		println("enum.valueOf=" + Color.valueOf("GREEN").ordinal)
	}

	private fun formats() {
		println("format=" + String.format("%5.2f|%-6s|%04d|%x", 3.14159, "ab", 42, 255))
	}

	private fun regexes() {
		val r = Regex("(\\d+)-(\\w+)")
		val m = r.find("order 123-abc done")
		println("regex.find=" + m?.groupValues)
		println("regex.matches=" + r.matches("7-x") + " " + r.matches("x"))
		println("regex.replace=" + r.replace("1-a 2-b", "$2:$1"))
	}

	private fun extensions() {
		println("extension=" + "hey".shout())
	}

	@JvmStatic
	fun main(args: Array<String>) {
		run()
	}
}

package com.example

import dev.gmitch215.bytebox.Bytebox
import dev.gmitch215.bytebox.Env
import dev.gmitch215.bytebox.ExecutionCtx
import dev.gmitch215.bytebox.Request
import dev.gmitch215.bytebox.Response
import dev.gmitch215.bytebox.Worker

data class Language(val name: String, val year: Int)

/** A Worker written as a Kotlin object; the build reads its instance, so nothing constructs it. */
object KotlinWorker : Worker {
	private val languages = listOf(Language("Java", 1995), Language("Kotlin", 2011), Language("Rust", 2015))

	override fun fetch(request: Request, env: Env, ctx: ExecutionCtx): Response {
		val since = request.query("since", "2000").toInt()
		val released = languages.filter { it.year >= since }.associate { it.name to it.year }
		if (released.isEmpty()) return Bytebox.response("nothing since $since")

		return Bytebox.response(released.entries.joinToString("\n") { "${it.key} was released in ${it.value}" })
	}
}

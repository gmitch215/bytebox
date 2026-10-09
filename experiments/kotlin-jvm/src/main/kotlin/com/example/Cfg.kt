package com.example

import dev.gmitch215.bytebox.Bytebox
import dev.gmitch215.bytebox.Env
import dev.gmitch215.bytebox.ExecutionCtx
import dev.gmitch215.bytebox.Request
import dev.gmitch215.bytebox.Response
import dev.gmitch215.bytebox.Worker
import dev.gmitch215.bytebox.json.JSON
import dev.gmitch215.bytebox.json.JSONType

@JSONType
class Cfg {
	@JvmField var host: String = ""
	var port: Int = 0
}

class JsonWorker : Worker {
	companion object {
		init {
			val c = Cfg()
			c.host = "h"
			c.port = 8
			println("json=" + JSON.stringify(c, Cfg::class.java))
		}
	}

	override fun fetch(request: Request, env: Env, ctx: ExecutionCtx): Response = Bytebox.response("ok")
}

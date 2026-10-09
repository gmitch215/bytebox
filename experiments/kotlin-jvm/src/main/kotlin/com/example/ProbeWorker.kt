package com.example

import dev.gmitch215.bytebox.Bytebox
import dev.gmitch215.bytebox.Env
import dev.gmitch215.bytebox.ExecutionCtx
import dev.gmitch215.bytebox.Request
import dev.gmitch215.bytebox.Response
import dev.gmitch215.bytebox.Worker

class ProbeWorker : Worker {
	companion object {
		init {
			Probe.run()
		}
	}

	override fun fetch(request: Request, env: Env, ctx: ExecutionCtx): Response = Bytebox.response("ok")
}

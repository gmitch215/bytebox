package com.example

@OptIn(ExperimentalJsExport::class)
@JsExport
fun handle(path: String): String =
	try {
		check(path != "/boom") { "boom" }
		"hello from kotlin wasmJs " + path
	} catch (e: IllegalStateException) {
		"caught " + e.message
	}

fun main() {}

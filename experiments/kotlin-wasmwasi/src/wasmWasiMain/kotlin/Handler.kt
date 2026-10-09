package com.example

import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.WasmExport

@OptIn(ExperimentalWasmInterop::class)
@WasmExport
fun handle(n: Int): Int =
	try {
		require(n >= 0) { "negative" }
		n * 2 + 1
	} catch (e: IllegalArgumentException) {
		-1
	}

fun main() {
	println("hello from kotlin wasmWasi")
}

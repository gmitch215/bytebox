description = "The size ablation harness and its ledger"

dependencies {
	testImplementation(libs.junit.api)
	testRuntimeOnly(libs.junit.engine)
}

tasks.named<Test>("test") {
	// the fixture is a real compiled module, so the split is measured rather than modelled
	systemProperty("bytebox.fixture", "hello.wasm")
}

package com.example.sub;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

public final class Policy extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(named("java.util.concurrent.Semaphore")).replacePackage(
			"java.util.concurrent",
			"com.example.sub"
		);
	}
}

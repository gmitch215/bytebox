package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("reading a size")
class SizeSpecTest {

	@ParameterizedTest
	@CsvSource({
		"1024,1024",
		"1KiB,1024",
		"1kib,1024",
		"250KiB,256000",
		"1MiB,1048576",
		"3MiB,3145728",
		"1GiB,1073741824",
		"1KB,1000",
		"1MB,1000000",
		"1GB,1000000000",
		"1K,1024",
		"1M,1048576",
		"512B,512",
		"1.5KiB,1536",
		"' 2KiB ',2048",
		"1_024,1024"
	})
	@DisplayName("takes a byte count or a suffixed size")
	void reads(String text, long expected) {
		assertEquals(expected, SizeSpec.parseSize(text));
	}

	@Test
	@DisplayName("keeps both unit conventions apart, because both are in use")
	void bothConventions() {
		// KiB is 1024 and KB is 1000, and Cloudflare's own documentation mixes them
		assertEquals(1024, SizeSpec.parseSize("1KiB"));
		assertEquals(1000, SizeSpec.parseSize("1KB"));
	}

	@Test
	@DisplayName("names what it could not read")
	void refusesNonsense() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
			SizeSpec.parseSize("about a megabyte")
		);

		assertTrue(failure.getMessage().contains("about a megabyte"), failure.getMessage());
		assertTrue(failure.getMessage().contains("250KiB"), failure.getMessage());
	}

	@Test
	@DisplayName("names the decoder each compressor needs, and none for no compression")
	void decoders() {
		assertEquals("fzstd", SizeSpec.Compressor.ZSTD.decoder());
		assertEquals("fflate", SizeSpec.Compressor.GZIP.decoder());
		// nothing to inflate means nothing in the bundle to inflate it
		assertEquals(null, SizeSpec.Compressor.NONE.decoder());
	}
}

package dev.gmitch215.bytebox.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The provider asks the platform which identifiers exist, so what it does with an identifier is in
 * the workerd lane. What is here is what it does before asking.
 */
@DisplayName("the zone rules provider")
class ZoneRulesProviderTest {

	@Test
	@DisplayName("refuses a null identifier before it asks the platform about it")
	void refusesNull() {
		assertThrows(NullPointerException.class, () -> ZoneRulesProvider.getRules(null, false));
	}

	@Test
	@DisplayName("never refreshes, because the database changes only when the platform does")
	void neverRefreshes() {
		assertFalse(ZoneRulesProvider.refresh());
	}

	@Test
	@DisplayName("is constructible, which is the only thing the compiler needs from it")
	void constructible() {
		assertNotNull(new ZoneRulesProvider() {});
	}
}

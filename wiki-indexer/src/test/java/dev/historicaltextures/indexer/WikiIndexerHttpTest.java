package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WikiIndexerHttpTest {
	@Test
	void backoffUsesExponentialDefaults() {
		assertEquals(2000L, WikiIndexer.backoffMillis(1, Optional.empty()));
		assertEquals(8000L, WikiIndexer.backoffMillis(3, Optional.empty()));
		assertEquals(60000L, WikiIndexer.backoffMillis(7, Optional.empty()));
	}

	@Test
	void backoffHonoursRetryAfterSeconds() {
		assertEquals(5000L, WikiIndexer.backoffMillis(2, Optional.of("5")));
		assertEquals(120000L, WikiIndexer.backoffMillis(1, Optional.of("999")));
		assertEquals(4000L, WikiIndexer.backoffMillis(2, Optional.of("soon")));
	}
}

package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WikiIndexerSoundValidationTest {
	private static final Set<String> KNOWN = Set.of(
			"entity.cow.ambient",
			"block.amethyst_block.place"
	);
	private static final LegacySoundEvents LEGACY = new LegacySoundEvents(Map.of(), KNOWN);

	@Test
	void keepsKnownEventsUnchanged() {
		assertEquals("entity.cow.ambient", WikiIndexer.validateSoundEvent("entity.cow.ambient", KNOWN, LEGACY));
	}

	@Test
	void mapsLegacyEventsToCurrentNames() {
		assertEquals("entity.cow.ambient", WikiIndexer.validateSoundEvent("mob.cow.say", KNOWN, LEGACY));
	}

	@Test
	void dropsUnmappableGuesses() {
		assertNull(WikiIndexer.validateSoundEvent("block.amethyst.place", KNOWN, LEGACY));
	}

	@Test
	void skipsValidationWithoutKnownEvents() {
		assertEquals("block.amethyst.place", WikiIndexer.validateSoundEvent("block.amethyst.place", Set.of(), LEGACY));
	}

	@Test
	void skipsValidationWithoutLegacyMapper() {
		assertEquals("mob.cow.say", WikiIndexer.validateSoundEvent("mob.cow.say", KNOWN, null));
	}

	@Test
	void dropsNullAndBlankEvents() {
		assertNull(WikiIndexer.validateSoundEvent(null, KNOWN, LEGACY));
		assertNull(WikiIndexer.validateSoundEvent("  ", KNOWN, LEGACY));
	}
}

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

	@Test
	void buildsSoundVariantIds() {
		assertEquals(
				"water_splash_old_ogg",
				WikiIndexer.soundVariantId("Water Splash Old.ogg", "entity.player.splash", true)
		);
		assertEquals(
				"water_splash_old_ogg__entity_player_splash",
				WikiIndexer.soundVariantId("Water Splash Old.ogg", "entity.player.splash", false)
		);
	}

	@Test
	void addsTableEventsWithJavaPrecedence() {
		Map<String, Set<String>> events = new java.util.LinkedHashMap<>();
		Set<String> javaKeys = new java.util.LinkedHashSet<>();

		// (a) java key twice with two events -> set of both in order
		WikiIndexer.addTableEvent(events, javaKeys, "key_a", "event1", true);
		WikiIndexer.addTableEvent(events, javaKeys, "key_a", "event2", true);
		assertEquals(new java.util.LinkedHashSet<>(java.util.List.of("event1", "event2")), events.get("key_a"));

		// (b) bedrock first then java -> only the java event
		WikiIndexer.addTableEvent(events, javaKeys, "key_b", "bedrock_event", false);
		WikiIndexer.addTableEvent(events, javaKeys, "key_b", "java_event", true);
		assertEquals(Set.of("java_event"), events.get("key_b"));

		// (c) java then bedrock -> bedrock ignored
		WikiIndexer.addTableEvent(events, javaKeys, "key_c", "java_event", true);
		WikiIndexer.addTableEvent(events, javaKeys, "key_c", "bedrock_event", false);
		assertEquals(Set.of("java_event"), events.get("key_c"));

		// (d) bedrock only -> bedrock event kept
		WikiIndexer.addTableEvent(events, javaKeys, "key_d", "bedrock_event", false);
		assertEquals(Set.of("bedrock_event"), events.get("key_d"));
	}
}

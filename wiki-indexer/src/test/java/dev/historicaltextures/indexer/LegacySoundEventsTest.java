package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySoundEventsTest {
	private static final Set<String> KNOWN = Set.of(
			"entity.cow.ambient",
			"entity.cow.hurt",
			"block.stone.break",
			"block.wool.step",
			"entity.enderman.ambient",
			"entity.enderman.death",
			"entity.arrow.shoot",
			"entity.donkey.ambient",
			"entity.pig.death",
			"entity.arrow.hit_player",
			"block.grass.place"
	);

	private final LegacySoundEvents events = new LegacySoundEvents(Map.of(), KNOWN);

	@Test
	void parsesKnownEventsFromCdTemplates() {
		String wikitext = "Added {{cd|d=and|step.grass|step.gravel}} and {{cd|random.bow}}. "
				+ "Changed all \"{{cd|mob.}}\" events. {{cd|block.''<block>''.break}}";
		assertEquals(
				List.of("step.grass", "step.gravel", "random.bow"),
				List.copyOf(LegacySoundEvents.parseKnownEvents(wikitext))
		);
	}

	@Test
	void parsesRenamesFromHistoryLines() {
		String wikitext = "|Renamed {{cd|mob.blaze}} to {{cd|mob.blaze.breath}}.\n"
				+ "|Renamed {{cd|mob.enderman}} to {{cd|mob.endermen.idle}}, "
				+ "{{cd|mob.endermandeath}} to {{cd|mob.endermen.death}}.\n"
				+ "|Changed {{cd|entity.arrow.successful_hit}} to {{cd|entity.arrow.hit_player}}.\n"
				+ "|Changed all \"{{cd|mob.}}\" events to \"{{cd|entity.}}\".";

		Map<String, String> renames = LegacySoundEvents.parseRenames(wikitext);

		assertEquals(
				List.of(
						"mob.blaze=mob.blaze.breath",
						"mob.enderman=mob.endermen.idle",
						"mob.endermandeath=mob.endermen.death",
						"entity.arrow.successful_hit=entity.arrow.hit_player"
				),
				renames.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList()
		);
	}

	@Test
	void mapsLegacyEventNamesToCurrentEvents() {
		assertEquals(Optional.of("entity.cow.ambient"), events.toModern("mob.cow.say"));
		assertEquals(Optional.of("entity.cow.hurt"), events.toModern("mob.cow.hurt"));
		assertEquals(Optional.of("block.stone.break"), events.toModern("dig.stone"));
		assertEquals(Optional.of("block.wool.step"), events.toModern("step.cloth"));
		assertEquals(Optional.of("entity.arrow.shoot"), events.toModern("random.bow"));
		assertEquals(Optional.of("entity.donkey.ambient"), events.toModern("mob.horse.donkey.idle"));
		assertEquals(Optional.of("entity.pig.death"), events.toModern("mob.pigdeath"));
		assertEquals(Optional.of("block.grass.place"), events.toModern("use.grass"));
		assertEquals(Optional.of("entity.cow.ambient"), events.toModern("entity.cow.ambient"));
		assertTrue(events.toModern("mob.unknownthing.say").isEmpty());
		assertTrue(events.toModern("").isEmpty());
	}

	@Test
	void followsExplicitRenames() {
		LegacySoundEvents mobRenames = new LegacySoundEvents(
				Map.of("mob.enderman", "mob.endermen.idle"),
				KNOWN
		);
		assertEquals(Optional.of("entity.enderman.ambient"), mobRenames.toModern("mob.enderman"));

		LegacySoundEvents arrowRenames = new LegacySoundEvents(
				Map.of("entity.arrow.successful_hit", "entity.arrow.hit_player"),
				KNOWN
		);
		assertEquals(Optional.of("entity.arrow.hit_player"), arrowRenames.toModern("entity.arrow.successful_hit"));
	}

	@Test
	void returnsFirstCandidateWhenKnownEventsAreEmpty() {
		LegacySoundEvents unvalidated = new LegacySoundEvents(Map.of(), Set.of());
		assertEquals(Optional.of("entity.cow.ambient"), unvalidated.toModern("mob.cow.say"));
	}

	@Test
	void stopsOnRenameCycles() {
		LegacySoundEvents cyclic = new LegacySoundEvents(
				Map.of("a.b", "c.d", "c.d", "a.b"),
				Set.of("entity.cow.ambient")
		);
		assertTrue(cyclic.toModern("a.b").isEmpty());
	}
}

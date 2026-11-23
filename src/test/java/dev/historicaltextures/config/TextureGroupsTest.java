package dev.historicaltextures.config;

import dev.historicaltextures.catalog.TextureVariant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureGroupsTest {
	private static final String TOP = "minecraft:textures/block/cake_top.png";
	private static final String SIDE = "minecraft:textures/block/cake_side.png";

	private static TextureVariant variant(String id, String target, String javaVersion, String... tags) {
		return new TextureVariant(
				id,
				id + ".png",
				List.of(),
				"",
				"assets/test/" + id + ".png",
				List.of(target),
				id,
				true,
				List.of(tags),
				javaVersion,
				"",
				""
		);
	}

	@Test
	void membersDropsOtherBlocksOwnTextures() {
		Set<String> textures = Set.of(
				"minecraft:textures/block/grass_block_top.png",
				"minecraft:textures/block/grass_block_side.png",
				"minecraft:textures/block/dirt.png",
				"minecraft:textures/entity/grass.png"
		);
		Set<String> allBlocks = Set.of("dirt", "grass_block", "stone");

		List<String> members = TextureGroups.members("grass_block", textures, allBlocks);

		assertEquals(List.of(
				"minecraft:textures/block/grass_block_side.png",
				"minecraft:textures/block/grass_block_top.png",
				"minecraft:textures/entity/grass.png"
		), members);
	}

	@Test
	void membersKeepsTheBlocksOwnTexture() {
		Set<String> textures = Set.of(
				"minecraft:textures/block/oak_log.png",
				"minecraft:textures/block/oak_log_top.png"
		);
		Set<String> allBlocks = Set.of("oak_log", "oak_planks");

		List<String> members = TextureGroups.members("oak_log", textures, allBlocks);

		assertEquals(List.of(
				"minecraft:textures/block/oak_log.png",
				"minecraft:textures/block/oak_log_top.png"
		), members);
	}

	@Test
	void versionsEmitOneEntryPerDistinctChoiceChange() {
		TextureVariant topJe1 = variant("top_je1", TOP, "Beta 1.2", "JE1");
		TextureVariant topJe2 = variant("top_je2", TOP, "18w43a", "JE2");
		TextureVariant topTu = variant("top_tu", TOP, "1.0", "TU1");
		TextureVariant sideJe1 = variant("side_je1", SIDE, "Beta 1.2", "JE1");
		TextureVariant sideJe2 = variant("side_je2", SIDE, "Beta 1.7", "JE2");
		TextureVariant sideJe3 = variant("side_je3", SIDE, "18w43a", "JE3");

		Map<String, List<TextureVariant>> byTarget = Map.of(
				TOP, List.of(topJe1, topJe2, topTu),
				SIDE, List.of(sideJe1, sideJe2, sideJe3)
		);

		List<TextureGroups.GroupVersion> versions = TextureGroups.versions(List.of(TOP, SIDE), byTarget);

		assertEquals(3, versions.size());
		assertEquals("Beta 1.2", versions.get(0).label());
		assertEquals("Beta 1.7", versions.get(1).label());
		assertEquals("18w43a", versions.get(2).label());

		assertEquals(Map.of(TOP, "top_je1", SIDE, "side_je1"), versions.get(0).choices());
		assertEquals(Map.of(TOP, "top_je1", SIDE, "side_je2"), versions.get(1).choices());
		assertEquals(Map.of(TOP, "top_je2", SIDE, "side_je3"), versions.get(2).choices());

		for (TextureGroups.GroupVersion version : versions) {
			assertFalse(version.choices().containsValue("top_tu"));
		}
	}

	@Test
	void versionsSkipsKeysThatDoNotChangeChoices() {
		TextureVariant first = variant("m_same", "minecraft:textures/block/m.png", "Beta 1.2", "JE1");
		TextureVariant repeat = variant("m_same", "minecraft:textures/block/m.png", "Beta 1.3", "JE1");
		Map<String, List<TextureVariant>> byTarget = Map.of(
				"minecraft:textures/block/m.png", List.of(first, repeat)
		);

		List<TextureGroups.GroupVersion> versions = TextureGroups.versions(
				List.of("minecraft:textures/block/m.png"), byTarget);

		assertEquals(1, versions.size());
		assertEquals("Beta 1.2", versions.get(0).label());
		assertEquals(Map.of("minecraft:textures/block/m.png", "m_same"), versions.get(0).choices());
	}

	@Test
	void versionsHandleMembersWithoutVariants() {
		TextureVariant only = variant("solo", "minecraft:textures/block/solo.png", "1.2", "JE1");
		Map<String, List<TextureVariant>> byTarget = Map.of(
				"minecraft:textures/block/solo.png", List.of(only)
		);

		List<TextureGroups.GroupVersion> versions = TextureGroups.versions(
				List.of("minecraft:textures/block/solo.png", "minecraft:textures/block/empty.png"), byTarget);

		assertEquals(1, versions.size());
		assertEquals(Map.of("minecraft:textures/block/solo.png", "solo"), versions.get(0).choices());
		assertTrue(TextureGroups.versions(List.of(), byTarget).isEmpty());
	}

	@Test
	void matchesComparesEveryMemberChoice() {
		TextureGroups.GroupVersion version = new TextureGroups.GroupVersion(
				"v", 1, Map.of(TOP, "top_a"));
		List<String> members = List.of(TOP, SIDE);

		assertTrue(TextureGroups.matches(version, members, member ->
				member.equals(TOP) ? "top_a" : null));
		assertFalse(TextureGroups.matches(version, members, member ->
				member.equals(TOP) ? "top_b" : null));
		assertFalse(TextureGroups.matches(version, members, member -> "top_a"));
	}
}

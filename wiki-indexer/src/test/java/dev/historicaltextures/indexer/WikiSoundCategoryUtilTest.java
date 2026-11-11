package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiSoundCategoryUtilTest {
	@Test
	void mapsAmethystPlaceFromCategory() {
		var mapping = WikiSoundCategoryUtil.mapCategoryFile("Category:Amethyst sounds", "Amethyst place1.ogg").orElseThrow();
		assertEquals("block.amethyst_block.place", mapping.soundEvent());
		assertTrue(mapping.vanillaSoundPath().contains("amethyst_block"));
		assertTrue(mapping.vanillaSoundPath().contains("place"));
	}

	@Test
	void mapsZombieHurtFromCategory() {
		var mapping = WikiSoundCategoryUtil.mapCategoryFile("Category:Zombie sounds", "Zombie hurt1.ogg").orElseThrow();
		assertEquals("entity.zombie.hurt", mapping.soundEvent());
		assertTrue(mapping.vanillaSoundPath().contains("mob/zombie"));
	}

	@Test
	void mapsAncientDebrisDig() {
		var mapping = WikiSoundCategoryUtil.mapCategoryFile(
				"Category:Ancient debris sounds",
				"Ancient Debris dig1.ogg"
		).orElseThrow();
		assertEquals("block.ancient_debris.break", mapping.soundEvent());
	}

	@Test
	void mapsDoorOldFromHistoricalCategory() {
		var mapping = WikiSoundUtil.mapFile("Door closing old.ogg").orElseThrow();
		assertEquals("block.wooden_door.close", mapping.soundEvent());
	}
}

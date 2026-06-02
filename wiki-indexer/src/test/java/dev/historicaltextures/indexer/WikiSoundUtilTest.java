package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiSoundUtilTest {
	@Test
	void mapsDoorVariantsToSameEventWithDifferentPaths() {
		var oldClose = WikiSoundUtil.mapFile("Door closing old.ogg").orElseThrow();
		var modernClose = WikiSoundUtil.mapFile("Door close.ogg").orElseThrow();
		assertEquals("block.wooden_door.close", oldClose.soundEvent());
		assertEquals("block.wooden_door.close", modernClose.soundEvent());
		assertTrue(oldClose.vanillaSoundPath().contains("door_close"));
		assertTrue(modernClose.vanillaSoundPath().contains("wooden_door"));
		assertEquals("0.0.14a – 1.8.9", oldClose.versionHint());
		assertEquals("1.9+", modernClose.versionHint());
	}

	@Test
	void infersVersionFromFileName() {
		assertEquals("Historical (old)", WikiSoundUtil.inferVersionLabel("Cow Hurt Old.ogg", ""));
		assertEquals("1.9+", WikiSoundUtil.inferVersionLabel("Door close.ogg", ""));
	}
}

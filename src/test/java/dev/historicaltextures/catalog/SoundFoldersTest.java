package dev.historicaltextures.catalog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundFoldersTest {
	@Test
	void folderKeyStripsExtensionAndTrailingDigits() {
		assertEquals("explosion", SoundFolders.folderKey("Explosion1.ogg"));
		assertEquals("explosion old", SoundFolders.folderKey("Explosion Old.ogg"));
		assertEquals("water splash", SoundFolders.folderKey("Water splash2.ogg"));
		assertEquals("creeper_oldhurt", SoundFolders.folderKey("Creeper_Oldhurt1.ogg"));
		assertEquals("fizz", SoundFolders.folderKey("Fizz.ogg"));
		assertEquals("poplar leaves ambient", SoundFolders.folderKey("Poplar leaves ambient12.OGG"));
		assertEquals("1", SoundFolders.folderKey("1.ogg"));
		assertEquals("", SoundFolders.folderKey(null));
	}

	@Test
	void folderLabelPreservesOriginalCase() {
		assertEquals("Water splash", SoundFolders.folderLabel("Water splash2.ogg"));
	}

	@Test
	void groupVariantsByFolderKeyPreservingOrder() {
		SoundVariant v1 = createVariant("explosion1_ogg", "Explosion1.ogg");
		SoundVariant v2 = createVariant("explosion_old_ogg", "Explosion Old.ogg");
		SoundVariant v3 = createVariant("explosion2_ogg", "Explosion2.ogg");
		SoundVariant v4 = createVariant("explosion3_ogg", "Explosion3.ogg");

		List<SoundFolders.SoundFolder> folders = SoundFolders.group(List.of(v1, v2, v3, v4));
		assertEquals(2, folders.size());

		SoundFolders.SoundFolder f1 = folders.get(0);
		assertEquals("explosion", f1.key());
		assertEquals("Explosion", f1.label());
		assertEquals(List.of(v1, v3, v4), f1.members());

		SoundFolders.SoundFolder f2 = folders.get(1);
		assertEquals("explosion old", f2.key());
		assertEquals("Explosion Old", f2.label());
		assertEquals(List.of(v2), f2.members());
	}

	@Test
	void groupEmptyAndUnmodifiable() {
		List<SoundFolders.SoundFolder> empty = SoundFolders.group(List.of());
		assertTrue(empty.isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> empty.add(null));

		List<SoundFolders.SoundFolder> nullResult = SoundFolders.group(null);
		assertTrue(nullResult.isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> nullResult.add(null));

		SoundVariant v = createVariant("explosion1_ogg", "Explosion1.ogg");
		List<SoundFolders.SoundFolder> folders = SoundFolders.group(List.of(v));
		assertEquals(1, folders.size());
		assertThrows(UnsupportedOperationException.class, () -> folders.add(null));
		assertThrows(UnsupportedOperationException.class, () -> folders.get(0).members().add(null));

		assertThrows(UnsupportedOperationException.class, () ->
				new SoundFolders.SoundFolder("key", "label", new ArrayList<>()).members().add(v)
		);
	}

	@Test
	void soundFoldersForEventDelegatesToGroup() {
		HistoricalCatalog catalog = HistoricalCatalog.get();
		List<SoundFolders.SoundFolder> folders = catalog.soundFoldersForEvent("entity.generic.explode");
		assertEquals(SoundFolders.group(catalog.soundVariantsForEvent("entity.generic.explode")), folders);
	}

	private static SoundVariant createVariant(String id, String wikiFile) {
		return new SoundVariant(
				id,
				wikiFile,
				"entity.generic.explode",
				"sounds/test.ogg",
				"",
				"Explosion",
				List.of(),
				"1.0",
				"",
				"1.0"
		);
	}
}

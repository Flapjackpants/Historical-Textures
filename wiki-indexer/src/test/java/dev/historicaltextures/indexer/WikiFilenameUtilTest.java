package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiFilenameUtilTest {
	@Test
	void acceptsRealTextureFileNamesByContext() {
		assertTrue(WikiFilenameUtil.isTextureFile(
				"Grass Block (top texture) JE2.png",
				WikiFilenameUtil.CrawlContext.BLOCKS
		));
		assertTrue(WikiFilenameUtil.isTextureFile(
				"Apple JE1 BE1.png",
				WikiFilenameUtil.CrawlContext.ITEMS
		));
		assertTrue(WikiFilenameUtil.isTextureFile(
				"Barrier (held) JE1 BE1.png",
				WikiFilenameUtil.CrawlContext.ITEMS
		));
		assertTrue(WikiFilenameUtil.isTextureFile(
				"Acacia Boat (item) JE1 BE2.png",
				WikiFilenameUtil.CrawlContext.ITEMS
		));
	}

	@Test
	void rejectsFileNamesWithoutTextureMarkerOrEditionTag() {
		assertFalse(WikiFilenameUtil.isTextureFile("Blank.png", WikiFilenameUtil.CrawlContext.BLOCKS));
		assertFalse(WikiFilenameUtil.isTextureFile("Apple JE1 BE1.png", WikiFilenameUtil.CrawlContext.UNKNOWN));
		assertFalse(WikiFilenameUtil.isTextureFile("Cow say1.ogg", WikiFilenameUtil.CrawlContext.ENTITIES));
	}

	@Test
	void extractsBaseNamesAndTextureSuffixes() {
		assertEquals("Apple", WikiFilenameUtil.extractBaseName("Apple JE1 BE1.png"));
		String grass = "Grass Block (top texture) JE2.png";
		assertEquals("Grass Block", WikiFilenameUtil.extractBaseName(grass));
		assertEquals("_top", WikiFilenameUtil.textureSuffix(grass));
	}

	@Test
	void parsesPageContexts() {
		assertEquals(
				WikiFilenameUtil.CrawlContext.ITEMS,
				WikiFilenameUtil.parsePageContext("Legacy Console Edition item texture history/Debug fourj item")
		);
		assertEquals(
				WikiFilenameUtil.CrawlContext.MAP_ICONS,
				WikiFilenameUtil.parsePageContext("Java Edition history of textures/Map icons")
		);
		assertEquals(
				WikiFilenameUtil.CrawlContext.ITEMS,
				WikiFilenameUtil.parsePageContext("List of historical item textures")
		);
		assertEquals(
				WikiFilenameUtil.CrawlContext.PARTICLES,
				WikiFilenameUtil.parsePageContext("Java Edition history of textures/Particles")
		);
		assertEquals(
				WikiFilenameUtil.CrawlContext.PAINTINGS,
				WikiFilenameUtil.parsePageContext("Bedrock Edition history of textures/Paintings")
		);
		assertEquals(
				WikiFilenameUtil.CrawlContext.EFFECTS,
				WikiFilenameUtil.parsePageContext("Java Edition history of textures/Effects")
		);
	}

	@Test
	void parsesPocketAndLegacyConsoleEditionTags() {
		assertEquals(List.of("PE1", "LCE2"), WikiFilenameUtil.parseTextureTags("Potato PE1 LCE2.png"));
		assertEquals(List.of("PE"), WikiFilenameUtil.parseEditions("Potato PE1.png"));
	}

	@Test
	void extractsFileNamesFromTemplatesAndGalleries() {
		String wikitext = "[[File:A (texture) JE1.png|32px]]\n"
				+ "<gallery>\n"
				+ "B (held) JE1 BE1.png|{{LCEV|x}}\n"
				+ "File:C JE2.png\n"
				+ "</gallery>";
		assertEquals(
				List.of("A (texture) JE1.png", "B (held) JE1 BE1.png", "C JE2.png"),
				WikiIndexer.extractFileNames(wikitext)
		);
	}
}

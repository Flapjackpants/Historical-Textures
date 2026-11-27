package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiIndexerParseTest {
	@Test
	void normalizesWikiFilenames() {
		assertEquals("oak_planks_(texture)_je1.png", WikiFilenameUtil.normalizeKey("Oak Planks (texture) JE1.png"));
		assertEquals("cow_hurt_old.ogg", WikiFilenameUtil.normalizeKey("Cow Hurt Old.ogg"));
	}

	@Test
	void parsesEditionTagsFromFileName() {
		assertEquals(List.of("JE", "BE"), WikiFilenameUtil.parseEditions("Stone_(texture)_JE2_BE1.png"));
		assertEquals(List.of("JE2", "BE1"), WikiFilenameUtil.parseTextureTags("Stone (texture) JE2 BE1.png"));
	}

	@Test
	void detectsTextureFiles() {
		assertTrue(WikiFilenameUtil.isTextureFile("Dirt_(texture)_JE1_BE1.png"));
		assertTrue(WikiFilenameUtil.isTextureFile("Grass Block (top_texture) JE1.png"));
		assertTrue(WikiFilenameUtil.isTextureFile("Grass Block (side_texture) JE1 BE1.png"));
	}

	@Test
	void buildsStableIds() {
		assertEquals("stone_texture_je2_be1_png", WikiFilenameUtil.toId("Stone_(texture)_JE2_BE1.png"));
	}

	@Test
	void guessesBlockTargetsFromContext() {
		List<String> targets = WikiFilenameUtil.guessTargets(
				"Oak Planks (texture) JE3 BE1.png",
				WikiFilenameUtil.CrawlContext.BLOCKS
		);
		assertEquals(List.of("minecraft:textures/block/oak_planks.png"), targets);
	}

	@Test
	void guessesTopTextureTarget() {
		List<String> targets = WikiFilenameUtil.guessTargets(
				"Grass Block (top_texture) JE2.png",
				WikiFilenameUtil.CrawlContext.BLOCKS
		);
		assertEquals(List.of("minecraft:textures/block/grass_block_top.png"), targets);
	}

	@Test
	void parsesVersionDisplayFromTableCells() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseVersionsFromWikitext(
				"{| class=\"wikitable\"\n"
						+ "|-\n"
						+ "| [[Java Edition 1.14|1.14]] || [[File:Stone (texture) JE2 BE1.png|thumb]]\n"
						+ "|}"
		).get("stone_(texture)_je2_be1.png");
		assertEquals("1.14", info.javaVersion());
		assertEquals("1.14", info.displayVersion());
	}

	@Test
	void keepsBetaPrefixFromPipedJavaEditionLink() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseVersionsFromWikitext(
				"{| class=\"wikitable\"\n"
						+ "|-\n"
						+ "| [[Java Edition Beta 1.7|1.7]] || [[File:Cobblestone (texture) JE3 BE2.png|thumb]]\n"
						+ "|}"
		).get("cobblestone_(texture)_je3_be2.png");
		assertEquals("Beta 1.7", info.javaVersion());
		assertEquals("Beta 1.7", info.displayVersion());
	}

	@Test
	void parsesVersionFromHistoryLineSnapshot() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine||1.14|dev=18w43a|[[File:Stone (texture) JE4.png|32px]] Changed stone.}}",
				WikiTableParser.WikiEdition.JAVA
		).get("stone_(texture)_je4.png");
		assertEquals("18w43a", info.javaVersion());
		assertEquals("18w43a", info.displayVersion());
	}

	@Test
	void parsesVersionFromHistoryLinePreRelease() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|||dev=Beta 1.9 Prerelease 5|[[File:Stone (texture) JE3 BE2.png|32px]] Changed stone.}}",
				WikiTableParser.WikiEdition.JAVA
		).get("stone_(texture)_je3_be2.png");
		assertEquals("Beta 1.9 Prerelease 5", info.javaVersion());
	}

	@Test
	void mergesJavaAndBedrockVersions() {
		Map<String, WikiTableParser.VersionInfo> javaVersions = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine||1.14|dev=18w43a|[[File:Stone (texture) JE4.png|32px]] Changed stone.}}",
				WikiTableParser.WikiEdition.JAVA
		);
		Map<String, WikiTableParser.VersionInfo> bedrockVersions = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine||1.10.0|dev=beta 1.10.0.3|[[File:Stone (texture) JE4.png|32px]] Changed stone.}}",
				WikiTableParser.WikiEdition.BEDROCK
		);
		WikiTableParser.VersionInfo merged = WikiTableParser.VersionInfo.merge(
				javaVersions.get("stone_(texture)_je4.png"),
				bedrockVersions.get("stone_(texture)_je4.png")
		);
		assertEquals("18w43a", merged.javaVersion());
		assertEquals("1.10.0.3", merged.bedrockVersion());
		assertEquals("18w43a / 1.10.0.3", merged.displayVersion());
	}

	@Test
	void resolvesDevLabelToRowVersionUnderAlpha() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|java alpha}}\n"
						+ "{{HistoryLine||v1.2.0|dev=preview|[[File:Pumpkin (side texture) JE1 BE1.png|32px]]}}",
				WikiTableParser.WikiEdition.JAVA
		).get("pumpkin_(side_texture)_je1_be1.png");
		assertEquals("v1.2.0", info.javaVersion());
	}

	@Test
	void resolvesDevLabelToRowVersionWithBetaEraPrefix() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|java beta}}\n"
						+ "{{HistoryLine||1.8|dev=Pre-release|[[File:Iron Bars (texture) JE1 BE1.png|32px]]}}",
				WikiTableParser.WikiEdition.JAVA
		).get("iron_bars_(texture)_je1_be1.png");
		assertEquals("Beta 1.8", info.javaVersion());
	}

	@Test
	void prefixesBetaEraToRowVersionWithoutDev() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|java beta}}\n"
						+ "{{HistoryLine||1.2|[[File:Sugar JE1 BE1.png|32px]]}}",
				WikiTableParser.WikiEdition.JAVA
		).get("sugar_je1_be1.png");
		assertEquals("Beta 1.2", info.javaVersion());
	}

	@Test
	void resolvesReleaseDevLabelToRowVersion() {
		WikiTableParser.VersionInfo info = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|java}}\n"
						+ "{{HistoryLine||1.2.4|dev=release|[[File:Birch Planks (texture) JE1 BE1.png|32px]]}}",
				WikiTableParser.WikiEdition.JAVA
		).get("birch_planks_(texture)_je1_be1.png");
		assertEquals("1.2.4", info.javaVersion());
	}

	@Test
	void carriesRowVersionAcrossRows() {
		Map<String, WikiTableParser.VersionInfo> versions = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|java}}\n"
						+ "{{HistoryLine||1.16|dev=20w06a|[[File:A.png|32px]]}}\n"
						+ "{{HistoryLine|||dev=pre3|[[File:B.png|32px]]}}",
				WikiTableParser.WikiEdition.JAVA
		);
		assertEquals("20w06a", versions.get("a.png").javaVersion());
		assertEquals("1.16", versions.get("b.png").javaVersion());
	}

	@Test
	void headerResetsCarriedRowVersion() {
		Map<String, WikiTableParser.VersionInfo> versions = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|java}}\n"
						+ "{{HistoryLine||1.16|[[File:A.png|32px]]}}\n"
						+ "{{HistoryLine|java beta}}\n"
						+ "{{HistoryLine|||dev=snap3|[[File:B.png|32px]]}}",
				WikiTableParser.WikiEdition.JAVA
		);
		assertEquals("snap3", versions.get("b.png").javaVersion());
	}

	@Test
	void keepsDevLabelsMatchingVersionOrLeadingDigit() {
		Map<String, WikiTableParser.VersionInfo> versions = WikiTableParser.parseHistoryLinesFromWikitext(
				"{{HistoryLine|java}}\n"
						+ "{{HistoryLine||1.0.0|dev=Beta 1.9 Prerelease 2|[[File:C.png|32px]]}}\n"
						+ "{{HistoryLine|java indev}}\n"
						+ "{{HistoryLine||0.31|dev=20091223-0040|[[File:D.png|32px]]}}",
				WikiTableParser.WikiEdition.JAVA
		);
		assertEquals("Beta 1.9 Prerelease 2", versions.get("c.png").javaVersion());
		assertEquals("20091223-0040", versions.get("d.png").javaVersion());
	}
}

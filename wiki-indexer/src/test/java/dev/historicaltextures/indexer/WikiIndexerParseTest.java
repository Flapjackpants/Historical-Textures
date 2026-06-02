package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiIndexerParseTest {
	private static final Pattern EDITION_PATTERN = Pattern.compile("_(JE|BE)(\\d+)");

	@Test
	void parsesEditionTagsFromFileName() {
		Matcher matcher = EDITION_PATTERN.matcher("Stone_(texture)_JE2_BE1.png");
		List<String> editions = new java.util.ArrayList<>();
		while (matcher.find()) {
			editions.add(matcher.group(1));
		}
		assertEquals(List.of("JE", "BE"), editions);
	}

	@Test
	void detectsTextureFiles() {
		assertTrue("Dirt_(texture)_JE1_BE1.png".toLowerCase().endsWith(".png"));
		assertTrue("Dirt_(texture)_JE1_BE1.png".contains("(texture)"));
	}

	@Test
	void buildsStableIds() {
		String id = "Stone_(texture)_JE2_BE1.png".replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase();
		assertEquals("stone_texture_je2_be1_png", id);
	}
}

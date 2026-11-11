package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiSoundTableParserTest {
	private static final String COW_PAGE = "{{SoundTable\n"
			+ "| type = java\n"
			+ "| {{SoundLine\n"
			+ "|sound=Cow milk1.ogg\n"
			+ "|sound2=Cow milk2.ogg\n"
			+ "|subtitle=Cow gets milked\n"
			+ "|id=entity.cow.milk\n"
			+ "|volume=1.0}}\n"
			+ "| {{SoundLine\n"
			+ "|rowspan=2\n"
			+ "| sound =Cow hurt1.ogg\n"
			+ "|sound2=Cow hurt2.ogg\n"
			+ "|subtitle=Cow dies\n"
			+ "|id=entity.cow.death}}\n"
			+ "| {{SoundLine\n"
			+ "|subtitle=Cow hurts{{until|java 26.4}}<br>Cow is hurt{{upcoming|java 26.4}}\n"
			+ "|id=entity.cow.hurt}}\n"
			+ "}}\n"
			+ "{{SoundTable\n"
			+ "| type = bedrock\n"
			+ "| {{SoundLine\n"
			+ "|sound=Cow idle1.ogg\n"
			+ "|id=mob.cow.say\n"
			+ "|pitch=0.8-1.2}}\n"
			+ "}}\n"
			+ "{{SoundTable\n"
			+ "| {{SoundLine\n"
			+ "|sound=Ignored.ogg\n"
			+ "|id=x.y}}\n"
			+ "}}";

	@Test
	void parsesJavaAndBedrockSoundTables() {
		assertEquals(
				List.of(
						"Cow milk1.ogg|entity.cow.milk|JAVA",
						"Cow milk2.ogg|entity.cow.milk|JAVA",
						"Cow hurt1.ogg|entity.cow.death|JAVA",
						"Cow hurt2.ogg|entity.cow.death|JAVA",
						"Cow hurt1.ogg|entity.cow.hurt|JAVA",
						"Cow hurt2.ogg|entity.cow.hurt|JAVA",
						"Cow idle1.ogg|mob.cow.say|BEDROCK"
				),
				WikiSoundTableParser.parse(COW_PAGE).stream()
						.map(entry -> entry.fileName() + "|" + entry.id() + "|" + entry.edition())
						.toList()
		);
	}

	@Test
	void toleratesSpacesAroundEquals() {
		List<WikiSoundTableParser.Entry> entries = WikiSoundTableParser.parse(
				"{{SoundTable\n"
						+ "| type = bedrock\n"
						+ "| {{SoundLine\n"
						+ "| sound =Cow idle1.ogg\n"
						+ "| id = mob.cow.say}}\n"
						+ "}}"
		);
		assertEquals(1, entries.size());
		assertEquals("Cow idle1.ogg", entries.get(0).fileName());
		assertEquals("mob.cow.say", entries.get(0).id());
		assertEquals(WikiSoundTableParser.Edition.BEDROCK, entries.get(0).edition());
	}

	@Test
	void returnsEmptyForMissingInput() {
		assertTrue(WikiSoundTableParser.parse(null).isEmpty());
		assertTrue(WikiSoundTableParser.parse("").isEmpty());
	}
}

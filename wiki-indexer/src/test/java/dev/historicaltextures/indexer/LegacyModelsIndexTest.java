package dev.historicaltextures.indexer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyModelsIndexTest {
	private static final Path ROOT = Path.of("../src/main/resources/assets/historical_textures/legacy_models");
	private static final List<String> DEST_PREFIXES = List.of("models/", "textures/", "optifine/cem/");

	private static final JsonObject INDEX = readIndex();

	private static JsonObject readIndex() {
		Path index = ROOT.resolve("index.json");
		assertTrue(Files.isRegularFile(index), "missing legacy_models/index.json");
		try {
			return JsonParser.parseString(Files.readString(index)).getAsJsonObject();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Test
	void indexIsObjectOfObjectsOfStrings() {
		assertFalse(INDEX.entrySet().isEmpty(), "index.json has no entries");
		for (var target : INDEX.entrySet()) {
			assertFalse(target.getKey().isBlank(), "blank target key");
			JsonElement variants = target.getValue();
			assertTrue(variants.isJsonObject(), "target " + target.getKey() + " is not an object");
			assertFalse(variants.getAsJsonObject().entrySet().isEmpty(), "target " + target.getKey() + " has no variants");
			for (var variant : variants.getAsJsonObject().entrySet()) {
				assertFalse(variant.getKey().isBlank(), "blank variant id under " + target.getKey());
				JsonElement files = variant.getValue();
				assertTrue(files.isJsonObject(), "variant " + variant.getKey() + " is not an object");
				assertFalse(files.getAsJsonObject().entrySet().isEmpty(), "variant " + variant.getKey() + " has no files");
				for (var file : files.getAsJsonObject().entrySet()) {
					JsonElement source = file.getValue();
					assertTrue(source.isJsonPrimitive() && source.getAsJsonPrimitive().isString(),
							"source for " + file.getKey() + " is not a string");
				}
			}
		}
	}

	@Test
	void sourcesExistAndJsonSourcesParse() {
		for (Entry entry : entries()) {
			Path source = ROOT.resolve(entry.source());
			assertTrue(Files.isRegularFile(source), "missing legacy model source " + entry.source());
			if (entry.source().endsWith(".json") || entry.source().endsWith(".jem")) {
				String text;
				try {
					text = Files.readString(source);
				} catch (IOException exception) {
					throw new UncheckedIOException(exception);
				}
				JsonElement parsed = JsonParser.parseString(text);
				assertTrue(parsed.isJsonObject(), entry.source() + " is not a JSON object");
			} else if (entry.source().endsWith(".png")) {
				try {
					assertTrue(ImageIO.read(source.toFile()) != null, entry.source() + " is not a readable PNG");
				} catch (IOException exception) {
					throw new UncheckedIOException(exception);
				}
			}
		}
	}

	@Test
	void destinationsAreSafeResourcePaths() {
		for (Entry entry : entries()) {
			String dest = entry.dest();
			assertFalse(dest.isBlank(), "blank destination for " + entry.variantId());
			assertFalse(dest.contains(".."), "unsafe destination " + dest);
			assertFalse(dest.contains("\\"), "unsafe destination " + dest);
			assertFalse(dest.startsWith("/"), "unsafe destination " + dest);
			assertTrue(DEST_PREFIXES.stream().anyMatch(dest::startsWith),
					"destination " + dest + " is outside models/, textures/ or optifine/cem/");
		}
	}

	@Test
	void entriesMatchCatalogVariantsAndTargets() {
		Path catalogPath = Path.of("build/catalog/catalog.json");
		Assumptions.assumeTrue(Files.isRegularFile(catalogPath), "wiki-indexer/build/catalog/catalog.json is not present");
		JsonObject catalog;
		try {
			catalog = JsonParser.parseString(Files.readString(catalogPath)).getAsJsonObject();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		for (Entry entry : entries()) {
			JsonObject variant = null;
			for (JsonElement element : catalog.getAsJsonArray("variants")) {
				JsonObject candidate = element.getAsJsonObject();
				if (candidate.get("id").getAsString().equals(entry.variantId())) {
					variant = candidate;
					break;
				}
			}
			assertTrue(variant != null, "catalog has no variant " + entry.variantId());
			boolean targetsTarget = false;
			for (JsonElement target : variant.getAsJsonArray("targets")) {
				if (target.getAsString().equals(entry.target())) {
					targetsTarget = true;
					break;
				}
			}
			assertTrue(targetsTarget, "variant " + entry.variantId() + " does not target " + entry.target());
		}
	}

	private static List<Entry> entries() {
		List<Entry> result = new ArrayList<>();
		for (var target : INDEX.entrySet()) {
			for (var variant : target.getValue().getAsJsonObject().entrySet()) {
				for (var file : variant.getValue().getAsJsonObject().entrySet()) {
					result.add(new Entry(target.getKey(), variant.getKey(), file.getKey(),
							file.getValue().getAsString()));
				}
			}
		}
		return result;
	}

	private record Entry(String target, String variantId, String dest, String source) {
	}
}

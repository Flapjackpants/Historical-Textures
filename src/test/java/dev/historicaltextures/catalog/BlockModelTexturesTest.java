package dev.historicaltextures.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockModelTexturesTest {
	private static JsonObject json(String source) {
		return JsonParser.parseString(source).getAsJsonObject();
	}

	private static Function<String, Optional<JsonObject>> loader(Map<String, String> files) {
		return path -> Optional.ofNullable(files.get(path)).map(BlockModelTexturesTest::json);
	}

	private static Map<String, String> files(String... entries) {
		Map<String, String> files = new HashMap<>();
		for (int i = 0; i < entries.length; i += 2) {
			files.put(entries[i], entries[i + 1]);
		}
		return files;
	}

	@Test
	void collectsBlockTexturesThroughVariantModelsAndParents() {
		Map<String, String> files = files(
				"blockstates/furnace.json", """
						{"variants":{
							"facing=north,lit=false":{"model":"minecraft:block/furnace"},
							"facing=north,lit=true":{"model":"minecraft:block/furnace_on"}
						}}""",
				"models/block/furnace.json", """
						{"parent":"minecraft:block/orientable","textures":{
							"top":"minecraft:block/furnace_top",
							"front":"#front",
							"side":"block/furnace_side"
						}}""",
				"models/block/furnace_on.json", """
						{"parent":"minecraft:block/furnace","textures":{
							"front":"minecraft:block/furnace_front_on"
						}}""",
				"models/block/orientable.json", """
						{"textures":{"top":"#top","front":"#front","side":"#side"}}"""
		);

		Set<String> textures = BlockModelTextures.collect("furnace", loader(files));

		assertEquals(Set.of(
				"minecraft:textures/block/furnace_front_on.png",
				"minecraft:textures/block/furnace_side.png",
				"minecraft:textures/block/furnace_top.png"
		), textures);
	}

	@Test
	void collectsFromMultipartApplyObjectsAndArrays() {
		Map<String, String> files = files(
				"blockstates/weird.json", """
						{"multipart":[
							{"when":{"facing":"north"},"apply":{"model":"block/multi_a"}},
							{"apply":[{"model":"block/multi_b"},{"model":"minecraft:block/multi_c"}]}
						]}""",
				"models/block/multi_a.json", """
						{"textures":{"tex":"block/tex_a"}}""",
				"models/block/multi_b.json", """
						{"textures":{"tex":"block/tex_b"}}""",
				"models/block/multi_c.json", """
						{"textures":{"tex":"block/tex_c"}}"""
		);

		Set<String> textures = BlockModelTextures.collect("weird", loader(files));

		assertEquals(Set.of(
				"minecraft:textures/block/tex_a.png",
				"minecraft:textures/block/tex_b.png",
				"minecraft:textures/block/tex_c.png"
		), textures);
	}

	@Test
	void collectsFromArrayVariantValues() {
		Map<String, String> files = files(
				"blockstates/arrayed.json", """
						{"variants":{"a":[{"model":"block/v1"},{"model":"block/v2"}]}}""",
				"models/block/v1.json", """
						{"textures":{"tex":"block/tex_one"}}""",
				"models/block/v2.json", """
						{"textures":{"tex":"block/tex_two"}}"""
		);

		Set<String> textures = BlockModelTextures.collect("arrayed", loader(files));

		assertEquals(Set.of(
				"minecraft:textures/block/tex_one.png",
				"minecraft:textures/block/tex_two.png"
		), textures);
	}

	@Test
	void returnsEmptyWhenBlockstateIsMissing() {
		assertTrue(BlockModelTextures.collect("missing", loader(Map.of())).isEmpty());
	}

	@Test
	void returnsEmptyWhenModelsAreMissing() {
		Map<String, String> files = files(
				"blockstates/ghost.json", """
						{"variants":{"a":{"model":"block/ghost"}}}"""
		);
		assertTrue(BlockModelTextures.collect("ghost", loader(files)).isEmpty());
	}

	@Test
	void ignoresNonBlockAndNonMinecraftTextures() {
		Map<String, String> files = files(
				"blockstates/chesty.json", """
						{"variants":{"a":{"model":"block/chesty"}}}""",
				"models/block/chesty.json", """
						{"textures":{
							"entity":"entity/chest/normal",
							"item":"item/chest",
							"other":"otherns:block/evil",
							"side":"block/chesty_side"
						}}"""
		);

		Set<String> textures = BlockModelTextures.collect("chesty", loader(files));

		assertEquals(Set.of("minecraft:textures/block/chesty_side.png"), textures);
	}

	@Test
	void parentCycleTerminates() {
		Map<String, String> files = files(
				"blockstates/loopy.json", """
						{"variants":{"a":{"model":"block/loop_a"}}}""",
				"models/block/loop_a.json", """
						{"parent":"block/loop_b","textures":{"tex":"block/loop_a_tex"}}""",
				"models/block/loop_b.json", """
						{"parent":"minecraft:block/loop_a","textures":{"tex":"block/loop_b_tex"}}"""
		);

		Set<String> textures = BlockModelTextures.collect("loopy", loader(files));

		assertEquals(Set.of(
				"minecraft:textures/block/loop_a_tex.png",
				"minecraft:textures/block/loop_b_tex.png"
		), textures);
	}

	@Test
	void skipsBuiltinParents() {
		Map<String, String> files = files(
				"blockstates/gen.json", """
						{"variants":{"a":{"model":"block/generated_thing"}}}""",
				"models/block/generated_thing.json", """
						{"parent":"builtin/generated","textures":{"layer0":"block/gen_tex"}}"""
		);

		Set<String> textures = BlockModelTextures.collect("gen", loader(files));

		assertEquals(Set.of("minecraft:textures/block/gen_tex.png"), textures);
	}
}

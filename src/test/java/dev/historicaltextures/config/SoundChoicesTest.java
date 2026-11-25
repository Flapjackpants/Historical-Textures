package dev.historicaltextures.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundChoicesTest {
	@Test
	void fromJsonReadsLegacyStringAsSingletonList() {
		Map<String, JsonElement> raw = new LinkedHashMap<>();
		raw.put("entity.cow.ambient", new JsonPrimitive("cow_old"));

		assertEquals(Map.of("entity.cow.ambient", List.of("cow_old")), SoundChoices.fromJson(raw));
	}

	@Test
	void fromJsonReadsArraysAndSkipsOtherShapes() {
		JsonArray array = new JsonArray();
		array.add("a");
		array.add(3);
		array.add("a");
		array.add("b");

		Map<String, JsonElement> raw = new LinkedHashMap<>();
		raw.put("array", array);
		raw.put("empty", new JsonArray());
		raw.put("null", JsonNull.INSTANCE);
		raw.put("object", new JsonObject());
		raw.put("number", new JsonPrimitive(7));

		assertEquals(Map.of("array", List.of("a", "b")), SoundChoices.fromJson(raw));
	}

	@Test
	void fromJsonNullGivesEmptyMap() {
		assertEquals(Map.of(), SoundChoices.fromJson(null));
	}

	@Test
	void copyOfDropsBlankIdsAndEmptyListsAndCopiesDeeply() {
		Map<String, List<String>> source = new HashMap<>();
		source.put(null, List.of("a"));
		source.put("  ", List.of("b"));
		source.put("event", new ArrayList<>(List.of("a", " ", "a", "b")));
		source.put("empty", List.of());

		Map<String, List<String>> copy = SoundChoices.copyOf(source);

		assertEquals(Map.of("event", List.of("a", "b")), copy);
		assertThrows(UnsupportedOperationException.class, () -> copy.put("x", List.of("y")));
		assertThrows(UnsupportedOperationException.class, () -> copy.get("event").add("c"));

		source.get("event").add("late");
		assertEquals(List.of("a", "b"), copy.get("event"));
	}

	@Test
	void copyOfNullGivesEmptyMap() {
		assertEquals(Map.of(), SoundChoices.copyOf(null));
	}

	@Test
	void toJsonAlwaysWritesArrays() {
		Map<String, JsonElement> json = SoundChoices.toJson(Map.of("event", List.of("only")));

		JsonElement value = json.get("event");
		assertTrue(value.isJsonArray());
		assertEquals(1, value.getAsJsonArray().size());
		assertEquals("only", value.getAsJsonArray().get(0).getAsString());
	}

	@Test
	void toJsonNullGivesEmptyMap() {
		assertEquals(Map.of(), SoundChoices.toJson(null));
	}

	@Test
	void toggleAddsThenRemoves() {
		List<String> added = SoundChoices.toggle(List.of(), "a");
		assertEquals(List.of("a"), added);

		List<String> removed = SoundChoices.toggle(added, "a");
		assertEquals(List.of(), removed);
		assertThrows(UnsupportedOperationException.class, () -> added.add("b"));
	}

	@Test
	void toggleAllAddsEveryMemberWhenNoneSelected() {
		assertEquals(List.of("a", "b"), SoundChoices.toggleAll(List.of(), List.of("a", "b")));
	}

	@Test
	void toggleAllAddsOnlyMissingMembersInMemberOrder() {
		assertEquals(List.of("b", "x", "a", "c"), SoundChoices.toggleAll(List.of("b", "x"), List.of("a", "b", "c")));
	}

	@Test
	void toggleAllRemovesEveryMemberWhenAllSelected() {
		assertEquals(List.of("x"), SoundChoices.toggleAll(List.of("x", "a", "b", "c"), List.of("a", "b", "c")));
	}

	@Test
	void toggleAllWithNoMembersKeepsCurrent() {
		List<String> current = SoundChoices.toggleAll(List.of("a"), List.of());

		assertEquals(List.of("a"), current);
		assertThrows(UnsupportedOperationException.class, () -> current.add("b"));
	}
}

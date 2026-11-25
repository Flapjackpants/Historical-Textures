package dev.historicaltextures.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SoundChoices {
	private SoundChoices() {
	}

	public static Map<String, List<String>> copyOf(Map<String, ? extends Collection<String>> choices) {
		if (choices == null || choices.isEmpty()) {
			return Map.of();
		}
		Map<String, List<String>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, ? extends Collection<String>> entry : choices.entrySet()) {
			String event = entry.getKey();
			if (event == null || event.isBlank()) {
				continue;
			}
			List<String> ids = distinct(entry.getValue());
			if (!ids.isEmpty()) {
				copy.put(event, ids);
			}
		}
		return Collections.unmodifiableMap(copy);
	}

	public static Map<String, List<String>> fromJson(Map<String, JsonElement> raw) {
		if (raw == null || raw.isEmpty()) {
			return Map.of();
		}
		Map<String, List<String>> choices = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
			JsonElement value = entry.getValue();
			if (value == null) {
				continue;
			}
			if (isString(value)) {
				choices.put(entry.getKey(), List.of(value.getAsString()));
			} else if (value.isJsonArray()) {
				List<String> ids = new ArrayList<>();
				for (JsonElement element : value.getAsJsonArray()) {
					if (isString(element)) {
						ids.add(element.getAsString());
					}
				}
				choices.put(entry.getKey(), ids);
			}
		}
		return copyOf(choices);
	}

	public static Map<String, JsonElement> toJson(Map<String, List<String>> choices) {
		if (choices == null || choices.isEmpty()) {
			return Map.of();
		}
		Map<String, JsonElement> json = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : choices.entrySet()) {
			JsonArray ids = new JsonArray();
			if (entry.getValue() != null) {
				for (String id : entry.getValue()) {
					ids.add(id);
				}
			}
			json.put(entry.getKey(), ids);
		}
		return json;
	}

	public static List<String> toggle(List<String> current, String id) {
		List<String> result = new ArrayList<>(current == null ? List.of() : current);
		if (!result.remove(id)) {
			result.add(id);
		}
		return List.copyOf(result);
	}

	public static List<String> toggleAll(List<String> current, List<String> members) {
		List<String> base = current == null ? List.of() : current;
		if (members == null || members.isEmpty()) {
			return List.copyOf(base);
		}
		List<String> result = new ArrayList<>(base);
		if (base.containsAll(members)) {
			result.removeAll(members);
			return List.copyOf(result);
		}
		for (String member : members) {
			if (!result.contains(member)) {
				result.add(member);
			}
		}
		return List.copyOf(result);
	}

	static List<String> distinct(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		List<String> result = new ArrayList<>(ids.size());
		for (String id : ids) {
			if (id != null && !id.isBlank() && !result.contains(id)) {
				result.add(id);
			}
		}
		return List.copyOf(result);
	}

	private static boolean isString(JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
	}
}

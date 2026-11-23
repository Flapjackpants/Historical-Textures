package dev.historicaltextures.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public final class BlockModelTextures {
	private static final int MAX_PARENT_DEPTH = 16;

	private BlockModelTextures() {
	}

	public static Set<String> collect(String blockPath, Function<String, Optional<JsonObject>> loader) {
		Set<String> textures = new TreeSet<>();
		Optional<JsonObject> blockstate = loader.apply("blockstates/" + blockPath + ".json");
		if (blockstate.isEmpty()) {
			return textures;
		}
		JsonObject root = blockstate.get();
		Set<String> visited = new TreeSet<>();
		collectVariantModels(root.get("variants"), loader, textures, visited);
		collectMultipartModels(root.get("multipart"), loader, textures, visited);
		return textures;
	}

	private static void collectVariantModels(
			JsonElement variants,
			Function<String, Optional<JsonObject>> loader,
			Set<String> textures,
			Set<String> visited
	) {
		if (variants == null || !variants.isJsonObject()) {
			return;
		}
		for (JsonElement value : variants.getAsJsonObject().asMap().values()) {
			collectModelEntries(value, loader, textures, visited);
		}
	}

	private static void collectMultipartModels(
			JsonElement multipart,
			Function<String, Optional<JsonObject>> loader,
			Set<String> textures,
			Set<String> visited
	) {
		if (multipart == null || !multipart.isJsonArray()) {
			return;
		}
		for (JsonElement entry : multipart.getAsJsonArray()) {
			if (entry == null || !entry.isJsonObject()) {
				continue;
			}
			collectModelEntries(entry.getAsJsonObject().get("apply"), loader, textures, visited);
		}
	}

	private static void collectModelEntries(
			JsonElement value,
			Function<String, Optional<JsonObject>> loader,
			Set<String> textures,
			Set<String> visited
	) {
		if (value == null) {
			return;
		}
		if (value.isJsonArray()) {
			for (JsonElement element : value.getAsJsonArray()) {
				collectModelEntries(element, loader, textures, visited);
			}
			return;
		}
		if (!value.isJsonObject()) {
			return;
		}
		JsonElement model = value.getAsJsonObject().get("model");
		if (model != null && model.isJsonPrimitive() && model.getAsJsonPrimitive().isString()) {
			collectModelTextures(model.getAsString(), loader, textures, visited, 0);
		}
	}

	private static void collectModelTextures(
			String modelId,
			Function<String, Optional<JsonObject>> loader,
			Set<String> textures,
			Set<String> visited,
			int depth
	) {
		String resourcePath = modelResourcePath(modelId);
		if (resourcePath == null || depth >= MAX_PARENT_DEPTH || !visited.add(resourcePath)) {
			return;
		}
		Optional<JsonObject> loaded = loader.apply(resourcePath);
		if (loaded.isEmpty()) {
			return;
		}
		JsonObject model = loaded.get();
		JsonElement texturesElement = model.get("textures");
		if (texturesElement != null && texturesElement.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : texturesElement.getAsJsonObject().entrySet()) {
				JsonElement texture = entry.getValue();
				if (texture != null && texture.isJsonPrimitive() && texture.getAsJsonPrimitive().isString()) {
					addTexture(texture.getAsString(), textures);
				}
			}
		}
		JsonElement parent = model.get("parent");
		if (parent != null && parent.isJsonPrimitive() && parent.getAsJsonPrimitive().isString()) {
			collectModelTextures(parent.getAsString(), loader, textures, visited, depth + 1);
		}
	}

	private static String modelResourcePath(String modelId) {
		if (modelId == null) {
			return null;
		}
		String path = modelId;
		int colon = path.indexOf(':');
		if (colon >= 0) {
			if (!path.substring(0, colon).equals("minecraft")) {
				return null;
			}
			path = path.substring(colon + 1);
		}
		if (path.startsWith("builtin/")) {
			return null;
		}
		return "models/" + path + ".json";
	}

	private static void addTexture(String textureId, Set<String> textures) {
		if (textureId == null || textureId.startsWith("#")) {
			return;
		}
		String namespace = "minecraft";
		String path = textureId;
		int colon = textureId.indexOf(':');
		if (colon >= 0) {
			namespace = textureId.substring(0, colon);
			path = textureId.substring(colon + 1);
		}
		if (!namespace.equals("minecraft") || !path.startsWith("block/")) {
			return;
		}
		textures.add("minecraft:textures/" + path + ".png");
	}
}

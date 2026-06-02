package dev.historicaltextures.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.GamePaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = TypeToken.getParameterized(Map.class, String.class, String.class).getType();

	private static ModConfig instance;
	private final Path configPath;

	private Map<String, String> textureChoices = new HashMap<>();
	private Map<String, String> soundChoices = new HashMap<>();

	public ModConfig(Path configPath) {
		this.configPath = configPath;
	}

	public static ModConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	private static ModConfig load() {
		Path path = GamePaths.configFile();
		ModConfig config = new ModConfig(path);
		if (!Files.isRegularFile(path)) {
			return config;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			ConfigData data = GSON.fromJson(reader, ConfigData.class);
			if (data != null) {
				if (data.textureChoices != null) {
					config.textureChoices = new HashMap<>(data.textureChoices);
				}
				if (data.soundChoices != null) {
					config.soundChoices = new HashMap<>(data.soundChoices);
				}
			}
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.warn("Could not read config, using defaults", exception);
		}
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(configPath.getParent());
			ConfigData data = new ConfigData();
			data.textureChoices = new HashMap<>(textureChoices);
			data.soundChoices = new HashMap<>(soundChoices);
			try (Writer writer = Files.newBufferedWriter(configPath)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.error("Failed to save config", exception);
		}
	}

	public String getTextureChoice(String target) {
		return textureChoices.get(target);
	}

	public void setTextureChoice(String target, String variantId) {
		if (variantId == null || variantId.isEmpty()) {
			textureChoices.remove(target);
		} else {
			textureChoices.put(target, variantId);
		}
	}

	public String getSoundChoice(String event) {
		return soundChoices.get(event);
	}

	public void setSoundChoice(String event, String variantId) {
		if (variantId == null || variantId.isEmpty()) {
			soundChoices.remove(event);
		} else {
			soundChoices.put(event, variantId);
		}
	}

	public Map<String, String> textureChoices() {
		return Map.copyOf(textureChoices);
	}

	public Map<String, String> soundChoices() {
		return Map.copyOf(soundChoices);
	}

	public void clearAll() {
		textureChoices.clear();
		soundChoices.clear();
	}

	private static final class ConfigData {
		Map<String, String> textureChoices;
		Map<String, String> soundChoices;
	}
}

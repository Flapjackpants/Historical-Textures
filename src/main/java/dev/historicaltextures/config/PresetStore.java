package dev.historicaltextures.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import dev.historicaltextures.GamePaths;
import dev.historicaltextures.HistoricalTextures;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PresetStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static PresetStore instance;

	private final Path file;
	private final List<Preset> presets = new ArrayList<>();
	private boolean unreadableFile;

	public PresetStore(Path file) {
		this.file = file;
		load();
	}

	public static PresetStore get() {
		if (instance == null) {
			instance = new PresetStore(GamePaths.presetsFile());
		}
		return instance;
	}

	public List<Preset> userPresets() {
		return presets.stream()
				.filter(preset -> !preset.builtIn())
				.sorted(Comparator.comparing(Preset::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public void save(Preset preset) {
		presets.removeIf(existing -> existing.name().equalsIgnoreCase(preset.name()));
		presets.add(preset);
		persist();
	}

	public boolean delete(String name) {
		boolean removed = presets.removeIf(preset -> preset.name().equalsIgnoreCase(name));
		if (removed) {
			persist();
		}
		return removed;
	}

	public boolean exists(String name) {
		return presets.stream().anyMatch(preset -> preset.name().equalsIgnoreCase(name));
	}

	private void load() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			FileData data = GSON.fromJson(reader, FileData.class);
			if (data == null || data.presets == null) {
				throw new IOException("empty presets file");
			}
			for (PresetData entry : data.presets) {
				if (entry == null || entry.name == null || entry.name.isBlank()) {
					continue;
				}
				presets.add(new Preset(
						entry.name,
						entry.textureChoices,
						SoundChoices.fromJson(entry.soundChoices),
						entry.hideVillagerHood == null || entry.hideVillagerHood,
						false
				));
			}
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Could not read presets file {}, starting empty", file, exception);
			presets.clear();
			unreadableFile = true;
		}
	}

	private void persist() {
		try {
			Files.createDirectories(file.getParent());
			if (unreadableFile && Files.isRegularFile(file)) {
				Path backup = file.resolveSibling(file.getFileName() + ".bak");
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
				unreadableFile = false;
			}
			FileData data = new FileData();
			data.presets = presets.stream().map(PresetStore::toData).toList();
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.error("Failed to save presets file", exception);
		}
	}

	private static PresetData toData(Preset preset) {
		PresetData data = new PresetData();
		data.name = preset.name();
		data.textureChoices = preset.textureChoices();
		data.soundChoices = SoundChoices.toJson(preset.soundChoices());
		data.hideVillagerHood = preset.hideVillagerHood();
		return data;
	}

	private static final class FileData {
		List<PresetData> presets;
	}

	private static final class PresetData {
		String name;
		Map<String, String> textureChoices;
		Map<String, JsonElement> soundChoices;
		Boolean hideVillagerHood;
	}
}

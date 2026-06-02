package dev.historicaltextures;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class GamePaths {
	private GamePaths() {
	}

	public static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("historical_textures.json");
	}

	public static Path overlayDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve("historical_textures").resolve("overlay");
	}
}

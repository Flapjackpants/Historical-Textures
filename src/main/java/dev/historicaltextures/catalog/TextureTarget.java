package dev.historicaltextures.catalog;

import net.minecraft.resources.Identifier;

public record TextureTarget(
		String resourcePath,
		Identifier registryId,
		TextureTargetKind kind,
		String displayName
) {
	public String configKey() {
		return resourcePath;
	}
}

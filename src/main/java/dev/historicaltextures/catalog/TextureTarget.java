package dev.historicaltextures.catalog;

import net.minecraft.resources.Identifier;

import java.util.List;

public record TextureTarget(
		String resourcePath,
		Identifier registryId,
		TextureTargetKind kind,
		String displayName,
		List<String> members
) {
	public TextureTarget {
		members = members == null ? List.of() : List.copyOf(members);
		if (!members.isEmpty()) {
			resourcePath = representativeResourcePath(members);
		}
	}

	public TextureTarget(String resourcePath, Identifier registryId, TextureTargetKind kind, String displayName) {
		this(resourcePath, registryId, kind, displayName, List.of());
	}

	public boolean isGroup() {
		return !members.isEmpty();
	}

	public String configKey() {
		return isGroup() ? "group:" + registryId : resourcePath;
	}

	private static String representativeResourcePath(List<String> members) {
		String fallback = members.get(0);
		String front = null;
		for (String member : members) {
			String fileName = member.substring(member.lastIndexOf('/') + 1);
			if (fileName.contains("_side")) {
				return member;
			}
			if (front == null && fileName.contains("_front")) {
				front = member;
			}
		}
		return front != null ? front : fallback;
	}
}

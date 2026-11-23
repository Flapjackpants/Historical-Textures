package dev.historicaltextures.config;

import dev.historicaltextures.catalog.ReleaseTimeline;
import dev.historicaltextures.catalog.TextureVariant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

public final class TextureGroups {
	private static final String BLOCK_TEXTURE_PREFIX = "minecraft:textures/block/";
	private static final String TEXTURE_SUFFIX = ".png";

	private TextureGroups() {
	}

	public record GroupVersion(String label, int releaseKey, Map<String, String> choices) {
		public GroupVersion {
			choices = Map.copyOf(choices);
		}
	}

	public static List<String> members(String blockPath, Set<String> textures, Set<String> allBlockPaths) {
		Set<String> result = new TreeSet<>();
		for (String texture : textures) {
			if (!isOtherBlockTexture(texture, blockPath, allBlockPaths)) {
				result.add(texture);
			}
		}
		return List.copyOf(result);
	}

	public static List<GroupVersion> versions(List<String> members, Map<String, List<TextureVariant>> variantsByTarget) {
		List<String> memberList = List.copyOf(members);
		Map<String, List<TextureVariant>> eligibleByMember = new LinkedHashMap<>();
		Set<Integer> keys = new TreeSet<>();
		for (String member : memberList) {
			List<TextureVariant> eligible = new ArrayList<>();
			for (TextureVariant variant : variantsByTarget.getOrDefault(member, List.of())) {
				if (BuiltInPresets.isJavaVariant(variant)
						&& ReleaseTimeline.releaseKey(variant.javaVersion()) >= 0) {
					eligible.add(variant);
					keys.add(ReleaseTimeline.releaseKey(variant.javaVersion()));
				}
			}
			eligibleByMember.put(member, eligible);
		}

		List<GroupVersion> versions = new ArrayList<>();
		Map<String, String> previous = null;
		for (int key : keys) {
			Map<String, String> choices = new TreeMap<>();
			TextureVariant labelVariant = null;
			for (String member : memberList) {
				List<TextureVariant> eligible = eligibleByMember.get(member);
				TextureVariant best = BuiltInPresets.bestTexture(eligible, key);
				if (best != null) {
					choices.put(member, best.id());
				}
				for (TextureVariant variant : eligible) {
					if (ReleaseTimeline.releaseKey(variant.javaVersion()) == key
							&& (labelVariant == null || variant.id().compareTo(labelVariant.id()) < 0)) {
						labelVariant = variant;
					}
				}
			}
			if (previous != null && previous.equals(choices)) {
				continue;
			}
			String label = labelVariant == null ? "" : labelVariant.javaVersion();
			versions.add(new GroupVersion(label, key, choices));
			previous = choices;
		}
		return List.copyOf(versions);
	}

	public static boolean matches(GroupVersion version, List<String> members, Function<String, String> currentChoice) {
		for (String member : members) {
			if (!Objects.equals(currentChoice.apply(member), version.choices().get(member))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isOtherBlockTexture(String texture, String blockPath, Set<String> allBlockPaths) {
		if (texture == null
				|| !texture.startsWith(BLOCK_TEXTURE_PREFIX)
				|| !texture.endsWith(TEXTURE_SUFFIX)) {
			return false;
		}
		String block = texture.substring(
				BLOCK_TEXTURE_PREFIX.length(),
				texture.length() - TEXTURE_SUFFIX.length()
		);
		return !block.equals(blockPath) && allBlockPaths.contains(block);
	}
}

package dev.historicaltextures.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.BlockModelTextures;
import dev.historicaltextures.catalog.LegacyModels;
import dev.historicaltextures.catalog.TextureTarget;
import dev.historicaltextures.catalog.TextureTargetRegistry;
import dev.historicaltextures.catalog.TextureVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the selected variant as a small rotating model inside the preview pane.
 */
final class VariantPreviewRenderer {
	private static final float FIT_FACTOR = 0.88f;
	private static final float HORIZONTAL_ALLOWANCE = 1.1f;
	/**
	 * GuiSkinRenderer translates every model by -1.601 blocks before rendering, so the
	 * wrapper has to move the model back down by the same amount to stay centered.
	 */
	private static final float SKIN_Y_OFFSET_BLOCKS = 1.601f;

	private static final Map<String, ModelLayerLocation> LAYER_INDEX = new HashMap<>();
	private static final Map<String, Optional<ModelPart>> BAKED_MODELS = new HashMap<>();
	private static final Map<String, ModelPart> CUBE_GEOMETRY = new HashMap<>();
	private static final Map<String, ModelPart> PLANE_GEOMETRY = new HashMap<>();
	private static final Map<String, ModelPart> CROSS_GEOMETRY = new HashMap<>();
	private static final Map<String, Shape> BLOCK_SHAPES = new HashMap<>();
	private static final Map<ModelPart, Bounds> BOUNDS = new IdentityHashMap<>();
	private static final Map<String, Optional<ModelPart>> LEGACY_MODELS = new HashMap<>();
	private static final Map<String, Optional<ModelPart>> LEGACY_BABY_MODELS = new HashMap<>();
	private static boolean layersScanned;

	private VariantPreviewRenderer() {
	}

	enum Shape {
		CUBE,
		PLANE,
		CROSS,
		ENTITY,
		FLAT,
		NONE
	}

	static Shape shapeFor(TextureTarget target) {
		if (target == null) {
			return Shape.NONE;
		}
		return switch (target.kind()) {
			case BLOCK -> blockShape(target);
			case ITEM -> Shape.FLAT;
			case ENTITY -> entityModel(target).isPresent() ? Shape.ENTITY : Shape.FLAT;
			case OTHER -> Shape.FLAT;
		};
	}

	private static Shape blockShape(TextureTarget target) {
		Shape cached = BLOCK_SHAPES.get(target.configKey());
		if (cached != null) {
			return cached;
		}
		Shape shape = computeBlockShape(target);
		BLOCK_SHAPES.put(target.configKey(), shape);
		return shape;
	}

	private static Shape computeBlockShape(TextureTarget target) {
		Block block = null;
		String blockPath = null;
		if (target.registryId() != null) {
			block = BuiltInRegistries.BLOCK.getOptional(target.registryId()).orElse(null);
			if (block != null) {
				blockPath = target.registryId().getPath();
			}
		}
		if (blockPath == null || blockPath.isEmpty()) {
			blockPath = stem(target.resourcePath());
		}
		if (!blockPath.isEmpty()
				&& BlockModelTextures.usesCrossModel(blockPath, TextureTargetRegistry::loadJsonResource)) {
			return Shape.CROSS;
		}
		if (block == null) {
			return Shape.CUBE;
		}
		try {
			VoxelShape shape = block.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			return Block.isShapeFullBlock(shape) ? Shape.CUBE : Shape.PLANE;
		} catch (Exception exception) {
			return Shape.CUBE;
		}
	}

	static Optional<ModelPart> entityModel(TextureTarget target) {
		if (target == null) {
			return Optional.empty();
		}
		String stem = stem(target.resourcePath());
		List<String> keys = new ArrayList<>();
		if (!stem.isEmpty()) {
			keys.add(stem);
			String reversed = reversedTokens(stem);
			if (!reversed.isEmpty() && !reversed.equals(stem)) {
				keys.add(reversed);
			}
		}
		if (target.registryId() != null && !target.registryId().getPath().isEmpty()) {
			keys.add(target.registryId().getPath());
		}
		for (String key : keys) {
			Optional<ModelPart> cached = BAKED_MODELS.get(key);
			if (cached != null) {
				if (cached.isPresent()) {
					return cached;
				}
				continue;
			}
			ModelLayerLocation location = layerIndex().get(key);
			if (location == null) {
				continue;
			}
			try {
				ModelPart baked = Minecraft.getInstance().getEntityModels().bakeLayer(location);
				BAKED_MODELS.put(key, Optional.of(baked));
				return Optional.of(baked);
			} catch (Exception exception) {
				BAKED_MODELS.put(key, Optional.empty());
			}
		}
		return Optional.empty();
	}

	static Optional<String> legacyTextureSource(TextureTarget target, TextureVariant variant) {
		if (target == null || variant == null || target.resourcePath() == null) {
			return Optional.empty();
		}
		String path = target.resourcePath();
		int colon = path.indexOf(':');
		String noNamespace = colon >= 0 ? path.substring(colon + 1) : path;
		for (Map.Entry<String, String> file : LegacyModels.filesFor(target.configKey(), variant.id()).entrySet()) {
			String dest = file.getKey();
			if (dest != null && dest.endsWith(".png") && dest.equals(noNamespace)) {
				return Optional.ofNullable(file.getValue());
			}
		}
		return Optional.empty();
	}

	static Optional<ModelPart> legacyModel(TextureTarget target, TextureVariant variant) {
		if (target == null || variant == null) {
			return Optional.empty();
		}
		for (Map.Entry<String, String> file : LegacyModels.filesFor(target.configKey(), variant.id()).entrySet()) {
			String dest = file.getKey();
			if (dest == null || !dest.endsWith(".jem")) {
				continue;
			}
			String source = file.getValue();
			Optional<ModelPart> cached = LEGACY_MODELS.get(source);
			if (cached != null) {
				return cached;
			}
			Optional<ModelPart> parsed = Optional.empty();
			try (InputStream stream = LegacyModels.open(source)) {
				if (stream != null) {
					parsed = Optional.of(parseLegacyModel(stream));
				}
			} catch (Exception exception) {
				HistoricalTextures.LOGGER.warn("Failed to parse legacy model {}", source, exception);
			}
			LEGACY_MODELS.put(source, parsed);
			return parsed;
		}
		return Optional.empty();
	}

	static Optional<ModelPart> legacyBabyModel(String cacheKey, JsonObject jem) {
		if (cacheKey == null || jem == null) {
			return Optional.empty();
		}
		Optional<ModelPart> cached = LEGACY_BABY_MODELS.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		Optional<ModelPart> parsed;
		try {
			parsed = Optional.of(parseLegacyModel(jem));
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Failed to parse legacy baby model {}", cacheKey, exception);
			parsed = Optional.empty();
		}
		LEGACY_BABY_MODELS.put(cacheKey, parsed);
		return parsed;
	}

	private static ModelPart parseLegacyModel(InputStream stream) throws IOException {
		JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				.getAsJsonObject();
		return parseLegacyModel(root);
	}

	static ModelPart parseLegacyModel(JsonObject root) {
		JsonArray textureSize = root.getAsJsonArray("textureSize");
		int texW = textureSize != null && textureSize.size() > 0 ? textureSize.get(0).getAsInt() : 64;
		int texH = textureSize != null && textureSize.size() > 1 ? textureSize.get(1).getAsInt() : 32;
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition rootPart = mesh.getRoot();
		Set<String> usedNames = new HashSet<>();
		Map<List<String>, Float> partScales = new HashMap<>();
		JsonArray models = root.getAsJsonArray("models");
		if (models == null) {
			return LayerDefinition.create(mesh, texW, texH).bakeRoot();
		}
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			String rawName = model.has("id") && !model.get("id").getAsString().isEmpty()
					? model.get("id").getAsString()
					: model.has("part") ? model.get("part").getAsString() : "part";
			String name = uniquePartName(rawName, usedNames);
			float[] translate = floats(model.getAsJsonArray("translate"), 3);
			float[] rotate = floats(model.getAsJsonArray("rotate"), 3);
			// EMF's root part has no vanilla pivot; its frame stays at the origin.
			boolean isRoot = model.has("part") && "root".equals(model.get("part").getAsString());
			float pivotX = isRoot ? 0.0f : translate[0];
			float pivotY = isRoot ? 0.0f : 24.0f + translate[1];
			float pivotZ = isRoot ? 0.0f : -translate[2];
			PartDefinition part = rootPart.addOrReplaceChild(
					name,
					legacyCubes(model, pivotX, pivotY - 24.0f, pivotZ),
					PartPose.offsetAndRotation(
							pivotX,
							pivotY,
							pivotZ,
							(float) Math.toRadians(-rotate[0]),
							(float) Math.toRadians(-rotate[1]),
							(float) Math.toRadians(rotate[2])
					)
			);
			// EMF places a part's custom frame at the vanilla pivot plus its inverted translate.
			addLegacySubmodels(part, model, -translate[0], -translate[1], translate[2], List.of(name), partScales);
			if (model.has("scale")) {
				partScales.put(List.of(name), model.get("scale").getAsFloat());
			}
		}
		ModelPart baked = LayerDefinition.create(mesh, texW, texH).bakeRoot();
		for (Map.Entry<List<String>, Float> entry : partScales.entrySet()) {
			ModelPart child = baked;
			for (String name : entry.getKey()) {
				child = child.getChild(name);
			}
			child.xScale = entry.getValue();
			child.yScale = entry.getValue();
			child.zScale = entry.getValue();
		}
		return baked;
	}

	/**
	 * Adds submodels the way EMF reads them: translate and box minimums inverted on x and y,
	 * relative to the parent's frame.
	 */
	private static void addLegacySubmodels(
			PartDefinition parent,
			JsonObject model,
			float offsetX,
			float offsetY,
			float offsetZ,
			List<String> path,
			Map<List<String>, Float> partScales
	) {
		JsonArray submodels = model.getAsJsonArray("submodels");
		if (submodels == null) {
			return;
		}
		Set<String> usedNames = new HashSet<>();
		for (JsonElement element : submodels) {
			JsonObject submodel = element.getAsJsonObject();
			String name = uniquePartName(submodel.has("id") ? submodel.get("id").getAsString() : "submodel", usedNames);
			float[] translate = floats(submodel.getAsJsonArray("translate"), 3);
			float[] rotate = floats(submodel.getAsJsonArray("rotate"), 3);
			PartDefinition child = parent.addOrReplaceChild(
					name,
					legacyCubes(submodel, 0.0f, 0.0f, 0.0f),
					PartPose.offsetAndRotation(
							offsetX - translate[0],
							offsetY - translate[1],
							offsetZ + translate[2],
							(float) Math.toRadians(-rotate[0]),
							(float) Math.toRadians(-rotate[1]),
							(float) Math.toRadians(rotate[2])
					)
			);
			List<String> childPath = new ArrayList<>(path);
			childPath.add(name);
			if (submodel.has("scale")) {
				partScales.put(childPath, submodel.get("scale").getAsFloat());
			}
			addLegacySubmodels(child, submodel, 0.0f, 0.0f, 0.0f, childPath, partScales);
		}
	}

	private static CubeListBuilder legacyCubes(JsonObject model, float originX, float originY, float originZ) {
		CubeListBuilder cubes = CubeListBuilder.create();
		if (model.has("mirrorTexture") && "u".equals(model.get("mirrorTexture").getAsString())) {
			cubes.mirror();
		}
		JsonArray boxes = model.getAsJsonArray("boxes");
		if (boxes == null) {
			return cubes;
		}
		for (JsonElement boxElement : boxes) {
			JsonObject box = boxElement.getAsJsonObject();
			float[] coordinates = floats(box.getAsJsonArray("coordinates"), 6);
			JsonArray textureOffset = box.getAsJsonArray("textureOffset");
			int u = textureOffset != null && textureOffset.size() > 0 ? textureOffset.get(0).getAsInt() : 0;
			int v = textureOffset != null && textureOffset.size() > 1 ? textureOffset.get(1).getAsInt() : 0;
			float sizeAddX = box.has("sizeAddX") ? box.get("sizeAddX").getAsFloat() : sizeAdd(box, "sizeAdd");
			float sizeAddY = box.has("sizeAddY") ? box.get("sizeAddY").getAsFloat() : sizeAdd(box, "sizeAdd");
			float sizeAddZ = box.has("sizeAddZ") ? box.get("sizeAddZ").getAsFloat() : sizeAdd(box, "sizeAdd");
			float w = coordinates[3];
			float h = coordinates[4];
			float d = coordinates[5];
			cubes.texOffs(u, v).addBox(
					-(coordinates[0] + w) - originX,
					-(coordinates[1] + h) - originY,
					coordinates[2] - originZ,
					w,
					h,
					d,
					new CubeDeformation(sizeAddX, sizeAddY, sizeAddZ)
			);
		}
		return cubes;
	}

	private static float sizeAdd(JsonObject box, String key) {
		return box.has(key) ? box.get(key).getAsFloat() : 0.0f;
	}

	private static float[] floats(JsonArray array, int count) {
		float[] result = new float[count];
		if (array != null) {
			for (int index = 0; index < Math.min(count, array.size()); index++) {
				result[index] = array.get(index).getAsFloat();
			}
		}
		return result;
	}

	private static String uniquePartName(String base, Set<String> used) {
		String name = base;
		int suffix = 2;
		while (!used.add(name)) {
			name = base + "_" + suffix;
			suffix++;
		}
		return name;
	}

	private static Map<String, ModelLayerLocation> layerIndex() {
		if (layersScanned) {
			return LAYER_INDEX;
		}
		layersScanned = true;
		for (Field field : ModelLayers.class.getFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != ModelLayerLocation.class) {
				continue;
			}
			try {
				if (field.get(null) instanceof ModelLayerLocation location && "main".equals(location.layer())) {
					LAYER_INDEX.putIfAbsent(location.model().getPath(), location);
				}
			} catch (IllegalAccessException ignored) {
				// Static public field; skip if the runtime denies access.
			}
		}
		return LAYER_INDEX;
	}

	static ModelPart cube(int texW, int texH) {
		int width = Math.max(1, texW);
		int height = Math.max(1, texH);
		return CUBE_GEOMETRY.computeIfAbsent(width + "x" + height, key -> {
			CubeListBuilder cubes = CubeListBuilder.create();
			cubes.addBox(-8.0f, -8.0f, -8.0f, 16.0f, 16.0f, 0.0f, Set.of(Direction.NORTH));
			cubes.addBox(-8.0f, -8.0f, 8.0f, 16.0f, 16.0f, 0.0f, Set.of(Direction.SOUTH));
			cubes.addBox(-8.0f, -8.0f, -8.0f, 0.0f, 16.0f, 16.0f, Set.of(Direction.WEST));
			cubes.addBox(8.0f, -8.0f, -8.0f, 0.0f, 16.0f, 16.0f, Set.of(Direction.EAST));
			cubes.addBox(-8.0f, -8.0f, -8.0f, 16.0f, 0.0f, 16.0f, Set.of(Direction.UP));
			cubes.addBox(-8.0f, 8.0f, -8.0f, 16.0f, 0.0f, 16.0f, Set.of(Direction.DOWN));
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("body", cubes, PartPose.ZERO);
			return LayerDefinition.create(mesh, 16, layerHeight(width, height)).bakeRoot();
		});
	}

	static ModelPart plane(int texW, int texH) {
		int width = Math.max(1, texW);
		int height = Math.max(1, texH);
		return PLANE_GEOMETRY.computeIfAbsent(width + "x" + height, key -> {
			CubeListBuilder cubes = CubeListBuilder.create();
			cubes.addBox(-8.0f, -8.0f, 0.0f, 16.0f, 16.0f, 0.0f, Set.of(Direction.NORTH, Direction.SOUTH));
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("body", cubes, PartPose.ZERO);
			return LayerDefinition.create(mesh, 16, layerHeight(width, height)).bakeRoot();
		});
	}

	static ModelPart cross(int texW, int texH) {
		int width = Math.max(1, texW);
		int height = Math.max(1, texH);
		return CROSS_GEOMETRY.computeIfAbsent(width + "x" + height, key -> {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			CubeListBuilder first = CubeListBuilder.create();
			first.addBox(-8.0f, -8.0f, 0.0f, 16.0f, 16.0f, 0.0f, Set.of(Direction.NORTH, Direction.SOUTH));
			root.addOrReplaceChild("cross_a", first, PartPose.rotation(0.0f, (float) Math.PI / 4.0f, 0.0f));
			CubeListBuilder second = CubeListBuilder.create();
			second.addBox(-8.0f, -8.0f, 0.0f, 16.0f, 16.0f, 0.0f, Set.of(Direction.NORTH, Direction.SOUTH));
			root.addOrReplaceChild("cross_b", second, PartPose.rotation(0.0f, -(float) Math.PI / 4.0f, 0.0f));
			return LayerDefinition.create(mesh, 16, layerHeight(width, height)).bakeRoot();
		});
	}

	private static int layerHeight(int texW, int texH) {
		return Math.max(1, 16 * texH / texW);
	}

	static void extract(
			GuiGraphicsExtractor graphics,
			Shape shape,
			ModelPart part,
			Identifier texture,
			int x0,
			int y0,
			int x1,
			int y1,
			float rotX,
			float rotY
	) {
		if (part == null || texture == null) {
			return;
		}
		int boxW = Math.max(1, x1 - x0);
		int boxH = Math.max(1, y1 - y0);
		Bounds bounds = bounds(part);
		// The bind-pose bounds grow once the model is pitched: rotation about X
		// mixes Y and Z, and the yaw then spreads that into the screen width.
		// Fit the worst-case rotated extents so the model stays inside the box
		// at every spin angle without the scale pulsing.
		double pitchRadians = Math.toRadians(rotX);
		float cosPitch = (float) Math.abs(Math.cos(pitchRadians));
		float sinPitch = (float) Math.abs(Math.sin(pitchRadians));
		float sizeX = bounds.sizeX();
		float sizeY = bounds.sizeY();
		float sizeZ = bounds.sizeZ();
		float pitchedZ = sizeY * sinPitch + sizeZ * cosPitch;
		float extentX = (float) Math.sqrt(sizeX * sizeX + pitchedZ * pitchedZ) * HORIZONTAL_ALLOWANCE;
		float extentY = sizeY * cosPitch + sizeZ * sinPitch;
		if (extentX <= 0.0f || extentY <= 0.0f) {
			return;
		}
		float scale = Math.min(boxW * FIT_FACTOR / extentX, boxH * FIT_FACTOR / extentY);
		if (scale <= 0.0f || !Float.isFinite(scale)) {
			return;
		}

		// The pane's model origin sits at the bottom edge of the box, and the skin
		// pipeline sinks every model by SKIN_Y_OFFSET_BLOCKS, so shift the model
		// back up by that offset and center it in the box.
		float liftPixels = boxH * 8.0f / scale;
		ModelPart offset = new ModelPart(List.of(), Map.of("model", part));
		offset.x = -bounds.centerX() * 16.0f;
		offset.y = -bounds.centerY() * 16.0f;
		offset.z = -bounds.centerZ() * 16.0f;
		ModelPart yaw = new ModelPart(List.of(), Map.of("offset", offset));
		yaw.yRot = (float) Math.toRadians(rotY);
		ModelPart pitch = new ModelPart(List.of(), Map.of("yaw", yaw));
		pitch.xRot = (float) Math.toRadians(rotX);
		ModelPart root = new ModelPart(List.of(), Map.of("pitch", pitch));
		root.y = SKIN_Y_OFFSET_BLOCKS * 16.0f - liftPixels;

		Model.Simple model = new Model.Simple(root, RenderTypes::entityCutout);
		graphics.skin(model, texture, scale, 0.0f, 0.0f, 0.0f, x0, y0, x1, y1);
	}

	private static Bounds bounds(ModelPart part) {
		Bounds cached = BOUNDS.get(part);
		if (cached != null) {
			return cached;
		}
		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
		float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		part.getExtentsForGui(new PoseStack(), corner -> {
			min[0] = Math.min(min[0], corner.x());
			min[1] = Math.min(min[1], corner.y());
			min[2] = Math.min(min[2], corner.z());
			max[0] = Math.max(max[0], corner.x());
			max[1] = Math.max(max[1], corner.y());
			max[2] = Math.max(max[2], corner.z());
		});
		Bounds computed = min[0] > max[0]
				? new Bounds(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
				: new Bounds(min[0], min[1], min[2], max[0], max[1], max[2]);
		BOUNDS.put(part, computed);
		return computed;
	}

	private static String stem(String resourcePath) {
		if (resourcePath == null) {
			return "";
		}
		String value = resourcePath;
		int slash = value.lastIndexOf('/');
		if (slash >= 0) {
			value = value.substring(slash + 1);
		}
		if (value.endsWith(".png")) {
			value = value.substring(0, value.length() - ".png".length());
		}
		int colon = value.indexOf(':');
		if (colon >= 0) {
			value = value.substring(colon + 1);
		}
		return value.toLowerCase(Locale.ROOT);
	}

	private static String reversedTokens(String stem) {
		String[] tokens = stem.split("_");
		StringBuilder builder = new StringBuilder();
		for (int index = tokens.length - 1; index >= 0; index--) {
			if (tokens[index].isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append('_');
			}
			builder.append(tokens[index]);
		}
		return builder.toString();
	}

	private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		float sizeX() {
			return maxX - minX;
		}

		float sizeY() {
			return maxY - minY;
		}

		float sizeZ() {
			return maxZ - minZ;
		}

		float centerX() {
			return (minX + maxX) * 0.5f;
		}

		float centerY() {
			return (minY + maxY) * 0.5f;
		}

		float centerZ() {
			return (minZ + maxZ) * 0.5f;
		}
	}
}

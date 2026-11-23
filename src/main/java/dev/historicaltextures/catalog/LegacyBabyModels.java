package dev.historicaltextures.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashSet;
import java.util.Set;

/**
 * Derives a pre-26.1 baby JEM from a legacy adult JEM by replaying the old
 * BabyModelTransform parameters, in the coordinate convention used by
 * VariantPreviewRenderer.parseLegacyModel.
 */
public final class LegacyBabyModels {
	private LegacyBabyModels() {
	}

	public static JsonObject fromAdultJem(JsonObject adultJem, LegacyBabies.BabyTransform transform) {
		JsonObject result = adultJem.deepCopy();
		JsonArray models = result.getAsJsonArray("models");
		if (models == null || transform == null) {
			return result;
		}
		if ("uniform".equals(transform.type())) {
			applyUniform(models, transform.scale());
		} else {
			applyBabyTransform(models, transform);
		}
		return result;
	}

	private static void applyBabyTransform(JsonArray models, LegacyBabies.BabyTransform transform) {
		Set<String> headParts = new HashSet<>(transform.headParts());
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			String part = model.has("part") ? model.get("part").getAsString() : null;
			if ("root".equals(part)) {
				continue;
			}
			String id = model.has("id") ? model.get("id").getAsString() : null;
			boolean head = headParts.contains(id) || headParts.contains(part);
			float factor = head
					? (transform.scaleHead() && transform.headScale() != 0.0f ? 1.5f / transform.headScale() : 1.0f)
					: (transform.bodyScale() != 0.0f ? 1.0f / transform.bodyScale() : 1.0f);
			float dx = 0.0f;
			float dy = head ? transform.yHeadOffset() : transform.bodyYOffset();
			float dz = head ? transform.zHeadOffset() : 0.0f;
			transformTopLevel(model, factor, dx, dy, dz);
		}
	}

	private static void applyUniform(JsonArray models, float scale) {
		boolean hasRoot = false;
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			if (model.has("part") && "root".equals(model.get("part").getAsString())) {
				hasRoot = true;
				break;
			}
		}
		if (hasRoot) {
			for (JsonElement element : models) {
				JsonObject model = element.getAsJsonObject();
				if (!model.has("part") || !"root".equals(model.get("part").getAsString())) {
					continue;
				}
				JsonArray submodels = model.getAsJsonArray("submodels");
				if (submodels == null) {
					continue;
				}
				for (JsonElement sub : submodels) {
					JsonObject submodel = sub.getAsJsonObject();
					float existing = submodel.has("scale") ? submodel.get("scale").getAsFloat() : 1.0f;
					submodel.addProperty("scale", round4(existing * scale));
				}
			}
			return;
		}
		for (JsonElement element : models) {
			transformTopLevel(element.getAsJsonObject(), scale, 0.0f, 0.0f, 0.0f);
		}
	}

	/**
	 * Rewrites one top-level model: pivot moves to {@code (pivot + d) * f}, boxes shift
	 * with the pivot delta and the part scale is multiplied by {@code f}.
	 */
	private static void transformTopLevel(JsonObject model, float factor, float dx, float dy, float dz) {
		float[] translate = floats(model.getAsJsonArray("translate"), 3);
		float pivotX = translate[0];
		float pivotY = 24.0f + translate[1];
		float pivotZ = -translate[2];
		float newPivotX = (pivotX + dx) * factor;
		float newPivotY = (pivotY + dy) * factor;
		float newPivotZ = (pivotZ + dz) * factor;
		float deltaX = newPivotX - pivotX;
		float deltaY = newPivotY - pivotY;
		float deltaZ = newPivotZ - pivotZ;

		JsonArray translateOut = new JsonArray();
		translateOut.add(round4(newPivotX));
		translateOut.add(round4(newPivotY - 24.0f));
		translateOut.add(round4(-newPivotZ));
		model.add("translate", translateOut);

		JsonArray boxes = model.getAsJsonArray("boxes");
		if (boxes != null) {
			for (JsonElement boxElement : boxes) {
				JsonArray coordinates = boxElement.getAsJsonObject().getAsJsonArray("coordinates");
				if (coordinates == null) {
					continue;
				}
				coordinates.set(0, new JsonPrimitive(round4(coordinates.get(0).getAsFloat() - deltaX)));
				coordinates.set(1, new JsonPrimitive(round4(coordinates.get(1).getAsFloat() - deltaY)));
				coordinates.set(2, new JsonPrimitive(round4(coordinates.get(2).getAsFloat() + deltaZ)));
			}
		}

		float existingScale = model.has("scale") ? model.get("scale").getAsFloat() : 1.0f;
		model.addProperty("scale", round4(existingScale * factor));
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

	private static double round4(double value) {
		return Math.round(value * 10000.0) / 10000.0;
	}
}

package legacybabies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports pre-26.1 (1.21.11) baby mob layer definitions from the obfuscated
 * client jar to EMF .jem files, in the exact inverse of the conventions used by
 * VariantPreviewRenderer.parseLegacyModel/addLegacySubmodels/legacyCubes.
 *
 * Usage: java ExportBabies <worklist.json> <outputDir>
 * worklist.json: [{"emf":"cow_baby","location":"minecraft:cow_baby/main","parts26":[...]}]
 *
 * Obfuscated names come from client_mappings_1.21.11.txt:
 *   LayerDefinitions -> hdd, ModelLayerLocation -> hde, LayerDefinition -> hdm,
 *   MaterialDefinition -> hdn, MeshDefinition -> hdo, PartDefinition -> hdq,
 *   PartPose -> hdi, CubeDefinition -> hdj, CubeDeformation -> hdk, UVPair -> hdr.
 */
public class ExportBabies {
	public static void main(String[] args) throws Exception {
		Path worklistPath = Paths.get(args[0]);
		Path outDir = Paths.get(args[1]);
		Files.createDirectories(outDir);

		JsonArray worklist;
		try (Reader r = Files.newBufferedReader(worklistPath, StandardCharsets.UTF_8)) {
			worklist = JsonParser.parseReader(r).getAsJsonArray();
		}

		// Bootstrap registries first (same sequence as the research harness p.Dump):
		// DetectedVersion -> SharedConstants.setVersion -> Bootstrap.bootStrap().
		Class<?> r = Class.forName("r");
		Object wv = r.getField("a").get(null);
		Class<?> sharedConstants = Class.forName("w");
		Class<?> worldVersion = Class.forName("aa");
		sharedConstants.getMethod("a", worldVersion).invoke(null, wv);
		Class.forName("amv").getMethod("a").invoke(null);

		// hdd.a() -> Map<ModelLayerLocation, LayerDefinition>
		Class<?> layerDefs = Class.forName("hdd");
		Map<?, ?> roots = (Map<?, ?>) layerDefs.getMethod("a").invoke(null);

		// Index by "model/layer"
		Map<String, Object> byLocation = new HashMap<>();
		for (Map.Entry<?, ?> e : roots.entrySet()) {
			Object key = e.getKey();
			String model = (String) key.getClass().getMethod("a").invoke(key).toString();
			String layer = (String) key.getClass().getMethod("b").invoke(key);
			byLocation.put(model + "/" + layer, e.getValue());
		}

		JsonObject manifest = new JsonObject();
		Set<String> warnings = new HashSet<>();
		for (JsonElement el : worklist) {
			JsonObject entry = el.getAsJsonObject();
			String emf = entry.get("emf").getAsString();
			String location = entry.get("location").getAsString();
			List<String> parts26 = new ArrayList<>();
			for (JsonElement p : entry.getAsJsonArray("parts26")) {
				parts26.add(p.getAsString());
			}
			Object layerDef = byLocation.get(location);
			if (layerDef == null) {
				warnings.add(emf + ": missing 1.21.11 layer " + location);
				continue;
			}
			JemWriter writer = new JemWriter();
			JsonObject treeDump = writeJem(emf, location, parts26, layerDef, writer, warnings);
			try (Writer out = Files.newBufferedWriter(outDir.resolve(emf + ".jem"), StandardCharsets.UTF_8)) {
				com.google.gson.GsonBuilder gb = new com.google.gson.GsonBuilder().setPrettyPrinting();
				gb.create().toJson(writer.json, out);
			}
			Files.writeString(outDir.resolve(emf + ".tree.json"), treeDump.toString());
			manifest.add(emf, treeDump);
		}
		Files.writeString(outDir.resolve("manifest.json"), manifest.toString());
		for (String warning : warnings) {
			System.err.println("WARNING " + warning);
		}
		System.out.println("exported " + manifest.size() + " models to " + outDir);
	}

	// ---------- reflection accessors (obfuscated names) ----------

	private static Field fld(Class<?> cls, String name) throws Exception {
		Field f = cls.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	private static float f32(Object o, String field) throws Exception {
		return fld(o.getClass(), field).getFloat(o);
	}

	private static int i32(Object o, String field) throws Exception {
		return fld(o.getClass(), field).getInt(o);
	}

	private static boolean bool(Object o, String field) throws Exception {
		return fld(o.getClass(), field).getBoolean(o);
	}

	private static Object obj(Object o, String field) throws Exception {
		return fld(o.getClass(), field).get(o);
	}

	private static float vec(Object vector, char axis) throws Exception {
		return (float) vector.getClass().getMethod(String.valueOf(axis)).invoke(vector);
	}

	// ---------- model tree ----------

	static final class Pose {
		float x, y, z, xRot, yRot, zRot, xScale, yScale, zScale;
	}

	static final class Cube {
		float ox, oy, oz, dx, dy, dz, gx, gy, gz, u, v;
		boolean mirror;
	}

	static final class Node {
		String name;
		Pose pose;
		List<Cube> cubes = new ArrayList<>();
		Map<String, Node> kids = new LinkedHashMap<>();
	}

	private static Node readPart(Object partDef, String name) throws Exception {
		Node n = new Node();
		n.name = name;
		Pose p = new Pose();
		Object pose = obj(partDef, "b"); // PartPose hdi
		p.x = f32(pose, "b");
		p.y = f32(pose, "c");
		p.z = f32(pose, "d");
		p.xRot = f32(pose, "e");
		p.yRot = f32(pose, "f");
		p.zRot = f32(pose, "g");
		p.xScale = f32(pose, "h");
		p.yScale = f32(pose, "i");
		p.zScale = f32(pose, "j");
		n.pose = p;

		List<?> cubes = (List<?>) obj(partDef, "a");
		for (Object c : cubes) {
			Cube cube = new Cube();
			Object origin = obj(c, "b");
			Object dims = obj(c, "c");
			cube.ox = vec(origin, 'x');
			cube.oy = vec(origin, 'y');
			cube.oz = vec(origin, 'z');
			cube.dx = vec(dims, 'x');
			cube.dy = vec(dims, 'y');
			cube.dz = vec(dims, 'z');
			Object grow = obj(c, "d"); // CubeDeformation hdk
			cube.gx = f32(grow, "b");
			cube.gy = f32(grow, "c");
			cube.gz = f32(grow, "d");
			cube.mirror = bool(c, "e");
			Object uv = obj(c, "f"); // UVPair hdr: u->a, v->b (fields)
			cube.u = fld(uv.getClass(), "a").getFloat(uv);
			cube.v = fld(uv.getClass(), "b").getFloat(uv);
			n.cubes.add(cube);
		}

		Map<?, ?> children = (Map<?, ?>) obj(partDef, "c");
		for (Map.Entry<?, ?> e : children.entrySet()) {
			n.kids.put((String) e.getKey(), readPart(e.getValue(), (String) e.getKey()));
		}
		return n;
	}

	// ---------- JEM emission ----------

	private static final class JemWriter {
		final JsonObject json = new JsonObject();
		final Set<String> usedIds = new HashSet<>();
		final JsonObject animations = new JsonObject();
		final List<String> warnings = new ArrayList<>();

		String uniqueId(String base) {
			String id = base;
			int i = 2;
			while (!usedIds.add(id)) {
				id = base + "_" + i++;
			}
			return id;
		}
	}

	private static void num(JsonArray arr, double v) {
		if (v == Math.rint(v) && Math.abs(v) < 9e15) {
			arr.add((long) v);
		} else {
			arr.add(v);
		}
	}

	private static double nz(double v) {
		return v == 0.0 ? 0.0 : v;
	}

	private static String fmt(float v) {
		if (v == Math.rint(v) && Math.abs(v) < 9e15) {
			return Long.toString((long) v);
		}
		return Float.toString(v);
	}

	private static JsonObject writeJem(String emf, String location, List<String> parts26,
									  Object layerDef, JemWriter w, Set<String> globalWarnings) throws Exception {
		Object mesh = obj(layerDef, "a"); // hdm.a -> hdo MeshDefinition
		Object material = obj(layerDef, "b"); // hdm.b -> hdn MaterialDefinition
		int texW = i32(material, "a");
		int texH = i32(material, "b");
		Object rootDef = obj(mesh, "a"); // hdo.a -> hdq PartDefinition

		JsonArray texSize = new JsonArray();
		texSize.add(texW);
		texSize.add(texH);
		w.json.add("textureSize", texSize);

		Node root = readPart(rootDef, "root");
		if (root.pose.x != 0 || root.pose.y != 0 || root.pose.z != 0
				|| root.pose.xRot != 0 || root.pose.yRot != 0 || root.pose.zRot != 0
				|| !root.cubes.isEmpty()) {
			globalWarnings.add(emf + ": mesh root has non-trivial pose/cubes");
		}

		JsonArray models = new JsonArray();
		JsonObject rootEntry = new JsonObject();
		rootEntry.addProperty("part", "root");
		rootEntry.addProperty("id", "root");
		rootEntry.addProperty("invertAxis", "xy");
		JsonArray t = new JsonArray();
		t.add(0);
		t.add(0);
		t.add(0);
		rootEntry.add("translate", t);
		rootEntry.add("boxes", new JsonArray());

		JsonArray submodels = new JsonArray();
		for (Map.Entry<String, Node> e : root.kids.entrySet()) {
			emitNode(e.getValue(), w, submodels);
		}
		rootEntry.add("submodels", submodels);
		if (w.animations.size() > 0) {
			JsonArray anims = new JsonArray();
			anims.add(w.animations);
			rootEntry.add("animations", anims);
		}
		models.add(rootEntry);

		// Blank entries hiding the 26.3 baby layer's top-level parts.
		for (String part : parts26) {
			JsonObject blank = new JsonObject();
			blank.addProperty("part", part);
			blank.addProperty("id", part);
			blank.addProperty("invertAxis", "xy");
			JsonArray bt = new JsonArray();
			bt.add(0);
			bt.add(0);
			bt.add(0);
			blank.add("translate", bt);
			blank.add("boxes", new JsonArray());
			models.add(blank);
		}
		w.json.add("models", models);

		// tree dump for verification
		JsonObject dump = new JsonObject();
		dump.addProperty("location", location);
		dump.addProperty("emf", emf);
		dump.add("textureSize", texSize.deepCopy());
		dump.add("root", dumpNode(root));
		for (String warning : w.warnings) {
			globalWarnings.add(emf + ": " + warning);
		}
		return dump;
	}

	private static JsonObject dumpNode(Node n) {
		JsonObject o = new JsonObject();
		JsonArray pose = new JsonArray();
		pose.add(n.pose.x); pose.add(n.pose.y); pose.add(n.pose.z);
		pose.add(n.pose.xRot); pose.add(n.pose.yRot); pose.add(n.pose.zRot);
		pose.add(n.pose.xScale); pose.add(n.pose.yScale); pose.add(n.pose.zScale);
		o.add("pose", pose);
		JsonArray cubes = new JsonArray();
		for (Cube c : n.cubes) {
			JsonObject co = new JsonObject();
			JsonArray orig = new JsonArray(); orig.add(c.ox); orig.add(c.oy); orig.add(c.oz);
			JsonArray dim = new JsonArray(); dim.add(c.dx); dim.add(c.dy); dim.add(c.dz);
			JsonArray grow = new JsonArray(); grow.add(c.gx); grow.add(c.gy); grow.add(c.gz);
			JsonArray uv = new JsonArray(); uv.add(c.u); uv.add(c.v);
			co.add("origin", orig); co.add("dims", dim); co.add("grow", grow);
			co.addProperty("mirror", c.mirror);
			co.add("uv", uv);
			cubes.add(co);
		}
		o.add("cubes", cubes);
		JsonObject kids = new JsonObject();
		for (Map.Entry<String, Node> e : n.kids.entrySet()) {
			kids.add(e.getKey(), dumpNode(e.getValue()));
		}
		o.add("kids", kids);
		return o;
	}

	private static void emitNode(Node node, JemWriter w, JsonArray into) {
		Pose p = node.pose;
		// Group cubes by mirror flag; mixed parts split into sibling nodes.
		List<Cube> plain = new ArrayList<>();
		List<Cube> mirrored = new ArrayList<>();
		for (Cube c : node.cubes) {
			(c.mirror ? mirrored : plain).add(c);
		}
		List<List<Cube>> groups = new ArrayList<>();
		if (!plain.isEmpty()) {
			groups.add(plain);
		}
		if (!mirrored.isEmpty()) {
			groups.add(mirrored);
		}
		if (groups.isEmpty()) {
			groups.add(new ArrayList<>());
		}

		boolean first = true;
		for (List<Cube> group : groups) {
			boolean isMirrorGroup = group == mirrored && !mirrored.isEmpty();
			String id = w.uniqueId("legacy_" + node.name);
			JsonObject m = new JsonObject();
			m.addProperty("id", id);
			m.addProperty("invertAxis", "xy");
			JsonArray tr = new JsonArray();
			tr.add(nz(-p.x)); tr.add(nz(-p.y)); tr.add(nz(p.z));
			m.add("translate", tr);
			if (p.xRot != 0 || p.yRot != 0 || p.zRot != 0) {
				JsonArray rot = new JsonArray();
				rot.add(nz(-Math.toDegrees(p.xRot)));
				rot.add(nz(-Math.toDegrees(p.yRot)));
				rot.add(nz(Math.toDegrees(p.zRot)));
				m.add("rotate", rot);
			}
			if (p.xScale == p.yScale && p.yScale == p.zScale && p.xScale != 1.0f) {
				m.addProperty("scale", p.xScale);
			} else if (!(p.xScale == 1.0f && p.yScale == 1.0f && p.zScale == 1.0f)) {
				// Non-uniform pose scale (only llama_baby): EMF .jem and the repo
				// reader only support a uniform "scale". Approximate with the
				// geometric mean so the part stays close to its true size.
				double gm = Math.cbrt((double) p.xScale * p.yScale * p.zScale);
				m.addProperty("scale", (float) gm);
				w.warnings.add("non-uniform pose scale on " + node.name
						+ " (" + p.xScale + "," + p.yScale + "," + p.zScale
						+ ") approximated with uniform scale " + fmt((float) gm));
			}
			if (isMirrorGroup) {
				m.addProperty("mirrorTexture", "u");
			}
			JsonArray boxes = new JsonArray();
			for (Cube c : group) {
				boxes.add(boxJson(c));
			}
			m.add("boxes", boxes);
			if (first && !node.kids.isEmpty()) {
				JsonArray subs = new JsonArray();
				for (Map.Entry<String, Node> e : node.kids.entrySet()) {
					emitNode(e.getValue(), w, subs);
				}
				m.add("submodels", subs);
			}
			into.add(m);
			addAnimations(node, id, w);
			first = false;
		}
	}

	private static JsonObject boxJson(Cube c) {
		JsonObject b = new JsonObject();
		JsonArray coords = new JsonArray();
		coords.add(nz(-(c.ox + c.dx)));
		coords.add(nz(-(c.oy + c.dy)));
		coords.add(nz(c.oz));
		coords.add(nz(c.dx));
		coords.add(nz(c.dy));
		coords.add(nz(c.dz));
		b.add("coordinates", coords);
		JsonArray uv = new JsonArray();
		num(uv, c.u);
		num(uv, c.v);
		b.add("textureOffset", uv);
		boolean gu = c.gx != 0 || c.gy != 0 || c.gz != 0;
		if (gu) {
			if (c.gx == c.gy && c.gy == c.gz) {
				b.addProperty("sizeAdd", c.gx);
			} else {
				// EMF supports per-axis sizeAdd; the repo preview reader only reads
				// scalar sizeAdd, so these cubes lose their grow in the preview.
				b.addProperty("sizeAddX", c.gx);
				b.addProperty("sizeAddY", c.gy);
				b.addProperty("sizeAddZ", c.gz);
			}
		}
		return b;
	}

	private static void addAnimations(Node node, String id, JemWriter w) {
		String rx = null, ry = null;
		switch (node.name) {
			case "head":
				rx = "torad(head_pitch)";
				ry = "torad(head_yaw)";
				break;
			case "right_hind_leg":
			case "left_front_leg":
				rx = "cos(limb_swing*0.6662)*1.4*limb_speed";
				break;
			case "left_hind_leg":
			case "right_front_leg":
				rx = "cos(limb_swing*0.6662+pi)*1.4*limb_speed";
				break;
			case "right_arm":
				rx = "cos(limb_swing*0.6662+pi)*limb_speed";
				break;
			case "left_arm":
				rx = "cos(limb_swing*0.6662)*limb_speed";
				break;
			case "right_leg":
				rx = "cos(limb_swing*0.6662)*1.4*limb_speed";
				break;
			case "left_leg":
				rx = "cos(limb_swing*0.6662+pi)*1.4*limb_speed";
				break;
			default:
				return;
		}
		if (rx != null && node.pose.xRot != 0) {
			rx = rx + " + " + fmt(node.pose.xRot);
		}
		if (ry != null && node.pose.yRot != 0) {
			ry = ry + " + " + fmt(node.pose.yRot);
		}
		if (rx != null) {
			w.animations.addProperty(id + ".rx", rx);
		}
		if (ry != null) {
			w.animations.addProperty(id + ".ry", ry);
		}
	}
}

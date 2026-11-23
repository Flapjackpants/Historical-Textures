package legacybabies;

import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.UVPair;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dumps cube UV/layout data for the 26.3 (Mojmap, unobfuscated) client jar so
 * adult texture UV usage can be compared against the 1.21.11 export.
 *
 * Usage: java DumpUV263 <minecraft-client-only.jar classpath> rabbit/main axolotl/main ...
 * Prints one line per requested location: model/layer|texW,texH|cube tuples.
 */
public class DumpUV263 {
	static String cube(CubeDefinition c) throws Exception {
		Field fo = CubeDefinition.class.getDeclaredField("origin");
		fo.setAccessible(true);
		Field fd = CubeDefinition.class.getDeclaredField("dimensions");
		fd.setAccessible(true);
		Field ft = CubeDefinition.class.getDeclaredField("texCoord");
		ft.setAccessible(true);
		Field fg = CubeDefinition.class.getDeclaredField("grow");
		fg.setAccessible(true);
		Field fm = CubeDefinition.class.getDeclaredField("mirror");
		fm.setAccessible(true);
		org.joml.Vector3fc o = (org.joml.Vector3fc) fo.get(c);
		org.joml.Vector3fc d = (org.joml.Vector3fc) fd.get(c);
		UVPair uv = (UVPair) ft.get(c);
		CubeDeformation g = (CubeDeformation) fg.get(c);
		Field gx = CubeDeformation.class.getDeclaredField("growX");
		gx.setAccessible(true);
		Field gy = CubeDeformation.class.getDeclaredField("growY");
		gy.setAccessible(true);
		Field gz = CubeDeformation.class.getDeclaredField("growZ");
		gz.setAccessible(true);
		return String.format("%.3f,%.3f,%.3f|%.3f,%.3f,%.3f|%.3f,%.3f,%.3f|%s|%.1f,%.1f",
				o.x(), o.y(), o.z(), d.x(), d.y(), d.z(),
				gx.getFloat(g), gy.getFloat(g), gz.getFloat(g),
				fm.getBoolean(c), uv.u(), uv.v());
	}

	static void walk(PartDefinition pd, StringBuilder sb) throws Exception {
		Field fc = PartDefinition.class.getDeclaredField("cubes");
		fc.setAccessible(true);
		Field fk = PartDefinition.class.getDeclaredField("children");
		fk.setAccessible(true);
		Field fp = PartDefinition.class.getDeclaredField("partPose");
		fp.setAccessible(true);
		PartPose pose = (PartPose) fp.get(pd);
		sb.append("P(").append(pose.x()).append(',').append(pose.y()).append(',')
			.append(pose.z()).append(',').append(pose.xRot()).append(',')
			.append(pose.yRot()).append(',').append(pose.zRot()).append(',')
			.append(pose.xScale()).append(',').append(pose.yScale()).append(',')
			.append(pose.zScale()).append(")[");
		for (CubeDefinition c : (List<CubeDefinition>) fc.get(pd)) {
			sb.append('C').append(cube(c));
		}
		sb.append(']');
		Map<String, PartDefinition> kids = (Map<String, PartDefinition>) fk.get(pd);
		List<String> names = new ArrayList<>(kids.keySet());
		names.sort(String::compareTo);
		sb.append('{');
		for (String n : names) {
			sb.append(n).append(':');
			walk(kids.get(n), sb);
		}
		sb.append('}');
	}

	public static void main(String[] args) throws Exception {
		Map<ModelLayerLocation, LayerDefinition> map = LayerDefinitions.createRoots();
		Field fm = LayerDefinition.class.getDeclaredField("mesh");
		fm.setAccessible(true);
		Field fmat = LayerDefinition.class.getDeclaredField("material");
		fmat.setAccessible(true);
		Field fx = fmat.getType().getDeclaredField("xTexSize");
		fx.setAccessible(true);
		Field fy = fmat.getType().getDeclaredField("yTexSize");
		fy.setAccessible(true);
		for (String want : args) {
			String[] parts = want.split("/");
			String model = parts[0];
			String layer = parts.length > 1 ? parts[1] : "main";
			for (Map.Entry<ModelLayerLocation, LayerDefinition> e : map.entrySet()) {
				if (!e.getKey().model().getPath().equals(model)
						|| !e.getKey().layer().equals(layer)) {
					continue;
				}
				Object material = fmat.get(e.getValue());
				StringBuilder sb = new StringBuilder();
				sb.append("minecraft:").append(model).append('/').append(layer)
					.append('|').append(fx.getInt(material)).append(',')
					.append(fy.getInt(material)).append('|');
				MeshDefinition md = (MeshDefinition) fm.get(e.getValue());
				Field fr = MeshDefinition.class.getDeclaredField("root");
				fr.setAccessible(true);
				walk((PartDefinition) fr.get(md), sb);
				System.out.println(sb);
			}
		}
	}
}

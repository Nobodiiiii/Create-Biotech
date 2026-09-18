package com.nobodiiiii.createbiotech.foundation.render;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

/** Parses and bakes the Bedrock-inspired machine-creature geometry format. */
final class MachineCreatureModelLoader {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String FORMAT_VERSION = "1.0.0";
	private static volatile Map<ResourceLocation, Definition> definitions = Map.of();
	private static volatile int generation;

	private MachineCreatureModelLoader() {}

	static void reload(ResourceManager resourceManager, List<MachineCreatureModels.ModelSpec> specs) {
		Map<ResourceLocation, Definition> loaded = new LinkedHashMap<>();
		try {
			for (MachineCreatureModels.ModelSpec spec : specs) {
				Definition definition = read(resourceManager, spec.id());
				definition.validate(spec);
				loaded.put(spec.id(), definition);
			}
		} catch (IOException | RuntimeException exception) {
			if (definitions.isEmpty())
				throw new IllegalStateException("Unable to load machine-creature models", exception);
			LOGGER.error("Unable to reload machine-creature models; retaining the previous valid definitions", exception);
			return;
		}

		definitions = Map.copyOf(loaded);
		generation++;
		LOGGER.debug("Loaded {} machine-creature model definitions", loaded.size());
	}

	static int generation() {
		return generation;
	}

	static BakedModel bake(MachineCreatureModels.ModelSpec spec) {
		Definition definition = definitions.get(spec.id());
		if (definition == null)
			throw new IllegalStateException("Machine model " + spec.id()
				+ " was requested before client resources were loaded");
		return definition.bake();
	}

	private static Definition read(ResourceManager manager, ResourceLocation modelId) throws IOException {
		ResourceLocation fileId = ResourceLocation.fromNamespaceAndPath(modelId.getNamespace(),
			"models/machine_creature/" + modelId.getPath() + ".json");
		Resource resource = manager.getResource(fileId)
			.orElseThrow(() -> new FileNotFoundException(fileId.toString()));
		try (BufferedReader reader = resource.openAsReader()) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject())
				throw error(fileId, "root must be an object");
			return Definition.parse(fileId, modelId, parsed.getAsJsonObject());
		}
	}

	private static IllegalArgumentException error(ResourceLocation file, String message) {
		return new IllegalArgumentException("Invalid machine model " + file + ": " + message);
	}

	private record Definition(ResourceLocation file, int textureWidth, int textureHeight,
		String rootBone, List<Bone> bones, Map<String, String> pathsByBone,
		Map<String, AnchorDefinition> anchors) {

		static Definition parse(ResourceLocation file, ResourceLocation expectedId, JsonObject json) {
			String version = string(json, "format_version", file);
			if (!FORMAT_VERSION.equals(version))
				throw error(file, "unsupported format_version '" + version + "'");

			JsonObject description = object(json, "description", file);
			ResourceLocation identifier = ResourceLocation.tryParse(string(description, "identifier", file));
			if (identifier == null || !identifier.equals(expectedId))
				throw error(file, "description.identifier must be '" + expectedId + "'");
			int textureWidth = positiveInt(description, "texture_width", file);
			int textureHeight = positiveInt(description, "texture_height", file);
			String rootBone = optionalString(description, "root_bone", file);

			JsonArray boneArray = array(json, "bones", file);
			if (boneArray.isEmpty())
				throw error(file, "bones must not be empty");
			List<Bone> bones = new ArrayList<>();
			Map<String, Bone> byName = new LinkedHashMap<>();
			for (JsonElement element : boneArray) {
				if (!element.isJsonObject())
					throw error(file, "each bone must be an object");
				Bone bone = Bone.parse(file, element.getAsJsonObject());
				if (byName.putIfAbsent(bone.name(), bone) != null)
					throw error(file, "duplicate bone name '" + bone.name() + "'");
				bones.add(bone);
			}

			for (Bone bone : bones)
				if (bone.parent() != null && !byName.containsKey(bone.parent()))
					throw error(file, "bone '" + bone.name() + "' has unknown parent '" + bone.parent() + "'");
			if (rootBone != null && !byName.containsKey(rootBone))
				throw error(file, "unknown root_bone '" + rootBone + "'");

			Map<String, String> paths = buildPaths(file, bones, byName, rootBone);
			Map<String, AnchorDefinition> anchors = parseAnchors(file, json, paths);
			return new Definition(file, textureWidth, textureHeight, rootBone,
				List.copyOf(bones), Map.copyOf(paths), Map.copyOf(anchors));
		}

		void validate(MachineCreatureModels.ModelSpec spec) {
			Set<String> paths = new HashSet<>(pathsByBone.values());
			for (String required : spec.requiredParts())
				if (!paths.contains(required))
					throw error(file, "missing required part '" + required + "'");
			for (String required : spec.requiredAnchors())
				if (!anchors.containsKey(required))
					throw error(file, "missing required anchor '" + required + "'");
		}

		BakedModel bake() {
			MeshDefinition mesh = new MeshDefinition();
			Map<String, List<Bone>> children = new HashMap<>();
			for (Bone bone : bones)
				children.computeIfAbsent(bone.parent(), ignored -> new ArrayList<>()).add(bone);
			for (Bone bone : children.getOrDefault(null, List.of()))
				bakeBone(mesh.getRoot(), bone, children);

			ModelPart bakedRoot = LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot();
			ModelPart modelRoot = rootBone == null ? bakedRoot : bakedRoot.getChild(rootBone);
			Map<String, ModelPart> parts = new HashMap<>();
			parts.put("$root", modelRoot);
			for (Bone bone : bones) {
				String path = pathsByBone.get(bone.name());
				if ("$root".equals(path)) {
					applyFlags(modelRoot, bone);
					continue;
				}
				ModelPart part = partAt(modelRoot, path);
				applyFlags(part, bone);
				parts.put(path, part);
			}

			Map<String, MachineCreatureModel.Anchor> bakedAnchors = new LinkedHashMap<>();
			for (Map.Entry<String, AnchorDefinition> entry : anchors.entrySet()) {
				AnchorDefinition anchor = entry.getValue();
				ModelPart part = parts.get(anchor.bonePath());
				if (part == null)
					throw error(file, "anchor '" + entry.getKey() + "' references unknown part '"
						+ anchor.bonePath() + "'");
				bakedAnchors.put(entry.getKey(), new MachineCreatureModel.Anchor(part,
					anchor.position().x(), anchor.position().y(), anchor.position().z()));
			}
			return new BakedModel(modelRoot, Map.copyOf(bakedAnchors));
		}

		private void bakeBone(PartDefinition parent, Bone bone, Map<String, List<Bone>> children) {
			CubeListBuilder cubes = CubeListBuilder.create();
			for (Cube cube : bone.cubes()) {
				cubes.texOffs(cube.u(), cube.v())
					.mirror(cube.mirror())
					.addBox(cube.origin().x(), cube.origin().y(), cube.origin().z(),
						cube.size().x(), cube.size().y(), cube.size().z(), new CubeDeformation(cube.inflate()));
			}
			Vec3 pivot = bone.pivot();
			Vec3 rotation = bone.rotation();
			PartPose pose = PartPose.offsetAndRotation(pivot.x(), pivot.y(), pivot.z(),
				rotation.x() * Mth.DEG_TO_RAD, rotation.y() * Mth.DEG_TO_RAD, rotation.z() * Mth.DEG_TO_RAD);
			PartDefinition part = parent.addOrReplaceChild(bone.name(), cubes, pose);
			for (Bone child : children.getOrDefault(bone.name(), List.of()))
				bakeBone(part, child, children);
		}
	}

	private record Bone(String name, String parent, Vec3 pivot, Vec3 rotation, List<Cube> cubes,
		boolean visible, boolean skipDraw) {

		static Bone parse(ResourceLocation file, JsonObject json) {
			String name = string(json, "name", file);
			if (name.isBlank() || name.contains("/"))
				throw error(file, "bone names must be non-empty and cannot contain '/'");
			String parent = optionalString(json, "parent", file);
			Vec3 pivot = optionalVec3(json, "pivot", Vec3.ZERO, file);
			Vec3 rotation = optionalVec3(json, "rotation", Vec3.ZERO, file);
			boolean visible = optionalBoolean(json, "visible", true, file);
			boolean skipDraw = optionalBoolean(json, "skip_draw", false, file);
			List<Cube> cubes = new ArrayList<>();
			JsonArray cubeArray = optionalArray(json, "cubes", file);
			if (cubeArray != null)
				for (JsonElement element : cubeArray) {
					if (!element.isJsonObject())
						throw error(file, "each cube must be an object");
					cubes.add(Cube.parse(file, element.getAsJsonObject()));
				}
			return new Bone(name, parent, pivot, rotation, List.copyOf(cubes), visible, skipDraw);
		}
	}

	private record Cube(Vec3 origin, Vec3 size, int u, int v, float inflate, boolean mirror) {

		static Cube parse(ResourceLocation file, JsonObject json) {
			Vec3 origin = vec3(json, "origin", file);
			Vec3 size = vec3(json, "size", file);
			if (size.x() < 0 || size.y() < 0 || size.z() < 0)
				throw error(file, "cube sizes cannot be negative");
			JsonArray uv = array(json, "uv", file);
			if (uv.size() != 2)
				throw error(file, "cube uv must contain two integers");
			int u = exactInt(uv.get(0), file, "cube uv[0]");
			int v = exactInt(uv.get(1), file, "cube uv[1]");
			float inflate = optionalFloat(json, "inflate", 0.0f, file);
			boolean mirror = optionalBoolean(json, "mirror", false, file);
			return new Cube(origin, size, u, v, inflate, mirror);
		}
	}

	private record AnchorDefinition(String bonePath, Vec3 position) {}

	private record Vec3(float x, float y, float z) {
		private static final Vec3 ZERO = new Vec3(0, 0, 0);
	}

	record BakedModel(ModelPart root, Map<String, MachineCreatureModel.Anchor> anchors) {}

	private static Map<String, String> buildPaths(ResourceLocation file, List<Bone> bones,
		Map<String, Bone> byName, String rootBone) {
		Map<String, String> fullPaths = new HashMap<>();
		for (Bone bone : bones)
			fullPath(file, bone, byName, fullPaths, new HashSet<>());

		Map<String, String> relativePaths = new HashMap<>();
		String rootPath = rootBone == null ? null : fullPaths.get(rootBone);
		for (Bone bone : bones) {
			String fullPath = fullPaths.get(bone.name());
			if (rootPath == null) {
				relativePaths.put(bone.name(), fullPath);
			} else if (fullPath.equals(rootPath)) {
				relativePaths.put(bone.name(), "$root");
			} else if (fullPath.startsWith(rootPath + "/")) {
				relativePaths.put(bone.name(), fullPath.substring(rootPath.length() + 1));
			} else {
				throw error(file, "bone '" + bone.name() + "' is outside root_bone '" + rootBone + "'");
			}
		}
		return relativePaths;
	}

	private static String fullPath(ResourceLocation file, Bone bone, Map<String, Bone> byName,
		Map<String, String> paths, Set<String> visiting) {
		String existing = paths.get(bone.name());
		if (existing != null)
			return existing;
		if (!visiting.add(bone.name()))
			throw error(file, "cyclic bone parent chain at '" + bone.name() + "'");
		String path = bone.parent() == null ? bone.name()
			: fullPath(file, byName.get(bone.parent()), byName, paths, visiting) + "/" + bone.name();
		visiting.remove(bone.name());
		paths.put(bone.name(), path);
		return path;
	}

	private static Map<String, AnchorDefinition> parseAnchors(ResourceLocation file, JsonObject json,
		Map<String, String> pathsByBone) {
		JsonObject jsonAnchors = optionalObject(json, "anchors", file);
		if (jsonAnchors == null)
			return Map.of();
		Map<String, AnchorDefinition> anchors = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : jsonAnchors.entrySet()) {
			if (!entry.getValue().isJsonObject())
				throw error(file, "anchor '" + entry.getKey() + "' must be an object");
			JsonObject anchor = entry.getValue().getAsJsonObject();
			String boneName = string(anchor, "bone", file);
			String bonePath = "$root".equals(boneName) ? "$root" : pathsByBone.get(boneName);
			if (bonePath == null)
				throw error(file, "anchor '" + entry.getKey() + "' references unknown bone '" + boneName + "'");
			anchors.put(entry.getKey(), new AnchorDefinition(bonePath, vec3(anchor, "position", file)));
		}
		return anchors;
	}

	private static ModelPart partAt(ModelPart root, String path) {
		ModelPart current = root;
		for (String name : path.split("/"))
			current = current.getChild(name);
		return current;
	}

	private static void applyFlags(ModelPart part, Bone bone) {
		part.visible = bone.visible();
		part.skipDraw = bone.skipDraw();
	}

	private static JsonObject object(JsonObject owner, String name, ResourceLocation file) {
		JsonObject value = optionalObject(owner, name, file);
		if (value == null)
			throw error(file, "missing object '" + name + "'");
		return value;
	}

	private static JsonObject optionalObject(JsonObject owner, String name, ResourceLocation file) {
		JsonElement value = owner.get(name);
		if (value == null)
			return null;
		if (!value.isJsonObject())
			throw error(file, "'" + name + "' must be an object");
		return value.getAsJsonObject();
	}

	private static JsonArray array(JsonObject owner, String name, ResourceLocation file) {
		JsonArray value = optionalArray(owner, name, file);
		if (value == null)
			throw error(file, "missing array '" + name + "'");
		return value;
	}

	private static JsonArray optionalArray(JsonObject owner, String name, ResourceLocation file) {
		JsonElement value = owner.get(name);
		if (value == null)
			return null;
		if (!value.isJsonArray())
			throw error(file, "'" + name + "' must be an array");
		return value.getAsJsonArray();
	}

	private static String string(JsonObject owner, String name, ResourceLocation file) {
		JsonElement value = owner.get(name);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw error(file, "'" + name + "' must be a string");
		return value.getAsString();
	}

	private static String optionalString(JsonObject owner, String name, ResourceLocation file) {
		JsonElement value = owner.get(name);
		if (value == null)
			return null;
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw error(file, "'" + name + "' must be a string");
		return value.getAsString();
	}

	private static int positiveInt(JsonObject owner, String name, ResourceLocation file) {
		int parsed = exactInt(owner.get(name), file, name);
		if (parsed <= 0)
			throw error(file, "'" + name + "' must be positive");
		return parsed;
	}

	private static int exactInt(JsonElement value, ResourceLocation file, String name) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw error(file, "'" + name + "' must be an integer");
		double number = value.getAsDouble();
		if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE
			|| number > Integer.MAX_VALUE)
			throw error(file, "'" + name + "' must be an integer");
		return (int) number;
	}

	private static Vec3 vec3(JsonObject owner, String name, ResourceLocation file) {
		JsonArray value = array(owner, name, file);
		if (value.size() != 3)
			throw error(file, "'" + name + "' must contain three numbers");
		return new Vec3(finiteFloat(value.get(0), file, name + "[0]"),
			finiteFloat(value.get(1), file, name + "[1]"),
			finiteFloat(value.get(2), file, name + "[2]"));
	}

	private static Vec3 optionalVec3(JsonObject owner, String name, Vec3 fallback, ResourceLocation file) {
		return owner.has(name) ? vec3(owner, name, file) : fallback;
	}

	private static float optionalFloat(JsonObject owner, String name, float fallback, ResourceLocation file) {
		JsonElement value = owner.get(name);
		return value == null ? fallback : finiteFloat(value, file, name);
	}

	private static float finiteFloat(JsonElement value, ResourceLocation file, String name) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw error(file, "'" + name + "' must be a number");
		float number = value.getAsFloat();
		if (!Float.isFinite(number))
			throw error(file, "'" + name + "' must be finite");
		return number;
	}

	private static boolean optionalBoolean(JsonObject owner, String name, boolean fallback, ResourceLocation file) {
		JsonElement value = owner.get(name);
		if (value == null)
			return fallback;
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
			throw error(file, "'" + name + "' must be a boolean");
		return value.getAsBoolean();
	}
}

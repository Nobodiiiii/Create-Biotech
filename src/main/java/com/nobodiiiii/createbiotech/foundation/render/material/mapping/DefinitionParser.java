package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.ComputedCost;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.IntPoint;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.IntRect;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.MappingTransform;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.MaterialOverride;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.PixelMapping;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.RegionMapping;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.Layer;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.TextureLayer;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.ModelLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DefinitionParser {
    private static final Set<String> TARGET_ROOT_FIELDS = Set.of("schema", "size", "source_grids", "regions", "pixels",
            "overlay", "material_overrides", "computed_cost", "render_policy", "layers", "body_textures");
    private static final Set<String> REGION_FIELDS = Set.of("source_slot", "source", "destination", "transform");
    private static final Set<String> PIXEL_FIELDS = Set.of("source_slot", "source", "destination");
    private static final Set<String> OVERRIDE_FIELDS = Set.of("slots", "overlay", "layers");
    private static final Set<String> TEXTURE_LAYER_FIELDS = Set.of("index", "texture", "grid", "source", "destination", "emissive", "material_variants");
    private static final Set<String> MODEL_LAYER_FIELDS = Set.of("index", "model", "part", "offset", "rotation", "scale", "source_slot", "source", "emissive");
    private static final Set<String> COST_FIELDS = Set.of("max_pieces_per_source_quad", "additional_quads");
    private static final Set<String> POLICY_FIELDS = Set.of("mode", "max_pieces_per_face",
            "max_additional_quads", "max_texture_batches");

    private static final Set<String> MATERIAL_ROOT_FIELDS = Set.of("schema", "slots");

    private DefinitionParser() {
    }

    public static MaterialDefinition parseMaterial(ResourceLocation id, String json) {
        JsonObject root = ParserSupport.root(json);
        ParserSupport.fields(root, MATERIAL_ROOT_FIELDS, "$");
        if (ParserSupport.integer(root, "schema", "$") != 1) {
            throw new IllegalArgumentException("$.schema: only schema 1 is supported");
        }
        if (!root.has("slots") || !root.get("slots").isJsonObject()) {
            throw new IllegalArgumentException("$.slots: expected object");
        }
        JsonObject slotsObject = root.getAsJsonObject("slots");
        if (slotsObject.isEmpty()) {
            throw new IllegalArgumentException("$.slots: at least one slot is required");
        }
        Map<String, ResourceLocation> slots = new LinkedHashMap<>();
        slotsObject.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey().isBlank()) {
                throw new IllegalArgumentException("$.slots: slot names cannot be blank");
            }
            slots.put(entry.getKey(), ParserSupport.id(entry.getValue().getAsString(),
                    "$.slots." + entry.getKey()));
        });
        return new MaterialDefinition(id, slots);
    }

    public static TargetDefinition parseTarget(ResourceLocation id, String json) {
        JsonObject root = ParserSupport.root(json);
        ParserSupport.fields(root, TARGET_ROOT_FIELDS, "$");
        if (ParserSupport.integer(root, "schema", "$") != 1) {
            throw new IllegalArgumentException("$.schema: only schema 1 is supported");
        }
        IntSize size = ParserSupport.size(root.get("size"), "$.size");
        if (size.width() > 256 || size.height() > 256) {
            throw new IllegalArgumentException("$.size: output dimensions cannot exceed 256");
        }
        Map<String, IntSize> grids = parseGrids(root);
        BitSet written = new BitSet(size.width() * size.height());
        List<RegionMapping> regions = parseRegions(root, size, grids, written);
        List<PixelMapping> pixels = parsePixels(root, size, grids, written);
        if (regions.isEmpty() && pixels.isEmpty()) {
            throw new IllegalArgumentException("$: mapping cannot be empty");
        }
        Optional<ResourceLocation> overlay = optionalId(root, "overlay", "$.overlay");
        List<Layer> layers = parseLayers(root, size, grids, "$.layers");
        Map<ResourceLocation, MaterialOverride> overrides = parseOverrides(root, size, grids);
        Optional<ResourceLocation> bodyTextures = Optional.empty();
        if (root.has("body_textures")) {
            JsonElement directory = root.get("body_textures");
            if (!directory.isJsonPrimitive() || !directory.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("$.body_textures: expected resource directory string");
            bodyTextures = optionalId(root, "body_textures", "$.body_textures");
            if (bodyTextures.orElseThrow().getPath().replaceAll("/+$", "").isEmpty())
                throw new IllegalArgumentException("$.body_textures: directory path must not be empty");
        }
        Optional<ComputedCost> cost = parseCost(root);
        return new TargetDefinition(id, size, grids, regions, pixels, overlay, overrides, cost, parsePolicy(root), layers,
                bodyTextures);
    }

    private static RenderPolicy parsePolicy(JsonObject root) {
        if (!root.has("render_policy")) {
            return RenderPolicy.DEFAULT;
        }
        String path = "$.render_policy";
        JsonObject policy = object(root.get("render_policy"), path);
        ParserSupport.fields(policy, POLICY_FIELDS, path);
        // Schema-1 backend hints remain readable; UV-only rendering ignores them.
        if (policy.has("mode")) {
            JsonElement value = policy.get("mode");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(path + ".mode: expected auto, direct or composite");
            }
            if (!Set.of("auto", "direct", "composite").contains(value.getAsString())) {
                throw new IllegalArgumentException(path + ".mode: expected auto, direct or composite");
            }
        }
        int pieces = policyLimit(policy, "max_pieces_per_face", RenderPolicy.DEFAULT.maxPiecesPerFace(), 1);
        int additional = policyLimit(policy, "max_additional_quads", RenderPolicy.DEFAULT.maxAdditionalQuads(), 0);
        int batches = policyLimit(policy, "max_texture_batches", RenderPolicy.DEFAULT.maxTextureBatches(), 1);
        return new RenderPolicy(pieces, additional, batches);
    }

    private static int policyLimit(JsonObject policy, String field, int fallback, int minimum) {
        if (!policy.has(field)) {
            return fallback;
        }
        int value;
        try {
            JsonElement number = policy.get(field);
            if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("not a number");
            }
            // A double comparison loses tiny fractional parts and can underflow to an integer zero.
            value = number.getAsBigDecimal().intValueExact();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("$.render_policy." + field + ": expected integer", error);
        }
        if (value < minimum) {
            throw new IllegalArgumentException("$.render_policy." + field + ": must be at least " + minimum);
        }
        return value;
    }

    private static Map<String, IntSize> parseGrids(JsonObject root) {
        if (!root.has("source_grids") || !root.get("source_grids").isJsonObject()) {
            throw new IllegalArgumentException("$.source_grids: expected object");
        }
        Map<String, IntSize> grids = new LinkedHashMap<>();
        root.getAsJsonObject("source_grids").entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> grids.put(entry.getKey(),
                        ParserSupport.size(entry.getValue(), "$.source_grids." + entry.getKey())));
        return grids;
    }

    private static List<RegionMapping> parseRegions(JsonObject root, IntSize target,
                                                     Map<String, IntSize> grids, BitSet written) {
        JsonArray array = optionalArray(root, "regions", "$.regions");
        List<RegionMapping> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.regions[" + i + "]";
            JsonObject object = object(array.get(i), path);
            ParserSupport.fields(object, REGION_FIELDS, path);
            String slot = ParserSupport.string(object, "source_slot", path);
            IntSize sourceGrid = requireGrid(grids, slot, path + ".source_slot");
            IntRect source = ParserSupport.rect(object.get("source"), path + ".source");
            IntRect destination = ParserSupport.rect(object.get("destination"), path + ".destination");
            MappingTransform transform = MappingTransform.parse(
                    object.has("transform") ? object.get("transform").getAsString() : "identity",
                    path + ".transform");
            if (!source.fits(sourceGrid)) {
                throw new IllegalArgumentException(path + ".source: outside source grid");
            }
            if (!destination.fits(target)) {
                throw new IllegalArgumentException(path + ".destination: outside target");
            }
            int expectedWidth = transform.swapsAxes() ? source.height() : source.width();
            int expectedHeight = transform.swapsAxes() ? source.width() : source.height();
            if (destination.width() != expectedWidth || destination.height() != expectedHeight) {
                throw new IllegalArgumentException(path + ".destination: dimensions do not match transform");
            }
            mark(destination, target.width(), written, path + ".destination");
            result.add(new RegionMapping(slot, source, destination, transform));
        }
        return result;
    }

    private static List<PixelMapping> parsePixels(JsonObject root, IntSize target,
                                                   Map<String, IntSize> grids, BitSet written) {
        JsonArray array = optionalArray(root, "pixels", "$.pixels");
        List<PixelMapping> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.pixels[" + i + "]";
            JsonObject object = object(array.get(i), path);
            ParserSupport.fields(object, PIXEL_FIELDS, path);
            String slot = ParserSupport.string(object, "source_slot", path);
            IntSize sourceGrid = requireGrid(grids, slot, path + ".source_slot");
            IntPoint source = ParserSupport.point(object.get("source"), path + ".source");
            IntPoint destination = ParserSupport.point(object.get("destination"), path + ".destination");
            if (source.x() >= sourceGrid.width() || source.y() >= sourceGrid.height()) {
                throw new IllegalArgumentException(path + ".source: outside source grid");
            }
            if (destination.x() >= target.width() || destination.y() >= target.height()) {
                throw new IllegalArgumentException(path + ".destination: outside target");
            }
            int bit = destination.y() * target.width() + destination.x();
            if (written.get(bit)) {
                throw new IllegalArgumentException(path + ".destination: overlaps an earlier mapping");
            }
            written.set(bit);
            result.add(new PixelMapping(slot, source, destination));
        }
        return result;
    }

    private static Map<ResourceLocation, MaterialOverride> parseOverrides(JsonObject root, IntSize target, Map<String, IntSize> grids) {
        if (!root.has("material_overrides")) {
            return Map.of();
        }
        JsonObject all = object(root.get("material_overrides"), "$.material_overrides");
        Map<ResourceLocation, MaterialOverride> result = new LinkedHashMap<>();
        all.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = "$.material_overrides." + entry.getKey();
            ResourceLocation materialId = ParserSupport.id(entry.getKey(), path);
            JsonObject value = object(entry.getValue(), path);
            ParserSupport.fields(value, OVERRIDE_FIELDS, path);
            Map<String, ResourceLocation> slots = new LinkedHashMap<>();
            if (value.has("slots")) {
                JsonObject slotObject = object(value.get("slots"), path + ".slots");
                slotObject.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(slot ->
                        slots.put(slot.getKey(), ParserSupport.id(slot.getValue().getAsString(),
                                path + ".slots." + slot.getKey())));
            }
            Optional<List<Layer>> layers = value.has("layers")
                    ? Optional.of(parseLayers(value, target, grids, path + ".layers")) : Optional.empty();
            result.put(materialId, new MaterialOverride(slots, optionalId(value, "overlay", path + ".overlay"), layers));
        });
        return result;
    }

    private static List<Layer> parseLayers(JsonObject owner, IntSize target, Map<String, IntSize> grids,
                                           String path) {
        JsonArray array = optionalArray(owner, "layers", path);
        List<Layer> layers = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String itemPath = path + "[" + i + "]";
            JsonObject value = object(array.get(i), itemPath);
            boolean texture = value.has("texture"), model = value.has("model");
            if (texture == model) {
                throw new IllegalArgumentException(itemPath + ": exactly one of texture or model is required");
            }
            int index = ParserSupport.integer(value, "index", itemPath);
            if (texture) {
                ParserSupport.fields(value, TEXTURE_LAYER_FIELDS, itemPath);
                ResourceLocation textureId = ParserSupport.id(ParserSupport.string(value, "texture", itemPath), itemPath + ".texture");
                IntSize grid = value.has("grid") ? ParserSupport.size(value.get("grid"), itemPath + ".grid") : target;
                IntRect source = value.has("source") ? ParserSupport.rect(value.get("source"), itemPath + ".source")
                        : new IntRect(0, 0, grid.width(), grid.height());
                IntRect destination = value.has("destination") ? ParserSupport.rect(value.get("destination"), itemPath + ".destination")
                        : new IntRect(0, 0, target.width(), target.height());
                if (!source.fits(grid)) throw new IllegalArgumentException(itemPath + ".source: outside layer grid");
                if (!destination.fits(target)) throw new IllegalArgumentException(itemPath + ".destination: outside target");
                layers.add(new TextureLayer(index, textureId, grid, source, destination,
                        booleanValue(value, "emissive", itemPath, false),
                        booleanValue(value, "material_variants", itemPath, false)));
            } else {
                ParserSupport.fields(value, MODEL_LAYER_FIELDS, itemPath);
                ResourceLocation modelId = ParserSupport.id(ParserSupport.string(value, "model", itemPath), itemPath + ".model");
                String part = value.has("part") ? ParserSupport.string(value, "part", itemPath) : "head";
                Vec3 offset = vector(value, "offset", itemPath, Vec3.ZERO);
                Vec3 rotation = vector(value, "rotation", itemPath, Vec3.ZERO);
                Vec3 scale = vector(value, "scale", itemPath, new Vec3(1, 1, 1));
                if (scale.x == 0 || scale.y == 0 || scale.z == 0) {
                    throw new IllegalArgumentException(itemPath + ".scale: components cannot be zero");
                }
                Optional<String> sourceSlot = value.has("source_slot")
                        ? Optional.of(ParserSupport.string(value, "source_slot", itemPath)) : Optional.empty();
                if (value.has("source") && sourceSlot.isEmpty()) {
                    throw new IllegalArgumentException(itemPath + ".source: requires source_slot");
                }
                Optional<IntRect> source = Optional.empty();
                if (sourceSlot.isPresent()) {
                    IntSize grid = requireGrid(grids, sourceSlot.get(), itemPath + ".source_slot");
                    IntRect rect = value.has("source") ? ParserSupport.rect(value.get("source"), itemPath + ".source")
                            : new IntRect(0, 0, grid.width(), grid.height());
                    if (!rect.fits(grid)) throw new IllegalArgumentException(itemPath + ".source: outside source grid");
                    source = Optional.of(rect);
                }
                layers.add(new ModelLayer(index, modelId, part, offset, rotation, scale, sourceSlot, source,
                        booleanValue(value, "emissive", itemPath, false)));
            }
        }
        return layers;
    }

    private static boolean booleanValue(JsonObject object, String name, String path, boolean fallback) {
        if (!object.has(name)) return fallback;
        JsonElement value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(path + "." + name + ": expected boolean");
        }
        return value.getAsBoolean();
    }

    private static Vec3 vector(JsonObject object, String name, String path, Vec3 fallback) {
        if (!object.has(name)) return fallback;
        JsonElement element = object.get(name);
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException(path + "." + name + ": expected array of length 3");
        }
        double[] components = new double[3];
        for (int i = 0; i < 3; i++) {
            try {
                JsonElement component = element.getAsJsonArray().get(i);
                if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                components[i] = component.getAsDouble();
                if (!Double.isFinite(components[i])) throw new IllegalArgumentException();
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(path + "." + name + "[" + i + "]: expected finite number", error);
            }
        }
        return new Vec3(components[0], components[1], components[2]);
    }

    private static Optional<ComputedCost> parseCost(JsonObject root) {
        if (!root.has("computed_cost")) {
            return Optional.empty();
        }
        JsonObject cost = object(root.get("computed_cost"), "$.computed_cost");
        ParserSupport.fields(cost, COST_FIELDS, "$.computed_cost");
        try {
            return Optional.of(new ComputedCost(
                    ParserSupport.integer(cost, "max_pieces_per_source_quad", "$.computed_cost"),
                    ParserSupport.integer(cost, "additional_quads", "$.computed_cost")));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("$.computed_cost: " + error.getMessage(), error);
        }
    }

    private static IntSize requireGrid(Map<String, IntSize> grids, String slot, String path) {
        IntSize grid = grids.get(slot);
        if (grid == null) {
            throw new IllegalArgumentException(path + ": slot is absent from source_grids");
        }
        return grid;
    }

    private static void mark(IntRect rect, int targetWidth, BitSet written, String path) {
        for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
            for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
                int bit = y * targetWidth + x;
                if (written.get(bit)) {
                    throw new IllegalArgumentException(path + ": overlaps an earlier mapping");
                }
                written.set(bit);
            }
        }
    }

    private static JsonArray optionalArray(JsonObject root, String name, String path) {
        if (!root.has(name)) {
            return new JsonArray();
        }
        if (!root.get(name).isJsonArray()) {
            throw new IllegalArgumentException(path + ": expected array");
        }
        return root.getAsJsonArray(name);
    }

    private static JsonObject object(JsonElement value, String path) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(path + ": expected object");
        }
        return value.getAsJsonObject();
    }

    private static Optional<ResourceLocation> optionalId(JsonObject object, String name, String path) {
        if (!object.has(name)) {
            return Optional.empty();
        }
        return Optional.of(ParserSupport.id(object.get(name).getAsString(), path));
    }

    private static final class ParserSupport {
    private ParserSupport() {
    }

    static JsonObject root(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("$: invalid JSON object", error);
        }
    }

    static void fields(JsonObject object, Set<String> allowed, String path) {
        for (String name : object.keySet()) {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException(path + "." + name + ": unknown field");
            }
        }
    }

    static int integer(JsonObject object, String name, String path) {
        try {
            JsonElement value = object.get(name);
            if (value == null || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            double number = value.getAsDouble();
            int integer = value.getAsInt();
            if (number != integer) {
                throw new IllegalArgumentException();
            }
            return integer;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + "." + name + ": expected integer", error);
        }
    }

    static String string(JsonObject object, String name, String path) {
        try {
            JsonElement value = object.get(name);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException();
            }
            return value.getAsString();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + "." + name + ": expected string", error);
        }
    }

    static ResourceLocation id(String value, String path) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException(path + ": invalid ResourceLocation " + value);
        }
        return parsed;
    }

    static IntSize size(JsonElement element, String path) {
        JsonArray array = array(element, 2, path);
        try {
            return new IntSize(exactInt(array.get(0), path + "[0]"), exactInt(array.get(1), path + "[1]"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    static IntPoint point(JsonElement element, String path) {
        JsonArray array = array(element, 2, path);
        try {
            return new IntPoint(exactInt(array.get(0), path + "[0]"), exactInt(array.get(1), path + "[1]"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    static IntRect rect(JsonElement element, String path) {
        JsonArray array = array(element, 4, path);
        try {
            return new IntRect(exactInt(array.get(0), path + "[0]"), exactInt(array.get(1), path + "[1]"),
                    exactInt(array.get(2), path + "[2]"), exactInt(array.get(3), path + "[3]"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    private static JsonArray array(JsonElement element, int size, String path) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != size) {
            throw new IllegalArgumentException(path + ": expected array of length " + size);
        }
        return element.getAsJsonArray();
    }

    private static int exactInt(JsonElement element, String path) {
        try {
            double number = element.getAsDouble();
            int integer = element.getAsInt();
            if (number != integer) {
                throw new IllegalArgumentException();
            }
            return integer;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + ": expected integer", error);
        }
    }
    }
}

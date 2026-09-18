package com.nobodiiiii.createbiotech.foundation.render;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.CreateBiotech;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Standard Java block-model parts used by the biological machines.
 *
 * <p>Each rigid part is an ordinary {@code models/block} JSON and is baked through the same
 * {@link PartialModel} path Create uses for funnel flaps, press heads and mechanical-arm pieces.
 * Java owns the hierarchy, pivots and animation state.</p>
 */
public final class MachineCreatureModels {

	private static final ModelSpec SPIDER = MachineCreatureModelData.spider();
	private static final ModelSpec SPIDER_EYES = SPIDER.withModelId(CreateBiotech.asResource("spider_eyes"));
	private static final ModelSpec SQUID = MachineCreatureModelData.squid();
	private static final ModelSpec FROG = MachineCreatureModelData.frog();
	private static final ModelSpec EVOKER = MachineCreatureModelData.evoker();
	private static final ModelSpec ALLAY = MachineCreatureModelData.allay();
	private static final ModelSpec SALMON = MachineCreatureModelData.salmon();
	private static final ModelSpec SHULKER = MachineCreatureModelData.shulker();
	private static final ModelSpec SLIME_INNER = MachineCreatureModelData.slimeInner();
	private static final ModelSpec SLIME_OUTER = MachineCreatureModelData.slimeOuter();
	private static final ModelSpec MAGMA_CUBE = MachineCreatureModelData.magmaCube();
	private static final ModelSpec CREEPER = MachineCreatureModelData.creeper();
	private static final ModelSpec CREEPER_POWER = MachineCreatureModelData.creeperPower();
	private static final ModelSpec BOOK = MachineCreatureModelData.book();

	private static final List<ModelSpec> ALL = List.of(SPIDER, SPIDER_EYES, SQUID, FROG, EVOKER,
		ALLAY, SALMON, SHULKER, SLIME_INNER, SLIME_OUTER, MAGMA_CUBE, CREEPER,
		CREEPER_POWER, BOOK);

	private MachineCreatureModels() {}

	public static MachineCreatureModel spider() {
		return new MachineCreatureModel(SPIDER);
	}

	public static MachineCreatureModel spiderEyes() {
		return new MachineCreatureModel(SPIDER_EYES);
	}

	public static MachineCreatureModel squid() {
		return new MachineCreatureModel(SQUID);
	}

	public static MachineCreatureModel frog() {
		return new MachineCreatureModel(FROG);
	}

	public static MachineCreatureModel evoker() {
		return new MachineCreatureModel(EVOKER);
	}

	public static MachineCreatureModel allay() {
		return new MachineCreatureModel(ALLAY);
	}

	public static MachineCreatureModel salmon() {
		return new MachineCreatureModel(SALMON);
	}

	public static MachineCreatureModel shulker() {
		return new MachineCreatureModel(SHULKER);
	}

	public static MachineCreatureModel slimeInner() {
		return new MachineCreatureModel(SLIME_INNER);
	}

	public static MachineCreatureModel slimeOuter() {
		return new MachineCreatureModel(SLIME_OUTER);
	}

	public static MachineCreatureModel magmaCube() {
		return new MachineCreatureModel(MAGMA_CUBE);
	}

	public static MachineCreatureModel creeper(float inflation) {
		if (inflation == 0.0f)
			return new MachineCreatureModel(CREEPER);
		if (inflation == 2.0f)
			return new MachineCreatureModel(CREEPER_POWER);
		throw new IllegalArgumentException("Only the normal and powered creeper machine models are defined");
	}

	public static MachineCreatureModel book() {
		return new MachineCreatureModel(BOOK);
	}

	/** All standalone models that must be included in Minecraft's model-baking pass. */
	public static Set<PartialModel> allPartials() {
		Set<PartialModel> result = new LinkedHashSet<>();
		for (ModelSpec model : ALL)
			for (PartSpec part : model.parts())
				if (part.hasGeometry())
					result.add(PartialModel.of(partLocation(model.id(), part.modelName())));
		return Set.copyOf(result);
	}

	static ResourceLocation partLocation(ResourceLocation modelId, String modelName) {
		return ResourceLocation.fromNamespaceAndPath(modelId.getNamespace(),
			"block/machine_creature/" + modelId.getPath() + "/" + modelName);
	}

	public enum Layer {
		CUTOUT,
		TRANSLUCENT;

		RenderType renderType() {
			return this == TRANSLUCENT ? RenderType.translucent() : RenderType.cutout();
		}
	}

	record ModelSpec(ResourceLocation id, Layer layer, List<PartSpec> parts,
		Map<String, AnchorSpec> anchors) {
		ModelSpec {
			parts = List.copyOf(parts);
			anchors = Map.copyOf(anchors);
		}

		ModelSpec withModelId(ResourceLocation replacement) {
			return new ModelSpec(replacement, layer, parts, anchors);
		}
	}

	record PartSpec(String path, String parentPath, String modelName,
		float x, float y, float z, float xRot, float yRot, float zRot,
		boolean visible, boolean skipDraw, boolean hasGeometry, Bounds firstCube) {}

	record AnchorSpec(String partPath, float x, float y, float z) {}

	public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {}
}

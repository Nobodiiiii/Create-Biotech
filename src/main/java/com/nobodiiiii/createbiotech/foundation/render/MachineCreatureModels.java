package com.nobodiiiii.createbiotech.foundation.render;

import java.util.List;
import java.util.Set;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Resource-backed geometry for creatures incorporated into machines.
 *
 * <p>The JSON files own geometry, UVs, hierarchy, rest poses and mechanical anchors. Java renderers
 * continue to own animation, materials and machine state. Each returned model has independent pose
 * state and transparently rebakes itself after a client resource reload.</p>
 */
public final class MachineCreatureModels {

	private static final ModelSpec SPIDER = spec("spider",
		parts("head", "body0", "body1", "right_hind_leg", "left_hind_leg",
			"right_middle_hind_leg", "left_middle_hind_leg", "right_middle_front_leg",
			"left_middle_front_leg", "right_front_leg", "left_front_leg"),
		anchors("left_front_leg_socket", "right_front_leg_socket", "left_middle_front_leg_socket",
			"right_middle_front_leg_socket", "left_middle_hind_leg_socket", "right_middle_hind_leg_socket",
			"left_hind_leg_socket", "right_hind_leg_socket"));
	private static final ModelSpec SQUID = spec("squid",
		parts("body", "tentacle0", "tentacle1", "tentacle2", "tentacle3", "tentacle4", "tentacle5",
			"tentacle6", "tentacle7"));
	private static final ModelSpec FROG = spec("frog",
		parts("body", "body/head", "body/head/eyes", "body/head/eyes/right_eye",
			"body/head/eyes/left_eye", "body/croaking_body", "body/tongue", "body/left_arm",
			"body/left_arm/left_hand", "body/right_arm", "body/right_arm/right_hand", "left_leg",
			"left_leg/left_foot", "right_leg", "right_leg/right_foot"),
		anchors("tongue_pivot", "tongue_front"));
	private static final ModelSpec EVOKER = spec("evoker",
		parts("head", "head/nose", "body", "arms", "arms/left_shoulder", "right_leg", "left_leg",
			"right_arm", "left_arm"));
	private static final ModelSpec ALLAY = spec("allay",
		parts("head", "body", "body/right_arm", "body/left_arm", "body/right_wing", "body/left_wing"));
	private static final ModelSpec SALMON = spec("salmon",
		parts("body_front", "body_front/top_front_fin", "body_back", "body_back/back_fin",
			"body_back/top_back_fin", "head", "right_fin", "left_fin"));
	private static final ModelSpec SHULKER = spec("shulker", parts("lid", "base"));
	private static final ModelSpec SLIME_INNER = spec("slime_inner",
		parts("cube", "right_eye", "left_eye", "mouth"));
	private static final ModelSpec SLIME_OUTER = spec("slime_outer", parts("cube"));
	private static final ModelSpec MAGMA_CUBE = spec("magma_cube",
		parts("cube0", "cube1", "cube2", "cube3", "cube4", "cube5", "cube6", "cube7", "inside_cube"));
	private static final ModelSpec CREEPER = spec("creeper",
		parts("head", "body", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg"));
	private static final ModelSpec CREEPER_POWER = spec("creeper_power",
		parts("head", "body", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg"));
	private static final ModelSpec BOOK = spec("book",
		parts("left_lid", "right_lid", "seam", "left_pages", "right_pages", "flip_page1", "flip_page2"));

	private static final List<ModelSpec> ALL = List.of(SPIDER, SQUID, FROG, EVOKER, ALLAY, SALMON,
		SHULKER, SLIME_INNER, SLIME_OUTER, MAGMA_CUBE, CREEPER, CREEPER_POWER, BOOK);

	public static final ResourceManagerReloadListener RELOAD_LISTENER =
		resourceManager -> MachineCreatureModelLoader.reload(resourceManager, ALL);

	private MachineCreatureModels() {}

	public static MachineCreatureModel spider() {
		return new MachineCreatureModel(SPIDER);
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
		return new MachineCreatureModel(ALLAY, RenderType::entityTranslucent);
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
		return new MachineCreatureModel(BOOK, RenderType::entitySolid);
	}

	private static ModelSpec spec(String path, Set<String> requiredParts) {
		return spec(path, requiredParts, Set.of());
	}

	private static ModelSpec spec(String path, Set<String> requiredParts, Set<String> requiredAnchors) {
		return new ModelSpec(CreateBiotech.asResource(path), requiredParts, requiredAnchors);
	}

	private static Set<String> parts(String... names) {
		return Set.of(names);
	}

	private static Set<String> anchors(String... names) {
		return Set.of(names);
	}

	record ModelSpec(ResourceLocation id, Set<String> requiredParts, Set<String> requiredAnchors) {}
}

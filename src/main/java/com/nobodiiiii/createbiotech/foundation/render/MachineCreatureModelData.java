package com.nobodiiiii.createbiotech.foundation.render;

import java.util.List;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;

/** Generated rest-pose and hierarchy data for the standard block-model parts. */
final class MachineCreatureModelData {
	private MachineCreatureModelData() {}

	static MachineCreatureModels.ModelSpec allay() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("allay"), MachineCreatureModels.Layer.TRANSLUCENT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "_root", 				0f, 23.5f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("head", "$root", "head", 				0f, -3.99f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2.5f, -5f, -2.5f, 2.5f, 0f, 2.5f)),
			new MachineCreatureModels.PartSpec("body", "$root", "body", 				0f, -4f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1.5f, 0f, -1f, 1.5f, 4f, 1f)),
			new MachineCreatureModels.PartSpec("body/right_arm", "body", "body_right_arm", 				-1.75f, 0.5f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-0.74f, -0.49f, -0.99f, 0.24f, 3.49f, 0.99f)),
			new MachineCreatureModels.PartSpec("body/left_arm", "body", "body_left_arm", 				1.75f, 0.5f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-0.24f, -0.49f, -0.99f, 0.74f, 3.49f, 0.99f)),
			new MachineCreatureModels.PartSpec("body/right_wing", "body", "body_right_wing", 				-0.5f, 0f, 0.6f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, 1f, 0f, 0f, 6f, 8f)),
			new MachineCreatureModels.PartSpec("body/left_wing", "body", "body_left_wing", 				0.5f, 0f, 0.6f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, 1f, 0f, 0f, 6f, 8f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec book() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("book"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("left_lid", "$root", "left_lid", 				0f, 0f, -1f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-6f, -5f, -0.005f, 0f, 5f, 0f)),
			new MachineCreatureModels.PartSpec("right_lid", "$root", "right_lid", 				0f, 0f, 1f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, -5f, -0.005f, 6f, 5f, 0f)),
			new MachineCreatureModels.PartSpec("seam", "$root", "seam", 				0f, 0f, 0f, 0f, 1.570796f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, -5f, 0f, 1f, 5f, 0.005f)),
			new MachineCreatureModels.PartSpec("left_pages", "$root", "left_pages", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, -4f, -0.99f, 5f, 4f, 0.01f)),
			new MachineCreatureModels.PartSpec("right_pages", "$root", "right_pages", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, -4f, -0.01f, 5f, 4f, 0.99f)),
			new MachineCreatureModels.PartSpec("flip_page1", "$root", "flip_page1", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, -4f, 0f, 5f, 4f, 0.005f)),
			new MachineCreatureModels.PartSpec("flip_page2", "$root", "flip_page2", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, -4f, 0f, 5f, 4f, 0.005f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec creeper() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("creeper"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("head", "$root", "head", 				0f, 6f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, -8f, -4f, 4f, 0f, 4f)),
			new MachineCreatureModels.PartSpec("body", "$root", "body", 				0f, 6f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 0f, -2f, 4f, 12f, 2f)),
			new MachineCreatureModels.PartSpec("right_hind_leg", "$root", "right_hind_leg", 				-2f, 18f, 4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -2f, 2f, 6f, 2f)),
			new MachineCreatureModels.PartSpec("left_hind_leg", "$root", "left_hind_leg", 				2f, 18f, 4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -2f, 2f, 6f, 2f)),
			new MachineCreatureModels.PartSpec("right_front_leg", "$root", "right_front_leg", 				-2f, 18f, -4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -2f, 2f, 6f, 2f)),
			new MachineCreatureModels.PartSpec("left_front_leg", "$root", "left_front_leg", 				2f, 18f, -4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -2f, 2f, 6f, 2f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec creeperPower() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("creeper_power"), MachineCreatureModels.Layer.TRANSLUCENT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("head", "$root", "head", 				0f, 6f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-6f, -10f, -6f, 6f, 2f, 6f)),
			new MachineCreatureModels.PartSpec("body", "$root", "body", 				0f, 6f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-6f, -2f, -4f, 6f, 14f, 4f)),
			new MachineCreatureModels.PartSpec("right_hind_leg", "$root", "right_hind_leg", 				-2f, 18f, 4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, -2f, -4f, 4f, 8f, 4f)),
			new MachineCreatureModels.PartSpec("left_hind_leg", "$root", "left_hind_leg", 				2f, 18f, 4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, -2f, -4f, 4f, 8f, 4f)),
			new MachineCreatureModels.PartSpec("right_front_leg", "$root", "right_front_leg", 				-2f, 18f, -4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, -2f, -4f, 4f, 8f, 4f)),
			new MachineCreatureModels.PartSpec("left_front_leg", "$root", "left_front_leg", 				2f, 18f, -4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, -2f, -4f, 4f, 8f, 4f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec evoker() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("evoker"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("head", "$root", "head", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, -10f, -4f, 4f, 0f, 4f)),
			new MachineCreatureModels.PartSpec("head/nose", "head", "head_nose", 				0f, -2f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, -1f, -6f, 1f, 3f, -4f)),
			new MachineCreatureModels.PartSpec("body", "$root", "body", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 0f, -3f, 4f, 12f, 3f)),
			new MachineCreatureModels.PartSpec("arms", "$root", "arms", 				0f, 3f, -1f, -0.75f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-8f, -2f, -2f, -4f, 6f, 2f)),
			new MachineCreatureModels.PartSpec("arms/left_shoulder", "arms", "arms_left_shoulder", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(4f, -2f, -2f, 8f, 6f, 2f)),
			new MachineCreatureModels.PartSpec("right_leg", "$root", "right_leg", 				-2f, 12f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -2f, 2f, 12f, 2f)),
			new MachineCreatureModels.PartSpec("left_leg", "$root", "left_leg", 				2f, 12f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -2f, 2f, 12f, 2f)),
			new MachineCreatureModels.PartSpec("right_arm", "$root", "right_arm", 				-5f, 2f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-3f, -2f, -2f, 1f, 10f, 2f)),
			new MachineCreatureModels.PartSpec("left_arm", "$root", "left_arm", 				5f, 2f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, -2f, -2f, 3f, 10f, 2f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec frog() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("frog"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "_root", 				0f, 24f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("body", "$root", "body", 				0f, -2f, 4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-3.5f, -2f, -8f, 3.5f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("body/head", "body", "body_head", 				0f, -2f, -1f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-3.5f, -1f, -7f, 3.5f, -1f, 2f)),
			new MachineCreatureModels.PartSpec("body/head/eyes", "body/head", "body_head_eyes", 				-0.5f, 0f, 2f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("body/head/eyes/right_eye", "body/head/eyes", "body_head_eyes_right_eye", 				-1.5f, -3f, -6.5f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1.5f, -1f, -1.5f, 1.5f, 1f, 1.5f)),
			new MachineCreatureModels.PartSpec("body/head/eyes/left_eye", "body/head/eyes", "body_head_eyes_left_eye", 				2.5f, -3f, -6.5f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1.5f, -1f, -1.5f, 1.5f, 1f, 1.5f)),
			new MachineCreatureModels.PartSpec("body/croaking_body", "body", "body_croaking_body", 				0f, -1f, -5f, 0f, 0f, 0f, false, false, true, new MachineCreatureModels.Bounds(-3.4f, 0f, -2.8f, 3.4f, 1.8f, 0f)),
			new MachineCreatureModels.PartSpec("body/tongue", "body", "body_tongue", 				0f, -1.01f, 1f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -7.1f, 2f, 0f, -0.1f)),
			new MachineCreatureModels.PartSpec("body/left_arm", "body", "body_left_arm", 				4f, -1f, -6.5f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 3f, 2f)),
			new MachineCreatureModels.PartSpec("body/left_arm/left_hand", "body/left_arm", "body_left_arm_left_hand", 				0f, 3f, -1f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 0.01f, -4f, 4f, 0.01f, 4f)),
			new MachineCreatureModels.PartSpec("body/right_arm", "body", "body_right_arm", 				-4f, -1f, -6.5f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 3f, 2f)),
			new MachineCreatureModels.PartSpec("body/right_arm/right_hand", "body/right_arm", "body_right_arm_right_hand", 				0f, 3f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 0.01f, -5f, 4f, 0.01f, 3f)),
			new MachineCreatureModels.PartSpec("left_leg", "$root", "left_leg", 				3.5f, -3f, 4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -2f, 2f, 3f, 2f)),
			new MachineCreatureModels.PartSpec("left_leg/left_foot", "left_leg", "left_leg_left_foot", 				2f, 3f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 0.01f, -4f, 4f, 0.01f, 4f)),
			new MachineCreatureModels.PartSpec("right_leg", "$root", "right_leg", 				-3.5f, -3f, 4f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, -2f, 1f, 3f, 2f)),
			new MachineCreatureModels.PartSpec("right_leg/right_foot", "right_leg", "right_leg_right_foot", 				-2f, 3f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 0.01f, -4f, 4f, 0.01f, 4f))
		), Map.ofEntries(
			Map.entry("tongue_pivot", new MachineCreatureModels.AnchorSpec("$root", 0f, -3.01f, 5f)),
			Map.entry("tongue_front", new MachineCreatureModels.AnchorSpec("$root", 0f, -3.01f, -2.1f))
		)
		);
	}

	static MachineCreatureModels.ModelSpec magmaCube() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("magma_cube"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("cube0", "$root", "cube0", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 16f, -4f, 4f, 17f, 4f)),
			new MachineCreatureModels.PartSpec("cube1", "$root", "cube1", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 17f, -4f, 4f, 18f, 4f)),
			new MachineCreatureModels.PartSpec("cube2", "$root", "cube2", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 18f, -4f, 4f, 19f, 4f)),
			new MachineCreatureModels.PartSpec("cube3", "$root", "cube3", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 19f, -4f, 4f, 20f, 4f)),
			new MachineCreatureModels.PartSpec("cube4", "$root", "cube4", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 20f, -4f, 4f, 21f, 4f)),
			new MachineCreatureModels.PartSpec("cube5", "$root", "cube5", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 21f, -4f, 4f, 22f, 4f)),
			new MachineCreatureModels.PartSpec("cube6", "$root", "cube6", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 22f, -4f, 4f, 23f, 4f)),
			new MachineCreatureModels.PartSpec("cube7", "$root", "cube7", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 23f, -4f, 4f, 24f, 4f)),
			new MachineCreatureModels.PartSpec("inside_cube", "$root", "inside_cube", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-2f, 18f, -2f, 2f, 22f, 2f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec salmon() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("salmon"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("body_front", "$root", "body_front", 				0f, 20f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1.5f, -2.5f, 0f, 1.5f, 2.5f, 8f)),
			new MachineCreatureModels.PartSpec("body_front/top_front_fin", "body_front", "body_front_top_front_fin", 				0f, -4.5f, 5f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, 0f, 0f, 0f, 2f, 3f)),
			new MachineCreatureModels.PartSpec("body_back", "$root", "body_back", 				0f, 20f, 8f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1.5f, -2.5f, 0f, 1.5f, 2.5f, 8f)),
			new MachineCreatureModels.PartSpec("body_back/back_fin", "body_back", "body_back_back_fin", 				0f, 0f, 8f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, -2.5f, 0f, 0f, 2.5f, 6f)),
			new MachineCreatureModels.PartSpec("body_back/top_back_fin", "body_back", "body_back_top_back_fin", 				0f, -4.5f, -1f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, 0f, 0f, 0f, 2f, 4f)),
			new MachineCreatureModels.PartSpec("head", "$root", "head", 				0f, 20f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, -2f, -3f, 1f, 2f, 0f)),
			new MachineCreatureModels.PartSpec("right_fin", "$root", "right_fin", 				-1.5f, 21.5f, 0f, 0f, 0f, -0.785398f, true, false, true, new MachineCreatureModels.Bounds(-2f, 0f, 0f, 0f, 0f, 2f)),
			new MachineCreatureModels.PartSpec("left_fin", "$root", "left_fin", 				1.5f, 21.5f, 0f, 0f, 0f, 0.785398f, true, false, true, new MachineCreatureModels.Bounds(0f, 0f, 0f, 2f, 0f, 2f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec shulker() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("shulker"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("lid", "$root", "lid", 				0f, 24f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-8f, -16f, -8f, 8f, -4f, 8f)),
			new MachineCreatureModels.PartSpec("base", "$root", "base", 				0f, 24f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-8f, -8f, -8f, 8f, 0f, 8f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec slimeInner() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("slime_inner"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("cube", "$root", "cube", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-3f, 17f, -3f, 3f, 23f, 3f)),
			new MachineCreatureModels.PartSpec("right_eye", "$root", "right_eye", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-3.25f, 18f, -3.5f, -1.25f, 20f, -1.5f)),
			new MachineCreatureModels.PartSpec("left_eye", "$root", "left_eye", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(1.25f, 18f, -3.5f, 3.25f, 20f, -1.5f)),
			new MachineCreatureModels.PartSpec("mouth", "$root", "mouth", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(0f, 21f, -3.5f, 1f, 22f, -2.5f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec slimeOuter() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("slime_outer"), MachineCreatureModels.Layer.TRANSLUCENT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("cube", "$root", "cube", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, 16f, -4f, 4f, 24f, 4f))
		), Map.of()
		);
	}

	static MachineCreatureModels.ModelSpec spider() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("spider"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("head", "$root", "head", 				0f, 15f, -3f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-4f, -4f, -8f, 4f, 4f, 0f)),
			new MachineCreatureModels.PartSpec("body0", "$root", "body0", 				0f, 15f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-3f, -3f, -3f, 3f, 3f, 3f)),
			new MachineCreatureModels.PartSpec("body1", "$root", "body1", 				0f, 15f, 9f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-5f, -4f, -6f, 5f, 4f, 6f)),
			new MachineCreatureModels.PartSpec("right_hind_leg", "$root", "right_hind_leg", 				-4f, 15f, 2f, 0f, 0.785398f, -0.785398f, true, false, true, new MachineCreatureModels.Bounds(-15f, -1f, -1f, 1f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("left_hind_leg", "$root", "left_hind_leg", 				4f, 15f, 2f, 0f, -0.785398f, 0.785398f, true, false, true, new MachineCreatureModels.Bounds(-1f, -1f, -1f, 15f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("right_middle_hind_leg", "$root", "right_middle_hind_leg", 				-4f, 15f, 1f, 0f, 0.392699f, -0.581195f, true, false, true, new MachineCreatureModels.Bounds(-15f, -1f, -1f, 1f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("left_middle_hind_leg", "$root", "left_middle_hind_leg", 				4f, 15f, 1f, 0f, -0.392699f, 0.581195f, true, false, true, new MachineCreatureModels.Bounds(-1f, -1f, -1f, 15f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("right_middle_front_leg", "$root", "right_middle_front_leg", 				-4f, 15f, 0f, 0f, -0.392699f, -0.581195f, true, false, true, new MachineCreatureModels.Bounds(-15f, -1f, -1f, 1f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("left_middle_front_leg", "$root", "left_middle_front_leg", 				4f, 15f, 0f, 0f, 0.392699f, 0.581195f, true, false, true, new MachineCreatureModels.Bounds(-1f, -1f, -1f, 15f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("right_front_leg", "$root", "right_front_leg", 				-4f, 15f, -1f, 0f, -0.785398f, -0.785398f, true, false, true, new MachineCreatureModels.Bounds(-15f, -1f, -1f, 1f, 1f, 1f)),
			new MachineCreatureModels.PartSpec("left_front_leg", "$root", "left_front_leg", 				4f, 15f, -1f, 0f, 0.785398f, 0.785398f, true, false, true, new MachineCreatureModels.Bounds(-1f, -1f, -1f, 15f, 1f, 1f))
		), Map.ofEntries(
			Map.entry("right_hind_leg_socket", new MachineCreatureModels.AnchorSpec("right_hind_leg", -14f, 0f, 0f)),
			Map.entry("left_hind_leg_socket", new MachineCreatureModels.AnchorSpec("left_hind_leg", 14f, 0f, 0f)),
			Map.entry("right_middle_hind_leg_socket", new MachineCreatureModels.AnchorSpec("right_middle_hind_leg", -14f, 0f, 0f)),
			Map.entry("left_middle_hind_leg_socket", new MachineCreatureModels.AnchorSpec("left_middle_hind_leg", 14f, 0f, 0f)),
			Map.entry("right_middle_front_leg_socket", new MachineCreatureModels.AnchorSpec("right_middle_front_leg", -14f, 0f, 0f)),
			Map.entry("left_middle_front_leg_socket", new MachineCreatureModels.AnchorSpec("left_middle_front_leg", 14f, 0f, 0f)),
			Map.entry("right_front_leg_socket", new MachineCreatureModels.AnchorSpec("right_front_leg", -14f, 0f, 0f)),
			Map.entry("left_front_leg_socket", new MachineCreatureModels.AnchorSpec("left_front_leg", 14f, 0f, 0f))
		)
		);
	}

	static MachineCreatureModels.ModelSpec squid() {
		return new MachineCreatureModels.ModelSpec(CreateBiotech.asResource("squid"), MachineCreatureModels.Layer.CUTOUT, List.of(
			new MachineCreatureModels.PartSpec("$root", null, "root", 				0f, 0f, 0f, 0f, 0f, 0f, true, false, false, null),
			new MachineCreatureModels.PartSpec("body", "$root", "body", 				0f, 8f, 0f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-6.02f, -8.02f, -6.02f, 6.02f, 8.02f, 6.02f)),
			new MachineCreatureModels.PartSpec("tentacle0", "$root", "tentacle0", 				5f, 15f, 0f, 0f, 1.570796f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f)),
			new MachineCreatureModels.PartSpec("tentacle1", "$root", "tentacle1", 				3.535534f, 15f, 3.535534f, 0f, 0.785398f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f)),
			new MachineCreatureModels.PartSpec("tentacle2", "$root", "tentacle2", 				0f, 15f, 5f, 0f, 0f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f)),
			new MachineCreatureModels.PartSpec("tentacle3", "$root", "tentacle3", 				-3.535534f, 15f, 3.535534f, 0f, -0.785398f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f)),
			new MachineCreatureModels.PartSpec("tentacle4", "$root", "tentacle4", 				-5f, 15f, 0f, 0f, -1.570796f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f)),
			new MachineCreatureModels.PartSpec("tentacle5", "$root", "tentacle5", 				-3.535534f, 15f, -3.535534f, 0f, -2.356194f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f)),
			new MachineCreatureModels.PartSpec("tentacle6", "$root", "tentacle6", 				0f, 15f, -5f, 0f, -3.141593f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f)),
			new MachineCreatureModels.PartSpec("tentacle7", "$root", "tentacle7", 				3.535534f, 15f, -3.535534f, 0f, -3.926991f, 0f, true, false, true, new MachineCreatureModels.Bounds(-1f, 0f, -1f, 1f, 18f, 1f))
		), Map.of()
		);
	}

}

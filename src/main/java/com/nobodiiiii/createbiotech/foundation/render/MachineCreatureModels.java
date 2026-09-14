package com.nobodiiiii.createbiotech.foundation.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Fixed 1.21.1 geometry and UV layouts for creatures incorporated into machines. These definitions
 * deliberately do not call vanilla model factories or EntityModelSet.bakeLayer: CEM replacements
 * cannot change the dimensions, hierarchy or attachment pivots that the machinery depends on.
 */
public final class MachineCreatureModels {

	private MachineCreatureModels() {}

	public static MachineCreatureModel spider() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		box(root, "head", 32, 4, -4, -4, -8, 8, 8, 8, PartPose.offset(0, 15, -3));
		box(root, "body0", 0, 0, -3, -3, -3, 6, 6, 6, PartPose.offset(0, 15, 0));
		box(root, "body1", 0, 12, -5, -4, -6, 10, 8, 12, PartPose.offset(0, 15, 9));
		String[] rows = { "hind_leg", "middle_hind_leg", "middle_front_leg", "front_leg" };
		float[] yaw = { Mth.PI / 4, Mth.PI / 8, -Mth.PI / 8, -Mth.PI / 4 };
		for (int row = 0; row < rows.length; row++) {
			float roll = row == 0 || row == 3 ? Mth.PI / 4 : 0.58119464f;
			root.addOrReplaceChild("right_" + rows[row],
				CubeListBuilder.create().texOffs(18, 0).addBox(-15, -1, -1, 16, 2, 2),
				PartPose.offsetAndRotation(-4, 15, 2 - row, 0, yaw[row], -roll));
			root.addOrReplaceChild("left_" + rows[row],
				CubeListBuilder.create().texOffs(18, 0).mirror().addBox(-1, -1, -1, 16, 2, 2),
				PartPose.offsetAndRotation(4, 15, 2 - row, 0, -yaw[row], roll));
		}
		return bake(mesh, 64, 32);
	}

	public static MachineCreatureModel squid() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
			.addBox(-6, -8, -6, 12, 16, 12, new CubeDeformation(0.02f)), PartPose.offset(0, 8, 0));
		for (int index = 0; index < 8; index++) {
			double angle = index * Math.PI / 4;
			box(root, "tentacle" + index, 48, 0, -1, 0, -1, 2, 18, 2,
				PartPose.offsetAndRotation((float) Math.cos(angle) * 5, 15,
					(float) Math.sin(angle) * 5, 0, (float) (Math.PI / 2 - angle), 0));
		}
		return bake(mesh, 64, 32);
	}

	public static MachineCreatureModel frog() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(),
			PartPose.offset(0, 24, 0));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(3, 1).addBox(-3.5f, -2, -8, 7, 3, 9)
			.texOffs(23, 22).addBox(-3.5f, -1, -8, 7, 0, 9), PartPose.offset(0, -2, 4));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(23, 13).addBox(-3.5f, -1, -7, 7, 0, 9)
			.texOffs(0, 13).addBox(-3.5f, -2, -7, 7, 3, 9), PartPose.offset(0, -2, -1));
		PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(-0.5f, 0, 2));
		box(eyes, "right_eye", 0, 0, -1.5f, -1, -1.5f, 3, 2, 3, PartPose.offset(-1.5f, -3, -6.5f));
		box(eyes, "left_eye", 0, 5, -1.5f, -1, -1.5f, 3, 2, 3, PartPose.offset(2.5f, -3, -6.5f));
		body.addOrReplaceChild("croaking_body", CubeListBuilder.create().texOffs(26, 5)
			.addBox(-3.5f, -0.1f, -2.9f, 7, 2, 3, new CubeDeformation(-0.1f)), PartPose.offset(0, -1, -5));
		box(body, "tongue", 17, 13, -2, 0, -7.1f, 4, 0, 7, PartPose.offset(0, -1.01f, 1));
		PartDefinition leftArm = box(body, "left_arm", 0, 32, -1, 0, -1, 2, 3, 3,
			PartPose.offset(4, -1, -6.5f));
		box(leftArm, "left_hand", 18, 40, -4, 0.01f, -4, 8, 0, 8, PartPose.offset(0, 3, -1));
		PartDefinition rightArm = box(body, "right_arm", 0, 38, -1, 0, -1, 2, 3, 3,
			PartPose.offset(-4, -1, -6.5f));
		box(rightArm, "right_hand", 2, 40, -4, 0.01f, -5, 8, 0, 8, PartPose.offset(0, 3, 0));
		PartDefinition leftLeg = box(root, "left_leg", 14, 25, -1, 0, -2, 3, 3, 4,
			PartPose.offset(3.5f, -3, 4));
		box(leftLeg, "left_foot", 2, 32, -4, 0.01f, -4, 8, 0, 8, PartPose.offset(2, 3, 0));
		PartDefinition rightLeg = box(root, "right_leg", 0, 25, -2, 0, -2, 3, 3, 4,
			PartPose.offset(-3.5f, -3, 4));
		box(rightLeg, "right_foot", 18, 32, -4, 0.01f, -4, 8, 0, 8, PartPose.offset(-2, 3, 0));
		ModelPart baked = LayerDefinition.create(mesh, 48, 48).bakeRoot().getChild("root");
		baked.getChild("body").getChild("croaking_body").visible = false;
		return new MachineCreatureModel(baked);
	}

	public static MachineCreatureModel evoker() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = box(root, "head", 0, 0, -4, -10, -4, 8, 10, 8, PartPose.ZERO);
		box(head, "nose", 24, 0, -1, -1, -6, 2, 4, 2, PartPose.offset(0, -2, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(16, 20).addBox(-4, 0, -3, 8, 12, 6)
			.texOffs(0, 38).addBox(-4, 0, -3, 8, 20, 6, new CubeDeformation(0.5f)), PartPose.ZERO);
		PartDefinition arms = root.addOrReplaceChild("arms", CubeListBuilder.create()
			.texOffs(44, 22).addBox(-8, -2, -2, 4, 8, 4)
			.texOffs(40, 38).addBox(-4, 2, -2, 8, 4, 4),
			PartPose.offsetAndRotation(0, 3, -1, -0.75f, 0, 0));
		arms.addOrReplaceChild("left_shoulder", CubeListBuilder.create().texOffs(44, 22).mirror()
			.addBox(4, -2, -2, 4, 8, 4), PartPose.ZERO);
		box(root, "right_leg", 0, 22, -2, 0, -2, 4, 12, 4, PartPose.offset(-2, 12, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 22).mirror()
			.addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(2, 12, 0));
		box(root, "right_arm", 40, 46, -3, -2, -2, 4, 12, 4, PartPose.offset(-5, 2, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 46).mirror()
			.addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
		return bake(mesh, 64, 64);
	}

	public static MachineCreatureModel allay() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(),
			PartPose.offset(0, 23.5f, 0));
		box(root, "head", 0, 0, -2.5f, -5, -2.5f, 5, 5, 5, PartPose.offset(0, -3.99f, 0));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 10).addBox(-1.5f, 0, -1, 3, 4, 2)
			.texOffs(0, 16).addBox(-1.5f, 0, -1, 3, 5, 2, new CubeDeformation(-0.2f)),
			PartPose.offset(0, -4, 0));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(23, 0)
			.addBox(-0.75f, -0.5f, -1, 1, 4, 2, new CubeDeformation(-0.01f)), PartPose.offset(-1.75f, 0.5f, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(23, 6)
			.addBox(-0.25f, -0.5f, -1, 1, 4, 2, new CubeDeformation(-0.01f)), PartPose.offset(1.75f, 0.5f, 0));
		box(body, "right_wing", 16, 14, 0, 1, 0, 0, 5, 8, PartPose.offset(-0.5f, 0, 0.6f));
		box(body, "left_wing", 16, 14, 0, 1, 0, 0, 5, 8, PartPose.offset(0.5f, 0, 0.6f));
		return new MachineCreatureModel(LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("root"),
			RenderType::entityTranslucent);
	}

	public static MachineCreatureModel salmon() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition front = box(root, "body_front", 0, 0, -1.5f, -2.5f, 0, 3, 5, 8,
			PartPose.offset(0, 20, 0));
		PartDefinition back = box(root, "body_back", 0, 13, -1.5f, -2.5f, 0, 3, 5, 8,
			PartPose.offset(0, 20, 8));
		box(root, "head", 22, 0, -1, -2, -3, 2, 4, 3, PartPose.offset(0, 20, 0));
		box(back, "back_fin", 20, 10, 0, -2.5f, 0, 0, 5, 6, PartPose.offset(0, 0, 8));
		box(front, "top_front_fin", 2, 1, 0, 0, 0, 0, 2, 3, PartPose.offset(0, -4.5f, 5));
		box(back, "top_back_fin", 0, 2, 0, 0, 0, 0, 2, 4, PartPose.offset(0, -4.5f, -1));
		box(root, "right_fin", -4, 0, -2, 0, 0, 2, 0, 2,
			PartPose.offsetAndRotation(-1.5f, 21.5f, 0, 0, 0, -Mth.PI / 4));
		box(root, "left_fin", 0, 0, 0, 0, 0, 2, 0, 2,
			PartPose.offsetAndRotation(1.5f, 21.5f, 0, 0, 0, Mth.PI / 4));
		return bake(mesh, 32, 32);
	}

	public static MachineCreatureModel shulker() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		box(root, "lid", 0, 0, -8, -16, -8, 16, 12, 16, PartPose.offset(0, 24, 0));
		box(root, "base", 0, 28, -8, -8, -8, 16, 8, 16, PartPose.offset(0, 24, 0));
		return bake(mesh, 64, 64);
	}

	public static MachineCreatureModel slimeInner() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		box(root, "cube", 0, 16, -3, 17, -3, 6, 6, 6, PartPose.ZERO);
		box(root, "right_eye", 32, 0, -3.25f, 18, -3.5f, 2, 2, 2, PartPose.ZERO);
		box(root, "left_eye", 32, 4, 1.25f, 18, -3.5f, 2, 2, 2, PartPose.ZERO);
		box(root, "mouth", 32, 8, 0, 21, -3.5f, 1, 1, 1, PartPose.ZERO);
		return bake(mesh, 64, 32);
	}

	public static MachineCreatureModel slimeOuter() {
		MeshDefinition mesh = new MeshDefinition();
		box(mesh.getRoot(), "cube", 0, 0, -4, 16, -4, 8, 8, 8, PartPose.ZERO);
		return bake(mesh, 64, 32);
	}

	public static MachineCreatureModel magmaCube() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int slice = 0; slice < 8; slice++) {
			int u = slice == 2 || slice == 3 ? 24 : 0;
			int v = slice == 2 ? 10 : slice == 3 ? 19 : slice;
			box(root, "cube" + slice, u, v, -4, 16 + slice, -4, 8, 1, 8, PartPose.ZERO);
		}
		box(root, "inside_cube", 0, 16, -2, 18, -2, 4, 4, 4, PartPose.ZERO);
		return bake(mesh, 64, 32);
	}

	public static MachineCreatureModel creeper(float inflation) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation deformation = new CubeDeformation(inflation);
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
			.addBox(-4, -8, -4, 8, 8, 8, deformation), PartPose.offset(0, 6, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16)
			.addBox(-4, 0, -2, 8, 12, 4, deformation), PartPose.offset(0, 6, 0));
		CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 6, 4, deformation);
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2, 18, 4));
		root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(2, 18, 4));
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2, 18, -4));
		root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(2, 18, -4));
		return bake(mesh, 64, 32);
	}

	public static MachineCreatureModel book() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		box(root, "left_lid", 0, 0, -6, -5, -0.005f, 6, 10, 0.005f, PartPose.offset(0, 0, -1));
		box(root, "right_lid", 16, 0, 0, -5, -0.005f, 6, 10, 0.005f, PartPose.offset(0, 0, 1));
		box(root, "seam", 12, 0, -1, -5, 0, 2, 10, 0.005f, PartPose.rotation(0, Mth.PI / 2, 0));
		box(root, "left_pages", 0, 10, 0, -4, -0.99f, 5, 8, 1, PartPose.ZERO);
		box(root, "right_pages", 12, 10, 0, -4, -0.01f, 5, 8, 1, PartPose.ZERO);
		box(root, "flip_page1", 24, 10, 0, -4, 0, 5, 8, 0.005f, PartPose.ZERO);
		box(root, "flip_page2", 24, 10, 0, -4, 0, 5, 8, 0.005f, PartPose.ZERO);
		return new MachineCreatureModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), RenderType::entitySolid);
	}

	private static MachineCreatureModel bake(MeshDefinition mesh, int textureWidth, int textureHeight) {
		return new MachineCreatureModel(LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot());
	}

	private static PartDefinition box(PartDefinition parent, String name, int u, int v,
		float x, float y, float z, float width, float height, float depth, PartPose pose) {
		return parent.addOrReplaceChild(name,
			CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth), pose);
	}
}

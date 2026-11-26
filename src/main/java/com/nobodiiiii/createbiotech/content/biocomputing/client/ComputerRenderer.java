package com.nobodiiiii.createbiotech.content.biocomputing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerBlock;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerBlockEntity;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerBlockEntity.VillagerAppearance;
import com.nobodiiiii.createbiotech.foundation.render.BoundedRenderEntityCache;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ComputerRenderer implements BlockEntityRenderer<ComputerBlockEntity> {
	private static final double VILLAGER_Y = -2 / 16d;
	private static final BoundedRenderEntityCache<Void, Villager> VILLAGER =
		new BoundedRenderEntityCache<>(1, (level, ignored) -> EntityType.VILLAGER.create(level));

	public ComputerRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(ComputerBlockEntity computer, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		VillagerAppearance appearance = computer.getVillagerAppearance();
		if (appearance == null)
			return;
		Villager villager = VILLAGER.get(computer.getLevel(), null);
		if (villager == null)
			return;
		villager.setVillagerData(appearance.data());
		villager.setBaby(appearance.baby());

		Direction facing = computer.getBlockState().getValue(ComputerBlock.FACING);
		poseStack.pushPose();
		poseStack.translate(0.5d, VILLAGER_Y, 0.5d);
		poseStack.mulPose(Axis.YN.rotationDegrees(facing.toYRot()));
		EntityRenderHelper.renderUnoriented(villager, poseStack, buffer, LightTexture.FULL_BRIGHT);
		poseStack.popPose();
	}

	@Override
	public boolean shouldRender(ComputerBlockEntity computer, Vec3 cameraPosition) {
		return !computer.isRemoved() && computer.getVillagerAppearance() != null
			&& BlockEntityRenderer.super.shouldRender(computer, cameraPosition);
	}

	@Override
	public boolean shouldRenderOffScreen(ComputerBlockEntity computer) {
		// The lower half owns the model, which extends into the upper half's render section.
		return true;
	}

	@Override
	public AABB getRenderBoundingBox(ComputerBlockEntity computer) {
		return new AABB(computer.getBlockPos()).expandTowards(0, 1, 0);
	}
}

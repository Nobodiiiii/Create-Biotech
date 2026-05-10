package com.nobodiiiii.createbiotech.content.schrodingerscat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class SchrodingersCatRenderer extends SmartBlockEntityRenderer<SchrodingersCatBlockEntity> {

	private static final PartialModel TORCH_OFF =
		PartialModel.of(CreateBiotech.asResource("block/schrodingers_cat/redstone_torch_off"));
	private static final PartialModel TORCH_ON =
		PartialModel.of(CreateBiotech.asResource("block/schrodingers_cat/redstone_torch_on"));
	private static final ItemStack DISPLAY_SWORD = new ItemStack(Items.IRON_SWORD);
	private static final float SWORD_SCALE = 0.5f;

	public SchrodingersCatRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(SchrodingersCatBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		super.renderSafe(blockEntity, partialTicks, poseStack, buffer, packedLight, packedOverlay);

		BlockState state = blockEntity.getBlockState();
		if (!state.hasProperty(SchrodingersCatBlock.FACING))
			return;

		Direction facing = state.getValue(SchrodingersCatBlock.FACING);
		renderTorch(blockEntity, poseStack, buffer, packedLight, state, facing);
		renderSword(blockEntity, poseStack, buffer, packedLight, packedOverlay, facing);
	}

	private void renderTorch(SchrodingersCatBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, BlockState state, Direction facing) {
		boolean powered = blockEntity.getOutputSignal() > 0;
		VertexConsumer cutoutBuffer = buffer.getBuffer(RenderType.cutout());

		CachedBuffers.partial(powered ? TORCH_ON : TORCH_OFF, state)
			.center()
			.rotateYDegrees(blockModelYRotation(facing.getOpposite()))
			.uncenter()
			.light(packedLight)
			.renderInto(poseStack, cutoutBuffer);
	}

	private void renderSword(SchrodingersCatBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int packedOverlay, Direction facing) {
		if (blockEntity.getLevel() == null)
			return;

		ItemRenderer itemRenderer = Minecraft.getInstance()
			.getItemRenderer();
		BakedModel bakedModel = itemRenderer.getModel(DISPLAY_SWORD, blockEntity.getLevel(), null, 0);

		poseStack.pushPose();
		poseStack.translate(0.5f, 10f / 16f, 0.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(blockModelYRotation(facing)));
		poseStack.mulPose(Axis.ZP.rotationDegrees(225));
		poseStack.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);
		itemRenderer.render(DISPLAY_SWORD, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight,
			packedOverlay, bakedModel);
		poseStack.popPose();
	}

	private static int blockModelYRotation(Direction facing) {
		return switch (facing) {
		case EAST -> 90;
		case SOUTH -> 180;
		case WEST -> 270;
		default -> 0;
		};
	}
}

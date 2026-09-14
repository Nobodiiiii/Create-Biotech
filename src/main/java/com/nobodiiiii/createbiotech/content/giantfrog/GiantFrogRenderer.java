package com.nobodiiiii.createbiotech.content.giantfrog;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.render.ShadowRenderHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GiantFrogRenderer implements BlockEntityRenderer<GiantFrogBlockEntity> {
	private static final float LIVING_ENTITY_MODEL_Y_OFFSET = -1.501f;
	private static final float BELT_OPEN_HEAD_X_ROT = (float) Math.toRadians(-10.0d);
	private static final float MODEL_UNITS_PER_BLOCK = 16.0f;
	private static final float TONGUE_PIVOT_Z_UNITS = 5.0f;
	private static final float TONGUE_FRONT_Z_UNITS = -7.1f;
	private static final float BELT_CONNECTION_FORWARD_BLOCKS = 1.5f;
	private static final float BELT_TONGUE_Z_SCALE =
		(TONGUE_PIVOT_Z_UNITS + BELT_CONNECTION_FORWARD_BLOCKS * MODEL_UNITS_PER_BLOCK / GiantFrogBlock.FROG_SCALE)
			/ -TONGUE_FRONT_Z_UNITS;
	private static final double BELT_ITEM_Y = 15.0d / 16.0d;
	private static final double BELT_HANDOFF_DISTANCE_BLOCKS = 0.26d;
	private static final double BELT_TONGUE_TRANSFER_DISTANCE_BLOCKS = 0.5d;
	private static final double BELT_TRANSFER_DISTANCE_BLOCKS =
		BELT_HANDOFF_DISTANCE_BLOCKS + BELT_TONGUE_TRANSFER_DISTANCE_BLOCKS;
	private static final float BELT_TRANSFER_ITEM_SCALE = 0.5f;

	private final MachineCreatureModel frogModel;

	public GiantFrogRenderer(BlockEntityRendererProvider.Context context) {
		frogModel = MachineCreatureModels.frog();
	}

	@Override
	public void render(GiantFrogBlockEntity blockEntity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if (!GiantFrogBlock.isMain(blockEntity.getBlockState()))
			return;

		Direction facing = getFacing(blockEntity.getBlockState());
		boolean beltConnected = blockEntity.isMouthHeldOpenByBelt();
		boolean tongue = blockEntity.isTongueAnimating() && !beltConnected;
		float animationAge = tongue ? blockEntity.getTongueAnimationAge(partialTick) : 0.0f;
		prepareFrogModel(tongue, animationAge, beltConnected);

		BlockEntityModelElement.builder()
			.atLocal(0.5d, 0.0d, 0.5d)
			.rotateY(180.0f - facing.toYRot())
			.scale(-GiantFrogBlock.FROG_SCALE, -GiantFrogBlock.FROG_SCALE, GiantFrogBlock.FROG_SCALE)
			.packedLight(packedLight)
			.render(poseStack, buffer, (modelPose, modelBuffer, light) -> {
				modelPose.translate(0.0f, LIVING_ENTITY_MODEL_Y_OFFSET, 0.0f);
				renderFrogModel(modelPose, modelBuffer, light);
			});

		renderBeltTransferItem(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay, facing);
	}

	private void prepareFrogModel(boolean tongue, float ageInTicks, boolean beltConnected) {
		GiantFrogVisual.prepareModel(frogModel, tongue, ageInTicks);
		if (beltConnected) {
			frogHead().xRot += BELT_OPEN_HEAD_X_ROT;
			applyBeltTongueBridge();
		}
	}

	private void renderFrogModel(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		VertexConsumer consumer = buffer.getBuffer(frogModel.renderType(GiantFrogVisual.TEXTURE));
		frogModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	private void renderBeltTransferItem(GiantFrogBlockEntity blockEntity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay, Direction facing) {
		TransportedItemStack transported = blockEntity.getBeltTransferItem();
		if (transported == null || transported.stack.isEmpty())
			return;

		ItemStack stack = transported.stack;
		float progress = blockEntity.getBeltTransferProgress(partialTick);
		float sideOffset = Mth.lerp(partialTick, transported.prevSideOffset, transported.sideOffset);
		Vec3 outward = Vec3.atLowerCornerOf(facing.getNormal());
		Vec3 sideways = Vec3.atLowerCornerOf(facing.getClockWise()
			.getNormal());
		Vec3 position = new Vec3(0.5d, BELT_ITEM_Y, 0.5d)
			.add(outward.scale(BELT_CONNECTION_FORWARD_BLOCKS + BELT_HANDOFF_DISTANCE_BLOCKS))
			.add(outward.scale(-BELT_TRANSFER_DISTANCE_BLOCKS * progress))
			.add(sideways.scale(sideOffset));

		poseStack.pushPose();
		poseStack.translate(position.x, position.y, position.z);
		poseStack.pushPose();
		poseStack.translate(0.0d, -1.0d / 8.0d + 0.005d, 0.0d);
		ShadowRenderHelper.renderShadow(poseStack, buffer, 0.75f, 0.2f);
		poseStack.popPose();

		ItemRenderer itemRenderer = Minecraft.getInstance()
			.getItemRenderer();
		BakedModel bakedModel = itemRenderer.getModel(stack, blockEntity.getLevel(), null, 0);
		boolean blockItem = bakedModel.isGui3d();
		boolean box = PackageItem.isPackage(stack);
		poseStack.mulPose(Axis.YP.rotationDegrees(transported.angle));
		if (!blockItem) {
			poseStack.translate(0.0d, -0.09375d, 0.0d);
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
		}
		if (box) {
			poseStack.translate(0.0d, 4.0d / 16.0d, 0.0d);
			poseStack.scale(1.5f, 1.5f, 1.5f);
		} else {
			poseStack.scale(BELT_TRANSFER_ITEM_SCALE, BELT_TRANSFER_ITEM_SCALE, BELT_TRANSFER_ITEM_SCALE);
		}

		Level level = blockEntity.getLevel();
		int itemLight = level == null ? packedLight : LevelRenderer.getLightColor(level,
			GiantFrogBlock.getMouthInputPos(blockEntity.getBlockPos(), blockEntity.getBlockState()));
		itemRenderer.render(stack, ItemDisplayContext.FIXED, false, poseStack, buffer, itemLight, packedOverlay,
			bakedModel);
		poseStack.popPose();
	}

	private ModelPart frogHead() {
		return frogModel.root()
			.getChild("body")
			.getChild("head");
	}

	private void applyBeltTongueBridge() {
		ModelPart tongue = frogTongue();
		tongue.xRot = 0.0f;
		tongue.yRot = 0.0f;
		tongue.zRot = 0.0f;
		tongue.xScale = 1.0f;
		tongue.yScale = 1.0f;
		tongue.zScale = BELT_TONGUE_Z_SCALE;
	}

	private ModelPart frogTongue() {
		return frogModel.root()
			.getChild("body")
			.getChild("tongue");
	}

	private Direction getFacing(BlockState state) {
		return state.hasProperty(GiantFrogBlock.FACING) ? state.getValue(GiantFrogBlock.FACING) : Direction.SOUTH;
	}
}

package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class EvokerEnchantingChamberRenderer implements BlockEntityRenderer<EvokerEnchantingChamberBlockEntity> {

	private static final ResourceLocation BOOK_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/enchanting_table_book.png");

	private static final float ENCHANTING_TABLE_TOP_Y = 14f / 16f;
	private static final float UPPER_BLOCK_TOP_Y = 1.95f;
	private static final float EVOKER_BODY_HEIGHT_UNITS = 22f;
	private static final float EVOKER_SCALE =
		(UPPER_BLOCK_TOP_Y - ENCHANTING_TABLE_TOP_Y) * 16f / EVOKER_BODY_HEIGHT_UNITS;
	private static final float EVOKER_ROOT_Y = ENCHANTING_TABLE_TOP_Y + 12f / 16f * EVOKER_SCALE;
	private static final float EVOKER_BACK_GAP = 1f / 16f;
	private static final float EVOKER_BACK_HALF_DEPTH = 3f / 16f * EVOKER_SCALE;
	private static final float EVOKER_BACK_OFFSET_FROM_CENTER =
		0.5f - (EVOKER_BACK_HALF_DEPTH + EVOKER_BACK_GAP);

	private static final float BOOK_FRONT_OFFSET = 4f / 16f;
	private static final float BOOK_BASE_Y = ENCHANTING_TABLE_TOP_Y + 4f / 16f;
	private static final float BOOK_BOB_AMPLITUDE = 0.04f;
	private static final float BOOK_BOB_SPEED = 0.08f;
	private static final float BOOK_Z_ROTATION = 80f;

	private static final float ITEM_BASE_Y = BOOK_BASE_Y + 6f / 16f;
	private static final float ITEM_BOB_AMPLITUDE = 0.06f;
	private static final float ITEM_BOB_SPEED = 0.1f;
	private static final float ITEM_SPIN_SPEED = 2.5f;
	private static final float ITEM_SCALE = 0.7f;

	private final BlockRenderDispatcher blockRenderer;
	private final MachineCreatureModel evokerModel;
	private final MachineCreatureModel bookModel;
	private final BlockState enchantingTableState;

	public EvokerEnchantingChamberRenderer(BlockEntityRendererProvider.Context context) {
		blockRenderer = context.getBlockRenderDispatcher();
		evokerModel = MachineCreatureModels.evoker();
		bookModel = MachineCreatureModels.book();
		enchantingTableState = Blocks.ENCHANTING_TABLE.defaultBlockState();
	}

	@Override
	public void render(EvokerEnchantingChamberBlockEntity blockEntity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if (blockEntity.getBlockState().getValue(EvokerEnchantingChamberBlock.HALF)
			!= net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
			return;

		Direction facing = blockEntity.getBlockState().getValue(EvokerEnchantingChamberBlock.FACING);

		renderEnchantingTable(poseStack, buffer, packedLight, packedOverlay);
		renderEvoker(blockEntity, partialTick, poseStack, buffer, packedLight, facing);
		renderBook(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay, facing);
		renderEnchantingItem(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay, facing);
	}

	private void renderEnchantingTable(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int packedOverlay) {
		poseStack.pushPose();
		blockRenderer.renderSingleBlock(enchantingTableState, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private void renderEvoker(EvokerEnchantingChamberBlockEntity blockEntity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, Direction facing) {
		EvokerEnchantingVisual.prepareModel(evokerModel, blockEntity.isCastingSpell());

		double rootX = 0.5d - facing.getStepX() * EVOKER_BACK_OFFSET_FROM_CENTER;
		double rootZ = 0.5d - facing.getStepZ() * EVOKER_BACK_OFFSET_FROM_CENTER;

		BlockEntityModelElement.builder()
			.atLocal(rootX, EVOKER_ROOT_Y, rootZ)
			.rotateY(180.0f - facing.toYRot())
			.scale(-EVOKER_SCALE, -EVOKER_SCALE, EVOKER_SCALE)
			.packedLight(packedLight)
			.render(poseStack, buffer,
				(ms, buf, lightArg) -> EvokerEnchantingVisual.renderModel(evokerModel, ms, buf, lightArg));
	}

	private void renderBook(EvokerEnchantingChamberBlockEntity be, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay, Direction facing) {
		float time = AnimationTickHolder.getRenderTime(be.getLevel());
		float bob = Mth.sin(time * BOOK_BOB_SPEED) * BOOK_BOB_AMPLITUDE;

		double bookX = 0.5d + facing.getStepX() * BOOK_FRONT_OFFSET;
		double bookZ = 0.5d + facing.getStepZ() * BOOK_FRONT_OFFSET;
		double bookY = BOOK_BASE_Y + bob;

		float bookYRot = (180f - facing.toYRot()) - 90f;
		float pageFlutter = Mth.sin(time * 0.135f) * 0.05f;

		poseStack.pushPose();
		poseStack.translate(bookX, bookY, bookZ);
		poseStack.mulPose(Axis.YP.rotationDegrees(bookYRot));
		poseStack.mulPose(Axis.ZP.rotationDegrees(BOOK_Z_ROTATION));

		prepareBookModel(time, pageFlutter);
		VertexConsumer bookConsumer = buffer.getBuffer(bookModel.renderType(BOOK_TEXTURE));
		bookModel.renderToBuffer(poseStack, bookConsumer, packedLight, packedOverlay, 0xFFFFFFFF);
		poseStack.popPose();
	}

	private void renderEnchantingItem(EvokerEnchantingChamberBlockEntity be, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay, Direction facing) {
		ItemStack toDisplay;
		if (be.isCastingSpell())
			toDisplay = be.getHeldItem();
		else if (!be.getPendingOutput().isEmpty())
			toDisplay = be.getPendingOutput();
		else
			toDisplay = be.getHeldItem();

		if (toDisplay.isEmpty())
			return;

		Level level = be.getLevel();
		float time = AnimationTickHolder.getRenderTime(level);
		float bob = Mth.sin(time * ITEM_BOB_SPEED) * ITEM_BOB_AMPLITUDE;
		float spin = time * ITEM_SPIN_SPEED;

		double itemX = 0.5d + facing.getStepX() * BOOK_FRONT_OFFSET;
		double itemZ = 0.5d + facing.getStepZ() * BOOK_FRONT_OFFSET;
		double itemY = ITEM_BASE_Y + bob;

		poseStack.pushPose();
		poseStack.translate(itemX, itemY, itemZ);
		poseStack.mulPose(Axis.YP.rotationDegrees(spin));
		poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);

		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		itemRenderer.renderStatic(toDisplay, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, buffer,
			level, 0);
		poseStack.popPose();
	}

	private void prepareBookModel(float time, float pageFlutter) {
		bookModel.resetPose();
		ModelPart root = bookModel.root();
		float opening = Mth.sin(time * 0.02f) * 0.1f + 1.25f;
		root.getChild("left_lid").yRot = Mth.PI + opening;
		root.getChild("right_lid").yRot = -opening;
		root.getChild("left_pages").yRot = opening;
		root.getChild("right_pages").yRot = -opening;
		float pageRotation = opening * (1.0f - 2.0f * pageFlutter);
		root.getChild("flip_page1").yRot = pageRotation;
		root.getChild("flip_page2").yRot = pageRotation;
		float pageOffset = Mth.sin(opening);
		root.getChild("left_pages").x = pageOffset;
		root.getChild("right_pages").x = pageOffset;
		root.getChild("flip_page1").x = pageOffset;
		root.getChild("flip_page2").x = pageOffset;
	}
}

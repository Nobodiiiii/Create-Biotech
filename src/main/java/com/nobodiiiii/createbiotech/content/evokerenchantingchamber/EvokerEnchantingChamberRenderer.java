package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class EvokerEnchantingChamberRenderer implements BlockEntityRenderer<EvokerEnchantingChamberBlockEntity> {

	private static final ResourceLocation EVOKER_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/illager/evoker.png");
	private static final ResourceLocation BOOK_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/enchanting_table_book.png");

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

	private static final float HEAD_X_ROT = 0.08726646f;
	private static final float RIGHT_LEG_X_ROT = -1.4137167f;
	private static final float RIGHT_LEG_Y_ROT = 0.31415927f;
	private static final float RIGHT_LEG_Z_ROT = 0.07853982f;
	private static final float LEFT_LEG_X_ROT = -1.4137167f;
	private static final float LEFT_LEG_Y_ROT = -0.31415927f;
	private static final float LEFT_LEG_Z_ROT = -0.07853982f;
	private static final float ARM_RAISED_Z_ROT = 2.3561945f;

	private final BlockRenderDispatcher blockRenderer;
	private final IllagerModel<RenderEvoker> evokerModel;
	private final BookModel bookModel;
	private final BlockState enchantingTableState;
	private RenderEvoker cachedEvoker;
	private ClientLevel cachedLevel;

	public EvokerEnchantingChamberRenderer(BlockEntityRendererProvider.Context context) {
		blockRenderer = context.getBlockRenderDispatcher();
		evokerModel = new IllagerModel<>(context.bakeLayer(ModelLayers.EVOKER));
		bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
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
		RenderEvoker evoker = getOrCreateEvoker(blockEntity.getLevel());
		if (evoker == null)
			return;

		prepareEvokerModel(evoker, blockEntity, partialTick);

		double rootX = 0.5d - facing.getStepX() * EVOKER_BACK_OFFSET_FROM_CENTER;
		double rootZ = 0.5d - facing.getStepZ() * EVOKER_BACK_OFFSET_FROM_CENTER;

		BlockEntityModelElement.builder()
			.atLocal(rootX, EVOKER_ROOT_Y, rootZ)
			.rotateY(180.0f - facing.toYRot())
			.scale(-EVOKER_SCALE, -EVOKER_SCALE, EVOKER_SCALE)
			.packedLight(packedLight)
			.render(poseStack, buffer, (ms, buf, lightArg) -> {
				VertexConsumer consumer = buf.getBuffer(evokerModel.renderType(EVOKER_TEXTURE));
				evokerModel.renderToBuffer(ms, consumer, lightArg, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
			});
	}

	private void renderBook(EvokerEnchantingChamberBlockEntity be, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay, Direction facing) {
		Level level = be.getLevel();
		float time = level == null ? 0f : level.getGameTime() + partialTick;
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

		bookModel.setupAnim(time, pageFlutter, pageFlutter, 1.0f);
		VertexConsumer bookConsumer = buffer.getBuffer(bookModel.renderType(BOOK_TEXTURE));
		bookModel.renderToBuffer(poseStack, bookConsumer, packedLight, packedOverlay, 1f, 1f, 1f, 1f);
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
		float time = level == null ? 0f : level.getGameTime() + partialTick;
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

	private void prepareEvokerModel(RenderEvoker evoker, EvokerEnchantingChamberBlockEntity blockEntity,
		float partialTick) {
		boolean casting = blockEntity.isCastingSpell();
		evoker.setCasting(casting);

		ModelPart root = evokerModel.root();
		root.getAllParts().forEach(ModelPart::resetPose);
		float ageInTicks = blockEntity.getAnimationTime(partialTick);
		evokerModel.setupAnim(evoker, 0.0f, 0.0f, ageInTicks, 0.0f, 0.0f);

		ModelPart head = root.getChild("head");
		ModelPart body = root.getChild("body");
		ModelPart rightLeg = root.getChild("right_leg");
		ModelPart leftLeg = root.getChild("left_leg");

		head.xRot = HEAD_X_ROT;
		rightLeg.xRot = RIGHT_LEG_X_ROT;
		rightLeg.yRot = RIGHT_LEG_Y_ROT;
		rightLeg.zRot = RIGHT_LEG_Z_ROT;
		leftLeg.xRot = LEFT_LEG_X_ROT;
		leftLeg.yRot = LEFT_LEG_Y_ROT;
		leftLeg.zRot = LEFT_LEG_Z_ROT;

		applyArmAnimation(root, body, ageInTicks, casting);
	}

	private static void applyArmAnimation(ModelPart root, ModelPart body, float ageInTicks, boolean casting) {
		ModelPart rightArm = root.getChild("right_arm");
		ModelPart leftArm = root.getChild("left_arm");

		if (casting) {
			float swing = Mth.sin(ageInTicks * 0.5f) * 0.42f;
			float sway = Mth.cos(ageInTicks * 0.35f) * 0.22f;
			float twist = Mth.sin(ageInTicks * 0.7f) * 0.2f;

			rightArm.xRot = -0.95f + swing;
			leftArm.xRot = -0.95f - swing;
			rightArm.yRot = 0.18f + sway;
			leftArm.yRot = -0.18f - sway;
			rightArm.zRot = ARM_RAISED_Z_ROT + twist;
			leftArm.zRot = -ARM_RAISED_Z_ROT - twist;
			body.zRot = Mth.sin(ageInTicks * 0.12f) * 0.05f;
		} else {
			body.zRot = 0.0f;
		}
	}

	private RenderEvoker getOrCreateEvoker(Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return null;

		if (cachedEvoker == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedEvoker = new RenderEvoker(clientLevel);
			cachedEvoker.setNoAi(true);
			cachedEvoker.setSilent(true);
		}

		cachedEvoker.setYRot(0.0f);
		cachedEvoker.setYBodyRot(0.0f);
		cachedEvoker.yBodyRotO = 0.0f;
		cachedEvoker.yHeadRot = 0.0f;
		cachedEvoker.yHeadRotO = 0.0f;

		return cachedEvoker;
	}

	private static class RenderEvoker extends Evoker {

		private RenderEvoker(ClientLevel level) {
			super(EntityType.EVOKER, level);
		}

		private void setCasting(boolean casting) {
			setIsCastingSpell(casting ? SpellcasterIllager.IllagerSpell.FANGS : SpellcasterIllager.IllagerSpell.NONE);
		}
	}
}

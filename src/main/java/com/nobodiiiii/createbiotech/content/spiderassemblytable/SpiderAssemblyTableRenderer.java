package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SpiderAssemblyTableRenderer extends KineticBlockEntityRenderer<SpiderAssemblyTableBlockEntity> {

	private static final ResourceLocation SPIDER_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/spider/spider.png");
	private static final float SPIDER_SCALE = 0.85f;
	private static final float SPIDER_Y_OFFSET = 19f / 16f * SPIDER_SCALE;
	private static final float ACTIVE_LEG_BEND = 0.45f;
	private static final float SHAFT_OFFSET = 0.34f;

	private final SpiderModel<RenderSpider> spiderModel;
	private RenderSpider cachedSpider;
	private ClientLevel cachedLevel;

	public SpiderAssemblyTableRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		spiderModel = new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER));
	}

	@Override
	protected void renderSafe(SpiderAssemblyTableBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		BlockState state = be.getBlockState();
		if (!state.hasProperty(SpiderAssemblyTableBlock.FACING))
			return;

		Direction facing = state.getValue(SpiderAssemblyTableBlock.FACING);
		renderSpider(be, partialTicks, ms, buffer, light, facing);

		BlockState shaftState = shaft(getRotationAxisOf(be));
		RenderType shaftRenderType = getRenderType(be, shaftState);
		Direction tail = facing.getOpposite();
		ms.pushPose();
		ms.translate(tail.getStepX() * SHAFT_OFFSET, 0, tail.getStepZ() * SHAFT_OFFSET);
		renderRotatingBuffer(be, getRotatedModel(be, shaftState), ms, buffer.getBuffer(shaftRenderType), light);
		ms.popPose();
	}

	private void renderSpider(SpiderAssemblyTableBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, Direction facing) {
		RenderSpider spider = getOrCreateSpider(be.getLevel());
		if (spider == null)
			return;

		prepareSpiderModel(spider, be, partialTicks);

		ms.pushPose();
		ms.translate(0.5d, SPIDER_Y_OFFSET, 0.5d);
		ms.mulPose(Axis.YP.rotationDegrees(yRotation(facing)));
		ms.scale(-SPIDER_SCALE, -SPIDER_SCALE, SPIDER_SCALE);
		VertexConsumer spiderBuffer = buffer.getBuffer(spiderModel.renderType(SPIDER_TEXTURE));
		spiderModel.renderToBuffer(ms, spiderBuffer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
		ms.popPose();
	}

	private void prepareSpiderModel(RenderSpider spider, SpiderAssemblyTableBlockEntity be, float partialTicks) {
		ModelPart root = spiderModel.root();
		root.getAllParts().forEach(ModelPart::resetPose);

		float time = AnimationTickHolder.getRenderTime(be.getLevel());
		float limbSwing = time * 0.16f;
		float limbSwingAmount = be.getActiveSlot() >= 0 ? 0.35f : 0.0f;
		spiderModel.setupAnim(spider, limbSwing, limbSwingAmount, time + partialTicks, 0.0f, 0.0f);

		ModelPart activeLeg = getAnimatedLeg(root, be.getActiveSlot());
		if (activeLeg == null)
			return;

		float progress = be.getProcessingProgress(partialTicks);
		float bend = Mth.sin(progress * Mth.PI) * ACTIVE_LEG_BEND;
		boolean leftSide = be.getActiveSlot() < 3;
		activeLeg.xRot -= bend * 0.35f;
		activeLeg.zRot += leftSide ? bend : -bend;
	}

	private static ModelPart getAnimatedLeg(ModelPart root, int slot) {
		return switch (slot) {
		case 0 -> root.getChild("left_front_leg");
		case 1 -> root.getChild("left_middle_front_leg");
		case 2 -> root.getChild("left_hind_leg");
		case 3 -> root.getChild("right_front_leg");
		case 4 -> root.getChild("right_middle_front_leg");
		case 5 -> root.getChild("right_hind_leg");
		default -> null;
		};
	}

	private RenderSpider getOrCreateSpider(Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return null;

		if (cachedSpider == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedSpider = new RenderSpider(clientLevel);
			cachedSpider.setNoAi(true);
			cachedSpider.setSilent(true);
		}

		return cachedSpider;
	}

	private static float yRotation(Direction facing) {
		return switch (facing) {
		case EAST -> 270;
		case SOUTH -> 180;
		case WEST -> 90;
		default -> 0;
		};
	}

	private static class RenderSpider extends Spider {

		private RenderSpider(ClientLevel level) {
			super(EntityType.SPIDER, level);
		}
	}
}

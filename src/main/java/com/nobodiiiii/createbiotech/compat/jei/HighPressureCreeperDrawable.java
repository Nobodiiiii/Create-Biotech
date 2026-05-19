package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

public class HighPressureCreeperDrawable extends AnimatedKinetics {
	private static final int PRESS_CYCLE = 30;
	private static final int PRESS_RENDER_Z = 100;
	private static final int PRESS_SCALE = 20;
	private static final float PRESS_EFFECT_START_OFFSET = 0.4f;
	private static final CompoundTag CHARGED_CREEPER_TAG = createChargedCreeperTag();

	private final int width;
	private final int height;
	private final double creeperLocalY;
	private final float creeperYaw;
	private final float creeperPitch;
	private final float horizontalScale;
	private final float verticalScale;
	private final int swell;

	@Nullable
	private Creeper cachedCreeper;
	@Nullable
	private Level cachedLevel;

	public HighPressureCreeperDrawable(int width, int height, double creeperLocalY, float creeperYaw,
		float creeperPitch, float horizontalScale, float verticalScale, int swell) {
		this.width = width;
		this.height = height;
		this.creeperLocalY = creeperLocalY;
		this.creeperYaw = creeperYaw;
		this.creeperPitch = creeperPitch;
		this.horizontalScale = horizontalScale;
		this.verticalScale = verticalScale;
		this.swell = swell;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null)
			return;

		Creeper creeper = getOrCreateCreeper(level);
		if (creeper == null)
			return;

		float headOffset = getAnimatedHeadOffset();

		PoseStack poseStack = guiGraphics.pose();
		poseStack.pushPose();
		poseStack.translate(xOffset, yOffset, PRESS_RENDER_Z);
		poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
		poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));

		blockElement(shaft(Direction.Axis.Z))
			.rotateBlock(0, 0, getCurrentAngle())
			.scale(PRESS_SCALE)
			.render(guiGraphics);

		blockElement(AllBlocks.MECHANICAL_PRESS.getDefaultState())
			.scale(PRESS_SCALE)
			.render(guiGraphics);

		renderCreeperInScene(guiGraphics, creeper, headOffset);

		blockElement(AllPartialModels.MECHANICAL_PRESS_HEAD)
			.atLocal(0, -headOffset, 0)
			.scale(PRESS_SCALE)
			.render(guiGraphics);

		poseStack.popPose();
	}

	private void renderCreeperInScene(GuiGraphics guiGraphics, Creeper creeper, float headOffset) {
		float compression = getCompressionFromHeadOffset(headOffset);
		float pulse = 0.5f + 0.5f * Mth.sin(AnimationTickHolder.getRenderTime() * 0.9f);
		int renderSwell =
			Mth.floor(Mth.clamp(compression * Mth.lerp(pulse, 0.55f, 1f), 0f, 1f) * swell);

		CreeperAccessor accessor = (CreeperAccessor) creeper;
		accessor.createBiotech$setOldSwell(renderSwell);
		accessor.createBiotech$setSwell(renderSwell);

		float appliedHorizontalScale = Mth.lerp(compression, 1f, horizontalScale);
		float appliedVerticalScale = Mth.lerp(compression, 1f, verticalScale);

		creeper.tickCount = Mth.floor(AnimationTickHolder.getRenderTime());
		creeper.setYBodyRot(creeperYaw);
		creeper.yBodyRotO = creeperYaw;
		creeper.setYRot(creeperYaw);
		creeper.yRotO = creeperYaw;
		creeper.yHeadRot = creeperYaw;
		creeper.yHeadRotO = creeperYaw;
		creeper.setXRot(creeperPitch);
		creeper.xRotO = creeperPitch;

		PoseStack poseStack = guiGraphics.pose();
		poseStack.pushPose();

		poseStack.scale(PRESS_SCALE, PRESS_SCALE, PRESS_SCALE);
		UIRenderHelper.flipForGuiRender(poseStack);

		poseStack.translate(0.5d, creeperLocalY, 0.5d);
		poseStack.scale(appliedHorizontalScale, appliedVerticalScale, appliedHorizontalScale);

		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		Lighting.setupForEntityInInventory();
		MultiBufferSource.BufferSource buffer = guiGraphics.bufferSource();
		RenderSystem.runAsFancy(() -> dispatcher.render(creeper, 0, 0, 0, creeperYaw, 0, poseStack, buffer,
			LightTexture.FULL_BRIGHT));
		buffer.endBatch();
		Lighting.setupFor3DItems();
		dispatcher.setRenderShadow(true);

		poseStack.popPose();
	}

	private float getAnimatedHeadOffset() {
		float cycle = (AnimationTickHolder.getRenderTime() - offset * 8) % PRESS_CYCLE;
		if (cycle < 10) {
			float progress = cycle / 10;
			return -(progress * progress * progress);
		}
		if (cycle < 15)
			return -1;
		if (cycle < 20)
			return -1 + (1 - ((20 - cycle) / 5));
		return 0;
	}

	private static float getCompressionFromHeadOffset(float headOffset) {
		float pressOffset = Mth.clamp(-headOffset, 0f, 1f);
		return Mth.clamp((pressOffset - PRESS_EFFECT_START_OFFSET) / (1f - PRESS_EFFECT_START_OFFSET), 0f, 1f);
	}

	private static CompoundTag createChargedCreeperTag() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("powered", true);
		return tag;
	}

	@Nullable
	private Creeper getOrCreateCreeper(Level level) {
		if (cachedCreeper != null && cachedLevel == level)
			return cachedCreeper;

		Creeper creeper = net.minecraft.world.entity.EntityType.CREEPER.create(level);
		if (creeper == null)
			return null;
		creeper.setNoAi(true);
		creeper.readAdditionalSaveData(CHARGED_CREEPER_TAG);
		creeper.tickCount = 0;
		cachedLevel = level;
		cachedCreeper = creeper;
		return creeper;
	}
}

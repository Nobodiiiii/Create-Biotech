package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;
import com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;

public class HighPressureCreeperDrawable extends AnimatedKineticsWithEntities {
	private static final int PRESS_CYCLE = 30;
	private static final int PRESS_SCALE = 20;
	private static final double CREEPER_ATTACHMENT_Y = 2d;
	private static final float PRESS_EFFECT_START_OFFSET = 0.4f;
	private static final CompoundTag CHARGED_CREEPER_TAG = createChargedCreeperTag();

	private final int width;
	private final int height;
	private final float horizontalScale;
	private final float verticalScale;
	private final int swell;

	private final CachedRenderEntity<Creeper, Void> renderCreeper = CachedRenderEntity.of(EntityType.CREEPER)
		.configure(creeper -> creeper.readAdditionalSaveData(CHARGED_CREEPER_TAG));

	public HighPressureCreeperDrawable(int width, int height, float horizontalScale, float verticalScale, int swell) {
		this.width = width;
		this.height = height;
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
		@Nullable
		Creeper creeper = renderCreeper.get(Minecraft.getInstance().level);
		if (creeper == null)
			return;

		float headOffset = getAnimatedHeadOffset();
		scene(guiGraphics, xOffset, yOffset, () -> {
			blockElement(shaft(Direction.Axis.Z))
				.rotateBlock(0, 0, getCurrentAngle())
				.scale(PRESS_SCALE)
				.render(guiGraphics);

			blockElement(AllBlocks.MECHANICAL_PRESS.getDefaultState())
				.scale(PRESS_SCALE)
				.render(guiGraphics);

			renderCreeper(guiGraphics, creeper, headOffset);

			blockElement(AllPartialModels.MECHANICAL_PRESS_HEAD)
				.atLocal(0, -headOffset, 0)
				.scale(PRESS_SCALE)
				.render(guiGraphics);
		});
	}

	private void renderCreeper(GuiGraphics guiGraphics, Creeper creeper, float headOffset) {
		float compression = getCompressionFromHeadOffset(headOffset);
		float pulse = 0.5f + 0.5f * Mth.sin(AnimationTickHolder.getRenderTime() * 0.9f);
		int renderSwell =
			Mth.floor(Mth.clamp(compression * Mth.lerp(pulse, 0.55f, 1f), 0f, 1f) * swell);

		CreeperAccessor accessor = (CreeperAccessor) creeper;
		accessor.createBiotech$setOldSwell(renderSwell);
		accessor.createBiotech$setSwell(renderSwell);

		float appliedHorizontalScale = Mth.lerp(compression, 1f, horizontalScale);
		float appliedVerticalScale = Mth.lerp(compression, 1f, verticalScale);
		entityElement(creeper)
			.atLocal(0.5d, CREEPER_ATTACHMENT_Y, 0.5d)
			.scale(PRESS_SCALE)
			.scaleEntity(appliedHorizontalScale, appliedVerticalScale, appliedHorizontalScale)
			.ticks(Mth.floor(AnimationTickHolder.getRenderTime()))
			.render(guiGraphics);
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
}

package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.gui.GuiEntityElement;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;

public final class SquidJeiRenderer {

	private static final float GUI_SCALE = 20.0f;
	private static final float SQUID_ENTITY_SCALE = 0.8f;
	private static final float GUI_Y_OFFSET = -32.0f;
	private static final float GUI_RENDER_Z = 100.0f;
	private static final float RUN_CYCLE_SPEED = 0.045f;
	private static final float RUN_MIN_TENTACLE_ANGLE = 0.14f;
	private static final float RUN_MAX_TENTACLE_ANGLE = Mth.PI * 0.25f;

	@Nullable
	private static Squid cachedSquid;
	@Nullable
	private static Level cachedLevel;

	private SquidJeiRenderer() {
	}

	public static void render(GuiGraphics graphics, int centerX, int centerY, float scale) {
		Squid squid = getOrCreateSquid();
		if (squid == null)
			return;

		GuiEntityElement.of(squid)
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.at(centerX, centerY + GUI_Y_OFFSET, GUI_RENDER_Z)
			.rotate(-15.5d, 22.5d, 0d)
			.scale(GUI_SCALE * scale)
			.scaleEntity(SQUID_ENTITY_SCALE)
			.stateModifier(SquidJeiRenderer::animateTentacles)
			.render(graphics);
	}

	@Nullable
	public static Squid getOrCreateSquid() {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return null;
		if (cachedSquid != null && cachedLevel == level)
			return cachedSquid;

		Squid squid = EntityType.SQUID.create(level);
		if (squid == null)
			return null;
		squid.setNoAi(true);
		squid.tickCount = 0;
		cachedLevel = level;
		cachedSquid = squid;
		return squid;
	}

	static GuiEntityElement.StateRestorer animateTentacles(Squid squid, float partialTicks) {
		SquidAnimationState state = SquidAnimationState.capture(squid);
		float renderTime = AnimationTickHolder.getRenderTime();
		squid.oldTentacleAngle = getTentacleAngle(renderTime - partialTicks);
		squid.tentacleAngle = getTentacleAngle(renderTime);
		return () -> state.restore(squid);
	}

	private static float getTentacleAngle(float renderTime) {
		float runningCycle = renderTime * RUN_CYCLE_SPEED;
		runningCycle -= Mth.floor(runningCycle);
		float openness = smoothPingPong(runningCycle);
		return Mth.lerp(openness, RUN_MIN_TENTACLE_ANGLE, RUN_MAX_TENTACLE_ANGLE);
	}

	private static float smoothPingPong(float cycle) {
		float pingPong = cycle < 0.5f ? cycle * 2.0f : (1.0f - cycle) * 2.0f;
		pingPong = Mth.clamp(pingPong, 0.0f, 1.0f);
		return pingPong * pingPong * (3.0f - 2.0f * pingPong);
	}

	private record SquidAnimationState(float oldTentacleAngle, float tentacleAngle) {

		private static SquidAnimationState capture(Squid squid) {
			return new SquidAnimationState(squid.oldTentacleAngle, squid.tentacleAngle);
		}

		private void restore(Squid squid) {
			squid.oldTentacleAngle = oldTentacleAngle;
			squid.tentacleAngle = tentacleAngle;
		}
	}
}

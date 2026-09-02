package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.render.BoundedRenderEntityCache;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;
import com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CreeperBlastChamberRenderer implements BlockEntityRenderer<CreeperBlastChamberBlockEntity> {

	/** Enter from one block below and exit one block downward while scaling. */
	private static final float CREEPER_ANIMATION_Y_OFFSET = 1f;
	/** Scale a creeper pops in from, and shrinks back to on the way out. */
	private static final float CREEPER_ENTRY_START_SCALE = .35f;
	private static final float CREEPER_EXIT_END_SCALE = .35f;
	/** The vanilla model spans 26 pixels vertically; 16 / 26 leaves it exactly one block tall. */
	private static final float CREEPER_FINAL_HEIGHT_SCALE = 16f / 26f;
	private static final float CREEPER_MAX_SPREAD = .2f;

	/** Highest swell value fed to the model; {@code Creeper.maxSwell - 2} is what vanilla divides by. */
	private static final float MAX_RENDER_SWELL = 24f;
	private static final float SWELL_DIVISOR = 28f;
	/**
	 * {@code CreeperRenderer.scale} multiplies in its own swell-driven bulge on top of whatever the
	 * caller already applied. These mirror its coefficients so the compression below can divide them
	 * back out and land on the width and height actually asked for.
	 */
	private static final float VANILLA_SWELL_SPREAD = .4f;
	private static final float VANILLA_SWELL_RISE = .1f;

	private static final double PLAYER_ATTENTION_DISTANCE = 12d;
	private static final float MAX_LOOK_HEAD_YAW = 65f;
	private static final int NO_PLAYER_RECHECK_MIN_TICKS = 30;
	private static final int NO_PLAYER_RECHECK_MAX_TICKS = 70;
	private static final int ATTENTION_DELAY_MIN_TICKS = 70;
	private static final int ATTENTION_DELAY_MAX_TICKS = 180;
	private static final int LOOK_DURATION_MIN_TICKS = 30;
	private static final int LOOK_DURATION_MAX_TICKS = 55;
	private static final int TURN_DURATION_MIN_TICKS = 45;
	private static final int TURN_DURATION_MAX_TICKS = 75;

	/**
	 * Charged creepers get their energy swirl UV from {@code tickCount}, and every distinct value
	 * builds its own {@code RenderType}. Bucketing the offsets keeps a full chamber down to a handful
	 * of extra batches instead of one per creeper.
	 */
	private static final int SWIRL_PHASE_BUCKETS = 3;
	private static final int SWIRL_PHASE_SPACING = 13;

	private static final double CREEPER_RENDER_DISTANCE = 32d;
	private static final int MAX_CACHED_CREEPERS = 16 * 9;
	private static final PartialModel DISPLAY_PANEL =
		PartialModel.of(CreateBiotech.asResource("block/blast_chamber_display/panel"));
	private static final PartialModel DISPLAY_DIAL =
		PartialModel.of(CreateBiotech.asResource("block/blast_chamber_display/dial"));
	private static final PartialModel CREEPER_FACE =
		PartialModel.of(CreateBiotech.asResource("block/blast_chamber_display/creeper_face"));
	private static final BoundedRenderEntityCache<CreeperCacheKey, Creeper> CREEPER_CACHE =
		new BoundedRenderEntityCache<>(MAX_CACHED_CREEPERS, (level, key) -> {
			Entity entity = CapturedEntityBoxHelper.createCapturedEntity(key.payload, level);
			if (!(entity instanceof Creeper creeper))
				return null;
			// The key pins the packager, so the position never changes for the life of this entry.
			creeper.setPos(key.packagerPos.getX() + .5d, key.packagerPos.getY() + 1d, key.packagerPos.getZ() + .5d);
			return creeper;
		});
	/** Render proxies own the only strong references; attention state disappears when their cache entry does. */
	private static final Map<Creeper, CreeperAttentionState> ATTENTION_STATES = new WeakHashMap<>();

	public CreeperBlastChamberRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public AABB getRenderBoundingBox(CreeperBlastChamberBlockEntity blockEntity) {
		return blockEntity.getRenderBoundingBox();
	}

	@Override
	public void render(CreeperBlastChamberBlockEntity be, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int light, int overlay) {
		Level level = be.getLevel();
		if (level == null)
			return;

		BlockState blockState = be.getBlockState();
		VertexConsumer vertices = buffer.getBuffer(RenderType.cutout());
		float progress = be.displayGauge.getValue(partialTicks);
		if (be.isStructureValid()) {
			renderFormedPanels(be, poseStack, vertices, blockState, level, progress);
			if (be.shouldRenderCreeperFace())
				renderCreeperFace(be, poseStack, vertices, blockState, level);
			renderContainedCreepers(be, partialTicks, poseStack, buffer);
			return;
		}
		renderStandalonePanels(be, poseStack, vertices, blockState, level, progress);
	}

	private void renderFormedPanels(CreeperBlastChamberBlockEntity be, PoseStack poseStack, VertexConsumer vertices,
		BlockState blockState, Level level, float progress) {
		BlockPos origin = be.getStructureOrigin();
		if (origin == null)
			return;
		int size = be.getStructureSize();
		double centerLine = size / 2d;
		for (Direction direction : Iterate.horizontalDirections) {
			if (isFormedPanelBlocked(level, origin, size, direction))
				continue;
			double renderX = origin.getX() + (direction.getAxis() == Direction.Axis.X
				? direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - .5d : .5d : centerLine);
			double renderY = origin.getY() + .5d;
			double renderZ = origin.getZ() + (direction.getAxis() == Direction.Axis.Z
				? direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - .5d : .5d : centerLine);
			BlockPos lightPos = origin.offset(
				direction.getAxis() == Direction.Axis.X
					? direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - 1 : 0
					: Mth.clamp(Mth.floor(centerLine), 0, size - 1), 0,
				direction.getAxis() == Direction.Axis.Z
					? direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - 1 : 0
					: Mth.clamp(Mth.floor(centerLine), 0, size - 1));
			poseStack.pushPose();
			poseStack.translate(renderX - be.getBlockPos().getX(), renderY - be.getBlockPos().getY(),
				renderZ - be.getBlockPos().getZ());
			renderGauge(poseStack, vertices, blockState, direction,
				LevelRenderer.getLightColor(level, lightPos.relative(direction)), progress);
			poseStack.popPose();
		}
	}

	private boolean isFormedPanelBlocked(Level level, BlockPos origin, int size, Direction side) {
		int lowerCenter = (size - 1) / 2;
		int upperCenter = size / 2;
		int x = side.getAxis() == Direction.Axis.X
			? origin.getX() + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size : -1) : 0;
		int z = side.getAxis() == Direction.Axis.Z
			? origin.getZ() + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size : -1) : 0;
		if (side.getAxis() == Direction.Axis.X) {
			for (int zOffset = lowerCenter; zOffset <= upperCenter; zOffset++)
				if (!level.isEmptyBlock(new BlockPos(x, origin.getY(), origin.getZ() + zOffset)))
					return true;
			return false;
		}
		for (int xOffset = lowerCenter; xOffset <= upperCenter; xOffset++)
			if (!level.isEmptyBlock(new BlockPos(origin.getX() + xOffset, origin.getY(), z)))
				return true;
		return false;
	}

	private void renderStandalonePanels(CreeperBlastChamberBlockEntity be, PoseStack poseStack,
		VertexConsumer vertices, BlockState blockState, Level level, float progress) {
		BlockPos pos = be.getBlockPos();
		for (Direction direction : Iterate.horizontalDirections) {
			poseStack.pushPose();
			poseStack.translate(.5, .5, .5);
			renderGauge(poseStack, vertices, blockState, direction,
				LevelRenderer.getLightColor(level, pos.relative(direction)), progress);
			poseStack.popPose();
		}
	}

	private void renderGauge(PoseStack poseStack, VertexConsumer vertices, BlockState blockState, Direction side,
		int light, float progress) {
		float dialPivotY = 6f / 16;
		float dialPivotZ = 8f / 16;
		float yRot = -side.toYRot() - 90;
		CachedBuffers.partial(DISPLAY_PANEL, blockState).rotateYDegrees(yRot).uncenter()
			.translate(.5f - 6f / 16f, 0, 0).light(light).renderInto(poseStack, vertices);
		CachedBuffers.partial(DISPLAY_DIAL, blockState).rotateYDegrees(yRot).uncenter()
			.translate(.5f - 6f / 16f, 0, 0).translate(0, dialPivotY, dialPivotZ)
			.rotateXDegrees(-145 * progress + 90).translate(0, -dialPivotY, -dialPivotZ)
			.light(light).renderInto(poseStack, vertices);
	}

	private void renderCreeperFace(CreeperBlastChamberBlockEntity be, PoseStack poseStack, VertexConsumer vertices,
		BlockState blockState, Level level) {
		BlockPos pos = be.getBlockPos();
		for (Direction direction : Iterate.horizontalDirections) {
			poseStack.pushPose();
			poseStack.translate(.5, .5, .5);
			CachedBuffers.partial(CREEPER_FACE, blockState).rotateYDegrees(-direction.toYRot() - 90).uncenter()
				.light(LevelRenderer.getLightColor(level, pos.relative(direction))).renderInto(poseStack, vertices);
			poseStack.popPose();
		}
	}

	private void renderContainedCreepers(CreeperBlastChamberBlockEntity be, float partialTicks,
		PoseStack poseStack, MultiBufferSource buffer) {
		if (isBeyondCreeperRenderDistance(be))
			return;

		var working = be.getWorkingRenderCreepers();
		var animations = be.getRenderAnimations();
		if (working.isEmpty() && animations.isEmpty())
			return;
		Level level = be.getLevel();
		if (level == null)
			return;
		float renderTime = AnimationTickHolder.getRenderTime(level);
		Player nearbyPlayer = findNearbyPlayer(level, be);

		// One fancy-graphics scope and one lambda for the whole chamber rather than one per creeper.
		EntityRenderHelper.batch(() -> {
			for (CreeperBlastChamberBlockEntity.RenderManagedCreeper subject : working)
				renderProxy(be, subject.packagerPos(), subject.payload(), subject.renderSeed(), subject.defaultYaw(), partialTicks,
					0, 1, false, be.getWorkingCreeperCompression(subject.packagerPos(), partialTicks), poseStack,
					buffer, renderTime, nearbyPlayer);

			for (CreeperBlastChamberBlockEntity.RenderCreeperAnimation animation : animations)
				renderProxy(be, animation.packagerPos(), animation.payload(), animation.renderSeed(), animation.defaultYaw(),
					partialTicks, animation.ticksRemaining(), animation.totalTicks(), animation.exiting(), 0, poseStack,
					buffer, renderTime, nearbyPlayer);
		});
	}

	private Player findNearbyPlayer(Level level, CreeperBlastChamberBlockEntity be) {
		BlockPos origin = be.getStructureOrigin();
		if (origin == null)
			return null;
		double centerX = origin.getX() + be.getStructureSize() / 2d;
		double centerY = origin.getY() + 1.5d;
		double centerZ = origin.getZ() + be.getStructureSize() / 2d;
		double nearestDistanceSqr = PLAYER_ATTENTION_DISTANCE * PLAYER_ATTENTION_DISTANCE;
		Player nearest = null;
		for (Player player : level.players()) {
			if (!player.isAlive() || player.isSpectator())
				continue;
			double dx = player.getX() - centerX;
			double dy = player.getY() - centerY;
			double dz = player.getZ() - centerZ;
			double distanceSqr = dx * dx + dy * dy + dz * dz;
			if (distanceSqr > nearestDistanceSqr)
				continue;
			nearest = player;
			nearestDistanceSqr = distanceSqr;
		}
		return nearest;
	}

	/**
	 * The chamber is a sealed box, so the creepers inside it are barely legible from far away while
	 * still costing a full model plus, for charged ones, an extra swirl pass.
	 */
	private boolean isBeyondCreeperRenderDistance(CreeperBlastChamberBlockEntity be) {
		Vec3 camera = Minecraft.getInstance()
			.gameRenderer.getMainCamera()
			.getPosition();
		BlockPos pos = be.getBlockPos();
		return camera.distanceToSqr(pos.getX() + .5d, pos.getY() + .5d, pos.getZ() + .5d)
			> CREEPER_RENDER_DISTANCE * CREEPER_RENDER_DISTANCE;
	}

	private void renderProxy(CreeperBlastChamberBlockEntity be, BlockPos packagerPos, ItemStack payload,
		long renderSeed, float defaultYaw, float partialTicks, int ticksRemaining, int totalTicks, boolean exiting,
		float compression, PoseStack poseStack, MultiBufferSource buffer, float renderTime, Player nearbyPlayer) {
		Level level = be.getLevel();
		if (level == null || payload.isEmpty())
			return;
		Creeper creeper = CREEPER_CACHE.get(level,
			new CreeperCacheKey(be.getBlockPos(), packagerPos, renderSeed, payload));
		if (creeper == null)
			return;

		float scale = 1;
		float yOffset = 0;
		if (totalTicks > 1) {
			float linear = Mth.clamp((totalTicks - ticksRemaining + partialTicks) / totalTicks, 0, 1);
			float progress = linear * linear * (3 - 2 * linear);
			scale = exiting ? Mth.lerp(progress, 1, CREEPER_EXIT_END_SCALE)
				: Mth.lerp(progress, CREEPER_ENTRY_START_SCALE, 1);
			yOffset = exiting ? Mth.lerp(progress, 0, -CREEPER_ANIMATION_Y_OFFSET)
				: Mth.lerp(progress, -CREEPER_ANIMATION_Y_OFFSET, 0);
		}

		float pulse = .5f + .5f * Mth.sin(renderTime * .9f + (renderSeed & 31) * .07f);
		float swellValue = Mth.clamp(compression * Mth.lerp(pulse, .55f, 1f), 0, 1) * MAX_RENDER_SWELL;
		int swellFloor = Mth.floor(swellValue);
		float swellFraction = swellValue - swellFloor;

		CreeperAccessor accessor = (CreeperAccessor) creeper;
		int oldSwell = accessor.createBiotech$getOldSwell();
		int swell = accessor.createBiotech$getSwell();
		boolean charged = creeper.isPowered();

		// Straddling two swell values lets Mth.lerp inside Creeper.getSwelling recover the fraction,
		// which turns the strobe in getWhiteOverlayProgress from 25 steps into a continuous ramp.
		// Charged creepers keep real partial ticks instead, because their swirl layer reads them too.
		float renderPartialTicks = charged ? partialTicks : swellFraction;
		accessor.createBiotech$setOldSwell(swellFloor);
		accessor.createBiotech$setSwell(charged ? swellFloor : swellFloor + 1);

		float appliedSwell = Mth.clamp((charged ? swellFloor : swellValue) / SWELL_DIVISOR, 0f, 1f);
		float swellBulge = appliedSwell * appliedSwell * appliedSwell * appliedSwell;
		float horizontal = (1 + CREEPER_MAX_SPREAD * compression) / (1 + VANILLA_SWELL_SPREAD * swellBulge);
		float vertical = (1 + (CREEPER_FINAL_HEIGHT_SCALE - 1) * compression) / (1 + VANILLA_SWELL_RISE * swellBulge);

		CreeperAttentionState attention = ATTENTION_STATES
			.computeIfAbsent(creeper, ignored -> new CreeperAttentionState(renderSeed));
		attention.update(renderTime, defaultYaw, compression, creeper, nearbyPlayer);

		poseStack.pushPose();
		poseStack.translate(packagerPos.getX() - be.getBlockPos().getX() + .5d,
			packagerPos.getY() - be.getBlockPos().getY() + 1d + yOffset,
			packagerPos.getZ() - be.getBlockPos().getZ() + .5d);
		poseStack.scale(scale, scale, scale);
		poseStack.scale(horizontal, vertical, horizontal);

		EntityRenderHelper.RenderSettings<Creeper> settings = EntityRenderHelper.settings(creeper)
			.packedLight(LevelRenderer.getLightColor(level, packagerPos.above()))
			.partialTicks(renderPartialTicks)
			.yaw(attention.bodyYaw())
			.bodyYaw(attention.bodyYaw())
			.headYaw(attention.headYaw())
			.pitch(attention.pitch())
			.flushBuffers(false);
		if (charged)
			settings.ticks(Mth.floor(renderTime)
				+ (int) Math.floorMod(renderSeed, SWIRL_PHASE_BUCKETS) * SWIRL_PHASE_SPACING);
		EntityRenderHelper.render(settings, poseStack, buffer);
		poseStack.popPose();

		accessor.createBiotech$setOldSwell(oldSwell);
		accessor.createBiotech$setSwell(swell);
	}

	private enum AttentionMode {
		IDLE,
		LOOK,
		TURN
	}

	private static final class CreeperAttentionState {
		private static final long MIX_INCREMENT = 0x9E3779B97F4A7C15L;

		private final long renderSeed;
		private long sequence;
		private boolean initialized;
		private float lastRenderTime;
		private float nextDecisionTime;
		private float actionEndTime;
		/** Last body direction reached by a TURN action; compression must never reset it. */
		private float restingYaw;
		private float bodyYaw;
		private float headYaw;
		private float pitch;
		private AttentionMode mode = AttentionMode.IDLE;

		private CreeperAttentionState(long renderSeed) {
			this.renderSeed = renderSeed;
		}

		private void update(float renderTime, float defaultYaw, float compression, Creeper creeper,
			Player nearbyPlayer) {
			defaultYaw = Mth.wrapDegrees(defaultYaw);
			if (!initialized || renderTime < lastRenderTime) {
				initialized = true;
				lastRenderTime = renderTime;
				restingYaw = defaultYaw;
				bodyYaw = defaultYaw;
				headYaw = defaultYaw;
				pitch = 0;
				mode = AttentionMode.IDLE;
				nextDecisionTime = renderTime + nextDelay(ATTENTION_DELAY_MIN_TICKS, ATTENTION_DELAY_MAX_TICKS);
			}

			if (mode != AttentionMode.IDLE && renderTime >= actionEndTime) {
				if (mode == AttentionMode.TURN)
					restingYaw = bodyYaw;
				mode = AttentionMode.IDLE;
				nextDecisionTime = renderTime + nextDelay(ATTENTION_DELAY_MIN_TICKS, ATTENTION_DELAY_MAX_TICKS);
			}
			if (mode == AttentionMode.IDLE && renderTime >= nextDecisionTime) {
				if (nearbyPlayer == null) {
					nextDecisionTime = renderTime
						+ nextDelay(NO_PLAYER_RECHECK_MIN_TICKS, NO_PLAYER_RECHECK_MAX_TICKS);
				} else if (nextInt(3) == 0) {
					mode = AttentionMode.TURN;
					actionEndTime = renderTime + nextDelay(TURN_DURATION_MIN_TICKS, TURN_DURATION_MAX_TICKS);
				} else {
					mode = AttentionMode.LOOK;
					actionEndTime = renderTime + nextDelay(LOOK_DURATION_MIN_TICKS, LOOK_DURATION_MAX_TICKS);
				}
			}

			float desiredBodyYaw = restingYaw;
			float desiredHeadYaw = restingYaw;
			float desiredPitch = 0;
			if (mode != AttentionMode.IDLE && nearbyPlayer != null) {
				double dx = nearbyPlayer.getX() - creeper.getX();
				double dy = nearbyPlayer.getEyeY() - creeper.getEyeY();
				double dz = nearbyPlayer.getZ() - creeper.getZ();
				float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
				float targetPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
				float lookStrength = 1f - Mth.clamp(compression, 0f, 1f);
				if (mode == AttentionMode.TURN) {
					// Compression only changes the model shape; it must not pull the body
					// back toward the direction it faced before this turn.
					desiredBodyYaw = targetYaw;
					desiredHeadYaw = desiredBodyYaw;
				} else {
					float headDelta = Mth.clamp(Mth.wrapDegrees(targetYaw - restingYaw), -MAX_LOOK_HEAD_YAW,
						MAX_LOOK_HEAD_YAW);
					desiredHeadYaw = restingYaw + headDelta * lookStrength;
				}
				desiredPitch = Mth.clamp(targetPitch, -35f, 35f) * lookStrength;
			}

			float elapsed = Mth.clamp(renderTime - lastRenderTime, 0f, 5f);
			lastRenderTime = renderTime;
			float bodyBlend = 1f - (float) Math.pow(.78f, elapsed);
			float headBlend = 1f - (float) Math.pow(.6f, elapsed);
			bodyYaw = lerpAngle(bodyBlend, bodyYaw, desiredBodyYaw);
			headYaw = lerpAngle(headBlend, headYaw, desiredHeadYaw);
			pitch = Mth.lerp(headBlend, pitch, desiredPitch);
		}

		private float bodyYaw() {
			return bodyYaw;
		}

		private float headYaw() {
			return headYaw;
		}

		private float pitch() {
			return pitch;
		}

		private int nextDelay(int minInclusive, int maxInclusive) {
			return minInclusive + nextInt(maxInclusive - minInclusive + 1);
		}

		private int nextInt(int bound) {
			long mixed = mix64(renderSeed + sequence++ * MIX_INCREMENT);
			return (int) Math.floorMod(mixed, (long) bound);
		}

		private static long mix64(long value) {
			value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
			value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
			return value ^ value >>> 31;
		}

		private static float lerpAngle(float progress, float from, float to) {
			return Mth.wrapDegrees(from + Mth.wrapDegrees(to - from) * progress);
		}
	}

	@Override
	public boolean shouldRenderOffScreen(CreeperBlastChamberBlockEntity be) {
		return false;
	}

	private static final class CreeperCacheKey {
		private final BlockPos controllerPos;
		private final BlockPos packagerPos;
		private final long renderSeed;
		private final ItemStack payload;

		private CreeperCacheKey(BlockPos controllerPos, BlockPos packagerPos, long renderSeed, ItemStack payload) {
			this.controllerPos = controllerPos;
			this.packagerPos = packagerPos;
			this.renderSeed = renderSeed;
			this.payload = payload;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof CreeperCacheKey key && renderSeed == key.renderSeed
				&& controllerPos.equals(key.controllerPos) && packagerPos.equals(key.packagerPos);
		}

		@Override
		public int hashCode() {
			int result = controllerPos.hashCode();
			result = 31 * result + packagerPos.hashCode();
			return 31 * result + Long.hashCode(renderSeed);
		}
	}
}

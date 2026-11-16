package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.client.ClientParticleBudget;
import com.nobodiiiii.createbiotech.client.CreeperBlastChamberClientSoundHandler;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** ChamberClientEffects algorithms over the canonical CreeperBlastChamberBlockEntity state. */
final class ChamberClientEffects {
	private ChamberClientEffects() {}

	/** Replaced only on the client thread; Visuals retain a complete immutable frame while reading. */
	private static volatile PressRenderFrame pressRenderFrame = PressRenderFrame.EMPTY;
	private static boolean pressRenderStatesDirty = true;

	private record PressRenderFrame(@Nullable Level level, Map<Long, ChamberPressRenderState> chambers,
		Map<Long, ChamberPressRenderState.Phase> presses) {
		private static final PressRenderFrame EMPTY = new PressRenderFrame(null, Map.of(), Map.of());

		private PressRenderFrame {
			chambers = Map.copyOf(chambers);
			presses = Map.copyOf(presses);
		}
	}

	static void registerClientLoadedChamber(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || !level.isClientSide)
			return;
		CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.computeIfAbsent(level, ignored -> new HashSet<>())
			.add(operationOwner.getBlockPos().immutable());
		pressRenderStatesDirty = true;
	}

	static void unregisterClientLoadedChamber(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || !level.isClientSide)
			return;
		pressRenderStatesDirty = true;
		Set<BlockPos> chambers = CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.get(level);
		if (chambers == null)
			return;
		chambers.remove(operationOwner.getBlockPos());
		if (chambers.isEmpty())
			CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.remove(level);
	}

	static void tickClientAnimations(CreeperBlastChamberBlockEntity operationOwner) {
		ChamberClientEffects.tickClientAppearanceEffects(operationOwner);
		ChamberClientEffects.tickClientAnimationList(operationOwner.pendingAppearances);
		ChamberClientEffects.tickClientAnimationList(operationOwner.pendingPackagings);
		ChamberClientEffects.tickClientWorkingCreeperEffects(operationOwner);
	}

	/** Puffs a ring of smoke the first client tick a creeper appears in a packager. */
	static void tickClientAppearanceEffects(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || !level.isClientSide)
			return;
		if (operationOwner.pendingAppearances.isEmpty()) {
			operationOwner.clientAppearanceEffectsSpawned.clear();
			return;
		}

		for (CreeperBlastChamberBlockEntity.PendingAppearance pending : operationOwner.pendingAppearances) {
			if (!operationOwner.clientAppearanceEffectsSpawned.add(pending.packagerPos.asLong()))
				continue;
			ChamberClientEffects.spawnAppearancePuff(operationOwner, level, pending.packagerPos);
		}
		operationOwner.clientAppearanceEffectsSpawned.removeIf(key -> !operationOwner.isPackagerAppearing(BlockPos.of(key)));
	}

	static void spawnAppearancePuff(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos packagerPos) {
		int stride = ClientParticleBudget.decorativeStride();
		double centerX = packagerPos.getX() + .5d;
		double centerY = packagerPos.getY() + 1.15d;
		double centerZ = packagerPos.getZ() + .5d;
		for (int index = 0; index < CreeperBlastChamberBlockEntity.APPEARANCE_PUFF_COUNT; index += stride) {
			double angle = Math.PI * 2d * index / CreeperBlastChamberBlockEntity.APPEARANCE_PUFF_COUNT;
			double offsetX = Math.cos(angle);
			double offsetZ = Math.sin(angle);
			level.addParticle(ParticleTypes.POOF, centerX + offsetX * CreeperBlastChamberBlockEntity.APPEARANCE_PUFF_RADIUS, centerY,
				centerZ + offsetZ * CreeperBlastChamberBlockEntity.APPEARANCE_PUFF_RADIUS, offsetX * .02d, .02d, offsetZ * .02d);
		}
	}

	static void invalidatePressRenderStates(CreeperBlastChamberBlockEntity chamber) {
		Level level = chamber.getLevel();
		if (level != null && level.isClientSide) pressRenderStatesDirty = true;
	}

	static void prepareDirtyPressRenderStates(@Nullable Level level) {
		if (pressRenderStatesDirty || pressRenderFrame.level != level)
			preparePressRenderStates(level);
	}

	/** Called after client ticks or before rendering, never from Flywheel's parallel callbacks. */
	static void preparePressRenderStates(@Nullable Level level) {
		if (level == null) {
			clearPressRenderStates(null);
			return;
		}
		Map<Long, ChamberPressRenderState> chambers = new HashMap<>();
		Map<Long, ChamberPressRenderState.Phase> presses = new HashMap<>();
		Set<BlockPos> loaded = CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.get(level);
		if (loaded != null) {
			for (BlockPos position : loaded) {
				if (!(level.getBlockEntity(position) instanceof CreeperBlastChamberBlockEntity chamber)
					|| chamber.isRemoved()) continue;
				ChamberPressRenderState state = ChamberPressRenderState.capture(chamber);
				chambers.put(position.asLong(), state);
				presses.putAll(state.presses());
			}
		}
		// The level identity, all phases and every chamber's flags become visible together.
		pressRenderFrame = new PressRenderFrame(level, chambers, presses);
		pressRenderStatesDirty = false;
	}

	static void clearPressRenderStates(@Nullable LevelAccessor level) {
		if (level == null) {
			CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.clear();
		} else {
			CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.remove(level);
		}
		if (level == null || pressRenderFrame.level == level) pressRenderFrame = PressRenderFrame.EMPTY;
		pressRenderStatesDirty = false;
	}

	static ChamberPressRenderState pressRenderState(CreeperBlastChamberBlockEntity chamber) {
		PressRenderFrame frame = pressRenderFrame;
		return frame.level == chamber.getLevel()
			? frame.chambers.getOrDefault(chamber.getBlockPos().asLong(), ChamberPressRenderState.EMPTY)
			: ChamberPressRenderState.EMPTY;
	}

	static void tickClientWorkingCreeperEffects(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || !operationOwner.structureValid) {
			operationOwner.clientPressOffsets.clear();
			operationOwner.clientReturnEffectsArmed.clear();
			return;
		}

		operationOwner.clientNextPressOffsets.clear();
		operationOwner.clientActivePackagers.clear();
		boolean spawnedReturnEffectThisTick = false;
		var working = ChamberClientEffects.getWorkingRenderCreepers(operationOwner);
		// Tick effects keep their original timing and use a private capture, without publishing
		// intermediate block-entity tick order to the parallel renderer.
		ChamberPressRenderState state = working.isEmpty() ? ChamberPressRenderState.EMPTY
			: ChamberPressRenderState.capture(operationOwner);
		for (CreeperBlastChamberBlockEntity.RenderManagedCreeper creeper : working) {
			long key = creeper.packagerPos().asLong();
			float pressOffset = state.creeperOffset(key, 0);
			float previousOffset = operationOwner.clientPressOffsets.getOrDefault(key, 0f);
			boolean returning = ChamberClientEffects.isPressReturning(operationOwner, previousOffset, pressOffset);

			operationOwner.clientNextPressOffsets.put(key, pressOffset);
			operationOwner.clientActivePackagers.add(key);
			if (previousOffset < CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET && pressOffset >= CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET) {
				BlockPos pos = creeper.packagerPos();
				float pitch = 0.9f + ((Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 8)) * 0.025f);
				level.playLocalSound(pos.getX() + 0.5d, pos.getY() + 1d, pos.getZ() + 0.5d, SoundEvents.CREEPER_PRIMED,
					SoundSource.BLOCKS, 0.2f, pitch, false);
			}

			if (pressOffset >= CreeperBlastChamberBlockEntity.CLIENT_RETURN_EFFECT_ARM_THRESHOLD)
				operationOwner.clientReturnEffectsArmed.add(key);
			if (!spawnedReturnEffectThisTick && operationOwner.clientReturnEffectsArmed.contains(key) && returning) {
				ChamberClientEffects.spawnClientReturnExplosionEffect(operationOwner);
				spawnedReturnEffectThisTick = true;
			}
			if (pressOffset <= CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET * 0.5f)
				operationOwner.clientReturnEffectsArmed.remove(key);
		}

		operationOwner.clientPressOffsets.clear();
		operationOwner.clientPressOffsets.putAll(operationOwner.clientNextPressOffsets);
		if (spawnedReturnEffectThisTick)
			operationOwner.clientReturnEffectsArmed.clear();
		operationOwner.clientReturnEffectsArmed.removeIf(key -> !operationOwner.clientActivePackagers.contains(key));
	}

	static boolean isPressReturning(CreeperBlastChamberBlockEntity operationOwner, float previousOffset, float pressOffset) {
		return previousOffset > pressOffset + CreeperBlastChamberBlockEntity.CLIENT_PRESS_RETURN_EPSILON
			&& previousOffset >= CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET;
	}

	static void spawnClientReturnExplosionEffect(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || !level.isClientSide || !operationOwner.structureValid || operationOwner.structureOrigin == null)
			return;
		if (!CreeperBlastChamberBlockEntity.areExplosionParticlesEnabled())
			return;

		int innerSize = Mth.clamp(operationOwner.structureSize - 2, 1, 3);
		double centerX = operationOwner.structureOrigin.getX() + operationOwner.structureSize / 2d;
		double centerY = operationOwner.structureOrigin.getY() + CreeperBlastChamberBlockEntity.CLIENT_RETURN_EFFECT_Y_OFFSET;
		double centerZ = operationOwner.structureOrigin.getZ() + operationOwner.structureSize / 2d;
		double firstOffset = -((innerSize - 1) / 2d);
		CreeperBlastChamberClientSoundHandler.stopManagedPrimedSound();
		level.playLocalSound(centerX, centerY, centerZ, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.45f, 1.15f,
			false);

		for (int xIndex = 0; xIndex < innerSize; xIndex++) {
			for (int zIndex = 0; zIndex < innerSize; zIndex++) {
				double x = centerX + firstOffset + xIndex
					+ (level.random.nextDouble() * 2d - 1d) * CreeperBlastChamberBlockEntity.CLIENT_RETURN_BASE_EXPLOSION_JITTER;
				double z = centerZ + firstOffset + zIndex
					+ (level.random.nextDouble() * 2d - 1d) * CreeperBlastChamberBlockEntity.CLIENT_RETURN_BASE_EXPLOSION_JITTER;
				ChamberClientEffects.spawnRandomizedReturnExplosionParticle(operationOwner, level, x, centerY, z);
			}
		}

		int extraExplosionCount = Math.max(CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXTRA_EXPLOSION_MIN,
			(level.random.nextInt(CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXTRA_EXPLOSION_MAX - CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXTRA_EXPLOSION_MIN + 1)
				+ CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXTRA_EXPLOSION_MIN) / ClientParticleBudget.decorativeStride());
		for (int i = 0; i < extraExplosionCount; i++) {
			double x = centerX + (level.random.nextDouble() * 2d - 1d) * CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXTRA_EXPLOSION_RADIUS;
			double z = centerZ + (level.random.nextDouble() * 2d - 1d) * CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXTRA_EXPLOSION_RADIUS;
			ChamberClientEffects.spawnRandomizedReturnExplosionParticle(operationOwner, level, x, centerY, z);
		}

		ChamberClientEffects.spawnClientShellSurfaceExplosionEffect(operationOwner, level, centerY);
	}

	static void spawnRandomizedReturnExplosionParticle(CreeperBlastChamberBlockEntity operationOwner, Level level, double x, double y, double z) {
		if (!CreeperBlastChamberBlockEntity.areExplosionParticlesEnabled())
			return;
		double sizeParam = Mth.lerp(level.random.nextDouble(),
			CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MIN, CreeperBlastChamberBlockEntity.CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MAX);
		level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, x, y, z, sizeParam, 0, 0);
	}

	static void spawnClientShellSurfaceExplosionEffect(CreeperBlastChamberBlockEntity operationOwner, Level level, double centerY) {
		if (operationOwner.structureOrigin == null || operationOwner.structureSize <= 0)
			return;

		int stride = ClientParticleBudget.decorativeStride();
		int candidate = 0;
		for (int yOffset = 1; yOffset <= 2; yOffset++) {
			for (int xOffset = 0; xOffset < operationOwner.structureSize; xOffset++) {
				for (int zOffset = 0; zOffset < operationOwner.structureSize; zOffset++) {
					boolean onXWall = xOffset == 0 || xOffset == operationOwner.structureSize - 1;
					boolean onZWall = zOffset == 0 || zOffset == operationOwner.structureSize - 1;
					if (onXWall == onZWall)
						continue;
					if (candidate++ % stride != 0)
						continue;

					BlockPos pos = operationOwner.structureOrigin.offset(xOffset, yOffset, zOffset);
					BlockState state = level.getBlockState(pos);
					if (!ChamberClientEffects.isClientOuterShellBlock(operationOwner, state))
						continue;

					double normalX = xOffset == 0 ? -1d : xOffset == operationOwner.structureSize - 1 ? 1d : 0d;
					double normalZ = zOffset == 0 ? -1d : zOffset == operationOwner.structureSize - 1 ? 1d : 0d;
					double x = pos.getX() + 0.5d + normalX * 0.55d;
					double y = Mth.lerp(level.random.nextDouble(), pos.getY() + 0.2d, pos.getY() + 0.8d);
					double z = pos.getZ() + 0.5d + normalZ * 0.55d;
					ChamberClientEffects.spawnRandomizedReturnExplosionParticle(operationOwner, level, x, y, z);
				}
			}
		}

		double roofX = operationOwner.structureOrigin.getX() + operationOwner.structureSize / 2d;
		double roofZ = operationOwner.structureOrigin.getZ() + operationOwner.structureSize / 2d;
		ChamberClientEffects.spawnRandomizedReturnExplosionParticle(operationOwner, level, roofX, centerY + 0.35d, roofZ);
	}

	static boolean isClientOuterShellBlock(CreeperBlastChamberBlockEntity operationOwner, BlockState state) {
		if (state.isAir())
			return false;
		if (state.getBlock() instanceof CreeperBlastChamberBlock)
			return true;
		return state.is(CBBlocks.EXPLOSION_PROOF_CASING.get())
			|| state.is(CBBlocks.BLAST_PROOF_GLASS.get())
			|| state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get())
			|| state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get());
	}

	static <T extends CreeperBlastChamberBlockEntity.TimedAnimation> void tickClientAnimationList(List<T> animations) {
		Iterator<T> iterator = animations.iterator();
		while (iterator.hasNext()) {
			T animation = iterator.next();
			if (animation.ticksRemaining > 0)
				animation.ticksRemaining--;
			if (animation.ticksRemaining <= 0)
				iterator.remove();
		}
	}

	static @Nullable
	BlockPos findAvailablePackagerForNewInput(CreeperBlastChamberBlockEntity operationOwner) {
		if (ChamberContainment.getHeldCreeperCount(operationOwner) >= ChamberStructure.getPackagerPositions(operationOwner).size())
			return null;
		return ChamberContainment.findFirstEmptyPackagerSlot(operationOwner);
	}

	/**
	 * Returns a list reused between calls, so consume it before asking for another. Both this and
	 * {@link #getRenderAnimations()} run every frame for every visible chamber.
	 */
	static List<CreeperBlastChamberBlockEntity.RenderManagedCreeper> getWorkingRenderCreepers(CreeperBlastChamberBlockEntity operationOwner) {
		operationOwner.workingRenderCreepers.clear();
		if (!operationOwner.structureValid || operationOwner.getLevel() == null || operationOwner.storedCreepers.isEmpty())
			return operationOwner.workingRenderCreepers;
		for (CreeperBlastChamberBlockEntity.StoredCreeper stored : operationOwner.storedCreepers.values()) {
			BlockPos packagerPos = stored.packagerPos();
			if (operationOwner.isPackagerAppearing(packagerPos) || ChamberContainment.isPackagerPackaging(operationOwner, packagerPos))
				continue;
			operationOwner.workingRenderCreepers
				.add(new CreeperBlastChamberBlockEntity.RenderManagedCreeper(packagerPos, stored.normalizedPayloadBox(), stored.renderSeed(),
					stored.defaultYaw()));
		}
		return operationOwner.workingRenderCreepers;
	}

	/** @see #getWorkingRenderCreepers() for the reuse contract. */
	static List<CreeperBlastChamberBlockEntity.RenderCreeperAnimation> getRenderAnimations(CreeperBlastChamberBlockEntity operationOwner) {
		operationOwner.renderAnimations.clear();
		if (operationOwner.pendingAppearances.isEmpty() && operationOwner.pendingPackagings.isEmpty())
			return operationOwner.renderAnimations;
		for (CreeperBlastChamberBlockEntity.PendingAppearance pending : operationOwner.pendingAppearances) {
			CreeperBlastChamberBlockEntity.StoredCreeper stored = operationOwner.storedCreepers.get(pending.packagerPos);
			if (stored != null)
				operationOwner.renderAnimations.add(new CreeperBlastChamberBlockEntity.RenderCreeperAnimation(pending.packagerPos, stored.normalizedPayloadBox(),
					stored.renderSeed(), stored.defaultYaw(), pending.ticksRemaining, pending.totalTicks, false));
		}
		for (CreeperBlastChamberBlockEntity.PendingPackaging pending : operationOwner.pendingPackagings) {
			int packagingTicksElapsed = pending.totalTicks - pending.ticksRemaining;
			int exitTicksRemaining = CreeperBlastChamberBlockEntity.CREEPER_EXIT_ANIMATION_TICKS - packagingTicksElapsed;
			if (exitTicksRemaining <= 0)
				continue;
			CreeperBlastChamberBlockEntity.StoredCreeper stored = operationOwner.storedCreepers.get(pending.packagerPos);
			ItemStack payload = stored == null ? pending.boxStack : stored.normalizedPayloadBox();
			long renderSeed = stored == null ? pending.packagerPos.asLong() : stored.renderSeed();
			float defaultYaw = stored == null ? Direction.NORTH.toYRot() : stored.defaultYaw();
			operationOwner.renderAnimations.add(new CreeperBlastChamberBlockEntity.RenderCreeperAnimation(pending.packagerPos, payload, renderSeed, defaultYaw,
				exitTicksRemaining, CreeperBlastChamberBlockEntity.CREEPER_EXIT_ANIMATION_TICKS, true));
		}
		return operationOwner.renderAnimations;
	}

	static float getClientWorkingCreeperCompression(Creeper creeper, float partialTicks) {
		CompoundTag biotechData = CreeperBlastChamberBlockEntity.getExistingCreateBiotechData(creeper);
		if (biotechData != null) {
			if (biotechData.contains(CreeperBlastChamberBlockEntity.PONDER_COMPRESSION_ANIM_TAG, Tag.TAG_COMPOUND)) {
				return ChamberClientEffects.computePonderCompressionAnim(creeper, partialTicks,
					biotechData.getCompound(CreeperBlastChamberBlockEntity.PONDER_COMPRESSION_ANIM_TAG));
			}
			if (biotechData.contains(CreeperBlastChamberBlockEntity.PONDER_COMPRESSION_TAG, Tag.TAG_FLOAT)) {
				return Mth.clamp(biotechData.getFloat(CreeperBlastChamberBlockEntity.PONDER_COMPRESSION_TAG), 0f, 1f);
			}
		}
		return 0f;
	}

	static float computePonderCompressionAnim(Creeper creeper, float partialTicks, CompoundTag anim) {
		int startTick = anim.getInt("StartTick");
		int rtStart = anim.getInt("RtStart");
		int tickSpeed = anim.getInt("TickSpeed");
		float peak = anim.contains("Peak", Tag.TAG_FLOAT) ? anim.getFloat("Peak") : 1f;
		float elapsed = (creeper.tickCount + partialTicks) - startTick;
		if (elapsed < 0)
			return 0f;
		float rt = rtStart + elapsed * tickSpeed;
		if (rt < 0f || rt >= 240f)
			return 0f;
		float progress;
		if (rt < 160f)
			progress = (float) Math.pow(rt / 240f * 2f, 3);
		else
			progress = (240f - rt) / 240f * 3f;
		progress = Mth.clamp(progress, 0f, 1f);
		return Mth.clamp((progress - CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET)
			/ (1f - CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET), 0f, 1f) * peak;
	}

	static float getSynchronizedPressHeadProgress(@Nullable MechanicalPressBlockEntity press, float partialTicks) {
		if (press == null)
			return 0f;

		PressRenderFrame frame = pressRenderFrame;
		ChamberPressRenderState.Phase phase = frame.level != null && frame.level == press.getLevel()
			? frame.presses.get(press.getBlockPos().asLong()) : null;
		return phase == null ? ChamberClientEffects.getLocalPressHeadProgress(press.getPressingBehaviour(), partialTicks)
			: phase.progress(partialTicks);
	}

	static float getSynchronizedPressHeadOffset(@Nullable MechanicalPressBlockEntity press, float partialTicks) {
		if (press == null)
			return 0f;
		PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
		if (pressingBehaviour.mode == null)
			return 0f;
		return ChamberClientEffects.getSynchronizedPressHeadProgress(press, partialTicks) * pressingBehaviour.mode.headOffset;
	}

	static float getCompressionFromPressOffset(float pressOffset) {
		return Mth.clamp((pressOffset - CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET) / (1f - CreeperBlastChamberBlockEntity.CLIENT_PRESS_EFFECT_START_OFFSET), 0f,
			1f);
	}

	static float getLocalPressHeadProgress(PressingBehaviour pressingBehaviour, float partialTicks) {
		return ChamberPressRenderState.Phase.progress(pressingBehaviour.mode != null && pressingBehaviour.running,
			pressingBehaviour.prevRunningTicks, pressingBehaviour.runningTicks, partialTicks);
	}
}

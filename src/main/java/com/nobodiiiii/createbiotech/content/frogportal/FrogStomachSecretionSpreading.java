package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Sculk-catalyst-style spreading for Frog Stomach Secretion. A dying vanilla slime contributes its
 * normal experience value as charge to the nearest secretion block within eight blocks. Persistent
 * cursors then walk through connected secretion in all six directions and spend one charge for
 * each new three-dimensionally supported secretion block.
 */
public final class FrogStomachSecretionSpreading {

	private static final String DATA_NAME = "create_biotech_frog_stomach_secretion_spreading";
	private static final String CURSORS_TAG = "Cursors";
	private static final int LISTENER_RADIUS = 8;
	private static final double LISTENER_RADIUS_SQR = LISTENER_RADIUS * LISTENER_RADIUS;
	private static final int MAX_CURSORS = 32;
	private static final int MAX_CURSOR_CHARGE = 1000;
	private static final int SPREAD_DELAY = 1;
	private static final int SPREAD_SOUND_COOLDOWN_TICKS = 5;
	private static final double SPREAD_SOUND_RADIUS_SQR = 8.0d * 8.0d;
	private static final int MAX_STALLED_UPDATES = 32;
	private FrogStomachSecretionSpreading() {}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FrogStomachSecretionSpreading::onLivingDeath);
		NeoForge.EVENT_BUS.addListener(FrogStomachSecretionSpreading::onLevelTick);
	}

	private static void onLivingDeath(LivingDeathEvent event) {
		if (event.isCanceled() || event.getEntity().getType() != EntityType.SLIME
			|| !(event.getEntity() instanceof Slime slime)
			|| !(slime.level() instanceof ServerLevel level)
			|| !level.dimension().equals(FrogStomachDimensions.FROG_STOMACH)
			|| slime.wasExperienceConsumed())
			return;

		BlockPos secretionPos = findNearestSecretion(level, slime.blockPosition());
		if (secretionPos == null)
			return;

		int charge = slime.getExperienceReward(level, event.getSource().getEntity());
		if (slime.shouldDropExperience() && charge > 0)
			SpreadingData.get(level).addCharge(secretionPos, charge);

		// Like a sculk catalyst, the secretion converts the death's experience into spreading charge.
		slime.skipDropExperience();
		bloom(level, secretionPos);
	}

	private static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel level
			&& level.dimension().equals(FrogStomachDimensions.FROG_STOMACH))
			SpreadingData.get(level).tick(level);
	}

	private static BlockPos findNearestSecretion(ServerLevel level, BlockPos deathPos) {
		BlockPos closest = null;
		double closestDistance = LISTENER_RADIUS_SQR + 1.0d;
		BlockPos min = deathPos.offset(-LISTENER_RADIUS, -LISTENER_RADIUS, -LISTENER_RADIUS);
		BlockPos max = deathPos.offset(LISTENER_RADIUS, LISTENER_RADIUS, LISTENER_RADIUS);

		for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
			double distance = candidate.distSqr(deathPos);
			if (distance > LISTENER_RADIUS_SQR || distance >= closestDistance
				|| !level.getChunkSource().hasChunk(candidate.getX() >> 4, candidate.getZ() >> 4)
				|| !level.getBlockState(candidate).is(CBBlocks.FROG_STOMACH_SECRETION.get()))
				continue;
			closest = candidate.immutable();
			closestDistance = distance;
		}
		return closest;
	}

	private static void bloom(ServerLevel level, BlockPos pos) {
		RandomSource random = level.getRandom();
		sendSecretionParticles(level, pos, 8);
		level.playSound(null, pos, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS,
			1.0f, 0.6f + random.nextFloat() * 0.4f);
	}

	private static void sendSecretionParticles(ServerLevel level, BlockPos pos, int count) {
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK,
			CBBlocks.FROG_STOMACH_SECRETION.get().defaultBlockState());
		level.sendParticles(particle, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
			count, 0.3d, 0.3d, 0.3d, 0.05d);
	}

	private static final class SpreadingData extends SavedData {

		private final List<ChargeCursor> cursors = new ArrayList<>();
		// Runtime-only sound throttling shared by nearby cursors; never persisted.
		private final Map<BlockPos, Long> recentSpreadSounds = new HashMap<>();

		static SpreadingData get(ServerLevel level) {
			return level.getDataStorage()
				.computeIfAbsent(new SavedData.Factory<>(SpreadingData::new, SpreadingData::load), DATA_NAME);
		}

		static SpreadingData load(CompoundTag tag, HolderLookup.Provider registries) {
			SpreadingData data = new SpreadingData();
			ListTag cursorTags = tag.getList(CURSORS_TAG, Tag.TAG_COMPOUND);
			for (Tag rawCursor : cursorTags) {
				if (data.cursors.size() >= MAX_CURSORS)
					break;
				CompoundTag cursorTag = (CompoundTag) rawCursor;
				int charge = cursorTag.getInt("Charge");
				if (!cursorTag.contains("Pos", Tag.TAG_LONG) || charge <= 0)
					continue;
				data.cursors.add(new ChargeCursor(
					BlockPos.of(cursorTag.getLong("Pos")),
					Math.min(charge, MAX_CURSOR_CHARGE),
					Math.max(0, cursorTag.getInt("UpdateDelay")),
					Math.max(0, cursorTag.getInt("StalledUpdates"))));
			}
			return data;
		}

		void addCharge(BlockPos pos, int charge) {
			for (ChargeCursor cursor : cursors) {
				if (!cursor.pos.equals(pos) || cursor.charge >= MAX_CURSOR_CHARGE)
					continue;
				int transferred = Math.min(charge, MAX_CURSOR_CHARGE - cursor.charge);
				cursor.charge += transferred;
				charge -= transferred;
				if (charge == 0) {
					setDirty();
					return;
				}
			}

			while (charge > 0 && cursors.size() < MAX_CURSORS) {
				int cursorCharge = Math.min(charge, MAX_CURSOR_CHARGE);
				cursors.add(new ChargeCursor(pos.immutable(), cursorCharge, 0, 0));
				charge -= cursorCharge;
			}
			setDirty();
		}

		void tick(ServerLevel level) {
			long gameTime = level.getGameTime();
			recentSpreadSounds.entrySet().removeIf(entry -> gameTime - entry.getValue() >= SPREAD_SOUND_COOLDOWN_TICKS);
			if (cursors.isEmpty())
				return;

			RandomSource random = level.getRandom();
			boolean changed = false;
			for (int i = cursors.size() - 1; i >= 0; i--) {
				ChargeCursor cursor = cursors.get(i);
				if (!level.shouldTickBlocksAt(cursor.pos))
					continue;

				changed = true;
				if (cursor.updateDelay > 0) {
					cursor.updateDelay--;
				} else {
					updateCursor(level, random, cursor);
					cursor.updateDelay = SPREAD_DELAY;
				}

				if (cursor.charge <= 0) {
					sendSecretionParticles(level, cursor.pos, 1);
					cursors.remove(i);
				} else {
					sendSecretionParticles(level, cursor.pos, chargeParticleCount(cursor.charge));
				}
			}

			if (changed)
				setDirty();
		}

		private void updateCursor(ServerLevel level, RandomSource random, ChargeCursor cursor) {
			if (!level.getBlockState(cursor.pos).is(CBBlocks.FROG_STOMACH_SECRETION.get())) {
				cursor.charge = 0;
				return;
			}

			BlockPos spreadPos = findSpreadPos(level, cursor.pos, random);
			if (spreadPos != null) {
				BlockState replacedState = level.getBlockState(spreadPos);
				BlockState secretionState = CBBlocks.FROG_STOMACH_SECRETION.get().defaultBlockState();
				level.setBlock(spreadPos, secretionState, Block.UPDATE_ALL);
				Block.pushEntitiesUp(replacedState, secretionState, level, spreadPos);
				playSpreadSound(level, spreadPos);
				cursor.pos = spreadPos;
				cursor.charge--;
				cursor.stalledUpdates = 0;
				return;
			}

			BlockPos movementPos = findMovementPos(level, cursor.pos, random);
			if (movementPos != null)
				cursor.pos = movementPos;
			cursor.stalledUpdates++;
			if (movementPos == null || cursor.stalledUpdates >= MAX_STALLED_UPDATES) {
				cursor.charge /= 2;
				cursor.stalledUpdates = 0;
			}
		}

		private void playSpreadSound(ServerLevel level, BlockPos pos) {
			for (BlockPos recentPos : recentSpreadSounds.keySet())
				if (recentPos.distSqr(pos) <= SPREAD_SOUND_RADIUS_SQR)
					return;
			level.playSound(null, pos, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 0.3f, 1.0f);
			recentSpreadSounds.put(pos.immutable(), level.getGameTime());
		}

		private static BlockPos findSpreadPos(ServerLevel level, BlockPos pos, RandomSource random) {
			for (Direction direction : Direction.allShuffled(random)) {
				BlockPos candidate = pos.relative(direction);
				if (!level.isInWorldBounds(candidate)
					|| !level.getBlockState(candidate).isAir()
					|| !hasSturdyNeighbour(level, candidate))
					continue;
				return candidate.immutable();
			}
			return null;
		}

		private static boolean hasSturdyNeighbour(ServerLevel level, BlockPos candidate) {
			for (Direction direction : Direction.values()) {
				BlockPos supportPos = candidate.relative(direction);
				if (level.getBlockState(supportPos)
					.isFaceSturdy(level, supportPos, direction.getOpposite()))
					return true;
			}
			return false;
		}

		private static BlockPos findMovementPos(ServerLevel level, BlockPos pos, RandomSource random) {
			for (Direction direction : Direction.allShuffled(random)) {
				BlockPos candidate = pos.relative(direction);
				if (level.getBlockState(candidate).is(CBBlocks.FROG_STOMACH_SECRETION.get()))
					return candidate.immutable();
			}
			return null;
		}

		private static int chargeParticleCount(int charge) {
			return Math.min(8, (int) (Math.log1p(charge) / 2.3f) + 1);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag cursorTags = new ListTag();
			for (ChargeCursor cursor : cursors) {
				CompoundTag cursorTag = new CompoundTag();
				cursorTag.putLong("Pos", cursor.pos.asLong());
				cursorTag.putInt("Charge", cursor.charge);
				cursorTag.putInt("UpdateDelay", cursor.updateDelay);
				cursorTag.putInt("StalledUpdates", cursor.stalledUpdates);
				cursorTags.add(cursorTag);
			}
			tag.put(CURSORS_TAG, cursorTags);
			return tag;
		}
	}

	private static final class ChargeCursor {

		private BlockPos pos;
		private int charge;
		private int updateDelay;
		private int stalledUpdates;

		private ChargeCursor(BlockPos pos, int charge, int updateDelay, int stalledUpdates) {
			this.pos = pos;
			this.charge = charge;
			this.updateDelay = updateDelay;
			this.stalledUpdates = stalledUpdates;
		}
	}
}

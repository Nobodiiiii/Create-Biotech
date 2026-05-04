package com.nobodiiiii.createbiotech.content.evokertank;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class EvokerTankBlockEntity extends BlockEntity {

	private static final int CAST_DURATION_TICKS = 40;
	private static final double SPELL_RED = 0.4d;
	private static final double SPELL_GREEN = 0.3d;
	private static final double SPELL_BLUE = 0.35d;
	private int castingTicksRemaining;

	public EvokerTankBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EVOKER_TANK.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EvokerTankBlockEntity blockEntity) {
		if (level.isClientSide || blockEntity.castingTicksRemaining <= 0)
			return;

		spawnCastingParticles((ServerLevel) level, pos, state, blockEntity.castingTicksRemaining);

		blockEntity.castingTicksRemaining--;
		if (blockEntity.castingTicksRemaining == 0)
			blockEntity.syncToClient();
	}

	public void triggerCasting() {
		boolean wasCasting = isCastingSpell();
		castingTicksRemaining = CAST_DURATION_TICKS;
		setChanged();
		if (!wasCasting)
			syncToClient();
	}

	public boolean isCastingSpell() {
		return castingTicksRemaining > 0;
	}

	public float getAnimationTime(float partialTick) {
		return level == null ? partialTick : level.getGameTime() + partialTick;
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("CastingTicks", castingTicksRemaining);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		castingTicksRemaining = tag.getInt("CastingTicks");
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private void syncToClient() {
		if (level == null)
			return;

		setChanged();
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, 3);
	}

	private static void spawnCastingParticles(ServerLevel level, BlockPos pos, BlockState state, int animationTicks) {
		float bodyYaw = 180.0f - state.getValue(EvokerTankBlock.FACING).toYRot();
		float spellAngle = bodyYaw * Mth.DEG_TO_RAD + Mth.cos(animationTicks * 0.6662f) * 0.25f;
		float xOffset = Mth.cos(spellAngle) * 0.6f;
		float zOffset = Mth.sin(spellAngle) * 0.6f;
		double centerX = pos.getX() + 0.5d;
		double centerY = pos.getY() + 1.8d;
		double centerZ = pos.getZ() + 0.5d;

		level.sendParticles(ParticleTypes.ENTITY_EFFECT, centerX + xOffset, centerY, centerZ + zOffset, 0,
			SPELL_RED, SPELL_GREEN, SPELL_BLUE, 1.0d);
		level.sendParticles(ParticleTypes.ENTITY_EFFECT, centerX - xOffset, centerY, centerZ - zOffset, 0,
			SPELL_RED, SPELL_GREEN, SPELL_BLUE, 1.0d);
	}
}

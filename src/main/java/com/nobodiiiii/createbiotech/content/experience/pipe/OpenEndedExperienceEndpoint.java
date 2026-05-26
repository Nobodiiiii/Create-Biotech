package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.util.List;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.experience.ExperienceHelper;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class OpenEndedExperienceEndpoint implements ExperienceEndpoint {
	private static final double ABSORB_HALF_EXTENT = 0.75d;

	private final BlockFace location;
	@Nullable
	private Level world;

	public OpenEndedExperienceEndpoint(BlockFace location) {
		this.location = location;
	}

	public void bind(Level level) {
		this.world = level;
	}

	@Override
	public int extract(int maxAmount, boolean simulate) {
		if (maxAmount <= 0 || world == null || world.isClientSide)
			return 0;
		if (!world.isLoaded(location.getConnectedPos()))
			return 0;
		if (!ExperiencePropagator.isOpenEnd(world, location.getPos(), location.getFace()))
			return 0;

		Vec3 center = getEndpointCenter();
		AABB absorbBox = cubeAround(center, ABSORB_HALF_EXTENT);
		int remaining = maxAmount;
		int absorbed = 0;

		List<ExperienceOrb> orbs = world.getEntitiesOfClass(ExperienceOrb.class, absorbBox, ExperienceOrb::isAlive);
		for (ExperienceOrb orb : orbs) {
			if (remaining <= 0)
				break;
			int value = orb.getValue();
			if (value <= 0)
				continue;
			int taken = Math.min(value, remaining);
			if (!simulate) {
				orb.discard();
				if (value > taken)
					ExperienceHelper.spawnExperience(world, orb.position(), value - taken);
			}
			remaining -= taken;
			absorbed += taken;
		}

		if (remaining <= 0)
			return absorbed;

		List<Player> players = world.getEntitiesOfClass(Player.class, absorbBox,
			player -> player.isAlive() && !player.isSpectator());
		for (Player player : players) {
			if (remaining <= 0)
				break;
			int drained = simulate ? Math.min(remaining, Math.max(0, player.totalExperience))
				: ExperienceHelper.drainPlayerExperience(player, remaining);
			remaining -= drained;
			absorbed += drained;
		}

		return absorbed;
	}

	@Override
	public int insert(int amount, boolean simulate) {
		if (amount <= 0 || world == null || world.isClientSide)
			return 0;
		if (!world.isLoaded(location.getConnectedPos()))
			return 0;
		if (!ExperiencePropagator.isOpenEnd(world, location.getPos(), location.getFace()))
			return 0;
		if (!simulate)
			ExperienceHelper.spawnExperience(world, getEndpointCenter(), amount);
		return amount;
	}

	private Vec3 getEndpointCenter() {
		BlockPos outputPos = location.getConnectedPos();
		Direction side = location.getFace();
		BlockState state = world.getBlockState(outputPos);
		if (AllBlocks.NOZZLE.has(state) && state.hasProperty(BlockStateProperties.FACING)
			&& state.getValue(BlockStateProperties.FACING) == side.getOpposite())
			return Vec3.atCenterOf(outputPos).add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.35d));
		return Vec3.atCenterOf(outputPos);
	}

	private static AABB cubeAround(Vec3 center, double halfExtent) {
		return new AABB(center.x - halfExtent, center.y - halfExtent, center.z - halfExtent, center.x + halfExtent,
			center.y + halfExtent, center.z + halfExtent);
	}
}

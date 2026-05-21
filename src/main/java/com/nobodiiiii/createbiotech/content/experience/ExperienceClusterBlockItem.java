package com.nobodiiiii.createbiotech.content.experience;

import net.createmod.catnip.math.VecHelper;

import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class ExperienceClusterBlockItem extends BlockItem {
	private static final int MAX_ORBS_PER_PINCH = 5;
	// Below this much XP per orb a pinch emits a single orb instead of splitting.
	private static final int MIN_XP_PER_SPLIT_ORB = 37;

	private final int xpNuggetValue;

	public ExperienceClusterBlockItem(Block block, int xpNuggetValue, Properties properties) {
		super(block, properties);
		this.xpNuggetValue = xpNuggetValue;
	}

	public int getXpNuggetValue() {
		return xpNuggetValue;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack itemInHand = player.getItemInHand(usedHand);
		if (level.isClientSide) {
			level.playSound(player, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.5f,
				1.0f);
			return InteractionResultHolder.consume(itemInHand);
		}

		int amountUsed = player.isShiftKeyDown() ? 1 : itemInHand.getCount();
		int xpPerCluster = xpNuggetValue * ExperienceConstants.XP_PER_NUGGET;
		int total = amountUsed * xpPerCluster;

		// Cap each pinch at MAX_ORBS_PER_PINCH regardless of stack size. Each orb gets a slightly
		// different value (spread symmetrically around the mean) so vanilla's tryMergeToExisting
		// can't collapse same-value orbs back into one entity.
		int orbs = Math.max(1, Math.min(MAX_ORBS_PER_PINCH, total / MIN_XP_PER_SPLIT_ORB));
		int baseValue = total / orbs;
		int remainder = total - baseValue * orbs;
		int half = orbs / 2;

		for (int i = 0; i < orbs; i++) {
			int offset = i - half;
			if (orbs % 2 == 0 && offset >= 0)
				offset++;
			int value = baseValue + offset;
			if (orbs - 1 - i < remainder)
				value++;
			if (value < 1)
				value = 1;

			Vec3 randomOff = VecHelper.offsetRandomly(Vec3.ZERO, level.random, 1)
				.normalize();
			Vec3 look = player.getLookAngle();
			Vec3 motion = look.scale(0.2)
				.add(0, 0.2, 0)
				.add(randomOff.scale(0.1));
			Vec3 cross = look.cross(VecHelper.rotate(new Vec3(-0.75f, 0, 0), -player.getYRot(), Axis.Y));
			Vec3 global = player.getEyePosition()
				.add(look.scale(0.5f))
				.add(cross);

			ExperienceOrb xp = new ExperienceOrb(level, global.x, global.y, global.z, value);
			xp.setDeltaMovement(motion);
			level.addFreshEntity(xp);
		}

		itemInHand.shrink(amountUsed);
		if (!itemInHand.isEmpty())
			return InteractionResultHolder.success(itemInHand);

		player.setItemInHand(usedHand, ItemStack.EMPTY);
		return InteractionResultHolder.consume(itemInHand);
	}
}

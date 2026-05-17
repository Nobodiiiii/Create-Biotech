package com.nobodiiiii.createbiotech.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BioPackagerContraptionClientAnimationHandler {

	private static final Map<AnimationKey, AnimationState> ACTIVE = new HashMap<>();

	private BioPackagerContraptionClientAnimationHandler() {}

	public static void startAnimation(int entityId, BlockPos localPos, ItemStack heldBox, ItemStack previouslyUnwrapped,
		boolean animationInward) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null)
			return;
		AnimationKey key = new AnimationKey(entityId, localPos.immutable());
		AnimationState state = new AnimationState(level.getGameTime(), heldBox.copy(), previouslyUnwrapped.copy(),
			animationInward);
		ACTIVE.put(key, state);
		applyAnimation(level, key, state);
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null) {
			ACTIVE.clear();
			return;
		}

		Iterator<Map.Entry<AnimationKey, AnimationState>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<AnimationKey, AnimationState> entry = iterator.next();
			AnimationKey key = entry.getKey();
			AnimationState state = entry.getValue();
			long elapsed = level.getGameTime() - state.startTick;
			if (level.getEntity(key.entityId) == null) {
				if (elapsed < BioPackagerBlockEntity.CYCLE)
					continue;
				iterator.remove();
				continue;
			}

			if (elapsed >= BioPackagerBlockEntity.CYCLE) {
				resetAnimation(level, key);
				iterator.remove();
				continue;
			}

			applyAnimation(level, key, state);
		}
	}

	private static void applyAnimation(Level level, AnimationKey key, AnimationState state) {
		BioPackagerBlockEntity packager = getRenderedPackager(level, key);
		if (packager == null)
			return;

		int remainingTicks = (int) Math.max(0, BioPackagerBlockEntity.CYCLE - (level.getGameTime() - state.startTick));
		packager.animationInward = state.animationInward;
		packager.animationTicks = remainingTicks;
		packager.heldBox = state.heldBox.copy();
		packager.previouslyUnwrapped = state.previouslyUnwrapped.copy();
	}

	private static void resetAnimation(Level level, AnimationKey key) {
		BioPackagerBlockEntity packager = getRenderedPackager(level, key);
		if (packager == null)
			return;

		packager.animationInward = true;
		packager.animationTicks = 0;
		packager.heldBox = ItemStack.EMPTY;
		packager.previouslyUnwrapped = ItemStack.EMPTY;
	}

	private static BioPackagerBlockEntity getRenderedPackager(Level level, AnimationKey key) {
		if (!(level.getEntity(key.entityId) instanceof AbstractContraptionEntity contraptionEntity))
			return null;
		if (contraptionEntity.getContraption() == null)
			return null;

		BlockEntity blockEntity = contraptionEntity.getContraption()
			.getOrCreateClientContraptionLazy()
			.getRenderLevel()
			.getBlockEntity(key.localPos);
		return blockEntity instanceof BioPackagerBlockEntity packager ? packager : null;
	}

	private record AnimationKey(int entityId, BlockPos localPos) {}

	private record AnimationState(long startTick, ItemStack heldBox, ItemStack previouslyUnwrapped,
		boolean animationInward) {}
}

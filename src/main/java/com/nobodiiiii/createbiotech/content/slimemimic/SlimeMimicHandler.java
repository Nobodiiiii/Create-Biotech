package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlimeMimicHandler {
	public static final String SLIME_MIMIC_TAG = "CreateBiotechSlimeMimic";

	private SlimeMimicHandler() {
	}

	public static boolean isSlimeMimic(Entity entity) {
		return entity instanceof LivingEntity livingEntity && isSlimeMimic(livingEntity);
	}

	public static boolean isSlimeMimic(LivingEntity entity) {
		return entity instanceof SlimeMimicAccess access && access.createBiotech$isSlimeMimic();
	}

	public static void setSlimeMimic(LivingEntity entity, boolean slimeMimic) {
		if (entity instanceof SlimeMimicAccess access)
			access.createBiotech$setSlimeMimic(slimeMimic);
	}

	public static void markSpawnedEntity(@Nullable Entity entity) {
		if (entity instanceof LivingEntity livingEntity)
			setSlimeMimic(livingEntity, true);
	}

	public static CompoundTag createPreparedSpawnEggTag(ItemStack stack) {
		CompoundTag preparedTag = stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
		CompoundTag entityTag = preparedTag.contains("EntityTag", Tag.TAG_COMPOUND)
			? preparedTag.getCompound("EntityTag").copy()
			: new CompoundTag();
		entityTag.putBoolean(SLIME_MIMIC_TAG, true);
		preparedTag.put("EntityTag", entityTag);
		return preparedTag;
	}

	public static boolean shouldSlimeifySpawn(@Nullable Player player, InteractionHand usedHand) {
		return player != null && usedHand == InteractionHand.MAIN_HAND
			&& player.getOffhandItem().is(CBItems.BIONIC_MECHANISM.get());
	}

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!isSlimeMimic(event.getEntity()))
			return;

		List<ItemEntity> originalDrops = new ArrayList<>(event.getDrops());
		if (originalDrops.isEmpty())
			return;

		event.getDrops().clear();
		for (ItemEntity itemEntity : originalDrops) {
			ItemStack stack = itemEntity.getItem();
			if (stack.isEmpty())
				continue;

			int remaining = stack.getCount();
			while (remaining > 0) {
				int amount = Math.min(remaining, Items.SLIME_BALL.getMaxStackSize());
				ItemEntity slimeDrop = new ItemEntity(itemEntity.level(), itemEntity.getX(), itemEntity.getY(),
					itemEntity.getZ(), new ItemStack(Items.SLIME_BALL, amount));
				slimeDrop.setDeltaMovement(itemEntity.getDeltaMovement());
				event.getDrops().add(slimeDrop);
				remaining -= amount;
			}
		}
	}
}

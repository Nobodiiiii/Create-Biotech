package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.Comparator;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class SlimeBeltAcquisitionHandler {
	private static final String HAS_SLIME_BELT_TAG = "HasSlimeBelt";
	private static final String HAS_MAGMA_BELT_TAG = "HasMagmaBelt";
	private static final String DATA_ROOT = "CreateBiotech";

	private SlimeBeltAcquisitionHandler() {}

	@SubscribeEvent
	public static void onLivingTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof Slime slime))
			return;
		if (slime.level().isClientSide || slime.tickCount % 10 != 0)
			return;
		boolean magma = slime instanceof MagmaCube;
		String beltTag = magma ? HAS_MAGMA_BELT_TAG : HAS_SLIME_BELT_TAG;
		if (slime.getSize() < 2 || hasBelt(slime, beltTag))
			return;

		List<ItemEntity> nearbyBelts = slime.level().getEntitiesOfClass(ItemEntity.class, slime.getBoundingBox().inflate(.35d),
			itemEntity -> itemEntity.isAlive() && AllItems.BELT_CONNECTOR.isIn(itemEntity.getItem()));
		if (nearbyBelts.isEmpty())
			return;

		ItemEntity itemEntity = nearbyBelts.stream()
			.min(Comparator.comparingDouble(slime::distanceToSqr))
			.orElse(null);
		if (itemEntity == null)
			return;

		ItemStack stack = itemEntity.getItem();
		stack.shrink(1);
		if (stack.isEmpty())
			itemEntity.discard();
		else
			itemEntity.setItem(stack);

		setHasBelt(slime, beltTag, true);
		slime.level().playSound(null, slime.getX(), slime.getY(), slime.getZ(), SoundEvents.ITEM_PICKUP,
			SoundSource.HOSTILE, 0.2f, (slime.getRandom().nextFloat() - slime.getRandom().nextFloat()) * 0.7f + 1.0f);
	}

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!(event.getEntity() instanceof Slime slime))
			return;

		ItemStack drop = copyCarriedBelt(slime);
		if (drop.isEmpty())
			return;

		event.getDrops().add(new ItemEntity(slime.level(), slime.getX(), slime.getY(), slime.getZ(), drop));
		if (event.getSource().getEntity() instanceof ServerPlayer player)
			CBAdvancements.award(player, slime instanceof MagmaCube ? CBAdvancements.MAGMA_BELT : CBAdvancements.SLIME_BELT);
	}

	public static ItemStack copyCarriedBelt(Slime slime) {
		boolean magma = slime instanceof MagmaCube;
		String beltTag = magma ? HAS_MAGMA_BELT_TAG : HAS_SLIME_BELT_TAG;
		return hasBelt(slime, beltTag)
			? new ItemStack(magma ? CBItems.MAGMA_BELT_CONNECTOR.get() : CBItems.SLIME_BELT_CONNECTOR.get())
			: ItemStack.EMPTY;
	}

	private static boolean hasBelt(Slime slime, String tag) {
		return getCreateBiotechData(slime).getBoolean(tag);
	}

	private static void setHasBelt(Slime slime, String tag, boolean value) {
		CompoundTag data = getCreateBiotechData(slime);
		if (value)
			data.putBoolean(tag, true);
		else
			data.remove(tag);
	}

	private static CompoundTag getCreateBiotechData(Slime slime) {
		CompoundTag persistentData = slime.getPersistentData();
		if (!persistentData.contains(DATA_ROOT))
			persistentData.put(DATA_ROOT, new CompoundTag());
		return persistentData.getCompound(DATA_ROOT);
	}
}

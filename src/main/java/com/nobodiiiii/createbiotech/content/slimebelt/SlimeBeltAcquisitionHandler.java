package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.Comparator;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SlimeBeltAcquisitionHandler {
	private static final String HAS_SLIME_BELT_TAG = "HasSlimeBelt";
	private static final String DATA_ROOT = "CreateBiotech";

	private SlimeBeltAcquisitionHandler() {}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		if (!(event.getEntity() instanceof Slime slime))
			return;
		if (slime.level().isClientSide || slime.tickCount % 10 != 0)
			return;
		if (slime.getSize() < 2 || hasSlimeBelt(slime))
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

		setHasSlimeBelt(slime, true);
		slime.level().playSound(null, slime.getX(), slime.getY(), slime.getZ(), SoundEvents.ITEM_PICKUP,
			SoundSource.HOSTILE, 0.2f, (slime.getRandom().nextFloat() - slime.getRandom().nextFloat()) * 0.7f + 1.0f);
	}

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!(event.getEntity() instanceof Slime slime) || !hasSlimeBelt(slime))
			return;

		ItemStack drop = new ItemStack(CBItems.SLIME_BELT_CONNECTOR.get());
		event.getDrops().add(new ItemEntity(slime.level(), slime.getX(), slime.getY(), slime.getZ(), drop));
	}

	private static boolean hasSlimeBelt(Slime slime) {
		return getCreateBiotechData(slime).getBoolean(HAS_SLIME_BELT_TAG);
	}

	private static void setHasSlimeBelt(Slime slime, boolean value) {
		CompoundTag data = getCreateBiotechData(slime);
		if (value)
			data.putBoolean(HAS_SLIME_BELT_TAG, true);
		else
			data.remove(HAS_SLIME_BELT_TAG);
	}

	private static CompoundTag getCreateBiotechData(Slime slime) {
		CompoundTag persistentData = slime.getPersistentData();
		if (!persistentData.contains(DATA_ROOT))
			persistentData.put(DATA_ROOT, new CompoundTag());
		return persistentData.getCompound(DATA_ROOT);
	}
}

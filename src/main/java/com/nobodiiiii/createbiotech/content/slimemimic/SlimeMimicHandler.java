package com.nobodiiiii.createbiotech.content.slimemimic;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class SlimeMimicHandler {
	public static final String SLIME_MIMIC_TAG = "CreateBiotechSlimeMimic";
	public static final String HAUNT_PROGRESS_TAG = "CreateBiotechHaunting";
	public static final String HAUNTED_MIMIC_TAG = "CreateBiotechHauntedMimic";

	private SlimeMimicHandler() {
	}

	public static boolean isSlimeMimic(Entity entity) {
		return entity instanceof LivingEntity livingEntity && isSlimeMimic(livingEntity);
	}

	public static boolean isSlimeMimic(LivingEntity entity) {
		return entity instanceof SlimeMimicAccess access && access.createBiotech$isSlimeMimic();
	}

	public static void setSlimeMimic(LivingEntity entity, boolean slimeMimic) {
		setSlimeMimic(entity, slimeMimic, true);
	}

	/** Used by explicit creative tools which must not be limited by the survival entity lists. */
	public static void forceSetSlimeMimic(LivingEntity entity, boolean slimeMimic) {
		setSlimeMimic(entity, slimeMimic, false);
	}

	private static void setSlimeMimic(LivingEntity entity, boolean slimeMimic, boolean respectEntityList) {
		boolean wasSlimeMimic = isSlimeMimic(entity);
		if (entity instanceof net.minecraft.world.entity.npc.AbstractVillager villager && !slimeMimic)
			SlimeMimicVillagerTrades.restoreOriginalOffers(villager);
		if (slimeMimic && respectEntityList && !canBecomeSlimeMimic(entity)) {
			slimeMimic = false;
		}
		if (entity instanceof SlimeMimicAccess access)
			access.createBiotech$setSlimeMimic(slimeMimic);
		if (slimeMimic)
			entity.getPersistentData().remove(HAUNTED_MIMIC_TAG);
		else if (wasSlimeMimic)
			entity.getPersistentData().putBoolean(HAUNTED_MIMIC_TAG, true);
		if (entity instanceof net.minecraft.world.entity.npc.AbstractVillager villager && slimeMimic
			&& CBConfigs.SERVER.slimeMimic.rewriteVillagerTrades.get())
			SlimeMimicVillagerTrades.rewriteSellItems(villager);
	}

	public static boolean isHauntedMimic(Entity entity) {
		return entity instanceof LivingEntity livingEntity && isHauntedMimic(livingEntity);
	}

	public static boolean isHauntedMimic(LivingEntity entity) {
		return !isSlimeMimic(entity) && entity.getPersistentData().getBoolean(HAUNTED_MIMIC_TAG);
	}

	public static void markHauntedMimic(LivingEntity entity) {
		setSlimeMimic(entity, false);
		entity.getPersistentData().putBoolean(HAUNTED_MIMIC_TAG, true);
	}

	public static void markSpawnedEntity(@Nullable Entity entity) {
		if (entity instanceof LivingEntity livingEntity)
			setSlimeMimic(livingEntity, true);
	}

	public static boolean canBecomeSlimeMimic(LivingEntity entity) {
		return canBecomeSlimeMimic(entity.getType());
	}

	public static boolean canBecomeSlimeMimic(EntityType<?> type) {
		CBConfigs.SlimeMimic config = CBConfigs.SERVER.slimeMimic;
		return CBConfigs.isEntityTypeAllowed(type, config.entityListMode.get(), config.entityAllowlist.get(),
			config.entityDenylist.get());
	}

	public static CompoundTag createPreparedEntityTag(@Nullable CompoundTag originalTag) {
		CompoundTag preparedTag = originalTag == null ? new CompoundTag() : originalTag.copy();
		preparedTag.putBoolean(SLIME_MIMIC_TAG, true);
		return preparedTag;
	}

	public static CompoundTag createPreparedSpawnEggTag(ItemStack stack) {
		return createPreparedEntityTag(CBItemData.get(stack));
	}

	public static boolean shouldSlimeifySpawn(@Nullable Player player, InteractionHand usedHand) {
		return player != null && usedHand == InteractionHand.MAIN_HAND
			&& CBConfigs.SERVER.slimeMimic.allowSpawnInjection.get()
			&& player.getOffhandItem().is(CBItems.BIONIC_MECHANISM.get());
	}

	public static boolean shouldSlimeifySpawn(@Nullable Player player, InteractionHand usedHand, EntityType<?> type) {
		return shouldSlimeifySpawn(player, usedHand) && canBecomeSlimeMimic(type);
	}

	@SubscribeEvent
	public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
		if (event.getEffectInstance().getEffect() == MobEffects.REGENERATION && isSlimeMimic(event.getEntity()))
			event.setResult(MobEffectEvent.Applicable.Result.APPLY);
	}

	public static void advanceHaunting(LivingEntity mimic, Level level) {
		if (level.isClientSide())
			return;

		CompoundTag data = mimic.getPersistentData();
		if (!mimic.hasEffect(MobEffects.REGENERATION)) {
			data.putInt(HAUNT_PROGRESS_TAG, 0);
			return;
		}

		int progress = data.getInt(HAUNT_PROGRESS_TAG);
		int hauntCycleTicks = getHauntCycleTicks();
		if (progress < hauntCycleTicks) {
			if (progress % 20 == 0)
				level.playSound(null, mimic.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL,
					1f, 0.5f + 1.5f * progress / hauntCycleTicks);
			data.putInt(HAUNT_PROGRESS_TAG, progress + 1);
			return;
		}

		data.remove(HAUNT_PROGRESS_TAG);
		mimic.removeEffect(MobEffects.REGENERATION);
		level.playSound(null, mimic.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.NEUTRAL,
			1.25f, 0.65f);
		setSlimeMimic(mimic, false);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDrops(LivingDropsEvent event) {
		if (isSlimeMimic(event.getEntity()) || event.getEntity() instanceof SlimeBionicEntity)
			event.getDrops().clear();
	}

	private static int getHauntCycleTicks() {
		return CBConfigs.SERVER.slimeMimic.hauntCycleTicks.get();
	}
}

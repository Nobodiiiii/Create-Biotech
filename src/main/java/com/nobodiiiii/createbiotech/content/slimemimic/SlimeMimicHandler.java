package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlimeMimicHandler {
	public static final String SLIME_MIMIC_TAG = "CreateBiotechSlimeMimic";
	public static final String HAUNT_PROGRESS_TAG = "CreateBiotechHaunting";
	public static final int HAUNT_CYCLE_TICKS = 100;
	private static final ResourceLocation VANILLA_SLIME_LOOT_TABLE = new ResourceLocation("minecraft", "entities/slime");

	private SlimeMimicHandler() {
	}

	public static boolean isSlimeMimic(Entity entity) {
		return entity instanceof LivingEntity livingEntity && isSlimeMimic(livingEntity);
	}

	public static boolean isSlimeMimic(LivingEntity entity) {
		return entity instanceof SlimeMimicAccess access && access.createBiotech$isSlimeMimic();
	}

	public static void setSlimeMimic(LivingEntity entity, boolean slimeMimic) {
		if (entity instanceof net.minecraft.world.entity.npc.AbstractVillager villager && !slimeMimic)
			SlimeMimicVillagerTrades.restoreOriginalOffers(villager);
		if (entity instanceof SlimeMimicAccess access)
			access.createBiotech$setSlimeMimic(slimeMimic);
		if (entity instanceof net.minecraft.world.entity.npc.AbstractVillager villager && slimeMimic)
			SlimeMimicVillagerTrades.rewriteSellItems(villager);
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
	public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
		if (event.getEffectInstance().getEffect() == MobEffects.REGENERATION && isSlimeMimic(event.getEntity()))
			event.setResult(Event.Result.ALLOW);
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
		if (progress < HAUNT_CYCLE_TICKS) {
			if (progress % 20 == 0)
				level.playSound(null, mimic.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL,
					1f, 0.5f + 1.5f * progress / HAUNT_CYCLE_TICKS);
			data.putInt(HAUNT_PROGRESS_TAG, progress + 1);
			return;
		}

		data.remove(HAUNT_PROGRESS_TAG);
		mimic.removeEffect(MobEffects.REGENERATION);
		level.playSound(null, mimic.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.NEUTRAL,
			1.25f, 0.65f);
		setSlimeMimic(mimic, false);
	}

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!isSlimeMimic(event.getEntity()))
			return;

		LivingEntity entity = event.getEntity();
		if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel))
			return;

		Vec3 origin = entity.position();
		if (!event.getDrops().isEmpty()) {
			ItemEntity firstDrop = event.getDrops().iterator().next();
			origin = new Vec3(firstDrop.getX(), firstDrop.getY(), firstDrop.getZ());
		}
		Vec3 dropVelocity = event.getDrops().isEmpty() ? Vec3.ZERO : event.getDrops().iterator().next().getDeltaMovement();

		LootParams.Builder lootParams = new LootParams.Builder(serverLevel)
			.withParameter(LootContextParams.THIS_ENTITY, entity)
			.withParameter(LootContextParams.ORIGIN, origin)
			.withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource());

		Entity attacker = event.getSource().getEntity();
		if (attacker != null)
			lootParams.withOptionalParameter(LootContextParams.KILLER_ENTITY, attacker);

		Entity directAttacker = event.getSource().getDirectEntity();
		if (directAttacker != null)
			lootParams.withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, directAttacker);

		Player player = entity.getKillCredit() instanceof Player killCreditPlayer ? killCreditPlayer : null;
		if (player != null)
			lootParams.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);

		List<ItemStack> slimeDrops = serverLevel.getServer()
			.getLootData()
			.getLootTable(VANILLA_SLIME_LOOT_TABLE)
			.getRandomItems(lootParams.create(LootContextParamSets.ENTITY));

		event.getDrops().clear();
		for (ItemStack stack : slimeDrops) {
			if (stack.isEmpty())
				continue;
			ItemEntity slimeDrop =
				new ItemEntity(entity.level(), origin.x(), origin.y(), origin.z(), stack.copy());
			slimeDrop.setDeltaMovement(dropVelocity);
			event.getDrops().add(slimeDrop);
		}
	}
}

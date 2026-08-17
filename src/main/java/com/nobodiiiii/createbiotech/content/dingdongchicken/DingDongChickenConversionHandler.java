package com.nobodiiiii.createbiotech.content.dingdongchicken;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DingDongChickenConversionHandler {

	private DingDongChickenConversionHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void convertChicken(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		ItemStack heldItem = player.getItemInHand(event.getHand());
		if (player.isSpectator() || !AllBlocks.DESK_BELL.isIn(heldItem)
			|| !(event.getTarget() instanceof Chicken chicken)
			|| chicken instanceof DingDongChickenEntity)
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		AllSoundEvents.DESK_BELL_USE.play(event.getLevel(), player, chicken.blockPosition());
		if (event.getLevel().isClientSide)
			return;

		DingDongChickenEntity converted = convertPreservingState(chicken);
		if (converted == null)
			return;

		if (!player.getAbilities().instabuild)
			heldItem.shrink(1);
	}

	private static DingDongChickenEntity convertPreservingState(Chicken chicken) {
		DingDongChickenEntity converted = CBEntityTypes.DING_DONG_CHICKEN.get().create(chicken.level());
		if (converted == null)
			return null;

		// Both entities share Chicken's save format. Loading it before the new entity is
		// added preserves motion, age, health, effects, equipment, names, leash data and
		// other persistent state while retaining a fresh UUID for the replacement.
		CompoundTag state = chicken.saveWithoutId(new CompoundTag());
		state.remove("UUID");
		converted.load(state);
		converted.copyPosition(chicken);

		// Body/head rotations and flap interpolation are transient render state and are
		// not included in entity NBT. Copy them explicitly to avoid a one-frame snap.
		converted.yRotO = chicken.yRotO;
		converted.xRotO = chicken.xRotO;
		converted.yBodyRot = chicken.yBodyRot;
		converted.yBodyRotO = chicken.yBodyRotO;
		converted.yHeadRot = chicken.yHeadRot;
		converted.yHeadRotO = chicken.yHeadRotO;
		converted.flap = chicken.flap;
		converted.oFlap = chicken.oFlap;
		converted.flapSpeed = chicken.flapSpeed;
		converted.oFlapSpeed = chicken.oFlapSpeed;
		converted.flapping = chicken.flapping;
		converted.walkAnimation.setSpeed(chicken.walkAnimation.speed());

		Entity vehicle = chicken.getVehicle();
		if (!chicken.level().addFreshEntity(converted))
			return null;
		if (vehicle != null) {
			chicken.stopRiding();
			converted.startRiding(vehicle, true);
		}
		chicken.discard();
		return converted;
	}
}

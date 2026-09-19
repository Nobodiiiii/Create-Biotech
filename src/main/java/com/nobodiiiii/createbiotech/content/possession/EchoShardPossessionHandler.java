package com.nobodiiiii.createbiotech.content.possession;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Echo-shard interactions which exchange the player's current body with a haunted mimic body. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class EchoShardPossessionHandler {

	private EchoShardPossessionHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (handleEntityInteraction(event.getEntity(), event.getItemStack(), event.getTarget()))
			consumeInteraction(event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		if (handleEntityInteraction(event.getEntity(), event.getItemStack(), event.getTarget()))
			consumeInteraction(event);
	}

	private static boolean handleEntityInteraction(Player eventPlayer, ItemStack heldItem, Entity interactedTarget) {
		if (!heldItem.is(Items.ECHO_SHARD) || eventPlayer.isSpectator())
			return false;
		if (!(eventPlayer instanceof ServerPlayer player)) {
			if (!eventPlayer.level().isClientSide())
				return false;
			CompoundTag state = possessionState(eventPlayer);
			if (eventPlayer.isShiftKeyDown() && EchoShardPossession.isActive(state))
				return true;
			LivingEntity target = resolveLivingTarget(interactedTarget);
			return target != null && target != eventPlayer && SlimeMimicHandler.isHauntedMimic(target);
		}

		CompoundTag currentState = possessionState(player);
		if (player.isShiftKeyDown() && EchoShardPossession.isActive(currentState))
			return releasePossession(player, currentState);

		LivingEntity target = resolveLivingTarget(interactedTarget);
		if (target == null || target == player || !target.isAlive()
			|| !SlimeMimicHandler.isHauntedMimic(target))
			return false;
		return possess(player, target, currentState);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (!event.getItemStack().is(Items.ECHO_SHARD) || !event.getEntity().isShiftKeyDown())
			return;
		CompoundTag state = possessionState(event.getEntity());
		if (!EchoShardPossession.isActive(state))
			return;

		if (event.getEntity() instanceof ServerPlayer player) {
			if (releasePossession(player, state))
				consumeInteraction(event);
		} else if (event.getEntity().level().isClientSide()) {
			consumeInteraction(event);
		}
	}

	private static boolean possess(ServerPlayer player, LivingEntity target, CompoundTag currentState) {
		ServerLevel level = player.serverLevel();
		Vec3 previousPosition = player.position();
		Vec3 previousMovement = player.getDeltaMovement();
		float previousYaw = player.getYRot();
		float previousPitch = player.getXRot();
		Vec3 targetPosition = target.position();
		Vec3 targetMovement = target.getDeltaMovement();
		float targetYaw = target.getYRot();
		float targetPitch = target.getXRot();
		float targetFallDistance = target.fallDistance;
		CompoundTag nextState = EchoShardPossession.capture(player, target, currentState);
		if (!EchoShardPossession.isActive(nextState))
			return false;

		if (!releaseCurrentForm(player, currentState, previousPosition, previousMovement,
			previousYaw, previousPitch))
			return false;

		player.stopRiding();
		target.stopRiding();
		target.ejectPassengers();
		player.teleportTo(level, targetPosition.x, targetPosition.y, targetPosition.z, targetYaw, targetPitch);
		player.setDeltaMovement(targetMovement);
		player.fallDistance = targetFallDistance;
		EchoShardPossession.applyBodyAttributes(player, target, nextState);
		setPossessionState(player, nextState);
		target.discard();
		return true;
	}

	private static boolean releasePossession(ServerPlayer player, CompoundTag state) {
		if (!releaseBody(player, state, player.position(), player.getDeltaMovement(),
			player.getYRot(), player.getXRot()))
			return false;

		EchoShardPossession.restorePlayerAttributes(player, state);
		setPossessionState(player, new CompoundTag());
		return true;
	}

	private static boolean releaseCurrentForm(ServerPlayer player, CompoundTag state, Vec3 position,
		Vec3 movement, float yaw, float pitch) {
		if (EchoShardPossession.isActive(state))
			return releaseBody(player, state, position, movement, yaw, pitch);

		PlayerMimicEntity playerBody = CBEntityTypes.PLAYER_MIMIC.get().create(player.level());
		if (playerBody == null)
			return false;
		playerBody.setImitatedPlayer(player.getGameProfile());
		AttributeInstance maximumHealth = playerBody.getAttribute(Attributes.MAX_HEALTH);
		if (maximumHealth != null)
			maximumHealth.setBaseValue(player.getMaxHealth());
		playerBody.setHealth(Math.min(player.getHealth(), playerBody.getMaxHealth()));
		playerBody.setAbsorptionAmount(Math.min(player.getAbsorptionAmount(), playerBody.getMaxAbsorption()));
		SlimeMimicHandler.markHauntedMimic(playerBody);
		positionReleasedBody(playerBody, position, movement, yaw, pitch, player);
		if (player.level().addFreshEntity(playerBody))
			return true;
		playerBody.discard();
		return false;
	}

	private static boolean releaseBody(ServerPlayer player, CompoundTag state, Vec3 position,
		Vec3 movement, float yaw, float pitch) {
		LivingEntity released = EchoShardPossession.createBody(player.level(), state);
		if (released == null)
			return false;
		released.setHealth(Math.min(player.getHealth(), released.getMaxHealth()));
		released.setAbsorptionAmount(Math.min(player.getAbsorptionAmount(), released.getMaxAbsorption()));
		positionReleasedBody(released, position, movement, yaw, pitch, player);
		if (player.level().addFreshEntity(released))
			return true;
		released.discard();
		return false;
	}

	private static void positionReleasedBody(LivingEntity body, Vec3 position, Vec3 movement,
		float yaw, float pitch, LivingEntity source) {
		body.moveTo(position.x, position.y, position.z, yaw, pitch);
		body.setDeltaMovement(movement);
		body.fallDistance = source.fallDistance;
		body.yRotO = source.yRotO;
		body.xRotO = source.xRotO;
		body.yBodyRot = source.yBodyRot;
		body.yBodyRotO = source.yBodyRotO;
		body.yHeadRot = source.yHeadRot;
		body.yHeadRotO = source.yHeadRotO;
	}

	private static CompoundTag possessionState(Player player) {
		return player instanceof PossessionAccess access
			? access.createBiotech$getPossessionState() : new CompoundTag();
	}

	private static void setPossessionState(Player player, CompoundTag state) {
		if (player instanceof PossessionAccess access)
			access.createBiotech$setPossessionState(state);
	}

	private static LivingEntity resolveLivingTarget(Entity target) {
		if (target instanceof LivingEntity living)
			return living;
		if (target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity living)
			return living;
		return null;
	}

	private static void consumeInteraction(PlayerInteractEvent.EntityInteract event) {
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}

	private static void consumeInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}

	private static void consumeInteraction(PlayerInteractEvent.RightClickItem event) {
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}
}

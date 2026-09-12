package com.nobodiiiii.createbiotech.content.bouncing;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Keeps the bouncing crouch's visual and logical proportions in sync. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class BouncingCrouch {
	public static final float LOGICAL_HEIGHT = 1.0F;
	public static final float HEIGHT_SCALE = LOGICAL_HEIGHT / Player.STANDING_DIMENSIONS.height();

	private static final Map<Player, Boolean> COMPRESSED_PLAYERS =
		Collections.synchronizedMap(new WeakHashMap<>());

	private BouncingCrouch() {}

	public static boolean isActive(Player player) {
		return player.getPose() == Pose.CROUCHING && player.hasEffect(CBMobEffects.BOUNCING);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPlayerSize(EntityEvent.Size event) {
		if (!(event.getEntity() instanceof Player player))
			return;

		boolean shouldCompress = player.isAddedToLevel()
			&& event.getPose() == Pose.CROUCHING
			&& player.hasEffect(CBMobEffects.BOUNCING);
		if (!shouldCompress) {
			COMPRESSED_PLAYERS.remove(player);
			return;
		}

		EntityDimensions compressedDimensions = Player.STANDING_DIMENSIONS
			.scale(1.0F, HEIGHT_SCALE)
			.scale(player.getScale());
		event.setNewSize(compressedDimensions);
		COMPRESSED_PLAYERS.put(player, Boolean.TRUE);
	}

	/**
	 * Effects can begin or expire while the player is already crouching, without a pose change to
	 * trigger a vanilla dimensions refresh. Detect only that edge and refresh once on both sides.
	 */
	@SubscribeEvent
	public static void onPlayerTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof Player player) || !player.isAddedToLevel())
			return;

		boolean shouldCompress = isActive(player);
		boolean isCompressed = COMPRESSED_PLAYERS.containsKey(player);
		if (shouldCompress != isCompressed)
			player.refreshDimensions();
	}
}

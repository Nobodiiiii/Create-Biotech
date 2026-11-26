package com.nobodiiiii.createbiotech.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.DevouredGrowthPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One swallowed passenger and the per-player release cooldowns of its host. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class DevouredPlayer {
	private static final int TICKS_PER_SECOND = 20;
	private static final ResourceKey<DamageType> DIGESTION =
		ResourceKey.create(Registries.DAMAGE_TYPE, CreateBiotech.asResource("devoured"));

	private final Mob hunter;
	@Nullable private ServerPlayer captive;
	@Nullable private Map<UUID, Long> releasedUntil;
	private long digestAt;
	private double escapeDamage;
	private double damageDealt;

	DevouredPlayer(Mob hunter) {
		this.hunter = hunter;
	}

	@Nullable
	private static DevouredPlayer state(@Nullable Entity entity) {
		DevourAttackGoal goal = null;
		if (entity instanceof DevourerEntity devourer) goal = devourer.devour;
		else if (entity instanceof SlimeBionicEntity bionic) goal = bionic.devour;
		return goal == null ? null : goal.swallowedPlayer;
	}

	@Nullable
	private static EntityDataAccessor<Optional<UUID>> captiveData(Entity entity) {
		if (entity instanceof DevourerEntity) return DevourerEntity.DEVOURED_PLAYER;
		if (entity instanceof SlimeBionicEntity) return SlimeBionicEntity.DEVOURED_PLAYER;
		return null;
	}

	public static boolean isInside(@Nullable Entity hunter, Entity passenger) {
		return passenger.getVehicle() == hunter && isMarkedCaptive(hunter, passenger);
	}

	/** The client can read the captive marker before the mount applies. */
	public static boolean isMarkedCaptive(@Nullable Entity hunter, Entity passenger) {
		if (hunter == null || !(passenger instanceof Player)) return false;
		var data = captiveData(hunter);
		return data != null && hunter.getEntityData().get(data).filter(passenger.getUUID()::equals).isPresent();
	}

	static boolean positionInside(Mob hunter, Entity passenger, Entity.MoveFunction move) {
		if (!(passenger instanceof Player player) || !isInside(hunter, player)) return false;
		AABB carrier = hunter instanceof SlimeBionicEntity bionic ? carrierBounds(bionic) : null;
		AABB box = carrier == null ? hunter.getBoundingBox() : carrier;
		// A size-three slime is shorter than a standing player but tall enough for a crouching one.
		if (player.isAlive() && player.getForcedPose() == null && box.getYsize() < player.getDimensions(Pose.STANDING).height()) {
			Pose pose = box.getYsize() >= player.getDimensions(Pose.CROUCHING).height() ? Pose.CROUCHING : Pose.SWIMMING;
			player.setPose(pose);
		}
		Vec3 center = box.getCenter();
		double feetY = center.y - player.getBbHeight() / 2;
		if (carrier == null) feetY = Math.max(box.minY, feetY);
		move.accept(player, center.x, feetY, center.z);
		return true;
	}

	@Nullable
	private static AABB carrierBounds(SlimeBionicEntity hunter) {
		int index = hunter.getEntityData().get(SlimeBionicEntity.DEVOUR_CARRIER);
		var assembly = hunter.getAssembly();
		if (index < 0 || assembly == null || assembly.attackGeometry() == null) return null;
		var member = new CombinationMember(index / SurgicalAssembly.MAX_CUBES, index % SurgicalAssembly.MAX_CUBES);
		var corners = assembly.attackGeometry().carrierCubes().get(member);
		if (corners == null) return null;
		Vec3 first = hunter.bodyToWorld(corners.getFirst());
		AABB bounds = new AABB(first, first);
		for (int i = 1; i < corners.size(); i++) {
			Vec3 point = hunter.bodyToWorld(corners.get(i));
			bounds = bounds.minmax(new AABB(point, point));
		}
		return bounds;
	}

	private static int largestCarrierIndex(SlimeBionicEntity hunter) {
		var assembly = hunter.getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return -1;
		var cubes = assembly.attackGeometry().carrierCubes();
		int largest = -1;
		double largestVolume = 0;
		for (var member : hunter.getBionicTraits().members(BionicTrait.DEVOUR)) {
			var corners = cubes.get(member);
			if (corners == null) continue;
			double volume = SurgicalVolumeSampler.cubeVolume(DevouredGrowthPose.orderedCorners(corners));
			int index = member.source() * SurgicalAssembly.MAX_CUBES + member.cube();
			if (volume > largestVolume || volume == largestVolume && index < largest) {
				largest = index;
				largestVolume = volume;
			}
		}
		return largest;
	}

	private void syncCarrier() {
		if (hunter instanceof SlimeBionicEntity bionic)
			bionic.getEntityData().set(SlimeBionicEntity.DEVOUR_CARRIER, largestCarrierIndex(bionic));
	}

	boolean hasCaptive() {
		return captive != null;
	}

	static boolean isHoldingPlayer(Mob hunter) {
		var state = state(hunter);
		return state != null && state.hasCaptive();
	}

	boolean canSwallow(ServerPlayer player) {
		return captive == null && !(player instanceof FakePlayer) && !hunter.isVehicle()
			&& !player.isPassenger() && !player.isVehicle() && !player.isSleeping()
			&& !player.getAbilities().invulnerable && hunter.level().getDifficulty() != Difficulty.PEACEFUL
			&& (releasedUntil == null || hunter.level().getGameTime() >= releasedUntil.getOrDefault(player.getUUID(), 0L));
	}

	boolean swallow(ServerPlayer player) {
		if (!canSwallow(player)) return false;
		var data = captiveData(hunter);
		var captiveId = Optional.of(player.getUUID());
		hunter.getEntityData().set(data, captiveId);
		if (!player.startRiding(hunter, true)) {
			hunter.getEntityData().set(data, Optional.empty());
			return false;
		}
		captive = player;
		var config = CBConfigs.SERVER.devourer;
		digestAt = hunter.level().getGameTime() + (long) config.playerGraceSeconds.get() * TICKS_PER_SECOND;
		escapeDamage = config.playerEscapeDamage.get();
		damageDealt = 0;
		syncCarrier();
		// The tracker normally sends the passenger list before dirty entity data.
		player.connection.send(new ClientboundSetEntityDataPacket(hunter.getId(),
			List.of(SynchedEntityData.DataValue.create(data, captiveId))));
		hunter.positionRider(player);
		player.resetFallDistance();
		if (hunter.getTarget() == player) hunter.setTarget(null);
		return true;
	}

	void tick() {
		long now = hunter.level().getGameTime();
		if (releasedUntil != null) {
			releasedUntil.values().removeIf(end -> end <= now);
			if (releasedUntil.isEmpty()) releasedUntil = null;
		}
		ServerPlayer player = captive;
		if (player == null) return;
		if (!hunter.isAlive() || !player.isAlive() || player.level() != hunter.level() || player.getVehicle() != hunter
			|| !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player) || hunter.isAlliedTo(player)
			|| !CBConfigs.SERVER.devourer.canHuntPlayers(DevourAttackGoal.size(hunter))
			|| hunter.level().getDifficulty() == Difficulty.PEACEFUL) {
			release();
			return;
		}

		syncCarrier();
		// The host ticks before its passenger. Suppress the normal sneak-to-dismount in Player.rideTick.
		if (player.isShiftKeyDown()) player.setShiftKeyDown(false);
		if (now < digestAt) return;
		if (!(hunter.level() instanceof ServerLevel level) || !DevourGrowth.hasGrowthCapacity(hunter)) {
			release();
			return;
		}

		hunter.positionRider(player);
		var source = new DigestionDamage(hunter, player);
		player.hurt(source, player.getHealth() + player.getAbsorptionAmount() + 1);
		release();
		if (source.deathDrops != null && !player.isAlive())
			DevourGrowth.finishPlayerMeal(level, hunter, player, source.deathDrops);
	}

	private void release() {
		ServerPlayer player = captive;
		if (player == null) return;
		onDismount(player);
		if (player.getVehicle() == hunter) player.stopRiding();
	}

	void onDismount(Entity passenger) {
		if (captive == null || passenger != captive) return;
		captive = null;
		hunter.getEntityData().set(captiveData(hunter), Optional.empty());
		if (hunter instanceof SlimeBionicEntity bionic)
			bionic.getEntityData().set(SlimeBionicEntity.DEVOUR_CARRIER, -1);
		int cooldown = CBConfigs.SERVER.devourer.playerCooldownSeconds.get();
		if (cooldown == 0) return;
		if (releasedUntil == null) releasedUntil = new HashMap<>();
		releasedUntil.put(passenger.getUUID(), hunter.level().getGameTime() + (long) cooldown * TICKS_PER_SECOND);
	}

	static void copyCooldowns(DevourerEntity original, SlimeBionicEntity grown) {
		var before = original.devour.swallowedPlayer.releasedUntil;
		if (before != null) grown.devour.swallowedPlayer.releasedUntil = new HashMap<>(before);
	}

	@SubscribeEvent
	public static void onDamage(LivingDamageEvent.Post event) {
		if (event.getEntity().level().isClientSide || !(event.getNewDamage() > 0)) return;
		var state = state(event.getEntity());
		if (state == null || state.captive == null || event.getSource().getEntity() != state.captive) return;
		state.damageDealt += event.getNewDamage();
		if (state.damageDealt >= state.escapeDamage) state.release();
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		var player = event.getEntity();
		var state = state(player.getVehicle());
		// Release before PlayerList saves and unloads the player's mount.
		if (state != null && state.captive == player) state.release();
	}

	static final class DigestionDamage extends DamageSource {
		private final ServerPlayer player;
		@Nullable private LivingDropsEvent deathDrops;

		DigestionDamage(Mob hunter, ServerPlayer player) {
			super(hunter.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DIGESTION), hunter);
			this.player = player;
		}

		void recordDrops(LivingDropsEvent event) {
			if (event.getEntity() == player) deathDrops = event;
		}
	}
}

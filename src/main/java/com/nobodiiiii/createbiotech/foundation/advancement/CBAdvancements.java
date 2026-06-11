package com.nobodiiiii.createbiotech.foundation.advancement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CBAdvancements {
	public static final ResourceLocation ROOT = CreateBiotech.asResource("root");
	public static final ResourceLocation BELT_COMPAT = CreateBiotech.asResource("belt_compat");
	public static final ResourceLocation SHIFTING_GEARS_COMPAT = CreateBiotech.asResource("shifting_gears_compat");
	public static final ResourceLocation CARDBOARD_COMPAT = CreateBiotech.asResource("cardboard_compat");
	public static final ResourceLocation SLIME_BELT = CreateBiotech.asResource("slime_belt");
	public static final ResourceLocation MAGMA_BELT = CreateBiotech.asResource("magma_belt");
	public static final ResourceLocation POWER_BELT = CreateBiotech.asResource("power_belt");
	public static final ResourceLocation VOLUNTARY_OVERTIME = CreateBiotech.asResource("voluntary_overtime");
	public static final ResourceLocation EXPERIENCE_PUMP = CreateBiotech.asResource("experience_pump");
	public static final ResourceLocation BUDDING_EXPERIENCE = CreateBiotech.asResource("budding_experience");
	public static final ResourceLocation EXPERIENCE_CLUSTER = CreateBiotech.asResource("experience_cluster");
	public static final ResourceLocation SQUID_PRINTER = CreateBiotech.asResource("squid_printer");
	public static final ResourceLocation EVOKER_ENCHANTING_CHAMBER =
		CreateBiotech.asResource("evoker_enchanting_chamber");
	public static final ResourceLocation GHAST_HOT_AIR_BALLOON = CreateBiotech.asResource("ghast_hot_air_balloon");
	public static final ResourceLocation GHAST_HELM = CreateBiotech.asResource("ghast_helm");
	public static final ResourceLocation SOFT_LANDING = CreateBiotech.asResource("soft_landing");
	public static final ResourceLocation UNIVERSAL_JOINT = CreateBiotech.asResource("universal_joint");
	public static final ResourceLocation SLIME_CLUTCH = CreateBiotech.asResource("slime_clutch");
	public static final ResourceLocation BONE_RATCHET = CreateBiotech.asResource("bone_ratchet");
	public static final ResourceLocation BIONIC_MECHANISM = CreateBiotech.asResource("bionic_mechanism");
	public static final ResourceLocation SPIDER_ASSEMBLY_TABLE = CreateBiotech.asResource("spider_assembly_table");
	public static final ResourceLocation BOMBPROOF = CreateBiotech.asResource("bombproof");
	public static final ResourceLocation CREEPER_BLAST_CHAMBER = CreateBiotech.asResource("creeper_blast_chamber");
	public static final ResourceLocation CREEPER_BLAST_CHAMBER_OVERLOAD =
		CreateBiotech.asResource("creeper_blast_chamber_overload");
	public static final ResourceLocation CARDBOARD_BOX = CreateBiotech.asResource("cardboard_box");
	public static final ResourceLocation SCHRODINGERS_CAT = CreateBiotech.asResource("schrodingers_cat");
	public static final ResourceLocation LARGE_CARDBOARD_BOX = CreateBiotech.asResource("large_cardboard_box");
	public static final ResourceLocation BIO_PACKAGER = CreateBiotech.asResource("bio_packager");

	private static final ResourceLocation CREATE_BELT = new ResourceLocation("create", "belt");
	private static final ResourceLocation CREATE_SHIFTING_GEARS = new ResourceLocation("create", "shifting_gears");
	private static final ResourceLocation CREATE_CARDBOARD = new ResourceLocation("create", "cardboard");

	private CBAdvancements() {}

	public static boolean award(ServerPlayer player, ResourceLocation advancementId) {
		Advancement advancement = getAdvancement(player, advancementId);
		if (advancement == null)
			return false;

		AdvancementProgress progress = player.getAdvancements()
			.getOrStartProgress(advancement);
		List<String> remainingCriteria = new ArrayList<>();
		for (String criterion : progress.getRemainingCriteria())
			remainingCriteria.add(criterion);
		for (String criterion : remainingCriteria)
			player.getAdvancements()
				.award(advancement, criterion);
		return !remainingCriteria.isEmpty();
	}

	public static boolean has(ServerPlayer player, ResourceLocation advancementId) {
		Advancement advancement = getAdvancement(player, advancementId);
		return advancement != null && player.getAdvancements()
			.getOrStartProgress(advancement)
			.isDone();
	}

	public static boolean awardPlayer(ServerLevel level, UUID playerId, ResourceLocation advancementId) {
		ServerPlayer player = level.getServer()
			.getPlayerList()
			.getPlayer(playerId);
		return player != null && award(player, advancementId);
	}

	public static boolean awardNearby(Level level, BlockPos pos, double radius, ResourceLocation advancementId) {
		return awardNearby(level, Vec3.atCenterOf(pos), radius, advancementId);
	}

	public static boolean awardNearby(Level level, Vec3 center, double radius, ResourceLocation advancementId) {
		if (!(level instanceof ServerLevel serverLevel))
			return false;

		boolean awarded = false;
		AABB bounds = new AABB(center, center).inflate(radius);
		for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, bounds,
			current -> current.isAlive() && !current.isSpectator() && current.position().closerThan(center, radius))) {
			awarded |= award(player, advancementId);
		}
		return awarded;
	}

	@Nullable
	private static Advancement getAdvancement(ServerPlayer player, ResourceLocation advancementId) {
		return player.server.getAdvancements()
			.getAdvancement(advancementId);
	}

	@SubscribeEvent
	public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		ResourceLocation advancementId = event.getAdvancement().getId();
		if (CREATE_BELT.equals(advancementId)) {
			award(player, BELT_COMPAT);
			return;
		}
		if (CREATE_SHIFTING_GEARS.equals(advancementId)) {
			award(player, SHIFTING_GEARS_COMPAT);
			return;
		}
		if (CREATE_CARDBOARD.equals(advancementId))
			award(player, CARDBOARD_COMPAT);
	}

	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		if (has(player, CREATE_BELT))
			award(player, BELT_COMPAT);
		if (has(player, CREATE_SHIFTING_GEARS))
			award(player, SHIFTING_GEARS_COMPAT);
		if (has(player, CREATE_CARDBOARD))
			award(player, CARDBOARD_COMPAT);
	}
}

package com.nobodiiiii.createbiotech.foundation.feature;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public enum CBFeature {
	ALLAY_LOGISTICS("allayLogistics", "allay_port"),
	AUTOMATIC_FISH_RELEASE_MACHINE("automaticFishReleaseMachine", "automatic_fish_release_machine"),
	BIO_PACKAGER("bioPackager", "bio_packager"),
	BIOTECH_MATERIALS("biotechMaterials", "asurine_casing", "asurine_scaffolding", "asurine_ladder",
		"asurine_bars", "asurine_door", "biotech_casing"),
	BONE_RATCHET("boneRatchet", "bone_ratchet"),
	BUFFER_PAD("bufferPad"),
	BUTTER_CAT("butterCat", "cute_cat_on_shaft", "butter_cat_engine", "cream"),
	CARDBOARD_BOX("cardboardBox"),
	CREEPER_BLAST_CHAMBER("creeperBlastChamber", "creeper_blast_chamber", "explosion_proof_casing",
		"explosion_proof_item_vault", "blast_proof_glass", "blast_proof_framed_glass",
		"blast_proof_chain_drive"),
	EVOKER_ENCHANTING_CHAMBER("evokerEnchantingChamber", "evoker_enchanting_chamber"),
	EXPERIENCE("experience", "experience_pump", "budding_experience", "small_experience_bud",
		"medium_experience_bud", "large_experience_bud", "experience_cluster"),
	FIXED_CARROT_FISHING_ROD("fixedCarrotFishingRod", "fixed_carrot_fishing_rod"),
	FROG_STOMACH("frogStomach", "giant_frog", "frog_stomach_wall", "frog_stomach_mucosa",
		"frog_stomach_fungus", "frog_stomach_fungus_stem", "frog_stomach_fungus_cap",
		"frog_stomach_fungus_light", "frog_stomach_secretion", "frog_digestive_tract",
		"frog_digestive_tract_wall"),
	GHAST_HOT_AIR_BALLOON("ghastHotAirBalloon", "ghast_hot_air_balloon_assembly_station", "ghast_helm"),
	LIQUID_LIVING_SLIME("liquidLivingSlime", "liquid_living_slime"),
	MAGMA_BELT("magmaBelt", "magma_belt"),
	PETRI_DISH("petriDish", "petri_dish"),
	POWER_BELT("powerBelt", "power_belt"),
	SCHRODINGERS_CAT("schrodingersCat", "schrodingers_cat"),
	SHULKER_PACKAGER("shulkerPackager", "shulker_packager"),
	SHULKER_TELEPORTER("shulkerTeleporter", "shulker_teleporter"),
	SLIME_ARMOR("slimeArmor"),
	SLIME_BELT("slimeBelt", "slime_belt"),
	SLIME_CLUTCH("slimeClutch", "slime_clutch"),
	SMART_SUPER_GLUE("smartSuperGlue"),
	SPIDER_ASSEMBLY_TABLE("spiderAssemblyTable", "spider_assembly_table", "spider_assembly_table_cog"),
	SQUID_PRINTER("squidPrinter", "squid_printer"),
	TELEPORTATION_FLUID("teleportationFluid", "teleportation"),
	UNIVERSAL_JOINT("universalJoint", "universal_joint", "half_shaft"),
	WIRELESS_TERMINAL("wirelessTerminal");

	public static final Codec<CBFeature> CODEC = Codec.STRING.comapFlatMap(CBFeature::decode, CBFeature::serializedName);

	private static final Map<String, CBFeature> BY_NAME = new HashMap<>();
	private static final Map<ResourceLocation, CBFeature> BLOCK_FEATURES = new HashMap<>();

	static {
		for (CBFeature feature : values()) {
			BY_NAME.put(feature.serializedName, feature);
			for (String blockId : feature.blockIds)
				BLOCK_FEATURES.put(CreateBiotech.asResource(blockId), feature);
		}
	}

	private final String serializedName;
	private final String[] blockIds;

	CBFeature(String serializedName, String... blockIds) {
		this.serializedName = serializedName;
		this.blockIds = blockIds;
	}

	public String serializedName() {
		return serializedName;
	}

	public boolean isEnabled() {
		return CBConfigs.SERVER.features.isEnabled(this);
	}

	public static @Nullable CBFeature forBlock(Block block) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
		if (id == null)
			return null;
		if (CreateBiotech.MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith("buffer_pad"))
			return BUFFER_PAD;
		return BLOCK_FEATURES.get(id);
	}

	public static @Nullable CBFeature forPlaceableItem(Item item) {
		if (item instanceof BlockItem blockItem)
			return forBlock(blockItem.getBlock());
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		if (id == null || !CreateBiotech.MOD_ID.equals(id.getNamespace()))
			return null;
		return switch (id.getPath()) {
		case "cream_bucket" -> BUTTER_CAT;
		case "teleportation_bucket" -> TELEPORTATION_FLUID;
		case "liquid_living_slime_bucket" -> LIQUID_LIVING_SLIME;
		default -> null;
		};
	}

	public static @Nullable CBFeature bySerializedName(String name) {
		return BY_NAME.get(name);
	}

	private static DataResult<CBFeature> decode(String name) {
		CBFeature feature = bySerializedName(name);
		return feature == null
			? DataResult.error(() -> "Unknown Create: Biotech feature: " + name.toLowerCase(Locale.ROOT))
			: DataResult.success(feature);
	}
}

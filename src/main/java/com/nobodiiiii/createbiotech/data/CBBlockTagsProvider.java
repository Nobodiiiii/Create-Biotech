package com.nobodiiiii.createbiotech.data;

import java.util.concurrent.CompletableFuture;

import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class CBBlockTagsProvider extends BlockTagsProvider {

	public CBBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
		ExistingFileHelper existingFileHelper) {
		super(output, lookupProvider, "create_biotech", existingFileHelper);
	}

	@Override
	protected void addTags(HolderLookup.Provider provider) {
		add(BlockTags.MINEABLE_WITH_AXE,
			CBBlocks.SLIME_BELT.get(),
			CBBlocks.MAGMA_BELT.get(),
			CBBlocks.POWER_BELT.get(),
			CBBlocks.AUTOMATIC_FISH_RELEASE_MACHINE.get(),
			CBBlocks.SLIME_CLUTCH.get(),
			CBBlocks.BONE_RATCHET.get(),
			CBBlocks.FIXED_CARROT_FISHING_ROD.get(),
			CBBlocks.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(),
			CBBlocks.SCHRODINGERS_CAT.get(),
			CBBlocks.SPIDER_ASSEMBLY_TABLE.get(),
			CBBlocks.SPIDER_ASSEMBLY_TABLE_COG.get(),
			CBBlocks.ASURINE_CASING.get(),
			CBBlocks.BIOTECH_CASING.get());

		add(BlockTags.MINEABLE_WITH_PICKAXE,
			CBBlocks.SLIME_BELT.get(),
			CBBlocks.MAGMA_BELT.get(),
			CBBlocks.POWER_BELT.get(),
			CBBlocks.AUTOMATIC_FISH_RELEASE_MACHINE.get(),
			CBBlocks.EVOKER_ENCHANTING_CHAMBER.get(),
			CBBlocks.EXPERIENCE_PUMP.get(),
			CBBlocks.BUDDING_EXPERIENCE.get(),
			CBBlocks.SMALL_EXPERIENCE_BUD.get(),
			CBBlocks.MEDIUM_EXPERIENCE_BUD.get(),
			CBBlocks.LARGE_EXPERIENCE_BUD.get(),
			CBBlocks.EXPERIENCE_CLUSTER.get(),
			CBBlocks.SQUID_PRINTER.get(),
			CBBlocks.PETRI_DISH.get(),
			CBBlocks.SURGICAL_TABLE.get(),
			CBBlocks.UNIVERSAL_JOINT.get(),
			CBBlocks.HALF_SHAFT.get(),
			CBBlocks.SLIME_CLUTCH.get(),
			CBBlocks.BONE_RATCHET.get(),
			CBBlocks.MAGMA_CUBE_BURNER.get(),
			CBBlocks.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(),
			CBBlocks.GHAST_HELM.get(),
			CBBlocks.SPIDER_ASSEMBLY_TABLE.get(),
			CBBlocks.SPIDER_ASSEMBLY_TABLE_COG.get(),
			CBBlocks.ASURINE_CASING.get(),
			CBBlocks.BIOTECH_CASING.get(),
			CBBlocks.ASURINE_TABLE_CLOTH.get(),
			CBBlocks.BIO_PACKAGER.get(),
			CBBlocks.SHULKER_PACKAGER.get(),
			CBBlocks.SHULKER_TELEPORTER.get(),
			CBBlocks.ALLAY_PORT.get(),
			CBBlocks.CUTE_CAT_ON_SHAFT.get(),
			CBBlocks.BUTTER_CAT_ENGINE.get(),
			CBBlocks.CREEPER_BLAST_CHAMBER.get(),
			CBBlocks.EXPLOSION_PROOF_CASING.get(),
			CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get(),
			CBBlocks.BLAST_PROOF_GLASS.get(),
			CBBlocks.BLAST_PROOF_FRAMED_GLASS.get(),
			CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get());

		add(BlockTags.INSIDE_STEP_SOUND_BLOCKS,
			CBBlocks.ASURINE_TABLE_CLOTH.get());

		add(BlockTags.NEEDS_DIAMOND_TOOL,
			CBBlocks.CREEPER_BLAST_CHAMBER.get(),
			CBBlocks.EXPLOSION_PROOF_CASING.get(),
			CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get(),
			CBBlocks.BLAST_PROOF_GLASS.get(),
			CBBlocks.BLAST_PROOF_FRAMED_GLASS.get(),
			CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get());
	}

	private void add(TagKey<Block> tag, Block... blocks) {
		tag(tag).add(blocks);
	}
}

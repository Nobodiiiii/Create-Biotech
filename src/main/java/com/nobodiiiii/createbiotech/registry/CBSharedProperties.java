package com.nobodiiiii.createbiotech.registry;

import com.simibubi.create.foundation.data.SharedProperties;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Material baselines used by Create: Biotech blocks.
 *
 * <p>Every method returns a fresh mutable properties object. Tool tags remain
 * data-generated and are intentionally not part of this class.</p>
 */
public final class CBSharedProperties {

	private CBSharedProperties() {}

	public static Block.Properties createWooden() {
		return Block.Properties.ofFullCopy(SharedProperties.wooden());
	}

	public static Block.Properties createStone() {
		return Block.Properties.ofFullCopy(SharedProperties.stone());
	}

	/**
	 * Keeps the 1.21.1 legacy solid flag off without changing collision or occlusion.
	 * The cat shaft's 1 x 0.6 x 0.6 bounds otherwise pass vanilla's solid threshold;
	 * noOcclusion is not an equivalent replacement for this compatibility flag.
	 */
	@SuppressWarnings("deprecation")
	public static Block.Properties withLegacyNonSolid(Block.Properties properties) {
		return properties.forceSolidOff();
	}

	public static Block.Properties createSoftMetal() {
		return Block.Properties.ofFullCopy(SharedProperties.softMetal());
	}

	public static Block.Properties createCopperMetal() {
		return Block.Properties.ofFullCopy(SharedProperties.copperMetal());
	}

	public static Block.Properties enchantingTable() {
		return Block.Properties.ofFullCopy(Blocks.ENCHANTING_TABLE);
	}

	public static Block.Properties buddingExperience() {
		return Block.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST);
	}

	public static Block.Properties smallExperienceBud() {
		return Block.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD);
	}

	public static Block.Properties mediumExperienceBud() {
		return Block.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD);
	}

	public static Block.Properties largeExperienceBud() {
		return Block.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD);
	}

	public static Block.Properties experienceCluster() {
		return Block.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER);
	}

	public static Block.Properties vanillaGlass() {
		return Block.Properties.ofFullCopy(Blocks.GLASS);
	}

	public static Block.Properties withExplosionProofResistance(Block.Properties properties) {
		return properties.explosionResistance(1200.0f)
			.requiresCorrectToolForDrops();
	}
}

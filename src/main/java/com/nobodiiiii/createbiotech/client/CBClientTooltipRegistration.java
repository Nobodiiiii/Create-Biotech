package com.nobodiiiii.createbiotech.client;

import java.util.List;
import java.util.function.Predicate;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxStatsTooltip;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Internal CBClientTooltipRegistration implementation; public entry points remain on CreateBiotechClient. */
final class CBClientTooltipRegistration {
	private CBClientTooltipRegistration() {}


	static void registerItemTooltips() {
		ItemDescription.useKey(CBFluids.TELEPORTATION_BUCKET.get(), "fluid.create_biotech.teleportation");
		ItemDescription.useKey(CBItems.SMALL_EXPERIENCE_BUD.get(), "block.create_biotech.experience_bud");
		ItemDescription.useKey(CBItems.MEDIUM_EXPERIENCE_BUD.get(), "block.create_biotech.experience_bud");
		ItemDescription.useKey(CBItems.LARGE_EXPERIENCE_BUD.get(), "block.create_biotech.experience_bud");
		CBItems.BUFFER_PADS.values()
			.forEach(entry -> ItemDescription.useKey(entry.get(), "block.create_biotech.buffer_pad"));
		ItemDescription.useKey(CBItems.ASURINE_TABLE_CLOTH.get(), "block.create.table_cloth");

		registerCreateStyleTooltip(CBFluids.TELEPORTATION_BUCKET.get());
		registerCreateStyleTooltip(CBItems.BUDDING_EXPERIENCE.get());
		registerCreateStyleTooltip(CBItems.AUTOMATIC_FISH_RELEASE_MACHINE.get());
		registerCreateStyleTooltip(CBItems.SMALL_EXPERIENCE_BUD.get());
		registerCreateStyleTooltip(CBItems.MEDIUM_EXPERIENCE_BUD.get());
		registerCreateStyleTooltip(CBItems.LARGE_EXPERIENCE_BUD.get());
		registerCreateStyleTooltip(CBItems.EXPERIENCE_CLUSTER.get());
		registerCapturedBoxTooltip(CBItems.CARDBOARD_BOX.get());
		registerCapturedBoxTooltip(CBItems.LARGE_CARDBOARD_BOX.get());
		registerCreateStyleTooltip(CBItems.EMPTY_CARDBOARD_BOX.get());
		registerCreateStyleTooltip(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get());
		for (var entry : List.of(CBItems.WOODEN_CAPTURE_NET, CBItems.STONE_CAPTURE_NET, CBItems.IRON_CAPTURE_NET,
			CBItems.GOLDEN_CAPTURE_NET, CBItems.DIAMOND_CAPTURE_NET, CBItems.NETHERITE_CAPTURE_NET)) {
			var item = entry.get();
			TooltipModifier.REGISTRY.register(item, new CaptureNetTooltipModifier(item.tier()));
		}
		registerCreateStyleTooltip(CBItems.CAPTURED_SMALL_SLIME.get());
		registerCreateStyleTooltip(CBItems.DING_DONG_CHICKEN.get());
		registerCreateStyleTooltip(CBItems.SMART_SUPER_GLUE.get());
		registerCreateStyleTooltip(CBItems.FIXED_CARROT_FISHING_ROD.get());
		registerCreateStyleTooltip(CBItems.WIRELESS_TERMINAL.get());
		registerCreateStyleTooltip(CBItems.MAGMA_CUBE_BURNER.get());
		registerCreateStyleTooltip(CBItems.SHULKER_PACKAGER.get());
		registerCreateStyleTooltip(CBItems.SHULKER_TELEPORTER.get());
		registerCreateStyleTooltip(CBItems.GIANT_FROG.get());
		registerCreateStyleTooltip(CBItems.GIANT_FROG_FACTORY.get());
		registerCreateStyleTooltip(CBItems.SURGICAL_KIT.get());
		registerCreateStyleTooltip(CBItems.SURGICAL_JOINT.get());
		registerCreateStyleTooltip(CBItems.CREATIVE_SURGICAL_KIT.get());
		registerCreateStyleTooltip(CBItems.SURGICAL_TABLE.get());
		registerCreateStyleTooltip(CBItems.SURGERY_GUIDE.get());
		registerCreateStyleTooltip(CBItems.ALLAY_PORT.get());
		registerCreateStyleTooltip(CBItems.ALLAY_COURIER.get());
		registerCreateStyleTooltip(CBItems.CUTE_CAT_ON_SHAFT.get());
		registerCreateStyleTooltip(CBItems.ASURINE_TABLE_CLOTH.get());
		TooltipModifier.REGISTRY.register(CBItems.SONIC_DOG_CANNON.get(),
			new SonicDogCannonTooltipModifier()::modify);
		registerKineticCreateStyleTooltip(CBItems.BUTTER_CAT_ENGINE.get());
		registerKineticCreateStyleTooltip(CBItems.BUTTER.get());
		registerKineticCreateStyleTooltip(CBItems.INCOMPLETE_SUPER_BUTTER.get());
		registerKineticCreateStyleTooltip(CBItems.SUPER_BUTTER.get());
		registerKineticCreateStyleTooltip(CBFluids.CREAM_BUCKET.get());
		CBItems.BUFFER_PADS.values()
			.forEach(entry -> registerCreateStyleTooltip(entry.get()));
	}

	static void registerCreateStyleTooltip(Item item) {
		registerCreateStyleTooltip(item, stack -> false);
	}

	static void registerCreateStyleTooltip(Item item, Predicate<ItemStack> skipCondition) {
		TooltipModifier description = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
		TooltipModifier.REGISTRY.register(item, context -> {
			if (skipCondition.test(context.getItemStack()))
				return;
			description.modify(context);
		});
	}

	static void registerCapturedBoxTooltip(Item item) {
		TooltipModifier description = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
		TooltipModifier stats = new CapturedEntityBoxStatsTooltip();
		TooltipModifier.REGISTRY.register(item, context -> {
			if (!CapturedEntityBoxHelper.hasCapturedEntity(context.getItemStack()))
				description.modify(context);
			stats.modify(context);
		});
	}

	static void registerKineticCreateStyleTooltip(Item item) {
		TooltipModifier modifier = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
			.andThen(TooltipModifier.mapNull(KineticStats.create(item)));
		TooltipModifier.REGISTRY.register(item, modifier::modify);
	}
}

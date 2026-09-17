package com.nobodiiiii.createbiotech.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortItem;
import com.nobodiiiii.createbiotech.content.allay.client.render.AllayPortItemRenderer;
import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineItem;
import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineItemRenderer;
import com.nobodiiiii.createbiotech.content.buttercat.item.ButterCatBlockItem;
import com.nobodiiiii.createbiotech.content.buttercat.item.ButterCatItemRenderer;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItemRenderer;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberItem;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberItemRenderer;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogItem;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogItemRenderer;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.nobodiiiii.createbiotech.content.magmacubeburner.MagmaCubeBurnerItem;
import com.nobodiiiii.createbiotech.content.magmacubeburner.MagmaCubeBurnerItemRenderer;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltBlock;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItemRenderer;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonItem;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableItem;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableItemRenderer;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableRenderProperties;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterItem;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterItemRenderer;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalKitItemRenderer;
import com.nobodiiiii.createbiotech.foundation.fluid.CBFluidType;
import com.nobodiiiii.createbiotech.foundation.item.BlockCenteredRenderedLivingEntityItem;
import com.nobodiiiii.createbiotech.foundation.item.BlockCenteredSpawnableRenderedLivingEntityItem;
import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;
import com.nobodiiiii.createbiotech.foundation.render.BlockCenteredRenderedLivingEntityItemRenderer;
import com.nobodiiiii.createbiotech.foundation.render.RenderedLivingEntityItemRenderer;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.foundation.item.render.CustomRenderedItems;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Registers each extension once, after registries and the Minecraft client are available. */
final class CBClientExtensions {
	private CBClientExtensions() {
	}

	static void register(RegisterClientExtensionsEvent event) {
		for (var entry : CBItems.ITEMS.getEntries()) {
			Item item = entry.get();
			IClientItemExtensions extensions = createItemExtensions(item);
			if (extensions != null)
				event.registerItem(extensions, item);
		}

		event.registerBlock(new MagmaBeltBlock.RenderProperties(), CBBlocks.MAGMA_BELT.get());
		event.registerBlock(new PowerBeltBlock.RenderProperties(), CBBlocks.POWER_BELT.get());
		event.registerBlock(new SlimeBeltBlock.RenderProperties(), CBBlocks.SLIME_BELT.get());
		event.registerBlock(new SpiderAssemblyTableRenderProperties(), CBBlocks.SPIDER_ASSEMBLY_TABLE.get());
		for (var entry : CBFluids.FLUID_TYPES.getEntries()) {
			if (entry.get() instanceof CBFluidType type)
				event.registerFluidType(type.createClientExtensions(), type);
		}
	}

	@Nullable
	private static IClientItemExtensions createItemExtensions(Item item) {
		// Specific entity renderers must precede their shared base class.
		if (item instanceof CapturedSmallSlimeItem slime)
			return CapturedSmallSlimeItemRenderer.create(slime.getRenderedEntityScaleMultiplier());
		if (item instanceof BlockCenteredRenderedLivingEntityItem<?> entityItem)
			return BlockCenteredRenderedLivingEntityItemRenderer.create(entityItem);
		if (item instanceof BlockCenteredSpawnableRenderedLivingEntityItem<?> entityItem)
			return BlockCenteredRenderedLivingEntityItemRenderer.create(entityItem);
		if (item instanceof RenderedLivingEntityItem<?> entityItem)
			return RenderedLivingEntityItemRenderer.create(entityItem);

		if (item instanceof AutomaticFishReleaseMachineItem)
			return SimpleCustomRenderer.create(item, new AutomaticFishReleaseMachineItemRenderer());
		if (item instanceof EvokerEnchantingChamberItem)
			return SimpleCustomRenderer.create(item, new EvokerEnchantingChamberItemRenderer());
		if (item instanceof AllayPortItem)
			return SimpleCustomRenderer.create(item, new AllayPortItemRenderer());
		if (item instanceof ButterCatBlockItem)
			return SimpleCustomRenderer.create(item, new ButterCatItemRenderer());
		if (item instanceof CapturedEntityBoxItem)
			return SimpleCustomRenderer.create(item, new CapturedEntityBoxItemRenderer());
		if (item instanceof SpiderAssemblyTableItem)
			return SimpleCustomRenderer.create(item, new SpiderAssemblyTableItemRenderer());
		if (item instanceof SquidPrinterItem)
			return SimpleCustomRenderer.create(item, new SquidPrinterItemRenderer());
		if (item instanceof MagmaCubeBurnerItem burner && burner.hasCapturedMagmaCube())
			return SimpleCustomRenderer.create(item, new MagmaCubeBurnerItemRenderer());
		if (item instanceof GiantFrogItem)
			return itemRenderer(new GiantFrogItemRenderer());
		if (item instanceof SurgicalKitItem)
			return itemRenderer(new SurgicalKitItemRenderer());
		if (item instanceof SonicDogCannonItem)
			return sonicDogCannon(item);
		return null;
	}

	private static IClientItemExtensions itemRenderer(BlockEntityWithoutLevelRenderer renderer) {
		return new IClientItemExtensions() {
			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		};
	}

	private static IClientItemExtensions sonicDogCannon(Item item) {
		CustomRenderedItems.register(item);
		return new IClientItemExtensions() {
			private final SonicDogCannonItemRenderer renderer = new SonicDogCannonItemRenderer();

			@Override
			public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
				ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
				if (!player.isUsingItem() || !player.getUseItem().is(item))
					return false;

				HumanoidArm usedArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
					? player.getMainArm()
					: player.getMainArm().getOpposite();
				if (arm != usedArm)
					return false;

				// Vanilla resets the hand's equip height after every successful use(), which normally
				// lowers the item by 0.6 blocks before raising it again. Keep the neutral held transform
				// throughout charging while the internal equip height catches up in the background.
				float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
				poseStack.translate(side * 0.56f, -0.52f, -0.72f);
				return true;
			}

			@Override
			@Nullable
			public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
				return SonicDogCannonArmPose.ARM_POSE.getValue();
			}

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		};
	}
}

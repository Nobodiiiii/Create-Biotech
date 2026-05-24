package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EvokerEnchantingChamberConversionHandler {

	private EvokerEnchantingChamberConversionHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Level level = event.getLevel();
		if (!level.getBlockState(event.getPos()).is(Blocks.ENCHANTING_TABLE))
			return;

		ItemStack heldItem = event.getItemStack();
		if (!CapturedEntityBoxHelper.containsEntityType(heldItem, EntityType.EVOKER))
			return;

		if (EvokerEnchantingChamberBlock.hasSpaceForUpperHalf(level, event.getPos()))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
	}
}

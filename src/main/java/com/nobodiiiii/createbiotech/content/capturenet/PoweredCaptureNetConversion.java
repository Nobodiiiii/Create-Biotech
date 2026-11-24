package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.itemapplication.RecipeBackedItemApplication;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class PoweredCaptureNetConversion {

	public static final ResourceLocation RECIPE_ID = CreateBiotech.asResource("item_application/powered_capture_net_manual_only");
	private static final List<String> ARM_SETTINGS = List.of("InteractionPoints", "ScrollValue", "Goggles");

	private PoweredCaptureNetConversion() {}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void applyNet(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		ItemStack held = event.getItemStack();
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		if (event.isCanceled() || !(held.getItem() instanceof CaptureNetItem) || player.isSpectator()
			|| !player.mayBuild() || !level.mayInteract(player, pos)
			|| event.getUseItem() == TriState.FALSE || event.getUseBlock() == TriState.FALSE)
			return;
		BlockState state = level.getBlockState(pos);
		if (!AllBlocks.MECHANICAL_ARM.has(state))
			return;
		var recipe = RecipeBackedItemApplication.find(level, RECIPE_ID, state,
			new ItemStack(AllBlocks.MECHANICAL_ARM.asItem()), held);
		if (recipe.isEmpty() || RecipeBackedItemApplication.resultBlock(recipe.get()).orElse(null) != CBBlocks.POWERED_CAPTURE_NET.get())
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
		if (CaptureNetItem.isOccupied(held)) {
			if (!level.isClientSide)
				player.displayClientMessage(Component.translatable("create_biotech.arm_capture.net_occupied"), true);
			return;
		}
		if (level instanceof ServerLevel server) {
			if (!(server.getBlockEntity(pos) instanceof ArmBlockEntity arm) || !convert(server, arm, held.copyWithCount(1)))
				return;
			// The ingredient becomes the installed net, including its original damage and enchantments.
			if (!player.isCreative())
				held.shrink(1);
			recipe.get().rollResults(server.random).forEach(stack -> Block.popResource(server, pos, stack));
			server.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 1, 1.45f);
		}
		event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
	}

	private static boolean convert(ServerLevel level, ArmBlockEntity arm, ItemStack net) {
		BlockPos pos = arm.getBlockPos();
		BlockState original = arm.getBlockState();
		CompoundTag originalData = arm.saveWithoutMetadata(level.registryAccess());
		CompoundTag settings = new CompoundTag();
		for (String key : ARM_SETTINGS) {
			var value = originalData.get(key);
			if (value != null)
				settings.put(key, value.copy());
		}
		BlockState replacement = CBBlocks.POWERED_CAPTURE_NET.get().defaultBlockState()
			.setValue(PoweredCaptureNetBlock.CEILING, original.getValue(PoweredCaptureNetBlock.CEILING));
		if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)
			|| !(level.getBlockEntity(pos) instanceof PoweredCaptureNetBlockEntity capture))
			return false;
		capture.setOriginalArmSettings(settings);
		capture.replaceNet(net);
		capture.notifyUpdate();
		return true;
	}

	static boolean restoreArm(ServerLevel level, PoweredCaptureNetBlockEntity capture, Player player) {
		BlockPos pos = capture.getBlockPos();
		BlockState state = AllBlocks.MECHANICAL_ARM.getDefaultState()
			.setValue(PoweredCaptureNetBlock.CEILING, capture.getBlockState().getValue(PoweredCaptureNetBlock.CEILING));
		ItemStack net = capture.replaceNet(ItemStack.EMPTY);
		if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
			capture.replaceNet(net);
			capture.notifyUpdate();
			return false;
		}
		if (level.getBlockEntity(pos) instanceof ArmBlockEntity arm) {
			var registries = level.registryAccess();
			var tag = arm.saveWithoutMetadata(registries).merge(capture.originalArmSettings());
			arm.loadWithComponents(tag, registries);
			arm.redstoneUpdate();
			arm.notifyUpdate();
		}
		if (!net.isEmpty())
			player.getInventory().placeItemBackInInventory(net);
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.2f);
		return true;
	}

}

package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessStockKeeperRequestMenu;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

@Mixin(BlockEntityConfigurationPacket.class)
public abstract class BlockEntityConfigurationPacketMixin {

	@WrapOperation(
		method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/world/level/Level;"
		)
	)
	private Level createBiotech$routeWirelessTerminalPacketsToTargetLevel(ServerPlayer player,
		Operation<Level> original, @Share("wirelessMenu") LocalRef<WirelessStockKeeperRequestMenu> wirelessMenu) {
		Level playerLevel = original.call(player);
		if (!(player.containerMenu instanceof WirelessStockKeeperRequestMenu menu)
			|| menu.contentHolder == null
			|| menu.contentHolder.isRemoved()
			|| menu.contentHolder.getLevel() == null
			|| !menu.acceptsWirelessPacketType((BlockEntityConfigurationPacket<?>) (Object) this))
			return playerLevel;

		wirelessMenu.set(menu);
		return menu.contentHolder.getLevel();
	}

	@WrapOperation(
		method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithBlock(Lnet/minecraft/core/BlockPos;D)Z"
		)
	)
	private boolean createBiotech$allowWirelessTerminalPackets(ServerPlayer player, BlockPos targetPos,
		double maxRange, Operation<Boolean> original,
		@Share("wirelessMenu") LocalRef<WirelessStockKeeperRequestMenu> wirelessMenu) {
		boolean withinDefaultRange = original.call(player, targetPos, maxRange);
		WirelessStockKeeperRequestMenu menu = wirelessMenu.get();
		if (menu == null)
			return withinDefaultRange;

		if (targetPos.equals(menu.contentHolder.getBlockPos()) && menu.stillValid(player))
			return true;
		return menu.contentHolder.getLevel() == player.level() && withinDefaultRange;
	}
}

package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltSurfaceMovementPacket;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class PowerBeltClientReporter {

	private PowerBeltClientReporter() {}

	public static void reportSurfaceMovement(Player player, BlockPos pos, float surfaceSpeed) {
		if (Minecraft.getInstance().player != player)
			return;
		CBPackets.sendToServer(new PowerBeltSurfaceMovementPacket(pos, surfaceSpeed));
	}
}

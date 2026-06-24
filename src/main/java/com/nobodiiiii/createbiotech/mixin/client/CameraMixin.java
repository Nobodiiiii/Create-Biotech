package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterClientEvents;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

@Mixin(Camera.class)
public abstract class CameraMixin {

	@Shadow
	public abstract Vec3 getPosition();

	@Shadow
	protected abstract void setPosition(Vec3 pPos);

	@Inject(method = "setup", at = @At("RETURN"), require = 0)
	private void createBiotech$lowerFirstPersonViewInShulkerTeleporter(BlockGetter level, Entity entity,
		boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
		if (!CBConfigs.CLIENT.enableShulkerTeleporterCameraOffset.get())
			return;
		if (detached)
			return;
		if (!(entity instanceof Player player))
			return;
		if (player != Minecraft.getInstance().player)
			return;

		double yOffset = ShulkerTeleporterClientEvents.getFirstPersonCameraYOffset(player, partialTick);
		if (yOffset == 0.0d)
			return;

		setPosition(getPosition().add(0.0d, yOffset, 0.0d));
	}
}

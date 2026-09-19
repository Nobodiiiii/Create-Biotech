package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.possession.EchoShardPossession;
import com.nobodiiiii.createbiotech.content.possession.PossessionAccess;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

@Mixin(value = Player.class, priority = 900)
public abstract class PlayerPossessionDimensionsMixin {
	@ModifyReturnValue(
		method = "getDefaultDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
		at = @At("RETURN"))
	private EntityDimensions createBiotech$usePossessedBodyDimensions(EntityDimensions original, Pose pose) {
		Player player = (Player) (Object) this;
		if (!(player instanceof PossessionAccess access))
			return original;
		CompoundTag state = access.createBiotech$getPossessionState();
		if (!EchoShardPossession.isActive(state))
			return original;
		float playerScale = player.getScale();
		EntityDimensions bodyDimensions = EchoShardPossession.dimensions(state);
		return Float.isFinite(playerScale) && playerScale > 0.0f
			? bodyDimensions.scale(1.0f / playerScale) : bodyDimensions;
	}
}

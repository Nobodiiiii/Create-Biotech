package com.nobodiiiii.createbiotech.mixin.compat.alternatecurrent;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.dingdongchicken.AlternateCurrentNodeAccess;
import com.nobodiiiii.createbiotech.content.dingdongchicken.EntityRedstoneIndex;

import alternate.current.wire.WireNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

@Pseudo
@Mixin(targets = "alternate.current.wire.WireHandler")
public abstract class AlternateCurrentWireHandlerMixin {

	@Shadow(remap = false)
	@Final
	private ServerLevel level;

	/**
	 * Alternate Current deliberately reads neighboring block states directly. Preserve its power
	 * propagation and only augment the external-power result with entity-backed sources.
	 */
	@ModifyReturnValue(method = "getExternalPower", at = @At("RETURN"), remap = false)
	private int createBiotech$includeEntitySource(int original, WireNode wire) {
		BlockPos wirePos = ((AlternateCurrentNodeAccess) (Object) wire).createBiotech$getPos();
		return Math.max(original, EntityRedstoneIndex.get(level).getExternalPowerForWire(wirePos));
	}
}

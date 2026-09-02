package com.nobodiiiii.createbiotech.network;

import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerContraptionAnimationPacket;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerReleaseAnimationPacket;
import com.nobodiiiii.createbiotech.content.dingdongchicken.DingDongChickenVoiceSoundPacket;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogEatPacket;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltEntityAnimationPacket;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerPlacementPacket;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonChargeSoundPacket;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonFirePacket;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonGearAnimationPacket;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortFlapPacket;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.hud.AllayCourierHudPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableReleaseGeometryPacket;
import com.nobodiiiii.createbiotech.entity.SlimeBionicAttackActionPacket;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class CBClientPacketHandlers {

	private CBClientPacketHandlers() {}

	static void handle(Object packet, LocalPlayer player) {
		if (packet instanceof PowerBeltEntityAnimationPacket powerBeltAnimation) {
			powerBeltAnimation.handle(player);
		} else if (packet instanceof BioPackagerContraptionAnimationPacket bioPackagerAnimation) {
			bioPackagerAnimation.handle(player);
		} else if (packet instanceof ShulkerPackagerPlacementPacket.ClientBoundRequest shulkerPlacement) {
			shulkerPlacement.handle(player);
		} else if (packet instanceof ShulkerPackagerPlacementPacket.ClientBoundResult shulkerPlacementResult) {
			shulkerPlacementResult.handle(player);
		} else if (packet instanceof AllayPortFlapPacket allayPortFlap) {
			allayPortFlap.handle(player);
		} else if (packet instanceof AllayCourierHudPacket allayCourierHud) {
			allayCourierHud.handle(player);
		} else if (packet instanceof GiantFrogEatPacket giantFrogEat) {
			giantFrogEat.handle(player);
		} else if (packet instanceof SonicDogCannonFirePacket sonicDogCannonFire) {
			sonicDogCannonFire.handle(player);
		} else if (packet instanceof SonicDogCannonChargeSoundPacket sonicDogCannonChargeSound) {
			sonicDogCannonChargeSound.handle(player);
		} else if (packet instanceof SonicDogCannonGearAnimationPacket sonicDogCannonGearAnimation) {
			sonicDogCannonGearAnimation.handle(player);
		} else if (packet instanceof DingDongChickenVoiceSoundPacket dingDongChickenVoiceSound) {
			dingDongChickenVoiceSound.handle(player);
		} else if (packet instanceof ContainedEntityHandoffPacket containedEntityHandoff) {
			containedEntityHandoff.handle(player);
		} else if (packet instanceof SlimeBionicAttackActionPacket bionicAttack) {
			bionicAttack.handle(player);
		} else if (packet instanceof SurgicalTableReleaseGeometryPacket.ClientBoundRequest releaseGeometry) {
			releaseGeometry.handle(player);
		} else if (packet instanceof BioPackagerReleaseAnimationPacket bioPackagerRelease) {
			bioPackagerRelease.handle(player);
		} else {
			throw new IllegalArgumentException("Unhandled Create Biotech clientbound packet "
				+ packet.getClass().getName());
		}
	}
}

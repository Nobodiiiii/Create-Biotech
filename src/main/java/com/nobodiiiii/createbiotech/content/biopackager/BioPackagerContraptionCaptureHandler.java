package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxHandler;
import com.nobodiiiii.createbiotech.content.cardboardbox.LargeCardboardBoxHandler;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class BioPackagerContraptionCaptureHandler {

	private BioPackagerContraptionCaptureHandler() {}

	public static boolean tryLethalCapture(LivingDamageEvent.Pre event, Mob target) {
		if (!CBConfigs.SERVER.bioPackager.enableContraptionLethalCapture.get())
			return false;

		AbstractContraptionEntity contraptionEntity =
			BioPackagerContraptionDamageTracker.resolveDamagingContraption(target, event.getSource());
		if (contraptionEntity == null)
			return false;
		Contraption contraption = contraptionEntity.getContraption();
		if (contraption == null)
			return false;

		boolean smallMob = CardboardBoxHandler.isSmallMobType(target);
		boolean largeBoxAllowed = LargeCardboardBoxHandler.canLargeBoxCapture(target);
		if (!smallMob && !largeBoxAllowed)
			return false;
		BlockPos freePackagerLocal = findFreePackager(contraption, contraptionEntity.getUUID());
		if (freePackagerLocal == null)
			return false;

		ItemStack emptyBox = BioPackagerContraptionTracker.consumeBoxFromContraption(
			contraptionEntity, smallMob, largeBoxAllowed);
		if (emptyBox.isEmpty())
			return false;

		ItemStack filledBox = CapturedEntityBoxHelper.createCaptureBox(emptyBox);
		if (!CapturedEntityBoxHelper.captureEntity(filledBox, target)) {
			// can't capture — refund box
			BioPackagerContraptionTracker.startServerCapture(contraptionEntity, freePackagerLocal, emptyBox);
			return false;
		}

		BioPackagerContraptionTracker.startServerCapture(contraptionEntity, freePackagerLocal, filledBox);
		CardboardBoxHandler.finishLethalCapture(event);
		CBAdvancements.awardNearby(target.level(), target.blockPosition(), 16, CBAdvancements.BIO_PACKAGER);
		return true;
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		event.getServer().getAllLevels().forEach(BioPackagerContraptionTracker::tickAll);
	}

	@SubscribeEvent
	public static void onContraptionRemoved(EntityLeaveLevelEvent event) {
		if (event.getLevel().isClientSide)
			return;
		if (!(event.getEntity() instanceof AbstractContraptionEntity ace))
			return;
		BioPackagerContraptionTracker.releaseOnDisassembly(ace);
	}

	private static BlockPos findFreePackager(Contraption contraption, java.util.UUID contraptionId) {
		Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
		for (Map.Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
			BlockState state = entry.getValue().state();
			if (!(state.getBlock() instanceof BioPackagerBlock))
				continue;
			BlockPos localPos = entry.getKey();
			if (BioPackagerContraptionTracker.isPackagerOccupied(contraptionId, localPos))
				continue;
			return localPos;
		}
		return null;
	}
}

package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxHandler;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class BioPackagerContraptionCaptureHandler {

	private BioPackagerContraptionCaptureHandler() {}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDamage(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		Level level = target.level();
		if (level.isClientSide)
			return;
		if (!(target instanceof Mob mob))
			return;
		if (!CBConfigs.SERVER.bioPackager.enableContraptionLethalCapture.get())
			return;
		if (mob.getHealth() > event.getNewDamage())
			return;
		if (!CBConfigs.SERVER.cardboardBox.lethalCaptureEnabled.get())
			return;

		AbstractContraptionEntity contraptionEntity =
			BioPackagerContraptionDamageTracker.resolveDamagingContraption(target, event.getSource());
		if (contraptionEntity == null)
			return;
		Contraption contraption = contraptionEntity.getContraption();
		if (contraption == null)
			return;

		boolean smallMob = CardboardBoxHandler.isSmallMobType(mob);
		boolean largeBoxAllowed = CBConfigs.isEntityTypeAllowed(mob.getType(),
			CBConfigs.SERVER.cardboardBox.largeBoxEntityListMode.get(),
			CBConfigs.SERVER.cardboardBox.largeBoxEntityAllowlist.get(),
			CBConfigs.SERVER.cardboardBox.largeBoxEntityDenylist.get());
		if (!smallMob && !largeBoxAllowed)
			return;
		BlockPos freePackagerLocal = findFreePackager(contraption, contraptionEntity.getUUID());
		if (freePackagerLocal == null)
			return;

		ItemStack emptyBox = BioPackagerContraptionTracker.consumeBoxFromContraption(
			contraptionEntity, smallMob, largeBoxAllowed);
		if (emptyBox.isEmpty())
			return;

		ItemStack filledBox = CapturedEntityBoxHelper.createCaptureBox(emptyBox);
		if (!CapturedEntityBoxHelper.captureEntity(filledBox, target)) {
			// can't capture — refund box
			BioPackagerContraptionTracker.startServerCapture(contraptionEntity, freePackagerLocal, emptyBox);
			return;
		}

		BioPackagerContraptionTracker.startServerCapture(contraptionEntity, freePackagerLocal, filledBox);

		event.setNewDamage(0);
		target.discard();
		CBAdvancements.awardNearby(level, target.blockPosition(), 16, CBAdvancements.BIO_PACKAGER);
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

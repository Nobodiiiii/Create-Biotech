package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxHandler;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BioPackagerContraptionCaptureHandler {

	private BioPackagerContraptionCaptureHandler() {}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDamage(LivingDamageEvent event) {
		LivingEntity target = event.getEntity();
		Level level = target.level();
		if (level.isClientSide)
			return;
		if (!(target instanceof Mob mob))
			return;
		if (mob.getHealth() > event.getAmount())
			return;

		AbstractContraptionEntity contraptionEntity =
			BioPackagerContraptionDamageTracker.resolveDamagingContraption(target, event.getSource());
		if (contraptionEntity == null)
			return;
		Contraption contraption = contraptionEntity.getContraption();
		if (contraption == null)
			return;

		boolean smallMob = CardboardBoxHandler.isSmallMobType(mob);
		BlockPos freePackagerLocal = findFreePackager(contraption, contraptionEntity.getUUID());
		if (freePackagerLocal == null)
			return;

		ItemStack emptyBox = BioPackagerContraptionTracker.consumeBoxFromContraption(contraptionEntity, smallMob);
		if (emptyBox.isEmpty())
			return;

		ItemStack filledBox = emptyBox.copy();
		if (!CapturedEntityBoxHelper.captureEntity(filledBox, target)) {
			// can't capture — refund box
			BioPackagerContraptionTracker.startServerCapture(contraptionEntity, freePackagerLocal, emptyBox);
			return;
		}

		BioPackagerContraptionTracker.startServerCapture(contraptionEntity, freePackagerLocal, filledBox);

		event.setCanceled(true);
		target.discard();
		CBAdvancements.awardNearby(level, target.blockPosition(), 16, CBAdvancements.BIO_PACKAGER);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;
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

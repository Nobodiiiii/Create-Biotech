package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BombproofAdvancementHandler {

	private BombproofAdvancementHandler() {}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onBlastProofConversion(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel().isClientSide)
			return;
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		if (!event.isCanceled() || event.getCancellationResult() != InteractionResult.SUCCESS)
			return;

		BlockState state = event.getLevel().getBlockState(event.getPos());
		if (!state.is(CBBlocks.EXPLOSION_PROOF_CASING.get())
			&& !state.is(CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get())
			&& !state.is(CBBlocks.BLAST_PROOF_GLASS.get())
			&& !state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get()))
			return;

		CBAdvancements.award(player, CBAdvancements.BOMBPROOF);
	}
}

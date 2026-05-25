package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.UUID;

import com.google.common.base.Objects;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class GhastHelmMovingInteraction extends MovingInteractionBehaviour {

	@Override
	public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
		AbstractContraptionEntity contraptionEntity) {
		if (!(contraptionEntity instanceof GhastHotAirBalloonEntity ghastBalloon))
			return false;

		StructureBlockInfo info = contraptionEntity.getContraption().getBlocks().get(localPos);
		if (info == null || !info.state().hasProperty(ControlsBlock.FACING))
			return false;

		UUID currentlyControlling = contraptionEntity.getControllingPlayer().orElse(null);
		if (currentlyControlling != null) {
			contraptionEntity.stopControlling(localPos);
			if (Objects.equal(currentlyControlling, player.getUUID()))
				return true;
		}

		if (!player.level().isClientSide && !ghastBalloon.startControlling(localPos, player))
			return false;
		if (player instanceof ServerPlayer serverPlayer)
			CBAdvancements.award(serverPlayer, CBAdvancements.GHAST_HELM);

		contraptionEntity.setControllingPlayer(player.getUUID());
		if (player.level().isClientSide) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> {
					ControlsHandler.startControlling(contraptionEntity, localPos);
					GhastHelmClientHandler.startControlling(ghastBalloon);
				});
		}
		return true;
	}
}

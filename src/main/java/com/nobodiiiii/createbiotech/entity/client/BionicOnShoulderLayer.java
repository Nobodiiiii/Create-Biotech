package com.nobodiiiii.createbiotech.entity.client;

import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.SlimeBionicRenderer;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Vanilla shoulder slots keep the entire assembly; each occupied slot gets one unspawned render entity. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BionicOnShoulderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final Map<AbstractClientPlayer, ShoulderPair> PREVIEWS = new WeakHashMap<>();

	public BionicOnShoulderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
		float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		ShoulderPair shoulders = PREVIEWS.computeIfAbsent(player, ignored -> new ShoulderPair());
		renderShoulder(shoulders.left, player.getShoulderEntityLeft(), true, player, partialTick, poseStack, buffer, packedLight);
		renderShoulder(shoulders.right, player.getShoulderEntityRight(), false, player, partialTick, poseStack, buffer, packedLight);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		// Preview entities refer to their client level, so release them when that level is left.
		if (event.getLevel().isClientSide())
			PREVIEWS.clear();
	}

	private static void renderShoulder(ShoulderPreview preview, CompoundTag saved, boolean left,
		AbstractClientPlayer player, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		SlimeBionicEntity entity = preview.resolve(saved, player);
		if (entity == null)
			return;
		entity.tickCount = player.tickCount;
		entity.setPos(player.position());
		entity.setOnGround(true);
		entity.yBodyRot = entity.yBodyRotO = 0;
		entity.yHeadRot = entity.yHeadRotO = 0;
		if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof SlimeBionicRenderer renderer) {
			poseStack.pushPose();
			poseStack.translate(left ? 0.4f : -0.4f, player.isCrouching() ? 0.2f : 0, 0);
			// Player layers use model coordinates; assembled bodies use an upright, ground-level origin.
			poseStack.scale(1, -1, -1);
			renderer.renderOnShoulder(entity, partialTick, poseStack, buffer, packedLight);
			poseStack.popPose();
		}
	}

	private static final class ShoulderPair {
		private final ShoulderPreview left = new ShoulderPreview();
		private final ShoulderPreview right = new ShoulderPreview();
	}

	private static final class ShoulderPreview {
		private CompoundTag saved;
		@Nullable private SlimeBionicEntity entity;

		@Nullable
		private SlimeBionicEntity resolve(CompoundTag current, AbstractClientPlayer player) {
			if (saved != current) {
				saved = current;
				entity = null;
				if (EntityType.byString(current.getString("id")).orElse(null) == CBEntityTypes.SLIME_BIONIC.get())
					EntityType.create(current, player.level()).ifPresent(created -> {
						if (created instanceof SlimeBionicEntity bionic)
							entity = bionic;
					});
			}
			return entity;
		}
	}
}

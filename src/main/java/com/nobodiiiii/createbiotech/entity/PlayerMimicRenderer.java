package com.nobodiiiii.createbiotech.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public class PlayerMimicRenderer extends MobRenderer<PlayerMimicEntity, PlayerModel<PlayerMimicEntity>> {
	private final PlayerModel<PlayerMimicEntity> wideModel;
	private final PlayerModel<PlayerMimicEntity> slimModel;

	public PlayerMimicRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		wideModel = model;
		slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		wideModel.setAllVisible(true);
		slimModel.setAllVisible(true);
	}

	@Override
	public void render(PlayerMimicEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		selectModel(entity);
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	/** Keeps first-person possession arms on the same wide/slim model as the entity render. */
	public void selectModel(PlayerMimicEntity entity) {
		model = getSkin(entity).model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
	}

	@Override
	public ResourceLocation getTextureLocation(PlayerMimicEntity entity) {
		return getSkin(entity).texture();
	}

	private static PlayerSkin getSkin(PlayerMimicEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		GameProfile profile = entity.getImitatedPlayer();
		if (profile == null)
			return DefaultPlayerSkin.get(entity.getUUID());
		return minecraft.getSkinManager().getInsecureSkin(profile);
	}
}

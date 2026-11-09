package com.nobodiiiii.createbiotech.entity.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BionicTraitClient {
	private BionicTraitClient() {}
	@SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null && !client.isPaused())
			for (var entity : client.level.entitiesForRendering())
				if (entity instanceof SlimeBionicEntity bionic) animate(bionic);
		if (client.player != null && client.player.getVehicle() instanceof SlimeBionicEntity bionic
			&& bionic.getControllingPassenger() == client.player) bionic.setRiderAscending(client.options.keyJump.isDown());
	}
	public static void animate(SlimeBionicEntity entity) {
		int action = entity.getSpecialAction();
		if (action == 2) { BionicGuardianBeamRenderer.tick(entity); return; }
		if (action == 7) return;
		if (action == 0 || entity.tickCount % 3 != 0) return;
		var origin = entity.getSpecialOrigin();
		entity.level().addParticle(action == 5 ? ParticleTypes.ENCHANT : action == 4 ? ParticleTypes.ANGRY_VILLAGER
			: action == 1 ? ParticleTypes.SCULK_CHARGE_POP : ParticleTypes.CRIT,
			origin.x, origin.y, origin.z, 0, 0.03, 0);
	}
	public static void render(SlimeBionicEntity entity, float partialTick, com.mojang.blaze3d.vertex.PoseStack poses,
		net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
		var assembly = entity.getAssembly();
		if (assembly == null || assembly.hitboxGeometry() == null) return;
		BionicGuardianBeamRenderer.render(entity, partialTick, poses, buffers);
	}
	private static void item(SlimeBionicEntity entity, net.minecraft.world.item.ItemStack item, com.mojang.blaze3d.vertex.PoseStack poses,
		net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
		Minecraft.getInstance().getItemRenderer().renderStatic(item, net.minecraft.world.item.ItemDisplayContext.FIXED, light,
			net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poses, buffers, entity.level(), entity.getId());
	}
}

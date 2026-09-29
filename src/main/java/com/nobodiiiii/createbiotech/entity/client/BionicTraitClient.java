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
		var body = assembly.hitboxGeometry().body();
		poses.pushPose();
		poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
		if (entity.isSaddled()) {
			poses.pushPose();
			poses.translate((body.minX() + body.maxX()) / 2, body.maxY() + 0.015, (body.minZ() + body.maxZ()) / 2);
			poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
			poses.scale(0.7f, 0.7f, 0.7f);
			item(entity, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE), poses, buffers, light);
			poses.popPose();
		}
		if (entity.getBionicEquipment().hasChest()) {
			poses.pushPose();
			poses.translate(body.maxX() + 0.15, (body.minY() + body.maxY()) / 2, (body.minZ() + body.maxZ()) / 2);
			poses.scale(0.5f, 0.5f, 0.5f);
			item(entity, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST), poses, buffers, light);
			poses.popPose();
		}
		poses.popPose();
		if (entity.getSpecialAction() == 2 && entity.level().getEntity(entity.getSpecialTarget()) instanceof net.minecraft.world.entity.LivingEntity target) {
			var start = entity.getSpecialOrigin().subtract(entity.position());
			var end = target.getBoundingBox().getCenter().subtract(entity.position());
			var direction = end.subtract(start).normalize();
			var side = direction.cross(new net.minecraft.world.phys.Vec3(0, 1, 0));
			if (side.lengthSqr() < 0.001) side = new net.minecraft.world.phys.Vec3(1, 0, 0);
			side = side.normalize().scale(0.045);
			var up = direction.cross(side).normalize().scale(0.045);
			var vertices = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
			for (var axis : java.util.List.of(side, up))
				for (var point : java.util.List.of(start.subtract(axis), end.subtract(axis), end.add(axis), start.add(axis)))
					vertices.addVertex(poses.last().pose(), (float) point.x, (float) point.y, (float) point.z).setColor(80, 230, 210, 200);
		}
	}
	private static void item(SlimeBionicEntity entity, net.minecraft.world.item.ItemStack item, com.mojang.blaze3d.vertex.PoseStack poses,
		net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
		Minecraft.getInstance().getItemRenderer().renderStatic(item, net.minecraft.world.item.ItemDisplayContext.FIXED, light,
			net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poses, buffers, entity.level(), entity.getId());
	}
}

package com.nobodiiiii.createbiotech.entity.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicSpecialAttacks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.SpellcasterIllager.IllagerSpell;
import net.minecraft.world.phys.Vec3;
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
		if (action == BionicSpecialAttacks.EVOKER_FANGS_ACTION) { evokerSpellParticles(entity); return; }
		// Witch.performRangedAttack has no charging particles or spellcasting pose.
		if (action == BionicSpecialAttacks.WITCH_POTION_ACTION) return;
		if (action == 7) return;
		if (action == 0 || entity.tickCount % 3 != 0) return;
		var origin = entity.getSpecialOrigin();
		entity.level().addParticle(action == 1 ? ParticleTypes.SCULK_CHARGE_POP : ParticleTypes.CRIT,
			origin.x, origin.y, origin.z, 0, 0.03, 0);
	}
	private static void evokerSpellParticles(SlimeBionicEntity entity) {
		// SpellcasterIllager.tick's FANGS color, two emitters and native arm sway.
		// Only the emitter frame changes: the retained casting arms may be moved or attached to a wall.
		double[] color = IllagerSpell.FANGS.spellColor;
		var particle = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,
			(float) color[0], (float) color[1], (float) color[2]);
		float angle = Mth.cos(entity.tickCount * 0.6662f) * 0.25f;
		double radius = 0.6 * entity.getScale();
		Vec3 origin = entity.worldToBody(entity.getSpecialOrigin());
		for (int side : new int[] { -1, 1 }) {
			Vec3 point = entity.bodyToWorld(origin.add(Mth.cos(angle) * radius * side, 0, Mth.sin(angle) * radius * side));
			entity.level().addParticle(particle, point.x, point.y, point.z, 0, 0, 0);
		}
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

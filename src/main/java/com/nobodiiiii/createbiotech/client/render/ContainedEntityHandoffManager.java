package com.nobodiiiii.createbiotech.client.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;
import com.nobodiiiii.createbiotech.foundation.render.RenderProxyEntities;
import com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor;
import com.nobodiiiii.createbiotech.network.ContainedEntityHandoffPacket;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Renders short-lived ghosts until the matching client entity packet arrives. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class ContainedEntityHandoffManager {
	private static final Map<Integer, Handoff> HANDOFFS = new HashMap<>();

	private ContainedEntityHandoffManager() {}

	public static void handle(ContainedEntityHandoffPacket packet, LocalPlayer player) {
		if (packet.cancelled()) {
			HANDOFFS.remove(packet.entityId());
			return;
		}
		Level level = player.clientLevel;
		Entity created = BuiltInRegistries.ENTITY_TYPE.get(packet.entityType()).create(level);
		if (!(created instanceof LivingEntity ghost))
			return;
		configureGhost(ghost, packet);
		HANDOFFS.put(packet.entityId(), new Handoff(packet, ghost, packet.maximumWaitTicks(), false));
	}

	private static void configureGhost(LivingEntity ghost, ContainedEntityHandoffPacket packet) {
		// Never added to the level, and this manager applies its own pose: keep decorating mixins off it.
		RenderProxyEntities.mark(ghost);
		if (ghost instanceof Slime slime) {
			slime.setSize(1, false);
			applySlimePhase(slime, packet.animationPhase());
		} else if (ghost instanceof Creeper creeper && packet.charged()) {
			CompoundTag data = new CompoundTag();
			creeper.saveWithoutId(data);
			data.putBoolean("powered", true);
			creeper.load(data);
		}
		if (ghost instanceof Creeper creeper)
			applyCreeperPhase(creeper, packet.animationPhase());
		ghost.setSilent(true);
		ghost.setOnGround(true);
		ghost.moveTo(packet.position().x, packet.position().y, packet.position().z, packet.yaw(), packet.pitch());
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			HANDOFFS.clear();
			return;
		}
		Iterator<Map.Entry<Integer, Handoff>> iterator = HANDOFFS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Handoff> entry = iterator.next();
			Handoff handoff = entry.getValue();
			Entity real = minecraft.level.getEntity(entry.getKey());
			if (real != null) {
				applyArrivalPhase(real, handoff.packet);
				if (handoff.sawRealEntity) {
					iterator.remove();
					continue;
				}
				handoff.sawRealEntity = true;
			}
			if (--handoff.ticksRemaining <= 0)
				iterator.remove();
		}
	}

	private static void applyArrivalPhase(Entity entity, ContainedEntityHandoffPacket packet) {
		entity.setYRot(packet.yaw());
		entity.yRotO = packet.yaw();
		entity.setXRot(packet.pitch());
		entity.xRotO = packet.pitch();
		if (entity instanceof Slime slime)
			applySlimePhase(slime, packet.animationPhase());
		else if (entity instanceof Creeper creeper)
			applyCreeperPhase(creeper, packet.animationPhase());
	}

	private static void applySlimePhase(Slime slime, float phase) {
		float squish = (float) Math.sin(phase) * .22f;
		slime.oSquish = squish;
		slime.squish = squish;
		slime.targetSquish = squish;
	}

	private static void applyCreeperPhase(Creeper creeper, float phase) {
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		int swell = Math.max(0, Math.min(24, Math.round(phase * 24)));
		accessor.createBiotech$setOldSwell(swell);
		accessor.createBiotech$setSwell(swell);
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HANDOFFS.isEmpty())
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		float partialTicks = AnimationTickHolder.getPartialTicks();
		for (Handoff handoff : HANDOFFS.values()) {
			if (handoff.sawRealEntity)
				continue;
			LivingEntity ghost = handoff.ghost;
			Vec3 position = handoff.packet.position();
			poseStack.pushPose();
			poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
			int packedLight = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(position));
			float yaw = handoff.packet.yaw();
			float phase = handoff.packet.animationPhase();
			if (ghost instanceof Creeper) {
				float swelling = Mth.clamp(Math.round(phase * 24), 0, 24) / 28f;
				MachineCreatureRenderer.renderCreeper(poseStack, buffer, packedLight, yaw, 0,
					handoff.packet.pitch(), swelling, AnimationTickHolder.getRenderTime(minecraft.level),
					handoff.packet.charged());
			} else if (ghost instanceof MagmaCube) {
				MachineCreatureRenderer.renderMagmaCube(poseStack, buffer, packedLight, yaw, 1, Mth.sin(phase) * .22f);
			} else if (ghost instanceof Slime) {
				MachineCreatureRenderer.renderSlime(poseStack, buffer, packedLight, yaw, 1, Mth.sin(phase) * .22f);
			} else {
				EntityRenderHelper.render(EntityRenderHelper.settings(ghost)
					.packedLight(packedLight)
					.partialTicks(partialTicks)
					.yaw(yaw)
					.bodyYaw(yaw)
					.headYaw(yaw)
					.pitch(handoff.packet.pitch())
					.flushBuffers(false), poseStack, buffer);
			}
			poseStack.popPose();
		}
		buffer.endBatch();
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide())
			HANDOFFS.clear();
	}

	private static final class Handoff {
		private final ContainedEntityHandoffPacket packet;
		private final LivingEntity ghost;
		private int ticksRemaining;
		private boolean sawRealEntity;

		private Handoff(ContainedEntityHandoffPacket packet, LivingEntity ghost, int ticksRemaining,
			boolean sawRealEntity) {
			this.packet = packet;
			this.ghost = ghost;
			this.ticksRemaining = ticksRemaining;
			this.sawRealEntity = sawRealEntity;
		}
	}
}

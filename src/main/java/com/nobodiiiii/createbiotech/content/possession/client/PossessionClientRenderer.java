package com.nobodiiiii.createbiotech.content.possession.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.possession.EchoShardPossession;
import com.nobodiiiii.createbiotech.content.possession.PossessionAccess;
import com.nobodiiiii.createbiotech.mixin.WalkAnimationStateAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Draws a player through the renderer of the currently possessed body. */
public final class PossessionClientRenderer {
	private static final Map<Player, CachedBody> BODY_CACHE = new WeakHashMap<>();

	private PossessionClientRenderer() {}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean tryRender(Player player, float yaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		if (!(player instanceof PossessionAccess access))
			return false;
		CompoundTag state = access.createBiotech$getPossessionState();
		if (!EchoShardPossession.isActive(state)) {
			BODY_CACHE.remove(player);
			return false;
		}

		CachedBody cached = BODY_CACHE.get(player);
		if (cached == null || cached.state != state) {
			LivingEntity body = EchoShardPossession.createBody(player.level(), state);
			if (body == null)
				return false;
			cached = new CachedBody(state, body);
			BODY_CACHE.put(player, cached);
		}

		LivingEntity body = cached.body;
		copyAnimationState(player, body);
		EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
		renderer.render(body, yaw, partialTick, poseStack, buffer, packedLight);
		return true;
	}

	private static void copyAnimationState(Player player, LivingEntity body) {
		body.setPos(player.getX(), player.getY(), player.getZ());
		body.xo = player.xo;
		body.yo = player.yo;
		body.zo = player.zo;
		body.setYRot(player.getYRot());
		body.yRotO = player.yRotO;
		body.setXRot(player.getXRot());
		body.xRotO = player.xRotO;
		body.setYBodyRot(player.yBodyRot);
		body.yBodyRotO = player.yBodyRotO;
		body.setYHeadRot(player.yHeadRot);
		body.yHeadRotO = player.yHeadRotO;
		body.setPose(player.getPose());
		body.setDeltaMovement(player.getDeltaMovement());
		body.setOnGround(player.onGround());
		body.tickCount = player.tickCount;
		body.hurtTime = player.hurtTime;
		body.hurtDuration = player.hurtDuration;
		body.deathTime = player.deathTime;
		body.swinging = player.swinging;
		body.swingingArm = player.swingingArm;
		body.swingTime = player.swingTime;
		body.oAttackAnim = player.oAttackAnim;
		body.attackAnim = player.attackAnim;
		body.setHealth(Math.min(player.getHealth(), body.getMaxHealth()));
		body.setInvisible(player.isInvisible());

		WalkAnimationStateAccessor playerWalk = (WalkAnimationStateAccessor) (Object) player.walkAnimation;
		WalkAnimationStateAccessor bodyWalk = (WalkAnimationStateAccessor) (Object) body.walkAnimation;
		bodyWalk.createBiotech$setPosition(playerWalk.createBiotech$getPosition());
		bodyWalk.createBiotech$setSpeedOld(playerWalk.createBiotech$getSpeedOld());
		bodyWalk.createBiotech$setSpeed(playerWalk.createBiotech$getSpeed());
	}

	private record CachedBody(CompoundTag state, LivingEntity body) {}
}

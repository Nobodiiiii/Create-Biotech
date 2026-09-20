package com.nobodiiiii.createbiotech.content.possession.client;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.possession.EchoShardPossession;
import com.nobodiiiii.createbiotech.content.possession.PossessionAccess;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import com.nobodiiiii.createbiotech.entity.PlayerMimicRenderer;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.SlimeBionicRenderer;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;
import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;
import com.nobodiiiii.createbiotech.mixin.WalkAnimationStateAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Draws a player through the renderer of the currently possessed body. */
public final class PossessionClientRenderer {
	private static final float NORMAL_ARM_LENGTH = 12.0f / 16.0f;
	private static final float MAX_ARM_LENGTH = 16.0f / 16.0f;
	private static final float NORMAL_ARM_CENTER_X = 6.0f / 16.0f;
	private static final Map<Player, CachedBody> BODY_CACHE = new WeakHashMap<>();
	private static final List<String> RIGHT_LOWER_ARM_NAMES = List.of(
		"right_forearm", "right_lower_arm", "right_arm_lower");
	private static final List<String> LEFT_LOWER_ARM_NAMES = List.of(
		"left_forearm", "left_lower_arm", "left_arm_lower");
	private static final List<String> GENERIC_LOWER_ARM_NAMES = List.of(
		"forearm", "lower_arm", "arm_lower");

	private PossessionClientRenderer() {}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean tryRender(Player player, float yaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		LivingEntity body = body(player);
		if (body == null)
			return false;
		copyAnimationState(player, body);
		EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
		renderer.render(body, yaw, partialTick, poseStack, buffer, packedLight);
		return true;
	}

	/** Replaces the skin arm in empty-hand and map first-person passes with the possessed body's limb. */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean tryRenderFirstPersonArm(Player player, HumanoidArm side, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		LivingEntity body = body(player);
		if (body == null)
			return false;
		copyAnimationState(player, body);

		EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
		if (renderer instanceof SlimeBionicRenderer bionicRenderer
			&& body instanceof SlimeBionicEntity bionic) {
			bionicRenderer.renderFirstPersonArm(bionic, side, poseStack, buffer, packedLight);
			return true;
		}
		if (renderLivingBodyArm(body, renderer, side, poseStack, buffer, packedLight))
			return true;
		// A body without an actual arm deliberately leaves this first-person side empty.
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static boolean renderLivingBodyArm(LivingEntity body, EntityRenderer renderer, HumanoidArm side,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (!(renderer instanceof LivingEntityRenderer livingRenderer))
			return false;
		if (renderer instanceof PlayerMimicRenderer mimicRenderer && body instanceof PlayerMimicEntity mimic)
			mimicRenderer.selectModel(mimic);

		EntityModel model = livingRenderer.getModel();
		float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		model.attackTime = 0.0f;
		model.riding = false;
		model.young = body.isBaby();
		model.prepareMobModel(body, 0.0f, 0.0f, partialTick);
		model.setupAnim(body, 0.0f, 0.0f, body.tickCount + partialTick, 0.0f, 0.0f);

		List<ModelPart> parts = firstPersonParts(model, side);
		if (parts.isEmpty())
			return false;

		ResourceLocation texture = renderer.getTextureLocation(body);
		VertexConsumer vertices = buffer.getBuffer(model.renderType(texture));
		for (ModelPart part : parts)
			renderAtPlayerArmAnchor(part, side, poseStack, vertices, packedLight);
		return true;
	}

	private static LivingEntity body(Player player) {
		if (!(player instanceof PossessionAccess access))
			return null;
		CompoundTag state = access.createBiotech$getPossessionState();
		if (!EchoShardPossession.isActive(state)) {
			BODY_CACHE.remove(player);
			return null;
		}

		CachedBody cached = BODY_CACHE.get(player);
		if (cached == null || cached.state != state) {
			LivingEntity body = EchoShardPossession.createBody(player.level(), state);
			if (body == null)
				return null;
			cached = new CachedBody(state, body);
			BODY_CACHE.put(player, cached);
		}
		return cached.body;
	}

	private static List<ModelPart> firstPersonParts(EntityModel<?> model, HumanoidArm side) {
		if (model instanceof PlayerModel<?> playerModel) {
			ModelPart arm = side == HumanoidArm.RIGHT ? playerModel.rightArm : playerModel.leftArm;
			ModelPart sleeve = side == HumanoidArm.RIGHT ? playerModel.rightSleeve : playerModel.leftSleeve;
			return List.of(arm, sleeve);
		}
		if (model instanceof HumanoidModel<?> humanoid) {
			ModelPart arm = side == HumanoidArm.RIGHT ? humanoid.rightArm : humanoid.leftArm;
			return List.of(distalArmPart(arm, side));
		}
		if (!(model instanceof HierarchicalModel<?> hierarchical))
			return List.of();

		ModelPart lowerArm = namedDescendant(hierarchical.root(),
			side == HumanoidArm.RIGHT ? RIGHT_LOWER_ARM_NAMES : LEFT_LOWER_ARM_NAMES);
		if (lowerArm != null)
			return List.of(lowerArm);
		ModelPart arm = hierarchical.getAnyDescendantWithName(
			side == HumanoidArm.RIGHT ? "right_arm" : "left_arm").orElse(null);
		return arm == null ? List.of() : List.of(distalArmPart(arm, side));
	}

	private static ModelPart distalArmPart(ModelPart arm, HumanoidArm side) {
		List<String> sidedNames = side == HumanoidArm.RIGHT
			? RIGHT_LOWER_ARM_NAMES : LEFT_LOWER_ARM_NAMES;
		ModelPart lowerArm = namedDescendant(arm, sidedNames);
		if (lowerArm == null)
			lowerArm = namedDescendant(arm, GENERIC_LOWER_ARM_NAMES);
		return lowerArm == null ? arm : lowerArm;
	}

	private static ModelPart namedDescendant(ModelPart root, List<String> names) {
		Map<String, ModelPart> children = ((ModelPartAccessor) (Object) root)
			.createBiotech$getChildren();
		for (String name : names) {
			ModelPart direct = children.get(name);
			if (direct != null)
				return direct;
		}
		for (ModelPart child : children.values()) {
			ModelPart found = namedDescendant(child, names);
			if (found != null)
				return found;
		}
		return null;
	}

	private static void renderAtPlayerArmAnchor(ModelPart part, HumanoidArm side, PoseStack poseStack,
		VertexConsumer vertices, int packedLight) {
		PartState saved = PartState.capture(part);
		EntityGeometry.Bounds bounds = measureAtOrigin(part);
		if (bounds == null)
			return;
		float centerX = side == HumanoidArm.RIGHT ? -NORMAL_ARM_CENTER_X : NORMAL_ARM_CENTER_X;
		float visibleLength = Math.max(NORMAL_ARM_LENGTH,
			Math.min(MAX_ARM_LENGTH, bounds.sizeY()));
		part.x = (centerX - bounds.centerX()) * 16.0f;
		part.y = (visibleLength - bounds.maxY()) * 16.0f;
		part.z = -bounds.centerZ() * 16.0f;
		part.xRot = 0.0f;
		part.yRot = 0.0f;
		part.zRot = 0.0f;
		part.visible = true;
		part.skipDraw = false;
		try {
			part.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
		} finally {
			saved.restore(part);
		}
	}

	private static EntityGeometry.Bounds measureAtOrigin(ModelPart part) {
		PartState saved = PartState.capture(part);
		part.x = 0.0f;
		part.y = 0.0f;
		part.z = 0.0f;
		part.xRot = 0.0f;
		part.yRot = 0.0f;
		part.zRot = 0.0f;
		part.visible = true;
		part.skipDraw = false;
		EntityGeometry.Collector geometry = EntityGeometry.Collector.boundsOnly();
		try {
			part.render(new PoseStack(), geometry, LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY);
		} finally {
			saved.restore(part);
		}
		return geometry.hasVertices() ? geometry.bounds() : null;
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

	private record PartState(float x, float y, float z, float xRot, float yRot, float zRot,
		float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
		private static PartState capture(ModelPart part) {
			return new PartState(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
				part.xScale, part.yScale, part.zScale, part.visible, part.skipDraw);
		}

		private void restore(ModelPart part) {
			part.x = x;
			part.y = y;
			part.z = z;
			part.xRot = xRot;
			part.yRot = yRot;
			part.zRot = zRot;
			part.xScale = xScale;
			part.yScale = yScale;
			part.zScale = zScale;
			part.visible = visible;
			part.skipDraw = skipDraw;
		}
	}
}

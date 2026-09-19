package com.nobodiiiii.createbiotech.content.possession.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.possession.EchoShardPossession;
import com.nobodiiiii.createbiotech.content.possession.PossessionAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import com.nobodiiiii.createbiotech.entity.PlayerMimicRenderer;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;
import com.nobodiiiii.createbiotech.mixin.client.QuadrupedModelAccessor;
import com.nobodiiiii.createbiotech.mixin.WalkAnimationStateAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Draws a player through the renderer of the currently possessed body. */
public final class PossessionClientRenderer {
	private static final Map<Player, CachedBody> BODY_CACHE = new WeakHashMap<>();
	private static final List<String> RIGHT_LIMB_NAMES = List.of(
		"right_arm", "right_front_leg", "right_front_foot", "right_wing", "right_fin",
		"right_tentacle", "right_hind_leg", "leg0");
	private static final List<String> LEFT_LIMB_NAMES = List.of(
		"left_arm", "left_front_leg", "left_front_foot", "left_wing", "left_fin",
		"left_tentacle", "left_hind_leg", "leg1");

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
		if (renderLivingBodyArm(body, renderer, side, poseStack, buffer, packedLight))
			return true;
		if (body instanceof SlimeBionicEntity bionic)
			renderBionicSourceArm(bionic, side, poseStack, buffer, packedLight);
		// Never leak the player's original skin into a body without a conventional arm model.
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

	private static boolean renderBionicSourceArm(SlimeBionicEntity bionic, HumanoidArm side,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		SurgicalAssembly assembly = bionic.getAssembly();
		if (assembly == null)
			return false;

		Set<Integer> sourceOrder = new LinkedHashSet<>();
		int preferred = preferredArmSource(assembly, side);
		if (preferred >= 0)
			sourceOrder.add(preferred);
		for (int source = 0; source < assembly.sources().size(); source++)
			sourceOrder.add(source);

		for (int sourceIndex : sourceOrder) {
			LivingEntity preview = SurgicalSourceModelRenderer.preview(
				assembly.sources().get(sourceIndex).profile());
			if (preview == null)
				continue;
			preview.tickCount = bionic.tickCount;
			EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher()
				.getRenderer(preview);
			if (renderLivingBodyArm(preview, renderer, side, poseStack, buffer, packedLight))
				return true;
		}
		return false;
	}

	private static int preferredArmSource(SurgicalAssembly assembly, HumanoidArm side) {
		int preferred = -1;
		double preferredScore = Double.NEGATIVE_INFINITY;
		for (SurgicalAssembly.Limb limb : assembly.effectiveLimbs()) {
			if (limb.type() != SurgicalLimbType.SHOULDER)
				continue;
			SurgicalAssembly.Source source = assembly.sources().get(limb.childSource());
			Vec3 childOffset = source.cubeOffsets().getOrDefault(limb.childCube(), Vec3.ZERO);
			double x = source.originOffset().x + childOffset.x;
			double sideScore = side == HumanoidArm.RIGHT ? -x : x;
			if (sideScore > preferredScore) {
				preferredScore = sideScore;
				preferred = limb.childSource();
			}
		}
		return preferred;
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
		if (model instanceof HumanoidModel<?> humanoid)
			return List.of(side == HumanoidArm.RIGHT ? humanoid.rightArm : humanoid.leftArm);
		if (model instanceof QuadrupedModel<?>) {
			QuadrupedModelAccessor quadruped = (QuadrupedModelAccessor) model;
			return List.of(side == HumanoidArm.RIGHT
				? quadruped.createBiotech$getRightFrontLeg()
				: quadruped.createBiotech$getLeftFrontLeg());
		}
		if (!(model instanceof HierarchicalModel<?> hierarchical))
			return List.of();

		for (String name : side == HumanoidArm.RIGHT ? RIGHT_LIMB_NAMES : LEFT_LIMB_NAMES) {
			ModelPart candidate = hierarchical.getAnyDescendantWithName(name).orElse(null);
			if (candidate != null)
				return List.of(candidate);
		}
		ModelPart fallback = firstRenderablePart(hierarchical.root());
		return fallback == null ? List.of() : List.of(fallback);
	}

	private static ModelPart firstRenderablePart(ModelPart root) {
		ModelPartAccessor accessor = (ModelPartAccessor) (Object) root;
		for (ModelPart child : accessor.createBiotech$getChildren().values()) {
			ModelPart found = firstRenderablePart(child);
			if (found != null)
				return found;
		}
		return accessor.createBiotech$getCubes().isEmpty() ? null : root;
	}

	private static void renderAtPlayerArmAnchor(ModelPart part, HumanoidArm side, PoseStack poseStack,
		VertexConsumer vertices, int packedLight) {
		PartState saved = PartState.capture(part);
		part.x = side == HumanoidArm.RIGHT ? -5.0f : 5.0f;
		part.y = 2.0f;
		part.z = 0.0f;
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

package com.nobodiiiii.createbiotech.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonItem;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgrade;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SonicDogCannonItemRenderer extends CustomRenderedItemModelRenderer {

	public static final ResourceLocation GEAR_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/gear");
	public static final ResourceLocation SCOPE_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/scope");
	public static final ResourceLocation LEFT_SCOPE_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/scope_left");
	public static final ResourceLocation FOLDED_SCOPE_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/scope_folded");
	public static final ResourceLocation LEFT_FOLDED_SCOPE_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/scope_folded_left");
	public static final ResourceLocation COLLAR_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/collar");
	public static final ResourceLocation WOLF_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/wolf");
	public static final ResourceLocation WOLF_ANGRY_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/wolf_angry");
	public static final ResourceLocation SHRIEK_EYES_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/shriek_eyes");
	public static final ResourceLocation PAW_LEFT_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/paw_left");
	public static final ResourceLocation PAW_RIGHT_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/paw_right");
	public static final ResourceLocation PAW_LEFT_ANGRY_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/paw_left_angry");
	public static final ResourceLocation PAW_RIGHT_ANGRY_MODEL_LOCATION =
		CreateBiotech.asResource("item/sonic_dog_cannon/paw_right_angry");
	private static final PartialModel GEAR = PartialModel.of(GEAR_MODEL_LOCATION);
	private static final PartialModel SCOPE = PartialModel.of(SCOPE_MODEL_LOCATION);
	private static final PartialModel LEFT_SCOPE = PartialModel.of(LEFT_SCOPE_MODEL_LOCATION);
	private static final PartialModel FOLDED_SCOPE = PartialModel.of(FOLDED_SCOPE_MODEL_LOCATION);
	private static final PartialModel LEFT_FOLDED_SCOPE = PartialModel.of(LEFT_FOLDED_SCOPE_MODEL_LOCATION);
	private static final PartialModel COLLAR = PartialModel.of(COLLAR_MODEL_LOCATION);
	private static final PartialModel WOLF = PartialModel.of(WOLF_MODEL_LOCATION);
	private static final PartialModel WOLF_ANGRY = PartialModel.of(WOLF_ANGRY_MODEL_LOCATION);
	private static final PartialModel SHRIEK_EYES = PartialModel.of(SHRIEK_EYES_MODEL_LOCATION);
	private static final PartialModel PAW_LEFT = PartialModel.of(PAW_LEFT_MODEL_LOCATION);
	private static final PartialModel PAW_RIGHT = PartialModel.of(PAW_RIGHT_MODEL_LOCATION);
	private static final PartialModel PAW_LEFT_ANGRY = PartialModel.of(PAW_LEFT_ANGRY_MODEL_LOCATION);
	private static final PartialModel PAW_RIGHT_ANGRY = PartialModel.of(PAW_RIGHT_ANGRY_MODEL_LOCATION);
	private static final float GEAR_ACCELERATION = -0.75f;
	private static final float DECELERATION_TICKS = 10.0f;
	private static final float PAW_BOB_AMPLITUDE = 0.5f / 16.0f;
	private static final float PAW_BOB_SPEED = 0.8f;
	private static final float PAW_PHASE_OFFSET = (float) Math.PI;
	private static final Map<ItemStack, GearDeceleration> GEAR_DECELERATIONS = new WeakHashMap<>();

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		renderer.render(model.getOriginalModel(), light);
		float fullChargeAnimationTime = getFullChargeAnimationTime(stack);
		boolean fullCharge = fullChargeAnimationTime >= 0.0f;
		renderer.render((fullCharge ? WOLF_ANGRY : WOLF).get(), light);
		if (SonicDogCannonUpgrade.SHRIEK_SONIC_BOOM.isInstalled(stack))
			renderer.render(SHRIEK_EYES.get(), light);
		renderPaw(renderer, poseStack, fullCharge ? PAW_LEFT_ANGRY : PAW_LEFT,
			getPawOffset(fullChargeAnimationTime, 0.0f), light);
		renderPaw(renderer, poseStack, fullCharge ? PAW_RIGHT_ANGRY : PAW_RIGHT,
			getPawOffset(fullChargeAnimationTime, PAW_PHASE_OFFSET), light);

		if (SonicDogCannonUpgrade.DOG_COLLAR.isInstalled(stack))
			renderer.render(COLLAR.get(), light);

		float angle = getGearAngle(stack);
		poseStack.pushPose();
		// The gear's Blockbench pivot is [8, 7, 3.5], relative to the item model's [8, 8, 8] centre.
		poseStack.translate(0.0d, -1.0d / 16.0d, -4.5d / 16.0d);
		poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
		poseStack.translate(0.0d, 1.0d / 16.0d, 4.5d / 16.0d);
		renderer.render(GEAR.get(), light);
		poseStack.popPose();

		if (SonicDogCannonUpgrade.SCOPE.isInstalled(stack)) {
			boolean leftSide = shouldMirrorScope(transformType);
			boolean folded = SonicDogCannonItem.isScopeFolded(stack);
			PartialModel scope = folded
				? leftSide ? LEFT_FOLDED_SCOPE : FOLDED_SCOPE
				: leftSide ? LEFT_SCOPE : SCOPE;
			renderer.render(scope.get(), light);
		}
	}

	private static void renderPaw(PartialItemModelRenderer renderer, PoseStack poseStack, PartialModel paw,
		float verticalOffset, int light) {
		poseStack.pushPose();
		poseStack.translate(0.0f, verticalOffset, 0.0f);
		renderer.render(paw.get(), light);
		poseStack.popPose();
	}

	private static float getPawOffset(float animationTime, float phaseOffset) {
		if (animationTime < 0.0f)
			return 0.0f;
		return Mth.sin(animationTime * PAW_BOB_SPEED + phaseOffset) * PAW_BOB_AMPLITUDE;
	}

	private static float getFullChargeAnimationTime(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isUsingItem() || player.getUseItem() != stack)
			return -1.0f;

		float elapsed = player.getTicksUsingItem() + AnimationTickHolder.getPartialTicks();
		return elapsed < SonicDogCannonItem.FULL_CHARGE_TICKS
			? -1.0f
			: elapsed - SonicDogCannonItem.FULL_CHARGE_TICKS;
	}

	private static boolean shouldMirrorScope(ItemDisplayContext transformType) {
		if (transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
			return false;
		if (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
			return true;

		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getMainArm() == HumanoidArm.LEFT;
	}

	private static float getGearAngle(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null)
			return 0.0f;

		float renderTime = AnimationTickHolder.getRenderTime(player.clientLevel);
		float restingAngle = getDeceleratingAngle(stack, renderTime);
		if (!player.isUsingItem() || player.getUseItem() != stack)
			return restingAngle;

		float elapsed = player.getTicksUsingItem() + AnimationTickHolder.getPartialTicks();
		return (restingAngle + getChargingAngle(elapsed)) % 360.0f;
	}

	public static void onFired(int shooterId, InteractionHand hand, int chargeTicks) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null)
			return;

		Entity entity = level.getEntity(shooterId);
		if (!(entity instanceof Player shooter))
			return;

		ItemStack stack = shooter.getItemInHand(hand);
		if (!(stack.getItem() instanceof SonicDogCannonItem))
			return;

		float renderTime = AnimationTickHolder.getRenderTime(level);
		float startAngle = getDeceleratingAngle(stack, renderTime)
			+ getChargingAngle(Math.max(chargeTicks, 0));
		float startSpeed = GEAR_ACCELERATION * Math.min(Math.max(chargeTicks, 0),
			SonicDogCannonItem.FULL_CHARGE_TICKS);
		GEAR_DECELERATIONS.put(stack, new GearDeceleration(renderTime, startAngle % 360.0f, startSpeed));
	}

	private static float getChargingAngle(float elapsed) {
		float acceleratingTicks = Math.min(elapsed, SonicDogCannonItem.FULL_CHARGE_TICKS);
		float angle = GEAR_ACCELERATION * acceleratingTicks * acceleratingTicks / 2.0f;
		if (elapsed > SonicDogCannonItem.FULL_CHARGE_TICKS)
			angle += GEAR_ACCELERATION * SonicDogCannonItem.FULL_CHARGE_TICKS
				* (elapsed - SonicDogCannonItem.FULL_CHARGE_TICKS);
		return angle;
	}

	private static float getDeceleratingAngle(ItemStack stack, float renderTime) {
		GearDeceleration deceleration = GEAR_DECELERATIONS.get(stack);
		return deceleration == null ? 0.0f : deceleration.getAngle(renderTime);
	}

	private record GearDeceleration(float startTime, float startAngle, float startSpeed) {
		private float getAngle(float renderTime) {
			float progress = Mth.clamp((renderTime - startTime) / DECELERATION_TICKS, 0.0f, 1.0f);
			float travel = startSpeed * DECELERATION_TICKS
				* (progress - progress * progress / 2.0f);
			return (startAngle + travel) % 360.0f;
		}
	}
}

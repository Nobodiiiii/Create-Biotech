package com.nobodiiiii.createbiotech.content.capturenet.client;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetItem;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetTier;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Separate frame and mesh parts follow Create's wrench renderer and the surgical kit's hand transforms. */
public final class CaptureNetRenderer extends BlockEntityWithoutLevelRenderer implements IClientItemExtensions {
	private static final Map<CaptureNetTier, PartialModel> FRAMES = new EnumMap<>(CaptureNetTier.class);
	private static final PartialModel BAG = PartialModel.of(CreateBiotech.asResource("item/capture_net/bag"));
	// The net is a single open shell: its far wall must also be visible from inside the mouth.
	private static final RenderType NET_RENDER_TYPE = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
	private static final CachedRenderEntity<LivingEntity, ItemStack> CONTENTS = CachedRenderEntity
		.keyed((Level level, ItemStack stack) -> {
			var entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
			return entity instanceof LivingEntity living ? living : null;
		})
		.keyEquality((a, b) -> CBItemData.getReadOnlyComponent(a) == CBItemData.getReadOnlyComponent(b))
		.keyCopier(ItemStack::copy)
		.cacheCapacity(12);

	static {
		for (CaptureNetTier tier : CaptureNetTier.values())
			FRAMES.put(tier, PartialModel.of(CreateBiotech.asResource("item/capture_net/" + tier.id() + "_frame")));
	}

	public CaptureNetRenderer() {
		super(null, null);
	}

	public static void registerModels(java.util.function.Consumer<net.minecraft.resources.ResourceLocation> register) {
		FRAMES.values().forEach(model -> register.accept(model.modelLocation()));
		register.accept(BAG.modelLocation());
	}

	@Override
	public BlockEntityWithoutLevelRenderer getCustomRenderer() {
		return this;
	}

	@Override
	public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm,
		ItemStack stack, float partialTick, float equip, float swing) {
		float progress = CaptureNetItem.animationProgress(stack, player.level().getGameTime() + partialTick);
		if (progress < 0) return false;
		float side = arm == HumanoidArm.RIGHT ? 1 : -1;
		// Keep vanilla's equip offset; the Blockbench grip track owns the scoop itself.
		pose.translate(side * 0.56, -0.52 - equip * 0.6, -0.72);
		return true;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
		MultiBufferSource buffer, int light, int overlay) {
		if (!(stack.getItem() instanceof CaptureNetItem net)) return;
		var renderer = PartialItemModelRenderer.of(stack, context, pose, buffer, overlay);
		float time = AnimationTickHolder.getRenderTime();
		boolean occupied = CaptureNetItem.isOccupied(stack);
		boolean releasing = CaptureNetItem.isReleasing(stack);
		float side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			|| context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1 : 1;
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		float progress = CaptureNetItem.animationProgress(stack, time);
		float seconds = Math.max(0, progress) * (releasing
			? CaptureNetAnimation.RELEASE_LENGTH : CaptureNetAnimation.CAPTURE_LENGTH);
		float idleSeconds = (time / 20) % CaptureNetAnimation.OCCUPIED_LENGTH;
		float idleWeight = occupied ? (progress < 0 ? 1 : Mth.clamp((progress - 0.75f) * 4, 0, 1)) : 0;
		if (progress >= 0) {
			float[][] position = releasing ? CaptureNetAnimation.RELEASE_GRIP_POSITION
				: CaptureNetAnimation.CAPTURE_GRIP_POSITION;
			pose.translate(side * CaptureNetAnimation.sample(position, seconds, 0) / 16,
				CaptureNetAnimation.sample(position, seconds, 1) / 16,
				CaptureNetAnimation.sample(position, seconds, 2) / 16);
			applyJoint(pose, CaptureNetAnimation.GRIP_PIVOT, releasing
				? CaptureNetAnimation.RELEASE_GRIP_ROTATION : CaptureNetAnimation.CAPTURE_GRIP_ROTATION,
				seconds, null, 0, 0, side);
		}
		renderer.render(FRAMES.get(net.tier()).get(), light);
		pose.pushPose();
		applyJoint(pose, CaptureNetAnimation.BAG_PIVOT, progress < 0 ? null : releasing
			? CaptureNetAnimation.RELEASE_BAG_ROTATION : CaptureNetAnimation.CAPTURE_BAG_ROTATION,
			seconds, CaptureNetAnimation.OCCUPIED_BAG_ROTATION, idleSeconds, idleWeight, side);
		if (occupied) renderContents(stack, pose, buffer, light);
		// Entity renderers can use the same singleton partial renderer; reacquire it afterwards.
		PartialItemModelRenderer.of(stack, context, pose, buffer, overlay).render(BAG.get(), NET_RENDER_TYPE, light);
		pose.popPose();
		pose.popPose();
	}

	private static void applyJoint(PoseStack pose, float[] pivot, float[][] action, float seconds,
		float[][] idle, float idleSeconds, float idleWeight, float side) {
		float x = angle(action, seconds, idle, idleSeconds, idleWeight, 0);
		float y = angle(action, seconds, idle, idleSeconds, idleWeight, 1) * side;
		float z = angle(action, seconds, idle, idleSeconds, idleWeight, 2) * side;
		pose.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
		// Generic Model uses ZYX Euler order, matching the Blockbench preview.
		pose.mulPose(Axis.ZP.rotationDegrees(z));
		pose.mulPose(Axis.YP.rotationDegrees(y));
		pose.mulPose(Axis.XP.rotationDegrees(x));
		pose.translate(-pivot[0] / 16, -pivot[1] / 16, -pivot[2] / 16);
	}

	private static float angle(float[][] action, float seconds, float[][] idle,
		float idleSeconds, float idleWeight, int axis) {
		return (action == null ? 0 : CaptureNetAnimation.sample(action, seconds, axis))
			+ (idle == null || idleWeight == 0 ? 0 : CaptureNetAnimation.sample(idle, idleSeconds, axis) * idleWeight);
	}

	private static void renderContents(ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light) {
		LivingEntity entity = CONTENTS.get(Minecraft.getInstance().level, stack);
		if (entity == null) return;
		pose.pushPose();
		pose.translate(0, 3.0 / 16, 5.0 / 16);
		float scale = Math.min(0.30f / Math.max(0.1f, entity.getBbWidth()),
			0.30f / Math.max(0.1f, entity.getBbHeight()));
		pose.scale(scale, scale, scale);
		EntityRenderHelper.renderUnoriented(entity, pose, buffer, light);
		pose.popPose();
	}
}

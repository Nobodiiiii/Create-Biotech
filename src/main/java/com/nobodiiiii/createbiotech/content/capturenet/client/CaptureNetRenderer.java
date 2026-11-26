package com.nobodiiiii.createbiotech.content.capturenet.client;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.capturenet.ArmCapturePose;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetItem;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetScoop;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetTier;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterNamedRenderTypesEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Separate frame and mesh parts follow Create's wrench renderer and the surgical kit's hand transforms. */
public final class CaptureNetRenderer extends BlockEntityWithoutLevelRenderer implements IClientItemExtensions {
	private static final Map<CaptureNetTier, PartialModel> FRAMES = new EnumMap<>(CaptureNetTier.class);
	private static final Map<CaptureNetTier, PartialModel> MOUNTED_HEADS = new EnumMap<>(CaptureNetTier.class);
	private static final PartialModel MOUNTED_SHAFT = PartialModel.of(CreateBiotech.asResource("item/capture_net/mounted/shaft"));
	private static final PartialModel BAG = PartialModel.of(CreateBiotech.asResource("item/capture_net/bag"));
	// The wooden shaft runs from model Y = -8 to the socket at Y = 5.
	private static final float SHAFT_LENGTH = 13 / 16f;
	// The pouch spans model pixels [3, 9, 8] to [13, 19, 15], around the item's [8, 8, 8] origin.
	private static final float CONTENTS_Y = 6.0f;
	private static final float CONTENTS_Z = 3.5f;
	private static final float CONTENTS_FILL = 0.94f;
	private static final Map<LivingEntity, ContentsLayout> CONTENTS_LAYOUTS = new WeakHashMap<>();
	// The net is a single open shell: its far wall must also be visible from inside the mouth.
	private static final RenderType NET_RENDER_TYPE = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
	private static ShaderInstance guiNetShader;
	private static final RenderType GUI_NET_RENDER_TYPE = RenderType.create("create_biotech_capture_net_gui",
		DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
		RenderType.CompositeState.builder()
			.setShaderState(new RenderStateShard.ShaderStateShard(() -> guiNetShader))
			.setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
			.setCullState(RenderStateShard.NO_CULL)
			.setLightmapState(RenderStateShard.LIGHTMAP)
			.setOverlayState(RenderStateShard.OVERLAY)
			.createCompositeState(true));
	private static final CachedRenderEntity<LivingEntity, ItemStack> CONTENTS = CachedRenderEntity
		.keyed((Level level, ItemStack stack) -> {
			var entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
			return entity instanceof LivingEntity living ? living : null;
		})
		.keyEquality((a, b) -> CBItemData.getReadOnlyComponent(a) == CBItemData.getReadOnlyComponent(b))
		.keyCopier(ItemStack::copy)
		.cacheCapacity(12);

	static {
		for (CaptureNetTier tier : CaptureNetTier.values()) {
			FRAMES.put(tier, PartialModel.of(CreateBiotech.asResource("item/capture_net/" + tier.id() + "_frame")));
			MOUNTED_HEADS.put(tier, PartialModel.of(CreateBiotech.asResource("item/capture_net/mounted/" + tier.id() + "_head")));
		}
	}

	public CaptureNetRenderer() {
		super(null, null);
	}

	public static void registerRenderTypes(RegisterNamedRenderTypesEvent event) {
		event.register(CreateBiotech.asResource("capture_net_mesh"), RenderType.cutout(), NET_RENDER_TYPE);
	}

	public static void registerShaders(RegisterShadersEvent event) throws IOException {
		event.registerShader(new ShaderInstance(event.getResourceProvider(),
			CreateBiotech.asResource("capture_net_gui"), DefaultVertexFormat.NEW_ENTITY), shader -> guiNetShader = shader);
	}

	public static void registerModels(java.util.function.Consumer<net.minecraft.resources.ResourceLocation> register) {
		CONTENTS_LAYOUTS.clear();
		FRAMES.values().forEach(model -> register.accept(model.modelLocation()));
		MOUNTED_HEADS.values().forEach(model -> register.accept(model.modelLocation()));
		register.accept(MOUNTED_SHAFT.modelLocation());
		register.accept(BAG.modelLocation());
	}

	@Override
	public BlockEntityWithoutLevelRenderer getCustomRenderer() {
		return this;
	}

	public static void renderMounted(ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light, int overlay,
		float extension) {
		if (!(stack.getItem() instanceof CaptureNetItem net))
			return;
		var renderer = PartialItemModelRenderer.of(stack, ItemDisplayContext.NONE, pose, buffer, overlay);
		if (extension == 0) {
			renderer.render(FRAMES.get(net.tier()).get(), light);
			renderer.render(BAG.get(), NET_RENDER_TYPE, light);
			return;
		}

		float extraLength = extension / ArmCapturePose.NET_SCALE;
		pose.pushPose();
		pose.translate(0, CaptureNetScoop.HANDLE_BOTTOM, 0);
		pose.scale(1, 1 + extraLength / SHAFT_LENGTH, 1);
		pose.translate(0, -CaptureNetScoop.HANDLE_BOTTOM, 0);
		renderer.render(MOUNTED_SHAFT.get(), light);
		pose.popPose();

		pose.pushPose();
		pose.translate(0, extraLength, 0);
		renderer.render(MOUNTED_HEADS.get(net.tier()).get(), light);
		renderer.render(BAG.get(), NET_RENDER_TYPE, light);
		pose.popPose();
	}

	@Override
	public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm,
		ItemStack stack, float partialTick, float equip, float swing) {
		float progress = CaptureNetItem.animationProgress(stack, (double) player.level().getGameTime() + partialTick);
		if (progress < 0) return false;
		CaptureNetFirstPersonAnimation.apply(pose, player, arm, stack, equip, progress);
		return true;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
		MultiBufferSource buffer, int light, int overlay) {
		if (!(stack.getItem() instanceof CaptureNetItem net)) return;
		var renderer = PartialItemModelRenderer.of(stack, context, pose, buffer, overlay);
		float time = AnimationTickHolder.getRenderTime();
		Level level = Minecraft.getInstance().level;
		boolean occupied = CaptureNetItem.isOccupied(stack);
		float side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			|| context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1 : 1;
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		// Action timestamps use world game time; Catnip's render clock starts at client login.
		float progress = level == null ? -1 : CaptureNetItem.animationProgress(stack,
			(double) level.getGameTime() + AnimationTickHolder.getPartialTicks());
		float seconds = Math.max(0, progress) * CaptureNetAnimation.CAPTURE_LENGTH;
		float idleWeight = occupied ? (progress < 0 ? 1 : Mth.clamp((progress - 0.75f) * 4, 0, 1)) : 0;
		if (progress >= 0 && !context.firstPerson()) {
			float[][] position = CaptureNetAnimation.CAPTURE_GRIP_POSITION;
			pose.translate(side * CaptureNetAnimation.sample(position, seconds, 0) / 16,
				CaptureNetAnimation.sample(position, seconds, 1) / 16,
				CaptureNetAnimation.sample(position, seconds, 2) / 16);
			applyJoint(pose, CaptureNetAnimation.GRIP_PIVOT, CaptureNetAnimation.CAPTURE_GRIP_ROTATION,
				seconds, 1, side);
		}
		renderer.render(FRAMES.get(net.tier()).get(), light);
		LivingEntity contents = occupied ? CONTENTS.get(Minecraft.getInstance().level, stack) : null;
		// The pouch and its contents shake together; the embedded mouth stays inside the fixed rim.
		float horizontalSway = 0;
		if (idleWeight > 0) {
			var motion = CaptureNetStruggle.sample(time, contents == null ? 0 : contents.getUUID().hashCode(), idleWeight);
			horizontalSway = side * motion.x() / CaptureNetAnimation.BAG_TIP_DEPTH;
			pose.translate(0, motion.y() / 16, motion.z() / 16);
			applyRotation(pose, CaptureNetAnimation.BAG_PIVOT, motion.pitch(), 0, 0);
		}
		if (contents != null) renderContents(contents, pose, buffer, light, horizontalSway);
		// Increase sideways motion towards the far wall while keeping the mouth pinned inside the rim.
		// Apply this only to the mesh; the creature follows its local offset without being sheared.
		if (horizontalSway != 0)
			pose.mulPose(new Matrix4f().m20(horizontalSway)
				.m30(-horizontalSway * CaptureNetAnimation.BAG_PIVOT[2] / 16));
		// Entity renderers can use the same singleton partial renderer; reacquire it afterwards.
		RenderType netRenderType = context == ItemDisplayContext.GUI ? GUI_NET_RENDER_TYPE : NET_RENDER_TYPE;
		PartialItemModelRenderer.of(stack, context, pose, buffer, overlay).render(BAG.get(), netRenderType, light);
		pose.popPose();
	}

	private static void applyJoint(PoseStack pose, float[] pivot, float[][] rotation, float seconds,
		float weight, float side) {
		float x = CaptureNetAnimation.sample(rotation, seconds, 0) * weight;
		float y = CaptureNetAnimation.sample(rotation, seconds, 1) * weight * side;
		float z = CaptureNetAnimation.sample(rotation, seconds, 2) * weight * side;
		applyRotation(pose, pivot, x, y, z);
	}

	private static void applyRotation(PoseStack pose, float[] pivot, float x, float y, float z) {
		pose.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
		// Generic Model uses ZYX Euler order, matching the Blockbench preview.
		pose.mulPose(Axis.ZP.rotationDegrees(z));
		pose.mulPose(Axis.YP.rotationDegrees(y));
		pose.mulPose(Axis.XP.rotationDegrees(x));
		pose.translate(-pivot[0] / 16, -pivot[1] / 16, -pivot[2] / 16);
	}

	private static void renderContents(LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light,
		float horizontalSway) {
		ContentsLayout layout = CONTENTS_LAYOUTS.get(entity);
		if (layout == null) {
			EntityGeometry.Bounds bounds = EntityGeometry.measureWithFallback(entity,
				EntityGeometry.Collector.boundsOnly()).bounds();
			float scale = CONTENTS_FILL / 16 * Math.min(10 / Math.max(0.01f, bounds.sizeX()),
				Math.min(10 / Math.max(0.01f, bounds.sizeY()), 7 / Math.max(0.01f, bounds.sizeZ())));
			layout = new ContentsLayout(bounds.center(), scale);
			CONTENTS_LAYOUTS.put(entity, layout);
		}
		pose.pushPose();
		pose.translate(horizontalSway * (CONTENTS_Z - CaptureNetAnimation.BAG_PIVOT[2]) / 16,
			CONTENTS_Y / 16, CONTENTS_Z / 16);
		// Zero-yaw entities face +Z; the open mouth is on the pouch's -Z side.
		pose.mulPose(Axis.YP.rotationDegrees(180));
		pose.scale(layout.scale(), layout.scale(), layout.scale());
		pose.translate(-layout.center().x(), -layout.center().y(), -layout.center().z());
		EntityRenderHelper.renderUnoriented(entity, pose, buffer, light);
		pose.popPose();
	}

	private record ContentsLayout(Vector3f center, float scale) {}
}

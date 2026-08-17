package com.nobodiiiii.createbiotech.foundation.render;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * Renders a living entity as if it were an item: inside the unit cube that
 * vanilla item display transforms operate on.
 *
 * <p>This is the single entry point for every "entity shown in item space"
 * surface — the item models themselves (held, dropped, item frame, GUI), the
 * live GUI slot overrides, and the geometry passes that bake an entity onto a
 * cardboard box face. Because they all share this transform, an entity looks
 * the same wherever it appears as an item.
 *
 * <p>Compare {@link com.nobodiiiii.createbiotech.foundation.gui.GuiEntityElement},
 * which places an entity into a Create-style isometric JEI scene instead.
 */
public final class GuiEntityItemElement {

	/** Where the entity sits relative to the unit cube it is drawn in. */
	public enum Anchor {
		/**
		 * The center of the entity's drawn geometry is mapped to the center of the
		 * unit cube, so vanilla item transforms rotate and offset every display
		 * context around the same point.
		 */
		BLOCK_CENTERED,
		/**
		 * The entity stands on the bottom of the unit cube. Suited to items that read
		 * as "a creature standing there" rather than "a creature in a box".
		 */
		FOOT_ANCHORED
	}

	private static final Vector3f BLOCK_CENTER = new Vector3f(0.5f, 0.5f, 0.5f);

	/** Block-centered auto-scale: target size, and ceiling on the magnification. */
	private static final float BLOCK_CENTERED_TARGET_SIZE = 1.5f;
	private static final float BLOCK_CENTERED_MAX_SCALE = 1.5f;

	/** Foot-anchored auto-scale: target size, and ceiling on the magnification. */
	private static final float FOOT_ANCHORED_TARGET_SIZE = 1.75f;
	private static final float FOOT_ANCHORED_MAX_SCALE = 2.0f;

	/** Gap between the bottom of the unit cube and the entity's feet. */
	private static final double FOOT_GAP = 1.0d / 16.0d;

	private static final float BLOCK_CENTERED_Y_ROTATION = 90.0f;
	private static final float FIXED_Y_ROTATION = 180.0f;
	private static final float FOOT_ANCHORED_Y_ROTATION = 0.0f;

	private static final float MIN_POSE_SCALE = 1.0e-6f;
	private static final float MIN_PROJECTED_DIMENSION = 1.0e-6f;

	/** Depth GUI item rendering draws at, matching vanilla's item Z. */
	private static final float GUI_RENDER_Z = 150.0f;
	private static final int GUI_SLOT_SIZE = 16;

	/**
	 * Cap on retained measurement vertices. Past this the projection falls back to
	 * the corners of the accumulated box, which is close enough for scaling a model
	 * that large.
	 */
	private static final int MAX_MEASURED_VERTICES = 262_144;

	/**
	 * Shared across elements because a measurement is only ever read by the element
	 * that took it, within a single synchronous render call. Retaining the vertices
	 * lets one render pass answer both "where is the entity centered" and "how big
	 * does it land on screen", instead of one pass per question.
	 */
	private static final EntityGeometry.Collector MEASUREMENT_SCRATCH =
		EntityGeometry.Collector.caching(MAX_MEASURED_VERTICES);

	private final LivingEntity entity;
	private Anchor anchor = Anchor.BLOCK_CENTERED;
	private float scaleMultiplier = 1.0f;
	private boolean autoScale;
	private double footYOffset;
	private int packedLight = LightTexture.FULL_BRIGHT;
	private boolean measured;
	@Nullable
	private Float yRotation;
	@Nullable
	private Vector3f geometryCenter;

	private GuiEntityItemElement(LivingEntity entity) {
		this.entity = entity;
	}

	public static GuiEntityItemElement of(LivingEntity entity) {
		return new GuiEntityItemElement(entity);
	}

	public GuiEntityItemElement anchor(Anchor anchor) {
		this.anchor = anchor;
		return this;
	}

	public GuiEntityItemElement blockCentered() {
		return anchor(Anchor.BLOCK_CENTERED);
	}

	public GuiEntityItemElement footAnchored() {
		return anchor(Anchor.FOOT_ANCHORED);
	}

	/**
	 * Uses {@code multiplier} as the render scale directly.
	 */
	public GuiEntityItemElement fixedScale(float multiplier) {
		this.scaleMultiplier = multiplier;
		this.autoScale = false;
		return this;
	}

	/**
	 * Normalizes the entity to a consistent on-screen size before applying
	 * {@code multiplier}, so a bee and a ravager both fit the space they are given.
	 */
	public GuiEntityItemElement autoScale(float multiplier) {
		this.scaleMultiplier = multiplier;
		this.autoScale = true;
		return this;
	}

	/**
	 * Extra lift above the floor of the unit cube. Only meaningful for
	 * {@link Anchor#FOOT_ANCHORED}.
	 */
	public GuiEntityItemElement footYOffset(double footYOffset) {
		this.footYOffset = footYOffset;
		return this;
	}

	/**
	 * Overrides the rotation about the vertical axis. Defaults to
	 * {@value #BLOCK_CENTERED_Y_ROTATION} degrees when block-centered and
	 * {@value #FOOT_ANCHORED_Y_ROTATION} when foot-anchored.
	 */
	public GuiEntityItemElement yRotation(float yRotation) {
		this.yRotation = yRotation;
		return this;
	}

	/**
	 * Picks the vertical rotation that reads best for a given item display context.
	 */
	public GuiEntityItemElement yRotationFor(ItemDisplayContext displayContext) {
		return yRotation(baseYRotation(displayContext));
	}

	public GuiEntityItemElement packedLight(int packedLight) {
		this.packedLight = packedLight;
		return this;
	}

	/**
	 * Supplies a previously measured geometry center, skipping the measurement
	 * pass. Intended for callers that cache geometry across frames.
	 */
	public GuiEntityItemElement geometryCenter(Vector3f geometryCenter) {
		this.geometryCenter = geometryCenter;
		return this;
	}

	/**
	 * Renders into an existing pose, in item space. Used by the item renderers and
	 * by the cardboard box face bake.
	 */
	public void render(PoseStack poseStack, MultiBufferSource buffer) {
		poseStack.pushPose();
		applyTransform(poseStack);
		EntityRenderHelper.renderUnoriented(entity, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	/**
	 * Renders into a standard 16x16 inventory slot at {@code (x, y)}, using
	 * {@code transformStack}'s model to supply the GUI display transform.
	 */
	public void renderInGuiSlot(GuiGraphics graphics, ItemStack transformStack, int x, int y) {
		renderInGuiBox(graphics, transformStack, x, y, GUI_SLOT_SIZE, GUI_SLOT_SIZE);
	}

	/**
	 * Renders centered in an arbitrary {@code width} by {@code height} box, for GUI
	 * surfaces that are not slot-sized.
	 */
	public void renderInGuiBox(GuiGraphics graphics, ItemStack transformStack, int x, int y, int width,
		int height) {
		if (transformStack.isEmpty())
			return;

		Minecraft minecraft = Minecraft.getInstance();
		BakedModel model = minecraft.getItemRenderer()
			.getModel(transformStack, minecraft.level, minecraft.player, 0);

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(x + width / 2.0f, y + height / 2.0f, GUI_RENDER_Z);
		poseStack.mulPoseMatrix(new Matrix4f().scaling(1.0f, -1.0f, 1.0f));
		float boxScale = Math.min(width, height);
		poseStack.scale(boxScale, boxScale, boxScale);
		ForgeHooksClient.handleCameraTransforms(poseStack, model, ItemDisplayContext.GUI, false);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
		render(poseStack, graphics.bufferSource());
		graphics.flush();
		poseStack.popPose();
	}

	/**
	 * Applies the item-space transform without rendering, for callers that need to
	 * project geometry through it.
	 */
	public void applyTransform(PoseStack poseStack) {
		if (anchor == Anchor.FOOT_ANCHORED) {
			float scale = autoScale ? footAnchoredAutoScale() : scaleMultiplier;
			poseStack.translate(0.0d, FOOT_GAP + footYOffset, 0.0d);
			poseStack.mulPose(Axis.YP.rotationDegrees(resolveYRotation()));
			poseStack.scale(scale, scale, scale);
			return;
		}

		Vector3f center = resolveGeometryCenter();
		float scale = autoScale ? blockCenteredProjectedScale(poseStack, center) : scaleMultiplier;
		applyBlockCenteredTransform(poseStack, center, scale, resolveYRotation());
	}

	/**
	 * Vertical rotation that reads best for a given item display context: turned to
	 * face the viewer when mounted flat, and mirrored between hands.
	 */
	public static float baseYRotation(ItemDisplayContext displayContext) {
		if (displayContext == ItemDisplayContext.FIXED)
			return FIXED_Y_ROTATION;
		if (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			|| displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
			return -BLOCK_CENTERED_Y_ROTATION;
		return BLOCK_CENTERED_Y_ROTATION;
	}

	private float resolveYRotation() {
		if (yRotation != null)
			return yRotation;
		return anchor == Anchor.FOOT_ANCHORED ? FOOT_ANCHORED_Y_ROTATION : BLOCK_CENTERED_Y_ROTATION;
	}

	/**
	 * Measures the entity once per element, retaining the raw vertices so both the
	 * geometry center and the projected size come out of a single render pass.
	 */
	private EntityGeometry.Collector rawGeometry() {
		if (!measured) {
			EntityGeometry.measureWithFallback(entity, MEASUREMENT_SCRATCH);
			measured = true;
		}
		return MEASUREMENT_SCRATCH;
	}

	private Vector3f resolveGeometryCenter() {
		if (geometryCenter == null)
			geometryCenter = rawGeometry().bounds()
				.center();
		return geometryCenter;
	}

	/**
	 * Normalizes by the entity's largest drawn dimension. Foot-anchored items are
	 * viewed from arbitrary angles, so there is no single projection to fit to.
	 */
	private float footAnchoredAutoScale() {
		float largest = Math.max(rawGeometry().bounds()
			.largestDimension(), EntityGeometry.MIN_AUTO_SCALE_DIMENSION);
		return Math.min(FOOT_ANCHORED_TARGET_SIZE / largest, FOOT_ANCHORED_MAX_SCALE) * scaleMultiplier;
	}

	/**
	 * Measures how large the entity actually lands on screen under the caller's
	 * pose, then scales so it fills the space it was given.
	 *
	 * <p>Projected size is used rather than raw model size because the item display
	 * transform in effect may rotate the entity into a very different silhouette
	 * than its bounding box suggests.
	 */
	private float blockCenteredProjectedScale(PoseStack poseStack, Vector3f center) {
		EntityGeometry.Collector collector = rawGeometry();

		poseStack.pushPose();
		applyBlockCenteredTransform(poseStack, center, 1.0f, resolveYRotation());
		Matrix4f entityToScreen = new Matrix4f(poseStack.last()
			.pose());
		poseStack.popPose();

		EntityGeometry.Bounds bounds = collector.transformBounds(entityToScreen);
		if (!bounds.hasVertices())
			return scaleMultiplier;

		float poseScale = poseStack.last()
			.pose()
			.transformDirection(1.0f, 0.0f, 0.0f, new Vector3f())
			.length();
		if (poseScale <= MIN_POSE_SCALE)
			return scaleMultiplier;

		float projectedDimension = Math.max(bounds.sizeX(), bounds.sizeY()) / poseScale;
		if (projectedDimension <= MIN_PROJECTED_DIMENSION)
			return scaleMultiplier;
		return Math.min(BLOCK_CENTERED_TARGET_SIZE / projectedDimension, BLOCK_CENTERED_MAX_SCALE) * scaleMultiplier;
	}

	/**
	 * Applies the default block-centered transform to a pose without needing an
	 * element instance, for geometry-only passes that already know the center.
	 */
	public static void applyBlockCenteredTransform(PoseStack poseStack, Vector3f geometryCenter, float scale) {
		applyBlockCenteredTransform(poseStack, geometryCenter, scale, BLOCK_CENTERED_Y_ROTATION);
	}

	private static void applyBlockCenteredTransform(PoseStack poseStack, Vector3f center, float scale,
		float yRotation) {
		poseStack.translate(BLOCK_CENTER.x, BLOCK_CENTER.y, BLOCK_CENTER.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
		poseStack.scale(scale, scale, scale);
		poseStack.translate(-center.x, -center.y, -center.z);
	}
}

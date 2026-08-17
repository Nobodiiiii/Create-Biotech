package com.nobodiiiii.createbiotech.compat.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.gui.GuiEntityElement;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

/**
 * Extends Create's animated JEI artwork with living entities as scene props, so
 * a creature can stand among the blocks of a machine preview under the same
 * lighting and the same isometric view Create uses for its own categories.
 *
 * <p>Contrast {@link com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement},
 * which draws an entity as an item rather than as part of a scene.
 */
public abstract class AnimatedKineticsWithEntities extends AnimatedKinetics {

	/** Depth JEI scenes draw at, in front of the recipe background. */
	public static final int SCENE_Z = 100;

	/**
	 * Create's isometric view onto a JEI scene. Matching it exactly is what makes
	 * this mod's categories sit alongside Create's without looking pasted in.
	 */
	public static final float SCENE_X_ROTATION = -15.5f;
	public static final float SCENE_Y_ROTATION = 22.5f;

	/**
	 * Moves the pose to {@code (x, y)} and tilts it into the standard JEI scene
	 * view. Does not push or pop; callers that want that should use
	 * {@link #renderScene}.
	 */
	public static void applySceneTransform(PoseStack poseStack, double x, double y) {
		poseStack.translate(x, y, SCENE_Z);
		poseStack.mulPose(Axis.XP.rotationDegrees(SCENE_X_ROTATION));
		poseStack.mulPose(Axis.YP.rotationDegrees(SCENE_Y_ROTATION));
	}

	/**
	 * Runs {@code content} inside a scene positioned at {@code (x, y)}.
	 */
	public static void renderScene(GuiGraphics graphics, double x, double y, Runnable content) {
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		try {
			applySceneTransform(poseStack, x, y);
			content.run();
		} finally {
			poseStack.popPose();
		}
	}

	/**
	 * <b>Only use this method outside of subclasses.</b> Use
	 * {@link #entityElement(Entity)} if calling from inside a subclass.
	 */
	protected static <T extends Entity> GuiEntityElement.GuiEntityRenderBuilder<T> defaultEntityElement(T entity) {
		return GuiEntityElement.of(entity)
			.lighting(DEFAULT_LIGHTING);
	}

	protected <T extends Entity> GuiEntityElement.GuiEntityRenderBuilder<T> entityElement(T entity) {
		return defaultEntityElement(entity);
	}

	protected void scene(GuiGraphics graphics, double x, double y, Runnable content) {
		renderScene(graphics, x, y, content);
	}
}

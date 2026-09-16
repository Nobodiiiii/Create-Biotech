package com.nobodiiiii.createbiotech.compat.jei;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nobodiiiii.createbiotech.client.ButterCatPartials;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.AllBlocks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class CuteCatOnShaftJeiRenderer {
	private static final float RENDER_SCALE = 20f;
	private static final int PREVIEW_X = 74;
	private static final int PREVIEW_Y = 51;
	private static final Direction PREVIEW_FACING = Direction.NORTH;

	@Nullable
	private static ButterCatEngineBlockEntity cachedBlockEntity;
	@Nullable
	private static ClientLevel cachedLevel;

	private CuteCatOnShaftJeiRenderer() {}

	public static @Nullable PreviewKind getPreviewKind(ItemApplicationRecipe recipe, IRecipeSlotsView recipeSlotsView) {
		if (recipe.getProcessedItem().test(new ItemStack(AllBlocks.SHAFT.get())))
			return PreviewKind.SHAFT_TO_CAT;

		Optional<ItemStack> displayedIngredient = recipeSlotsView.getSlotViews()
			.get(0)
			.getDisplayedIngredient(VanillaTypes.ITEM_STACK);
		return displayedIngredient.filter(stack -> stack.is(CBBlocks.CUTE_CAT_ON_SHAFT.get()
			.asItem()))
			.map(stack -> PreviewKind.CAT_TO_ENGINE)
			.orElse(null);
	}

	public static boolean render(GuiGraphics graphics, PreviewKind previewKind) {
		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 47);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 74, 10);

		var poseStack = graphics.pose();
		poseStack.pushPose();
		AnimatedKineticsWithEntities.applySceneTransform(poseStack, PREVIEW_X, PREVIEW_Y);

		GuiGameElement.of(createShaftState())
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.scale(RENDER_SCALE)
			.render(graphics);

		if (previewKind == PreviewKind.CAT_TO_ENGINE) {
			ClientLevel level = Minecraft.getInstance().level;
			ButterCatEngineBlockEntity blockEntity = getOrCreateBlockEntity(level);
			if (level == null || blockEntity == null) {
				poseStack.popPose();
				return false;
			}

			renderAttachments(graphics, blockEntity);
		}

		poseStack.popPose();
		return true;
	}

	private static @Nullable ButterCatEngineBlockEntity getOrCreateBlockEntity(@Nullable Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return cachedBlockEntity;

		if (cachedBlockEntity == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedBlockEntity =
				new ButterCatEngineBlockEntity(CBBlockEntityTypes.BUTTER_CAT_ENGINE.get(), BlockPos.ZERO, createRenderState());
		}

		cachedBlockEntity.setLevel(clientLevel);
		return cachedBlockEntity;
	}

	private static void renderAttachments(GuiGraphics graphics, ButterCatEngineBlockEntity blockEntity) {
		var poseStack = graphics.pose();
		poseStack.pushPose();
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		AnimatedKinetics.DEFAULT_LIGHTING.applyLighting();

		try {
			poseStack.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
			UIRenderHelper.flipForGuiRender(poseStack);

			BlockState renderState = createRenderState();
			Direction facing = renderState.getValue(BlockStateProperties.HORIZONTAL_FACING);
			float angle = 0.0f;

			CachedBuffers.partialFacing(ButterCatPartials.getCatModel(blockEntity.getCatVariant()), renderState, facing)
				.rotateCenteredDegrees(angle, facing)
				.light(LightTexture.FULL_BRIGHT)
				.overlay(OverlayTexture.NO_OVERLAY)
				.renderInto(poseStack, graphics.bufferSource().getBuffer(RenderType.cutoutMipped()));

			CachedBuffers.partialFacing(
				ButterCatPartials.getButterModel(blockEntity.isInfinite(), blockEntity.getButterLevel()),
				renderState, facing)
				.rotateCenteredDegrees(angle, facing)
				.light(LightTexture.FULL_BRIGHT)
				.overlay(OverlayTexture.NO_OVERLAY)
				.renderInto(poseStack, graphics.bufferSource().getBuffer(RenderType.solid()));

			CachedBuffers.partialFacing(ButterCatPartials.getBreadModel(blockEntity.hasBread()), renderState, facing)
				.rotateCenteredDegrees(angle, facing)
				.light(LightTexture.FULL_BRIGHT)
				.overlay(OverlayTexture.NO_OVERLAY)
				.renderInto(poseStack, graphics.bufferSource().getBuffer(RenderType.solid()));

			CachedBuffers.partialFacing(ButterCatPartials.getRopeModel(blockEntity.hasBread()), renderState, facing)
				.rotateCenteredDegrees(angle, facing)
				.light(LightTexture.FULL_BRIGHT)
				.overlay(OverlayTexture.NO_OVERLAY)
				.renderInto(poseStack, graphics.bufferSource().getBuffer(RenderType.solid()));

			graphics.bufferSource().endBatch();
		} finally {
			poseStack.popPose();
			Lighting.setupFor3DItems();
		}
	}

	private static BlockState createShaftState() {
		return AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, PREVIEW_FACING.getAxis());
	}

	private static BlockState createRenderState() {
		return CBBlocks.CUTE_CAT_ON_SHAFT.get().defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, PREVIEW_FACING);
	}

	public enum PreviewKind {
		SHAFT_TO_CAT,
		CAT_TO_ENGINE
	}
}

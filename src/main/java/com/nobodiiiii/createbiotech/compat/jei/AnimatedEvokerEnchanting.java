package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class AnimatedEvokerEnchanting extends AnimatedKineticsWithEntities {

	private static final float RENDER_SCALE = 20f;
	private static final int PREVIEW_STORED_EXPERIENCE = 1;
	private static final int PREVIEW_XP_TOTAL = 1;
	private static final int DISPLAY_TRANSFORM_CYCLE = 80;
	private static final float OUTPUT_PHASE_START = 0.72f;

	private ItemStack inputCopy = ItemStack.EMPTY;
	private ItemStack outputBook = ItemStack.EMPTY;
	@Nullable
	private EvokerEnchantingChamberBlockEntity cachedBlockEntity;
	@Nullable
	private ClientLevel cachedLevel;

	public AnimatedEvokerEnchanting withRecipe(EvokerEnchantingChamberJeiRecipe recipe) {
		inputCopy = recipe.inputCopy()
			.copy();
		outputBook = recipe.outputBook()
			.copy();
		return this;
	}

	@Override
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		ClientLevel level = Minecraft.getInstance().level;
		EvokerEnchantingChamberBlockEntity blockEntity = getOrCreateBlockEntity(level);
		if (level == null || blockEntity == null)
			return;

		updatePreviewState(blockEntity);

		var poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(xOffset, yOffset, 100);
		poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
		poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));

		GuiGameElement.of(blockEntity)
			.lighting(DEFAULT_LIGHTING)
			.scale(RENDER_SCALE)
			.render(graphics);

		poseStack.popPose();
	}

	private void updatePreviewState(EvokerEnchantingChamberBlockEntity blockEntity) {
		ItemStack displayedItem = getDisplayedItem(AnimationTickHolder.getRenderTime());
		boolean casting = !displayedItem.isEmpty() && ItemStack.isSameItemSameTags(displayedItem, inputCopy);
		ItemStack heldItem = casting ? displayedItem : ItemStack.EMPTY;
		ItemStack pendingOutput = casting ? ItemStack.EMPTY : displayedItem;
		int xpRemaining = casting ? PREVIEW_XP_TOTAL : 0;
		CompoundTag tag = new CompoundTag();
		tag.putInt("StoredExperience", PREVIEW_STORED_EXPERIENCE);
		tag.putInt("XpRemaining", xpRemaining);
		tag.putInt("XpTotal", PREVIEW_XP_TOTAL);
		tag.putBoolean("WaitingForExperience", false);
		if (!heldItem.isEmpty())
			tag.put("HeldItem", heldItem.serializeNBT());
		if (!pendingOutput.isEmpty())
			tag.put("PendingOutput", pendingOutput.serializeNBT());
		blockEntity.load(tag);
	}

	private ItemStack getDisplayedItem(float renderTime) {
		if (inputCopy.isEmpty())
			return outputBook;
		if (outputBook.isEmpty())
			return inputCopy;

		float cycle = (renderTime % DISPLAY_TRANSFORM_CYCLE) / DISPLAY_TRANSFORM_CYCLE;
		return cycle >= OUTPUT_PHASE_START ? outputBook : inputCopy;
	}

	private @Nullable EvokerEnchantingChamberBlockEntity getOrCreateBlockEntity(@Nullable Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return cachedBlockEntity;

		if (cachedBlockEntity == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedBlockEntity = new EvokerEnchantingChamberBlockEntity(BlockPos.ZERO, createRenderState());
		}

		cachedBlockEntity.setLevel(clientLevel);
		return cachedBlockEntity;
	}

	private static BlockState createRenderState() {
		return CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()
			.defaultBlockState()
			.setValue(EvokerEnchantingChamberBlock.FACING, Direction.SOUTH)
			.setValue(EvokerEnchantingChamberBlock.HALF, DoubleBlockHalf.LOWER);
	}
}

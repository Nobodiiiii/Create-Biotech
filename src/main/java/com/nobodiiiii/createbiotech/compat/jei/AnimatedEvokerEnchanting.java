package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipClientServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class AnimatedEvokerEnchanting extends AnimatedKineticsWithEntities {

	private static final float RENDER_SCALE = 20f;
	private static final double RENDER_Y_OFFSET_BLOCKS = 2.0d;
	private static final int PREVIEW_STORED_FLUID = 1;
	private static final int PREVIEW_FLUID_TOTAL = 1;
	private static final int DISPLAY_TRANSFORM_CYCLE = 80;
	private static final float OUTPUT_PHASE_START = 0.72f;

	private ItemStack inputCopy = ItemStack.EMPTY;
	private ItemStack outputBook = ItemStack.EMPTY;
	@Nullable
	private EvokerEnchantingChamberBlockEntity cachedBlockEntity;
	@Nullable
	private ClientLevel cachedLevel;
	private final JeiSceneParticles enchantParticles = new JeiSceneParticles();

	public AnimatedEvokerEnchanting withItems(ItemStack input, ItemStack output) {
		inputCopy = input.copy();
		outputBook = output.copy();
		return this;
	}

	@Override
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		ClientLevel level = Minecraft.getInstance().level;
		EvokerEnchantingChamberBlockEntity blockEntity = getOrCreateBlockEntity(level);
		if (level == null || blockEntity == null)
			return;

		updatePreviewState(blockEntity);

		scene(graphics, xOffset, yOffset, () -> {
			GuiGameElement.of(blockEntity)
				.lighting(DEFAULT_LIGHTING)
				.atLocal(0.0d, RENDER_Y_OFFSET_BLOCKS, 0.0d)
				.scale(RENDER_SCALE)
				.render(graphics);

			renderStraightEnchantParticles(graphics, level, blockEntity);
		});
	}

	private void updatePreviewState(EvokerEnchantingChamberBlockEntity blockEntity) {
		ItemStack displayedItem = getDisplayedItem(AnimationTickHolder.getRenderTime());
		boolean casting = !displayedItem.isEmpty() && ItemStack.isSameItemSameTags(displayedItem, inputCopy);
		ItemStack heldItem = casting ? displayedItem : ItemStack.EMPTY;
		ItemStack pendingOutput = casting ? ItemStack.EMPTY : displayedItem;
		int fluidRemaining = casting ? PREVIEW_FLUID_TOTAL : 0;
		blockEntity.setRenderPreviewState(heldItem, pendingOutput, PREVIEW_STORED_FLUID, fluidRemaining,
			PREVIEW_FLUID_TOTAL, false);
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

	private void renderStraightEnchantParticles(GuiGraphics graphics, ClientLevel level,
		EvokerEnchantingChamberBlockEntity blockEntity) {
		syncStraightEnchantParticles(level, blockEntity);
		enchantParticles.render(graphics, RENDER_SCALE, 0.0d, RENDER_Y_OFFSET_BLOCKS, 0.0d);
	}

	/**
	 * Emits exactly what {@link EvokerEnchantingChamberBlockEntity} emits in the
	 * world while a spell is being cast.
	 */
	private void syncStraightEnchantParticles(ClientLevel level, EvokerEnchantingChamberBlockEntity blockEntity) {
		if (!enchantParticles.advanceOnce(level) || !blockEntity.isCastingSpell())
			return;

		EvokerEnchantingChamberBlockEntity.forEachStraightEnchantParticle(level, blockEntity.getBlockPos(),
			blockEntity.getBlockState(), (x, y, z, dx, dy, dz) -> enchantParticles.add(
				CatnipClientServices.CLIENT_HOOKS.createParticleFromData(CBParticleTypes.STRAIGHT_ENCHANT.get(),
					level, x, y, z, dx, dy, dz)));
	}

	private static BlockState createRenderState() {
		return CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()
			.defaultBlockState()
			.setValue(EvokerEnchantingChamberBlock.FACING, Direction.SOUTH)
			.setValue(EvokerEnchantingChamberBlock.HALF, DoubleBlockHalf.LOWER);
	}
}

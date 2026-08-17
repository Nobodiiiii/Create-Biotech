package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a slime the way a captured slime item looks in an inventory slot, so the
 * recipe artwork and the item it produces read as the same object.
 *
 * <p>The entity fills the box it is given rather than using a fixed scale, which
 * keeps a magma cube and a slime the same apparent size despite their differing
 * model bounds.
 */
public class SlimeEntityDrawable implements IDrawable {

	private static final ItemStack ENTITY_ITEM_TRANSFORM = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());

	private final int width;
	private final int height;
	private final CachedRenderEntity<Slime, Void> renderSlime;

	public SlimeEntityDrawable(int width, int height, int slimeSize, EntityType<? extends Slime> entityType) {
		this.width = width;
		this.height = height;
		this.renderSlime = CachedRenderEntity.<Slime>of(entityType::create)
			.configure(slime -> slime.setSize(slimeSize, false));
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		@Nullable
		Slime slime = renderSlime.get(Minecraft.getInstance().level);
		if (slime == null)
			return;

		GuiEntityItemElement.of(slime)
			.blockCentered()
			.autoScale(1.0f)
			.renderInGuiBox(guiGraphics, ENTITY_ITEM_TRANSFORM, xOffset, yOffset, width, height);
	}
}

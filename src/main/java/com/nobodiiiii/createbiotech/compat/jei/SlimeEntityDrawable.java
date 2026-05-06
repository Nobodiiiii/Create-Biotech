package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

public class SlimeEntityDrawable implements IDrawable {
	private final int width;
	private final int height;
	private final int scale;
	private final int slimeSize;
	private final float angleX;
	private final float angleY;
	private final int renderYOffset;
	private final EntityType<? extends Slime> entityType;

	@Nullable
	private Slime cachedSlime;
	@Nullable
	private Level cachedLevel;

	public SlimeEntityDrawable(int width, int height, int scale, int slimeSize, float angleX, float angleY,
		EntityType<? extends Slime> entityType) {
		this(width, height, scale, slimeSize, angleX, angleY, 2, entityType);
	}

	public SlimeEntityDrawable(int width, int height, int scale, int slimeSize, float angleX, float angleY,
		int renderYOffset, EntityType<? extends Slime> entityType) {
		this.width = width;
		this.height = height;
		this.scale = scale;
		this.slimeSize = slimeSize;
		this.angleX = angleX;
		this.angleY = angleY;
		this.renderYOffset = renderYOffset;
		this.entityType = entityType;
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
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null)
			return;

		Slime slime = getOrCreateSlime(level);
		if (slime == null)
			return;

		InventoryScreen.renderEntityInInventoryFollowsAngle(guiGraphics, xOffset + width / 2, yOffset + height + renderYOffset, scale,
			angleX, angleY, slime);
	}

	private Slime getOrCreateSlime(Level level) {
		if (cachedSlime != null && cachedLevel == level && cachedSlime.getType() == entityType)
			return cachedSlime;

		Slime slime = entityType.create(level);
		if (slime == null)
			return null;
		slime.setNoAi(true);
		slime.setSize(slimeSize, false);
		slime.tickCount = 0;
		cachedLevel = level;
		cachedSlime = slime;
		return slime;
	}
}

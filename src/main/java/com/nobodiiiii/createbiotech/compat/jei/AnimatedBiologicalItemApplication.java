package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.Level;

/**
 * The creature a biological item application is performed on, standing in a
 * Create-style JEI scene.
 */
public class AnimatedBiologicalItemApplication extends AnimatedKineticsWithEntities {

	private static final int ENTITY_SCALE = 20;
	private static final double ENTITY_LOCAL_X = 0.5d;
	private static final double ENTITY_LOCAL_Y = 0.0d;
	private static final double ENTITY_LOCAL_Z = 0.5d;

	private final CachedRenderEntity<Chicken, EntityType<? extends Chicken>> renderEntity =
		CachedRenderEntity.keyed((Level level, EntityType<? extends Chicken> entityType) -> entityType.create(level));

	private EntityType<? extends Chicken> entityType = EntityType.CHICKEN;

	public AnimatedBiologicalItemApplication withEntityType(EntityType<? extends Chicken> entityType) {
		this.entityType = entityType;
		return this;
	}

	@Override
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		@Nullable
		Chicken entity = renderEntity.get(Minecraft.getInstance().level, entityType);
		if (entity == null)
			return;

		scene(graphics, xOffset, yOffset, () -> entityElement(entity)
			.atLocal(ENTITY_LOCAL_X, ENTITY_LOCAL_Y, ENTITY_LOCAL_Z)
			.scale(ENTITY_SCALE)
			.packedLight(LightTexture.FULL_BRIGHT)
			.partialTicks(1.0f)
			.render(graphics));
	}
}

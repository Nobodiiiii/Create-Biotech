package com.nobodiiiii.createbiotech.client.particle;

import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterInkParticleOption;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;

/**
 * Squid ink that stops sinking a set distance below where it was released.
 *
 * <p>Vanilla ink keeps accelerating for as long as it is in open air and lives
 * for a random 6 to 30 ticks, so a printer's trail reaches anywhere from a
 * quarter of a block to nearly three blocks down — deep enough to sink past the
 * depot it is printing onto. Capping by distance rather than by lifetime keeps
 * the trail inside the machine however long any individual particle happened to
 * roll.
 *
 * <p>Vanilla's {@code SquidInkParticle} constructor is package-private on 1.20.1,
 * so its behaviour is reproduced here rather than subclassed.
 */
public class SquidPrinterInkParticle extends SimpleAnimatedParticle {

	private static final float INK_QUAD_SIZE = 0.5f;
	private static final float INK_FRICTION = 0.92f;
	private static final double AIR_GRAVITY = 0.0074d;

	private final double vanishBelowY;

	protected SquidPrinterInkParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
		double zSpeed, double fallLimit, SpriteSet sprites) {
		super(level, x, y, z, sprites, 0.0f);
		friction = INK_FRICTION;
		quadSize = INK_QUAD_SIZE;
		setAlpha(1.0f);
		setColor(255.0f, 255.0f, 255.0f);
		lifetime = (int) (quadSize * 12.0f / (Math.random() * 0.8d + 0.2d));
		setSpriteFromAge(sprites);
		hasPhysics = false;
		xd = xSpeed;
		yd = ySpeed;
		zd = zSpeed;
		vanishBelowY = y - fallLimit;
	}

	@Override
	public void tick() {
		super.tick();
		if (removed)
			return;

		setSpriteFromAge(sprites);
		if (age > lifetime / 2)
			setAlpha(1.0f - (age - lifetime / 2) / (float) lifetime);
		if (level.getBlockState(BlockPos.containing(x, y, z)).isAir())
			yd -= AIR_GRAVITY;
		if (y <= vanishBelowY)
			remove();
	}

	public static class Provider implements ParticleProvider<SquidPrinterInkParticleOption> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SquidPrinterInkParticleOption options, ClientLevel level, double x, double y,
			double z, double dx, double dy, double dz) {
			return new SquidPrinterInkParticle(level, x, y, z, dx, dy, dz, options.fallLimit(), sprites);
		}
	}
}

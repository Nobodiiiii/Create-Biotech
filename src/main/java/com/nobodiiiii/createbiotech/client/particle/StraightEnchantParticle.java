package com.nobodiiiii.createbiotech.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class StraightEnchantParticle extends TextureSheetParticle {

	private final SpriteSet sprites;
	private final double startX;
	private final double startY;
	private final double startZ;
	private final double deltaX;
	private final double deltaY;
	private final double deltaZ;
	private final int lastVisibleAge;

	protected StraightEnchantParticle(ClientLevel level, double x, double y, double z, double dx, double dy,
		double dz, SpriteSet sprites) {
		super(level, x, y, z);
		this.sprites = sprites;
		startX = x;
		startY = y;
		startZ = z;
		deltaX = dx;
		deltaY = dy;
		deltaZ = dz;
		hasPhysics = false;
		gravity = 0.0f;
		friction = 1.0f;
		lifetime = 10 + random.nextInt(5);
		lastVisibleAge = Math.max(1, lifetime - 1);
		quadSize = 0.1f * (0.7f + random.nextFloat() * 0.4f);
		float tint = random.nextFloat() * 0.6f + 0.4f;
		rCol = 0.9f * tint;
		gCol = 0.9f * tint;
		bCol = tint;
		alpha = 0.95f;
		setPos(x, y, z);
		xo = x;
		yo = y;
		zo = z;
		pickSprite(sprites);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;

		age++;
		if (age > lastVisibleAge) {
			remove();
			return;
		}

		float progress = Mth.clamp((float) age / (float) lastVisibleAge, 0.0f, 1.0f);
		setPos(startX + deltaX * progress, startY + deltaY * progress, startZ + deltaZ * progress);
		setSpriteFromAge(sprites);
	}

	@Override
	public int getLightColor(float partialTick) {
		return 0xF000F0;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
			double dx, double dy, double dz) {
			return new StraightEnchantParticle(level, x, y, z, dx, dy, dz, sprites);
		}
	}
}

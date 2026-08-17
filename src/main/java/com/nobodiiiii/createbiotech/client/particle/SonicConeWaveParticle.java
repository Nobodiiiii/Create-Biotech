package com.nobodiiiii.createbiotech.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicConeWaveParticleOption;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A scaled-up, directionally oriented shriek glyph whose radius follows the cone wave.
 *
 * <p>1.20.1's {@code ShriekParticle} has a package-private constructor and keeps its rotated-quad
 * emitter private, so the two mirrored quads are reproduced here on top of
 * {@link TextureSheetParticle}.
 */
public class SonicConeWaveParticle extends TextureSheetParticle {

	private static final int LIFETIME = 16;
	private static final float HALF_ANGLE = (float) Math.toRadians(60.0d);
	private static final Vector3f[] QUAD_CORNERS = {
		new Vector3f(-1.0f, -1.0f, 0.0f),
		new Vector3f(-1.0f, 1.0f, 0.0f),
		new Vector3f(1.0f, 1.0f, 0.0f),
		new Vector3f(1.0f, -1.0f, 0.0f)
	};

	private final Vec3 origin;
	private final Vec3 forward;
	private final float range;
	private final float maxWaveRadius;
	private final Quaternionf waveRotation;
	private final Quaternionf reverseWaveRotation;
	private int delay;

	private SonicConeWaveParticle(ClientLevel level, double x, double y, double z,
		SonicConeWaveParticleOption option, SpriteSet sprites) {
		super(level, x, y, z, 0.0d, 0.0d, 0.0d);
		origin = new Vec3(x, y, z);
		Vector3f optionDirection = option.direction();
		Vec3 suppliedDirection = new Vec3(optionDirection.x, optionDirection.y, optionDirection.z);
		forward = suppliedDirection.lengthSqr() < 1.0e-6d
			? new Vec3(0.0d, 0.0d, 1.0d)
			: suppliedDirection.normalize();
		range = option.range();
		maxWaveRadius = range * Mth.sin(HALF_ANGLE);
		waveRotation = new Quaternionf().rotationTo(
			new Vector3f(0.0f, 0.0f, 1.0f),
			new Vector3f((float) forward.x, (float) forward.y, (float) forward.z));
		reverseWaveRotation = new Quaternionf(waveRotation)
			.mul(new Quaternionf().rotationY((float) Math.PI));
		delay = option.delay();
		lifetime = LIFETIME;
		gravity = 0.0f;
		hasPhysics = false;
		xd = 0.0d;
		yd = 0.0d;
		zd = 0.0d;
		setColor(1.0f, 1.0f, 1.0f);
		pickSprite(sprites);
		setAlpha(1.0f);
	}

	@Override
	public void tick() {
		if (delay > 0) {
			delay--;
			return;
		}

		super.tick();
		if (removed)
			return;

		double distance = range * age / lifetime;
		Vec3 waveCenter = origin.add(forward.scale(distance));
		setPos(waveCenter.x, waveCenter.y, waveCenter.z);
	}

	@Override
	public float getQuadSize(float partialTick) {
		// Keep the expanded glyph on the same interpolated wavefront as its position.
		float progress = Mth.clamp((age - 1.0f + partialTick) / lifetime, 0.0f, 1.0f);
		return maxWaveRadius * progress;
	}

	@Override
	public int getLightColor(float partialTick) {
		return 240;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTick) {
		if (delay > 0)
			return;

		alpha = 1.0f - Mth.clamp((age + partialTick) / lifetime, 0.0f, 1.0f);
		renderRotatedQuad(buffer, camera, waveRotation, partialTick);
		renderRotatedQuad(buffer, camera, reverseWaveRotation, partialTick);
	}

	private void renderRotatedQuad(VertexConsumer buffer, Camera camera, Quaternionf rotation, float partialTick) {
		Vec3 cameraPosition = camera.getPosition();
		float x = (float) (Mth.lerp(partialTick, xo, this.x) - cameraPosition.x());
		float y = (float) (Mth.lerp(partialTick, yo, this.y) - cameraPosition.y());
		float z = (float) (Mth.lerp(partialTick, zo, this.z) - cameraPosition.z());
		float quadSize = getQuadSize(partialTick);
		int packedLight = getLightColor(partialTick);

		Vector3f[] corners = new Vector3f[QUAD_CORNERS.length];
		for (int i = 0; i < corners.length; i++) {
			corners[i] = new Vector3f(QUAD_CORNERS[i]);
			corners[i].rotate(rotation);
			corners[i].mul(quadSize);
			corners[i].add(x, y, z);
		}

		makeCornerVertex(buffer, corners[0], getU1(), getV1(), packedLight);
		makeCornerVertex(buffer, corners[1], getU1(), getV0(), packedLight);
		makeCornerVertex(buffer, corners[2], getU0(), getV0(), packedLight);
		makeCornerVertex(buffer, corners[3], getU0(), getV1(), packedLight);
	}

	private void makeCornerVertex(VertexConsumer buffer, Vector3f corner, float u, float v, int packedLight) {
		buffer.vertex(corner.x(), corner.y(), corner.z())
			.uv(u, v)
			.color(rCol, gCol, bCol, alpha)
			.uv2(packedLight)
			.endVertex();
	}

	public static class Provider implements ParticleProvider<SonicConeWaveParticleOption> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SonicConeWaveParticleOption option, ClientLevel level,
			double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			return new SonicConeWaveParticle(level, x, y, z, option, sprites);
		}
	}
}

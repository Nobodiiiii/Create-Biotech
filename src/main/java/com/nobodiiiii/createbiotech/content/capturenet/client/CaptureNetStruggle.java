package com.nobodiiiii.createbiotech.content.capturenet.client;

/** Stateless variation of the authored struggle: stable between frames and distinct per creature. */
final class CaptureNetStruggle {
	private CaptureNetStruggle() {}

	static Motion sample(float ticks, int seed, float weight) {
		float length = CaptureNetAnimation.OCCUPIED_LENGTH;
		float clock = ticks / 20 + random(seed, 0) * length;
		int cycle = (int) Math.floor(clock / length);
		int cycleSeed = seed ^ cycle * 0x9E3779B9;
		float delay = 0.15f * random(cycleSeed, 1);
		float speed = 1.1f + 0.45f * random(cycleSeed, 2);
		float seconds = Math.clamp((clock - cycle * length - delay) * speed, 0, length);
		// Both tracks rest around the midpoint and cycle boundary, so changing the seed cannot pop.
		int burstSeed = cycleSeed ^ (seconds < length / 2 ? 0 : 0x6A09E667);
		float amplitude = weight * (0.7f + 0.3f * random(burstSeed, 3));
		float pitch = CaptureNetAnimation.sample(CaptureNetAnimation.OCCUPIED_BAG_ROTATION, seconds, 0)
			* amplitude * direction(burstSeed, 4);
		float[][] position = CaptureNetAnimation.OCCUPIED_BAG_POSITION;
		return new Motion(
			CaptureNetAnimation.sample(position, seconds, 0) * amplitude * signedScale(burstSeed, 5),
			CaptureNetAnimation.sample(position, seconds, 1) * amplitude * signedScale(burstSeed, 7),
			CaptureNetAnimation.sample(position, seconds, 2) * amplitude * signedScale(burstSeed, 9), pitch);
	}

	private static float signedScale(int seed, int salt) {
		return (0.5f + 0.5f * random(seed, salt)) * direction(seed, salt + 1);
	}

	private static float direction(int seed, int salt) {
		return random(seed, salt) < 0.5f ? -1 : 1;
	}

	private static float random(int seed, int salt) {
		int value = seed + salt * 0x9E3779B9;
		value = (value ^ value >>> 16) * 0x7FEB352D;
		value = (value ^ value >>> 15) * 0x846CA68B;
		value ^= value >>> 16;
		return (value >>> 8) * 0x1.0p-24f;
	}

	/** x is the far-wall sway; the renderer reduces it towards the anchored mouth. */
	record Motion(float x, float y, float z, float pitch) {}
}

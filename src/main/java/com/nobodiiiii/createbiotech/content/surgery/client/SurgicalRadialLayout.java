package com.nobodiiiii.createbiotech.content.surgery.client;

/** Pixel layout shared by the surgical wheels and their headless art exporter. */
public final class SurgicalRadialLayout {
	public static final int SLOT_HALF_SIZE = 11;
	public static final int ICON_OFFSET = 3;
	public static final double SLOT_RADIUS = 54.0d;
	private static final double INNER_RADIUS_SQR = 24.0d * 24.0d;
	private static final double OUTER_RADIUS_SQR = 82.0d * 82.0d;

	public static int slotX(int slot, int count, double fade) {
		return (int) Math.floor(Math.cos(angle(slot, count)) * radius(fade)) - SLOT_HALF_SIZE;
	}

	public static int slotY(int slot, int count, double fade) {
		return (int) Math.floor(Math.sin(angle(slot, count)) * radius(fade)) - SLOT_HALF_SIZE;
	}

	public static int hoveredSlot(double x, double y, int count) {
		double distanceSqr = x * x + y * y;
		if (count <= 0 || distanceSqr < INNER_RADIUS_SQR || distanceSqr > OUTER_RADIUS_SQR)
			return -1;
		double fromTop = (Math.toDegrees(Math.atan2(y, x)) + 450.0d) % 360.0d;
		double slotAngle = 360.0d / count;
		return (int) Math.floor((fromTop + slotAngle / 2.0d) / slotAngle) % count;
	}

	private static double angle(int slot, int count) {
		return Math.toRadians(slot * 360.0d / count - 90.0d);
	}

	private static double radius(double fade) {
		return SLOT_RADIUS - 10.0d * (1.0d - fade) * (1.0d - fade);
	}

	private SurgicalRadialLayout() {}
}

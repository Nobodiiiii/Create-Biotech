package com.nobodiiiii.createbiotech.content.slimemimic.client;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicCubeGeometry;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicDeathGeometryPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Sends the one death-pose geometry report that a dedicated server cannot derive from models. */
@OnlyIn(Dist.CLIENT)
public final class SlimeMimicDeathClient {
	private static final Map<LivingEntity, Boolean> REPORTED = new WeakHashMap<>();

	private SlimeMimicDeathClient() {}

	public static boolean hasReported(LivingEntity entity) {
		return entity != null && REPORTED.containsKey(entity);
	}

	public static void report(LivingEntity entity, List<SlimeMimicCubeGeometry> cubes) {
		if (entity == null || !entity.isDeadOrDying() || cubes == null || cubes.isEmpty()
			|| cubes.size() > SurgicalAssembly.MAX_CUBES || REPORTED.putIfAbsent(entity, Boolean.TRUE) != null)
			return;
		CBPackets.sendToServer(new SlimeMimicDeathGeometryPacket(entity.getId(), cubes));
	}
}

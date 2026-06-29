package com.nobodiiiii.createbiotech.content.experience;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

public class ExperienceOpenPipeEffectHandler implements OpenPipeEffectHandler {
	private static final Map<Level, LevelOutputs> OUTPUTS = new WeakHashMap<>();

	public static void register() {
		ExperienceOpenPipeEffectHandler handler = new ExperienceOpenPipeEffectHandler();
		OpenPipeEffectHandler.REGISTRY.register(CBFluids.EXPERIENCE.get(), handler);
		OpenPipeEffectHandler.REGISTRY.register(CBFluids.EXPERIENCE_FLOWING.get(), handler);
	}

	@Override
	public void apply(Level level, AABB area, FluidStack fluid) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		if (!ExperienceFluidHelper.isPrimaryExperience(fluid))
			return;

		int xp = ExperienceFluidHelper.fluidAmountToXp(fluid.getAmount());
		if (xp <= 0)
			return;

		emit(serverLevel, area, xp);
	}

	private static void emit(ServerLevel level, AABB area, int xp) {
		long gameTime = level.getGameTime();
		AreaKey areaKey = AreaKey.from(area);
		LevelOutputs outputs = OUTPUTS.computeIfAbsent(level, ignored -> new LevelOutputs());
		ExperienceOrb existing = outputs.get(areaKey, gameTime);
		if (existing != null && existing.isAlive()) {
			existing.value = addXp(existing.value, xp);
			return;
		}

		Vec3 pos = area.getCenter();
		ExperienceOrb orb = new ExperienceOrb(level, pos.x, pos.y, pos.z, xp);
		level.addFreshEntity(orb);
		outputs.put(areaKey, orb);
	}

	private static int addXp(int current, int added) {
		long total = (long) current + added;
		return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
	}

	private record AreaKey(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		private static AreaKey from(AABB area) {
			return new AreaKey(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ);
		}
	}

	private static class LevelOutputs {
		private final Map<AreaKey, WeakReference<ExperienceOrb>> orbs = new HashMap<>();
		private long tick = Long.MIN_VALUE;

		private ExperienceOrb get(AreaKey area, long gameTime) {
			prepare(gameTime);
			WeakReference<ExperienceOrb> reference = orbs.get(area);
			return reference == null ? null : reference.get();
		}

		private void put(AreaKey area, ExperienceOrb orb) {
			orbs.put(area, new WeakReference<>(orb));
		}

		private void prepare(long gameTime) {
			if (tick == gameTime)
				return;
			tick = gameTime;
			orbs.clear();
		}
	}
}

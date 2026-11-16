package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.press.PressingBehaviour;

import net.minecraft.util.Mth;

/** Value-only animation data captured on the client thread, never rebuilt by a Visual. */
record ChamberPressRenderState(Map<Long, Phase> presses, Map<Long, Long> pressByPackager,
	@Nullable Phase synchronizedMaster, boolean workingMaster) {
	static final ChamberPressRenderState EMPTY = new ChamberPressRenderState(Map.of(), Map.of(), null, false);

	ChamberPressRenderState {
		presses = Map.copyOf(presses);
		pressByPackager = Map.copyOf(pressByPackager);
	}

	static ChamberPressRenderState capture(CreeperBlastChamberBlockEntity chamber) {
		if (!chamber.structureValid || chamber.structureOrigin == null) return EMPTY;
		var loaded = ChamberProcessing.getMechanicalPresses(chamber);
		if (loaded.isEmpty()) return EMPTY;
		var master = ChamberProcessing.getMasterPress(chamber, loaded);
		Phase shared = master == null || ChamberProcessing.hasUnworkablePresses(chamber, loaded)
			? null : Phase.capture(master.getPressingBehaviour());
		Map<Long, Phase> presses = new HashMap<>();
		for (var press : loaded) {
			var behaviour = press.getPressingBehaviour();
			Phase own = Phase.capture(behaviour);
			Phase rendered = shared == null || behaviour.mode == null ? own
				: new Phase(shared.running, shared.previousTicks, shared.ticks, own.headOffset);
			presses.put(press.getBlockPos().asLong(), rendered);
		}
		Map<Long, Long> pressByPackager = new HashMap<>();
		chamber.cachedPressByPackager.forEach((packager, press) -> pressByPackager.put(packager, press.asLong()));
		return new ChamberPressRenderState(presses, pressByPackager,
			shared, master != null && master.getSpeed() != 0);
	}

	float pressOffset(long packager, float partialTick) {
		Long position = pressByPackager.get(packager);
		Phase phase = position == null ? null : presses.get(position);
		return phase == null ? 0 : phase.offset(partialTick);
	}

	float creeperOffset(long packager, float partialTick) {
		if (!workingMaster) return 0;
		if (synchronizedMaster != null)
			return synchronizedMaster.progress(partialTick) * PressingBehaviour.Mode.WORLD.headOffset;
		return pressOffset(packager, partialTick);
	}

	record Phase(boolean running, int previousTicks, int ticks, float headOffset) {
		static Phase capture(PressingBehaviour behaviour) {
			return new Phase(behaviour.mode != null && behaviour.running, behaviour.prevRunningTicks,
				behaviour.runningTicks, behaviour.mode == null ? 0 : behaviour.mode.headOffset);
		}

		float progress(float partialTick) {
			return progress(running, previousTicks, ticks, partialTick);
		}

		float offset(float partialTick) {
			return progress(partialTick) * headOffset;
		}

		static float progress(boolean running, int previousTicks, int ticks, float partialTick) {
			if (!running) return 0;
			int absoluteTicks = Math.abs(ticks);
			float renderedTick = Mth.lerp(partialTick, previousTicks, absoluteTicks);
			if (absoluteTicks < 160)
				return (float) Mth.clamp(Math.pow(renderedTick / 240f * 2f, 3), 0d, 1d);
			return Mth.clamp((240f - renderedTick) / 240f * 3f, 0f, 1f);
		}
	}
}

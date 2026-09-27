package com.nobodiiiii.createbiotech.entity.trait;

import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;

/** Hearing only updates the last audible position of the already selected target. */
public final class BionicVibrationSense implements VibrationSystem, VibrationSystem.User {
	private final SlimeBionicEntity entity;
	private final Data data = new Data();
	private final DynamicGameEventListener<Listener> listener = new DynamicGameEventListener<>(new Listener(this));
	@Nullable private Entity heardTarget;
	@Nullable private Vec3 heardPosition;
	private long heardAt;

	public BionicVibrationSense(SlimeBionicEntity entity) { this.entity = entity; }
	public DynamicGameEventListener<Listener> listener() { return listener; }
	@Override public Data getVibrationData() { return data; }
	@Override public User getVibrationUser() { return this; }
	@Override public int getListenerRadius() { return 12; }
	@Override public PositionSource getPositionSource() { return new EntityPositionSource(entity, entity.getEyeHeight()); }

	private boolean tracking(@Nullable Entity source) {
		return source != null && source == entity.getTarget() && source.isAlive() && !source.isSilent()
			&& entity.isAlive() && !entity.isNoAi() && !entity.isOrderedToSit()
			&& entity.getBionicTraits().has(BionicTrait.VIBRATION_SENSE);
	}
	@Override public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, GameEvent.Context context) {
		return tracking(context.sourceEntity());
	}
	@Override public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event,
		@Nullable Entity source, @Nullable Entity projectileOwner, float distance) {
		if (!tracking(source)) return;
		heardTarget = source;
		heardPosition = Vec3.atBottomCenterOf(pos);
		heardAt = level.getGameTime();
	}
	public void tick() {
		VibrationSystem.Ticker.tick(entity.level(), data, this);
		if (!tracking(heardTarget)) { heardTarget = null; heardPosition = null; }
	}
	@Nullable public Vec3 lastHeardPosition(Entity target) {
		return tracking(target) && target == heardTarget && entity.level().getGameTime() - heardAt <= 40
			? heardPosition : null;
	}
}

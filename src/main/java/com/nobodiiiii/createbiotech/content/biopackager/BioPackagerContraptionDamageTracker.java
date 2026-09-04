package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllDamageTypes;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.slf4j.Logger;

public final class BioPackagerContraptionDamageTracker {
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final ThreadLocal<Deque<DamageContext>> ACTIVE_DAMAGE =
		ThreadLocal.withInitial(ArrayDeque::new);

	private BioPackagerContraptionDamageTracker() {}

	public static DamageContextScope openDamageContext(AbstractContraptionEntity contraptionEntity) {
		return createDamageContextScope(null, contraptionEntity);
	}

	@Nullable
	public static DamageContextScope openDamageContext(Entity target,
		AbstractContraptionEntity contraptionEntity) {
		if (!(target instanceof LivingEntity livingTarget))
			return null;
		return createDamageContextScope(livingTarget, contraptionEntity);
	}

	private static DamageContextScope createDamageContextScope(@Nullable LivingEntity target,
		AbstractContraptionEntity contraptionEntity) {
		DamageContext context = new DamageContext(target, contraptionEntity);
		ACTIVE_DAMAGE.get().push(context);
		return new DamageContextScope(context, Thread.currentThread());
	}

	private static boolean closeDamageContext(DamageContext expected, Thread owner) {
		if (Thread.currentThread() != owner) {
			LOGGER.error("Contraption damage context was closed from a different thread; refusing to mutate either stack");
			return false;
		}

		Deque<DamageContext> stack = ACTIVE_DAMAGE.get();
		if (stack.peek() != expected) {
			int leakedDepth = stack.size();
			stack.clear();
			ACTIVE_DAMAGE.remove();
			LOGGER.error("Contraption damage context stack was unbalanced; cleared {} frame(s)", leakedDepth);
			return true;
		}

		stack.pop();
		if (stack.isEmpty())
			ACTIVE_DAMAGE.remove();
		return true;
	}

	@Nullable
	public static AbstractContraptionEntity resolveDamagingContraption(LivingEntity target, DamageSource source) {
		if (source == null)
			return null;

		AbstractContraptionEntity contraptionFromSource = asContraption(source.getDirectEntity());
		if (contraptionFromSource == null)
			contraptionFromSource = asContraption(source.getEntity());
		if (contraptionFromSource != null && !contraptionFromSource.isRemoved())
			return contraptionFromSource;

		if (!isContraptionDamageType(source))
			return null;

		DamageContext context = ACTIVE_DAMAGE.get()
			.peek();
		if (context == null)
			return null;
		if (context.target() != null && context.target() != target)
			return null;
		AbstractContraptionEntity contraptionEntity = context.contraptionEntity();
		if (contraptionEntity == null || contraptionEntity.isRemoved())
			return null;
		return contraptionEntity;
	}

	private static boolean isContraptionDamageType(DamageSource source) {
		return source.is(AllDamageTypes.CRUSH)
			|| source.is(AllDamageTypes.DRILL)
			|| source.is(AllDamageTypes.ROLLER)
			|| source.is(AllDamageTypes.SAW)
			|| source.is(AllDamageTypes.RUN_OVER);
	}

	@Nullable
	private static AbstractContraptionEntity asContraption(@Nullable Entity entity) {
		return entity instanceof AbstractContraptionEntity contraptionEntity ? contraptionEntity : null;
	}

	public static final class DamageContextScope implements AutoCloseable {
		private final DamageContext context;
		private final Thread owner;
		private boolean closed;

		private DamageContextScope(DamageContext context, Thread owner) {
			this.context = context;
			this.owner = owner;
		}

		@Override
		public void close() {
			if (closed)
				return;
			closed = closeDamageContext(context, owner);
		}
	}

	private record DamageContext(@Nullable LivingEntity target, AbstractContraptionEntity contraptionEntity) {}
}

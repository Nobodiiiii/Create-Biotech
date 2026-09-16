package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.BlockCenteredSpawnableRenderedLivingEntityItem;
import com.nobodiiiii.createbiotech.network.ContainedEntityHandoffPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CapturedSmallSlimeItem extends BlockCenteredSpawnableRenderedLivingEntityItem<Slime> {
	private static final float ITEM_RENDER_SCALE = 1.5f;
	private static final String MATERIALIZED_REPLACEMENT_TAG = "CreateBiotechMaterializedReplacement";

	public CapturedSmallSlimeItem(Properties properties) {
		super(properties, EntityType.SLIME, CapturedSmallSlimeItem::configureSlime, ITEM_RENDER_SCALE);
	}

	@Override
	public boolean hasCustomEntity(ItemStack stack) {
		return !stack.isEmpty();
	}

	@Override
	public Entity createEntity(Level level, Entity location, ItemStack stack) {
		if (stack.isEmpty())
			return null;

		Vec3 position = location.position();
		Vec3 motion = location.getDeltaMovement();
		int count = stack.getCount();
		List<Slime> created = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Slime slime = BasinEntityProcessing.createSmallSlime(level,
				position.add(getDropSpread(i, count)), motion);
			if (slime == null)
				return null;
			created.add(slime);
		}
		Slime firstSlime = created.get(0);

		if (level instanceof ServerLevel serverLevel) {
			BlockPos sourcePos = location.blockPosition();
			ContainedEntityHandoffPacket.announce(serverLevel, firstSlime, sourcePos, sourcePos,
				location.getId(), BasinEntityProcessing.getContainedSlimeAnimationPhase(level, sourcePos, 0, 0));
			List<Slime> added = new ArrayList<>(Math.max(0, count - 1));
			for (int i = 1; i < count; i++) {
				Slime slime = created.get(i);
				ContainedEntityHandoffPacket.announce(serverLevel, slime, sourcePos, sourcePos,
					location.getId() * 31L + i,
					BasinEntityProcessing.getContainedSlimeAnimationPhase(level, sourcePos, i, 0));
				if (!level.addFreshEntity(slime)) {
					ContainedEntityHandoffPacket.cancel(serverLevel, slime, sourcePos);
					ContainedEntityHandoffPacket.cancel(serverLevel, firstSlime, sourcePos);
					for (Slime rollback : added) {
						ContainedEntityHandoffPacket.cancel(serverLevel, rollback, sourcePos);
						rollback.discard();
					}
					return null;
				}
				added.add(slime);
			}
			// NeoForge replaces the temporary ItemEntity after createEntity returns.
			// Preserve the replacement identity so a normal extracting funnel can keep
			// its vanilla last-output obstruction semantics.
			location.getPersistentData().putUUID(MATERIALIZED_REPLACEMENT_TAG, firstSlime.getUUID());
		}

		return firstSlime;
	}

	@Nullable
	public static Entity consumeMaterializedReplacement(Entity originalItemEntity) {
		CompoundTag data = originalItemEntity.getPersistentData();
		if (!data.hasUUID(MATERIALIZED_REPLACEMENT_TAG))
			return null;
		var replacementId = data.getUUID(MATERIALIZED_REPLACEMENT_TAG);
		data.remove(MATERIALIZED_REPLACEMENT_TAG);
		return originalItemEntity.level() instanceof ServerLevel serverLevel
			? serverLevel.getEntity(replacementId)
			: null;
	}

	/**
	 * Materialize a transported stack using the same dropped-item replacement contract as Create packages.
	 * Belt funnels normally insert straight into a belt inventory, so no {@link ItemEntity} is added to the
	 * level and NeoForge never gets an opportunity to call {@link #createEntity}. The temporary item entity here
	 * supplies the position and motion that the normal replacement callback would have received.
	 */
	public static boolean materializeTransportedStack(Level level, Vec3 position, Vec3 motion, ItemStack stack) {
		if (level == null || level.isClientSide || stack.isEmpty()
			|| !(stack.getItem() instanceof CapturedSmallSlimeItem))
			return false;

		if (!(level instanceof ServerLevel serverLevel))
			return false;
		BlockPos sourcePos = BlockPos.containing(position);
		List<Slime> slimes = new ArrayList<>(stack.getCount());
		for (int i = 0; i < stack.getCount(); i++) {
			Slime slime = BasinEntityProcessing.createSmallSlime(level,
				position.add(getDropSpread(i, stack.getCount())), motion);
			if (slime == null)
				return false;
			slimes.add(slime);
		}

		List<Slime> added = new ArrayList<>(slimes.size());
		for (int i = 0; i < slimes.size(); i++) {
			Slime slime = slimes.get(i);
			ContainedEntityHandoffPacket.announce(serverLevel, slime, sourcePos, sourcePos,
				sourcePos.asLong() * 31L + i,
				BasinEntityProcessing.getContainedSlimeAnimationPhase(level, sourcePos, i, 0));
			if (level.addFreshEntity(slime)) {
				added.add(slime);
				continue;
			}
			ContainedEntityHandoffPacket.cancel(serverLevel, slime, sourcePos);
			for (Slime rollback : added) {
				ContainedEntityHandoffPacket.cancel(serverLevel, rollback, sourcePos);
				rollback.discard();
			}
			return false;
		}
		return true;
	}

	@Override
	protected void configureSpawnedEntity(Slime slime) {
		slime.setSize(1, true);
		slime.setPersistenceRequired();
	}

	private static void configureSlime(Slime slime) {
		slime.setSize(1, false);
	}

	private static Vec3 getDropSpread(int index, int count) {
		if (count <= 1)
			return Vec3.ZERO;

		double angle = Math.PI * 2 * index / count;
		double radius = 0.15d + 0.03d * Math.min(count, 8);
		return new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
	}
}

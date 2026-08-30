package com.nobodiiiii.createbiotech.entity;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/** Pickable, non-colliding multipart proxy that forwards interaction and damage to its body. */
final class SlimeBionicHitPart extends PartEntity<SlimeBionicEntity> {
	private boolean active;

	SlimeBionicHitPart(SlimeBionicEntity parent) {
		super(parent);
	}

	void setHitBounds(@Nullable AABB bounds) {
		xo = getX();
		yo = getY();
		zo = getZ();
		xOld = getX();
		yOld = getY();
		zOld = getZ();
		if (bounds == null) {
			active = false;
			double x = getParent().getX();
			double y = getParent().getY();
			double z = getParent().getZ();
			setPos(x, y, z);
			setBoundingBox(new AABB(x, y, z, x, y, z));
			return;
		}
		active = true;
		setPos((bounds.minX + bounds.maxX) * 0.5d, bounds.minY,
			(bounds.minZ + bounds.maxZ) * 0.5d);
		setBoundingBox(bounds);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean isPickable() {
		return active && getParent().isAlive();
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!active || isInvulnerableTo(source))
			return false;
		boolean damaged = getParent().hurt(source, amount);
		if (damaged)
			gameEvent(GameEvent.ENTITY_DAMAGE);
		return damaged;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		return active ? getParent().interact(player, hand) : InteractionResult.PASS;
	}

	@Override
	public InteractionResult interactAt(Player player, Vec3 localHit, InteractionHand hand) {
		if (!active)
			return InteractionResult.PASS;
		Vec3 worldHit = position().add(localHit);
		return getParent().interactAt(player, worldHit.subtract(getParent().position()), hand);
	}

	@Override
	public boolean is(Entity entity) {
		return this == entity || getParent() == entity;
	}

	@Nullable
	@Override
	public ItemStack getPickResult() {
		return getParent().getPickResult();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}

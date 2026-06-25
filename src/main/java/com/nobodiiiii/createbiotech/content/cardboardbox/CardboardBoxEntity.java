package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.chute.ChuteBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PlayMessages.SpawnEntity;

public class CardboardBoxEntity extends PackageEntity {

	private Entity originalEntity;

	public CardboardBoxEntity(EntityType<?> entityType, Level level) {
		super(entityType, level);
	}

	public CardboardBoxEntity(Level level, double x, double y, double z) {
		this(CBEntityTypes.CARDBOARD_BOX.get(), level);
		setPos(x, y, z);
		refreshDimensions();
	}

	public static CardboardBoxEntity fromDroppedItem(Level level, Entity originalEntity, ItemStack stack) {
		CardboardBoxEntity boxEntity = CBEntityTypes.CARDBOARD_BOX.get()
			.create(level);
		if (boxEntity == null)
			return null;

		Vec3 position = originalEntity.position();
		boxEntity.setPos(position);
		boxEntity.setBox(stack);
		boxEntity.setDeltaMovement(originalEntity.getDeltaMovement()
			.scale(1.5f));
		boxEntity.originalEntity = originalEntity;

		if (level != null && !level.isClientSide)
			if (ChuteBlock.isChute(level.getBlockState(BlockPos.containing(position.x, position.y + .5f, position.z))))
				boxEntity.setYRot(((int) boxEntity.getYRot()) / 90 * 90);

		return boxEntity;
	}

	public static CardboardBoxEntity fromItemStack(Level level, Vec3 position, ItemStack stack) {
		CardboardBoxEntity boxEntity = CBEntityTypes.CARDBOARD_BOX.get()
			.create(level);
		if (boxEntity == null)
			return null;

		boxEntity.setPos(position);
		boxEntity.setBox(stack);
		return boxEntity;
	}

	public static CardboardBoxEntity spawn(SpawnEntity spawnEntity, Level level) {
		CardboardBoxEntity boxEntity =
			new CardboardBoxEntity(level, spawnEntity.getPosX(), spawnEntity.getPosY(), spawnEntity.getPosZ());
		boxEntity.setDeltaMovement(spawnEntity.getVelX(), spawnEntity.getVelY(), spawnEntity.getVelZ());
		boxEntity.clientPosition = boxEntity.position();
		return boxEntity;
	}

	@Override
	protected void verifyInitialEntity() {
		Entity source = originalEntity;
		originalEntity = null;
		if (!(source instanceof ItemEntity itemEntity))
			return;

		CompoundTag nbt = new CompoundTag();
		itemEntity.addAdditionalSaveData(nbt);
		if (nbt.getInt("PickupDelay") != 32767)
			return;

		discard();
	}

	@Override
	protected void dropAllDeathLoot(DamageSource damageSource) {
		if (!level().isClientSide
			&& CapturedEntityBoxHelper.releaseCapturedEntity(box, level(), position(), getDeltaMovement()))
			return;

		super.dropAllDeathLoot(damageSource);
	}
}

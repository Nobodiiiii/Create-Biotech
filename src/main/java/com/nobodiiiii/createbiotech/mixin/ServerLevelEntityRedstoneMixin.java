package com.nobodiiiii.createbiotech.mixin;

import com.nobodiiiii.createbiotech.content.dingdongchicken.EntityRedstoneIndex;
import com.nobodiiiii.createbiotech.content.dingdongchicken.EntityRedstoneLevelAccess;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerLevel.class)
public abstract class ServerLevelEntityRedstoneMixin implements EntityRedstoneLevelAccess {

	@Unique
	private final EntityRedstoneIndex createBiotech$entityRedstoneIndex = new EntityRedstoneIndex();

	@Override
	public EntityRedstoneIndex createBiotech$getEntityRedstoneIndex() {
		return createBiotech$entityRedstoneIndex;
	}
}

package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinInternalItemAccess;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinItemHandlerView;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

@Mixin(value = BasinBlockEntity.class, priority = 1001)
public abstract class BasinBlockEntityMixin implements BasinInternalItemAccess {
	@Shadow(remap = false)
	protected IItemHandlerModifiable itemCapability;

	@Unique
	private IItemHandlerModifiable createBiotech$internalItemCapability;

	@Unique
	private IItemHandlerModifiable createBiotech$funnelItemCapability;

	@Unique
	private boolean createBiotech$spoutputTargetReserved;

	/**
	 * Ticks remaining before the one-time 1.3.0.1 reconciliation runs, then latched to -1. The
	 * short delay lets the surrounding chunk finish loading its entities. Basins without legacy
	 * data remain latched at -1 and never enter the migration path.
	 */
	@Unique
	private int createBiotech$legacySlimeMigrationDelay = -1;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void createBiotech$separateItemCapability(BlockEntityType<?> type, net.minecraft.core.BlockPos pos,
		BlockState state, CallbackInfo ci) {
		createBiotech$internalItemCapability = itemCapability;
		createBiotech$funnelItemCapability = new BasinItemHandlerView(itemCapability, true);
		itemCapability = new BasinItemHandlerView(itemCapability, false);
	}

	@Override
	public IItemHandlerModifiable createBiotech$getInternalItemHandler() {
		return createBiotech$internalItemCapability;
	}

	@Override
	public IItemHandlerModifiable createBiotech$getFunnelItemHandler() {
		return createBiotech$funnelItemCapability;
	}

	@Inject(
		method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
		at = @At("TAIL"))
	private void createBiotech$scheduleLegacySlimeMigration(CompoundTag compound,
		HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
		if (!clientPacket && BasinEntityProcessing.hasLegacyContainedSlimeData(
			(BasinBlockEntity) (Object) this))
			createBiotech$legacySlimeMigrationDelay = 20;
	}

	@Inject(method = "tick()V", at = @At("TAIL"), remap = false)
	private void createBiotech$migrateLegacyCapturedSmallSlimes(CallbackInfo ci) {
		if (createBiotech$legacySlimeMigrationDelay < 0)
			return;
		if (createBiotech$legacySlimeMigrationDelay-- > 0)
			return;
		BasinEntityProcessing.migrateLegacyContainedSlimes((BasinBlockEntity) (Object) this);
	}

	@Inject(method = "tryClearingSpoutputOverflow()V", at = @At("HEAD"), remap = false)
	private void createBiotech$beginSpoutputTransfer(CallbackInfo ci) {
		createBiotech$spoutputTargetReserved = false;
	}

	@WrapOperation(
		method = "tryClearingSpoutputOverflow()V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/neoforged/neoforge/items/ItemHandlerHelper;insertItemStacked(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack createBiotech$materializeAcceptedSmallSlimeSpoutput(IItemHandler target, ItemStack stack,
		boolean simulate, Operation<ItemStack> original) {
		// A direct-belt target accepts one transported stack at a time. A materialized
		// slime does not occupy that item handler, so reserve it virtually for the rest
		// of this pass to preserve the same one-transfer scheduling as ordinary items.
		if (createBiotech$spoutputTargetReserved)
			return stack;
		if (!BasinEntityProcessing.isCapturedSmallSlimeItem(stack) || simulate)
			return original.call(target, stack, simulate);

		ItemStack remainder = original.call(target, stack, true);
		int accepted = stack.getCount() - remainder.getCount();
		if (accepted <= 0)
			return stack;

		BasinBlockEntity basin = (BasinBlockEntity) (Object) this;
		BlockState blockState = basin.getBlockState();
		if (!(blockState.getBlock() instanceof BasinBlock))
			return stack;
		Direction direction = blockState.getValue(BasinBlock.FACING);
		if (!direction.getAxis().isHorizontal())
			return stack;

		Vec3 directionVector = Vec3.atLowerCornerOf(direction.getNormal());
		Vec3 outputPosition = Vec3.atCenterOf(basin.getBlockPos())
			.add(directionVector.scale(.65d))
			.subtract(0, .25d, 0);
		Vec3 outputMotion = directionVector.scale(1 / 16d)
			.add(0, -1 / 16d, 0);
		ItemStack acceptedStack = stack.copyWithCount(accepted);
		if (!CapturedSmallSlimeItem.materializeTransportedStack(
			basin.getLevel(), outputPosition, outputMotion, acceptedStack))
			return stack;

		createBiotech$spoutputTargetReserved = true;
		return remainder;
	}
}

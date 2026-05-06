package com.nobodiiiii.createbiotech.content.universaljoint;

import java.util.List;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

public class UniversalJointBlockEntity extends KineticBlockEntity {

	@Nullable
	private BlockPos linkedPos;

	public UniversalJointBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.UNIVERSAL_JOINT.get(), pos, state);
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		if (linkedPos != null)
			compound.put("LinkedJoint", NbtUtils.writeBlockPos(linkedPos));
		super.write(compound, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		linkedPos = compound.contains("LinkedJoint") ? NbtUtils.readBlockPos(compound.getCompound("LinkedJoint")) : null;
	}

	public void setLinkedPos(BlockPos linkedPos) {
		this.linkedPos = linkedPos.immutable();
		updateSpeed = true;
		notifyUpdate();
	}

	@Nullable
	public BlockPos getLinkedPos() {
		return linkedPos;
	}

	public void clearLink() {
		linkedPos = null;
		if (level != null && !level.isClientSide) {
			detachKinetics();
			removeSource();
			updateSpeed = true;
		}
		notifyUpdate();
	}

	@Override
	public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
		if (linkedPos != null && level != null && level.isLoaded(linkedPos))
			neighbours.add(linkedPos);
		return neighbours;
	}

	@Override
	public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
		return isPairedWith(other);
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
		boolean connectedViaAxes, boolean connectedViaCogs) {
		return isPairedWith(target) ? 1 : 0;
	}

	private boolean isPairedWith(KineticBlockEntity target) {
		if (!(target instanceof UniversalJointBlockEntity other))
			return false;
		return linkedPos != null && linkedPos.equals(other.getBlockPos())
			&& other.linkedPos != null && other.linkedPos.equals(getBlockPos());
	}
}

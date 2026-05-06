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
import net.minecraft.world.phys.AABB;

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
		invalidateRenderBoundingBox();
	}

	public void setLinkedPos(BlockPos linkedPos) {
		this.linkedPos = linkedPos.immutable();
		updateSpeed = true;
		invalidateRenderBoundingBox();
		notifyUpdate();
	}

	@Nullable
	public BlockPos getLinkedPos() {
		return linkedPos;
	}

	public void clearLink() {
		linkedPos = null;
		invalidateRenderBoundingBox();
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

	@Override
	public AABB createRenderBoundingBox() {
		if (linkedPos == null)
			return super.createRenderBoundingBox();

		int minX = Math.min(worldPosition.getX(), linkedPos.getX());
		int minY = Math.min(worldPosition.getY(), linkedPos.getY());
		int minZ = Math.min(worldPosition.getZ(), linkedPos.getZ());
		int maxX = Math.max(worldPosition.getX(), linkedPos.getX()) + 1;
		int maxY = Math.max(worldPosition.getY(), linkedPos.getY()) + 1;
		int maxZ = Math.max(worldPosition.getZ(), linkedPos.getZ()) + 1;
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1);
	}

	private boolean isPairedWith(KineticBlockEntity target) {
		if (!(target instanceof UniversalJointBlockEntity other))
			return false;
		return linkedPos != null && linkedPos.equals(other.getBlockPos())
			&& other.linkedPos != null && other.linkedPos.equals(getBlockPos());
	}
}

package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.List;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class PoweredCaptureNetBlockEntity extends KineticBlockEntity {

	private static final String ARM_SETTINGS = "OriginalArmSettings";

	private final LerpedFloat baseAngle = LerpedFloat.angular();
	private final LerpedFloat shoulderAngle = LerpedFloat.angular().startWithValue(ArmCapturePose.RESTING.shoulder());
	private final LerpedFloat elbowAngle = LerpedFloat.angular().startWithValue(ArmCapturePose.RESTING.elbow());
	private final LerpedFloat headAngle = LerpedFloat.angular().startWithValue(ArmCapturePose.RESTING.head());
	private final LerpedFloat netExtension = LerpedFloat.linear();
	private ArmCaptureBehaviour capture;
	private CompoundTag originalArmSettings = new CompoundTag();

	public PoweredCaptureNetBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.POWERED_CAPTURE_NET.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		capture = new ArmCaptureBehaviour(this);
		behaviours.add(capture);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || !level.isClientSide)
			return;
		ArmCapturePose target = capture.getClientPose(baseAngle.getValue());
		float step = capture.getMovementStep();
		float response = capture.isSwinging() || step == 0 ? 1 : step;
		baseAngle.setValue(target.yaw());
		shoulderAngle.setValue(AngleHelper.angleLerp(response, shoulderAngle.getValue(), target.shoulder()));
		elbowAngle.setValue(AngleHelper.angleLerp(response, elbowAngle.getValue(), target.elbow()));
		headAngle.setValue(AngleHelper.angleLerp(response, headAngle.getValue(), target.head()));
		netExtension.setValue(target.extension());
	}

	public ArmCapturePose getCapturePose(float partialTick) {
		return new ArmCapturePose(baseAngle.getValue(partialTick), shoulderAngle.getValue(partialTick),
			elbowAngle.getValue(partialTick), headAngle.getValue(partialTick), netExtension.getValue(partialTick));
	}

	public ItemStack getNet() {
		return capture.getNet();
	}

	ItemStack replaceNet(ItemStack net) {
		return capture.replaceNet(net);
	}

	void setOriginalArmSettings(CompoundTag settings) {
		originalArmSettings = settings;
	}

	CompoundTag originalArmSettings() {
		return originalArmSettings;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (!clientPacket && !originalArmSettings.isEmpty())
			tag.put(ARM_SETTINGS, originalArmSettings.copy());
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (!clientPacket)
			originalArmSettings = tag.getCompound(ARM_SETTINGS).copy();
	}

	@Override
	protected AABB createRenderBoundingBox() {
		return super.createRenderBoundingBox().inflate(3);
	}
}

package com.nobodiiiii.createbiotech.content.experience;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ExperienceTankBlockEntity extends BlockEntity
	implements ExperienceSource, ExperienceSink, IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid {

	public static final int MAX_WIDTH = 3;
	public static final int MAX_HEIGHT = 32;

	@Nullable
	protected BlockPos controller;
	@Nullable
	protected BlockPos lastKnownPos;
	protected int storedExperience;
	protected int width = 1;
	protected int height = 1;
	protected boolean window = true;
	protected boolean updateConnectivity;

	public ExperienceTankBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EXPERIENCE_TANK.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ExperienceTankBlockEntity be) {
		if (level.isClientSide)
			return;
		if (be.lastKnownPos == null)
			be.lastKnownPos = pos;
		else if (!be.lastKnownPos.equals(pos)) {
			be.onPositionChanged();
			return;
		}

		if (be.updateConnectivity)
			be.updateConnectivity();

		if (be.isController()) {
			int capacity = be.getCapacity();
			if (be.storedExperience > capacity) {
				int overflow = be.storedExperience - capacity;
				be.storedExperience = capacity;
				ExperienceHelper.spawnExperience(level, pos.getCenter(), overflow);
				be.sendData();
			}
		}
	}

	protected void updateConnectivity() {
		updateConnectivity = false;
		if (level == null || level.isClientSide)
			return;
		if (!isController())
			return;
		ConnectivityHandler.formMulti(this);
	}

	public void requestConnectivityUpdate() {
		updateConnectivity = true;
	}

	private void onPositionChanged() {
		removeController(true);
		lastKnownPos = worldPosition;
	}

	@Override
	public boolean isController() {
		return controller == null || worldPosition.equals(controller);
	}

	@Override
	@SuppressWarnings("unchecked")
	public ExperienceTankBlockEntity getControllerBE() {
		if (isController())
			return this;
		if (level == null || controller == null)
			return null;
		BlockEntity be = level.getBlockEntity(controller);
		return be instanceof ExperienceTankBlockEntity tank ? tank : null;
	}

	@Override
	public BlockPos getController() {
		return isController() ? worldPosition : controller;
	}

	@Override
	public void setController(BlockPos controller) {
		if (level != null && level.isClientSide)
			return;
		if (Objects.equals(this.controller, controller))
			return;

		boolean wasController = isController();
		int transferred = wasController ? storedExperience : 0;
		this.controller = controller;
		if (wasController && transferred > 0 && !controller.equals(worldPosition)) {
			storedExperience = 0;
			ExperienceTankBlockEntity newController = getControllerBE();
			if (newController != null)
				newController.receiveTransferredExperience(transferred);
		}
		setChanged();
		sendData();
	}

	private void receiveTransferredExperience(int amount) {
		if (amount <= 0)
			return;
		storedExperience += amount;
		setChanged();
		sendData();
	}

	@Override
	public void removeController(boolean keepContents) {
		if (level == null || level.isClientSide)
			return;
		updateConnectivity = true;
		controller = null;
		width = 1;
		height = 1;
		BlockState state = getBlockState();
		if (ExperienceTankBlock.isTank(state))
			level.setBlock(worldPosition, state.setValue(ExperienceTankBlock.TOP, true)
				.setValue(ExperienceTankBlock.BOTTOM, true)
				.setValue(ExperienceTankBlock.SHAPE, window ? Shape.WINDOW : Shape.PLAIN), 22);
		setChanged();
		sendData();
	}

	@Override
	public BlockPos getLastKnownPos() {
		return lastKnownPos;
	}

	@Override
	public void preventConnectivityUpdate() {
		updateConnectivity = false;
	}

	@Override
	public void notifyMultiUpdated() {
		BlockState state = getBlockState();
		if (state.getBlock() instanceof ExperienceTankBlock) {
			BlockPos ctrl = getController();
			state = state.setValue(ExperienceTankBlock.BOTTOM, ctrl.getY() == worldPosition.getY());
			state = state.setValue(ExperienceTankBlock.TOP, ctrl.getY() + height - 1 == worldPosition.getY());
			level.setBlock(worldPosition, state, 6);
		}
		if (isController())
			setWindows(window);
		setChanged();
	}

	public void setWindows(boolean window) {
		this.window = window;
		if (level == null || !isController())
			return;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < width; z++) {
					BlockPos pos = worldPosition.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!(state.getBlock() instanceof ExperienceTankBlock))
						continue;

					Shape shape = Shape.PLAIN;
					if (window) {
						if (width == 1) {
							shape = Shape.WINDOW;
						} else if (width == 2) {
							shape = x == 0 ? z == 0 ? Shape.WINDOW_NW : Shape.WINDOW_SW
								: z == 0 ? Shape.WINDOW_NE : Shape.WINDOW_SE;
						} else if (width == 3) {
							if (Math.abs(Math.abs(x) - Math.abs(z)) == 1)
								shape = Shape.WINDOW;
						}
					}
					level.setBlock(pos, state.setValue(ExperienceTankBlock.SHAPE, shape), 22);
				}
			}
		}
	}

	@Override
	public Direction.Axis getMainConnectionAxis() {
		return Direction.Axis.Y;
	}

	@Override
	public int getMaxLength(Direction.Axis longAxis, int width) {
		if (longAxis == Direction.Axis.Y)
			return MAX_HEIGHT;
		return MAX_WIDTH;
	}

	@Override
	public int getMaxWidth() {
		return MAX_WIDTH;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void setHeight(int height) {
		this.height = Math.max(1, height);
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public void setWidth(int width) {
		this.width = Math.max(1, width);
	}

	@Override
	public boolean hasTank() {
		return false;
	}

	@Override
	public void setExtraData(@Nullable Object data) {
		if (data instanceof Boolean w)
			window = w;
	}

	@Override
	@Nullable
	public Object getExtraData() {
		return window;
	}

	@Override
	public Object modifyExtraData(Object data) {
		if (data instanceof Boolean windows)
			return windows | window;
		return data;
	}

	@Override
	public int insertExperience(int amount, boolean simulate) {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null || amount <= 0)
			return 0;
		if (controllerBE != this)
			return controllerBE.insertExperience(amount, simulate);
		int accepted = Math.min(amount, getExperienceSpace());
		if (!simulate && accepted > 0) {
			storedExperience += accepted;
			sendData();
		}
		return accepted;
	}

	@Override
	public int extractExperience(int maxAmount, boolean simulate) {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null || maxAmount <= 0)
			return 0;
		if (controllerBE != this)
			return controllerBE.extractExperience(maxAmount, simulate);
		int extracted = Math.min(maxAmount, storedExperience);
		if (!simulate && extracted > 0) {
			storedExperience -= extracted;
			sendData();
		}
		return extracted;
	}

	@Override
	public int getStoredExperience() {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		return controllerBE == null ? 0 : controllerBE.storedExperience;
	}

	@Override
	public int getExperienceSpace() {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return 0;
		return Math.max(0, controllerBE.getCapacity() - controllerBE.storedExperience);
	}

	public int getCapacity() {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return ExperienceConstants.TANK_CAPACITY_PER_BLOCK;
		return controllerBE.width * controllerBE.width * controllerBE.height
			* ExperienceConstants.TANK_CAPACITY_PER_BLOCK;
	}

	public int getTotalSize() {
		return width * width * height;
	}

	public boolean hasWindow() {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		return controllerBE == null ? window : controllerBE.window;
	}

	public float getFillState() {
		int capacity = getCapacity();
		return capacity <= 0 ? 0 : (float) getStoredExperience() / capacity;
	}

	public void toggleWindows() {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return;
		controllerBE.setWindows(!controllerBE.window);
		controllerBE.setChanged();
		controllerBE.sendData();
	}

	public void handleRemoved() {
		if (level != null && !level.isClientSide)
			splitTankAndInvalidate(this, worldPosition);
	}

	public static void splitTankAndInvalidate(ExperienceTankBlockEntity be, BlockPos removedPos) {
		Level level = be.getLevel();
		if (level == null || level.isClientSide)
			return;

		be = be.getControllerBE();
		if (be == null)
			return;

		int height = be.getHeight();
		int width = be.getWidth();
		BlockPos origin = be.getBlockPos();
		Direction.Axis axis = be.getMainConnectionAxis();
		Object extraData = be.getExtraData();
		int remainingExperience = be.storedExperience;

		if (!be.isRemoved()) {
			int retainedExperience = Math.min(ExperienceConstants.TANK_CAPACITY_PER_BLOCK, remainingExperience);
			be.storedExperience = retainedExperience;
			remainingExperience -= retainedExperience;
		} else {
			be.storedExperience = 0;
		}

		for (int yOffset = 0; yOffset < height; yOffset++) {
			for (int xOffset = 0; xOffset < width; xOffset++) {
				for (int zOffset = 0; zOffset < width; zOffset++) {
					BlockPos pos = switch (axis) {
						case X -> origin.offset(yOffset, xOffset, zOffset);
						case Y -> origin.offset(xOffset, yOffset, zOffset);
						case Z -> origin.offset(xOffset, zOffset, yOffset);
					};

					ExperienceTankBlockEntity partAt = ConnectivityHandler.partAt(be.getType(), level, pos);
					if (partAt == null)
						continue;
					if (!partAt.getController()
						.equals(origin))
						continue;

					partAt.setExtraData(extraData);
					partAt.removeController(true);

					if (partAt != be) {
						int split = Math.min(ExperienceConstants.TANK_CAPACITY_PER_BLOCK, remainingExperience);
						partAt.storedExperience = split;
						remainingExperience -= split;
					}

					partAt.setChanged();
					partAt.sendData();
				}
			}
		}

		if (remainingExperience > 0)
			ExperienceHelper.spawnExperience(level, removedPos.getCenter(), remainingExperience);
	}

	@Override
	public AABB getRenderBoundingBox() {
		if (!isController())
			return new AABB(worldPosition);
		return new AABB(worldPosition, worldPosition.offset(width, height, width));
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		ExperienceTankBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return false;

		CreateLang.builder()
			.add(Component.translatable("create_biotech.gui.goggles.experience_container"))
			.forGoggles(tooltip);

		int stored = controllerBE.getStoredExperience();
		int capacity = controllerBE.getCapacity();
		String unitKey = "create_biotech.generic.unit.experience_points";

		CreateLang.builder()
			.add(CreateLang.number(stored)
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.GOLD))
			.text(ChatFormatting.GRAY, " / ")
			.add(CreateLang.number(capacity)
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.DARK_GRAY))
			.forGoggles(tooltip, 1);

		return true;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (updateConnectivity)
			tag.putBoolean("Uninitialized", true);
		if (lastKnownPos != null)
			tag.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
		if (!isController() && controller != null)
			tag.put("Controller", NbtUtils.writeBlockPos(controller));
		if (isController()) {
			tag.putInt("StoredExperience", storedExperience);
			tag.putInt("Width", width);
			tag.putInt("Height", height);
			tag.putBoolean("Window", window);
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		updateConnectivity = tag.contains("Uninitialized");
		controller = null;
		lastKnownPos = null;
		if (tag.contains("LastKnownPos"))
			lastKnownPos = NbtUtils.readBlockPos(tag.getCompound("LastKnownPos"));
		if (tag.contains("Controller"))
			controller = NbtUtils.readBlockPos(tag.getCompound("Controller"));
		if (isController()) {
			storedExperience = tag.getInt("StoredExperience");
			width = Math.max(1, tag.getInt("Width"));
			height = Math.max(1, tag.getInt("Height"));
			window = !tag.contains("Window") || tag.getBoolean("Window");
		}
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	public void sendData() {
		if (level == null)
			return;
		setChanged();
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, 3);
	}
}

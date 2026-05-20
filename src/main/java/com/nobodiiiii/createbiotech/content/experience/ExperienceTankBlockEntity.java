package com.nobodiiiii.createbiotech.content.experience;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.compat.jade.JadeExperienceProvider;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ExperienceTankBlockEntity extends SmartBlockEntity
	implements ExperienceSource, ExperienceSink, IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid,
	JadeExperienceProvider {

	public static final int MAX_WIDTH = 3;
	public static final int MAX_HEIGHT = 32;

	@Nullable
	protected BlockPos controller;
	@Nullable
	protected BlockPos lastKnownPos;
	protected int storedExperience;
	protected int width;
	protected int height;
	protected boolean window;
	protected int luminosity;
	protected boolean updateConnectivity;

	public ExperienceTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		updateConnectivity = false;
		window = true;
		height = 1;
		width = 1;
		luminosity = 0;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (lastKnownPos == null)
			lastKnownPos = worldPosition;
		else if (!lastKnownPos.equals(worldPosition)) {
			onPositionChanged();
			return;
		}

		if (updateConnectivity)
			updateConnectivity();

		if (isController()) {
			int capacity = getCapacity();
			if (storedExperience > capacity) {
				int overflow = storedExperience - capacity;
				storedExperience = capacity;
				ExperienceHelper.spawnExperience(level, worldPosition.getCenter(), overflow);
				sendData();
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
		if (isController() || !hasLevel())
			return this;
		var be = level.getBlockEntity(controller);
		return be instanceof ExperienceTankBlockEntity tank ? tank : null;
	}

	@Override
	public BlockPos getController() {
		return isController() ? worldPosition : controller;
	}

	@Override
	public void setController(BlockPos controller) {
		if (level != null && level.isClientSide && !isVirtual())
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
		luminosity = 0;
		BlockState state = getBlockState();
		if (ExperienceTankBlock.isTank(state)) {
			state = state.setValue(ExperienceTankBlock.BOTTOM, true);
			state = state.setValue(ExperienceTankBlock.TOP, true);
			state = state.setValue(ExperienceTankBlock.SHAPE, window ? Shape.WINDOW : Shape.PLAIN);
			level.setBlock(worldPosition, state, 22);
		}
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
			state = state.setValue(ExperienceTankBlock.BOTTOM, getController().getY() == worldPosition.getY());
			state = state.setValue(ExperienceTankBlock.TOP,
				getController().getY() + height - 1 == worldPosition.getY());
			level.setBlock(worldPosition, state, 6);
		}
		if (isController())
			setWindows(window);
		setChanged();
	}

	public void setWindows(boolean window) {
		this.window = window;
		for (int yOffset = 0; yOffset < height; yOffset++) {
			for (int xOffset = 0; xOffset < width; xOffset++) {
				for (int zOffset = 0; zOffset < width; zOffset++) {
					BlockPos pos = worldPosition.offset(xOffset, yOffset, zOffset);
					BlockState blockState = level.getBlockState(pos);
					if (!ExperienceTankBlock.isTank(blockState))
						continue;

					Shape shape = Shape.PLAIN;
					if (window) {
						if (width == 1)
							shape = Shape.WINDOW;
						if (width == 2)
							shape = xOffset == 0 ? zOffset == 0 ? Shape.WINDOW_NW : Shape.WINDOW_SW
								: zOffset == 0 ? Shape.WINDOW_NE : Shape.WINDOW_SE;
						if (width == 3 && Math.abs(Math.abs(xOffset) - Math.abs(zOffset)) == 1)
							shape = Shape.WINDOW;
					}

					level.setBlock(pos, blockState.setValue(ExperienceTankBlock.SHAPE, shape), 22);
					level.getChunkSource()
						.getLightEngine()
						.checkBlock(pos);
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
		if (data instanceof Boolean)
			window = (boolean) data;
	}

	@Override
	@Nullable
	public Object getExtraData() {
		return window;
	}

	@Override
	public Object modifyExtraData(Object data) {
		if (data instanceof Boolean windows) {
			windows |= window;
			return windows;
		}
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

	@Override
	public int getJadeCurrentXp() {
		return getStoredExperience();
	}

	@Override
	public int getJadeMaxXp() {
		return getCapacity();
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

	public int getLuminosity() {
		return luminosity;
	}

	public void toggleWindows() {
		ExperienceTankBlockEntity be = getControllerBE();
		if (be == null)
			return;
		be.setWindows(!be.window);
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
	protected AABB createRenderBoundingBox() {
		if (isController())
			return super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1);
		return super.createRenderBoundingBox();
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
	protected void read(CompoundTag compound, boolean clientPacket) {
		BlockPos controllerBefore = controller;
		int prevSize = width;
		int prevHeight = height;
		int prevLum = luminosity;

		super.read(compound, clientPacket);

		updateConnectivity = compound.contains("Uninitialized");
		luminosity = compound.getInt("Luminosity");
		controller = null;
		lastKnownPos = null;

		if (compound.contains("LastKnownPos"))
			lastKnownPos = NbtUtils.readBlockPos(compound.getCompound("LastKnownPos"));
		if (compound.contains("Controller"))
			controller = NbtUtils.readBlockPos(compound.getCompound("Controller"));

		if (isController()) {
			storedExperience = compound.getInt("StoredExperience");
			width = Math.max(1, compound.getInt("Width"));
			height = Math.max(1, compound.getInt("Height"));
			window = !compound.contains("Window") || compound.getBoolean("Window");
		}

		if (!clientPacket)
			return;

		boolean changeOfController = !Objects.equals(controllerBefore, controller);
		if (changeOfController || prevSize != width || prevHeight != height) {
			if (hasLevel())
				level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
			invalidateRenderBoundingBox();
		}
		if (luminosity != prevLum && hasLevel())
			level.getChunkSource()
				.getLightEngine()
				.checkBlock(worldPosition);
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		if (updateConnectivity)
			compound.putBoolean("Uninitialized", true);
		if (lastKnownPos != null)
			compound.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
		if (!isController() && controller != null)
			compound.put("Controller", NbtUtils.writeBlockPos(controller));
		if (isController()) {
			compound.putInt("StoredExperience", storedExperience);
			compound.putInt("Width", width);
			compound.putInt("Height", height);
			compound.putBoolean("Window", window);
		}
		compound.putInt("Luminosity", luminosity);
	}

	@Override
	public void writeSafe(CompoundTag compound) {
		super.writeSafe(compound);
		if (isController()) {
			compound.putBoolean("Window", window);
			compound.putInt("Width", width);
			compound.putInt("Height", height);
		}
	}
}

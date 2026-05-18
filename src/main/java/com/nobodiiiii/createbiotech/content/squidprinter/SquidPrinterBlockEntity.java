package com.nobodiiiii.createbiotech.content.squidprinter;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;

public class SquidPrinterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int FILLING_TIME = 20;
	public static final int CYCLE_TICKS = 20;
	public static final int CYCLE_WATER_COST = 50;
	public static final int TANK_CAPACITY = 1000;

	public int processingTicks;
	public boolean sendSplash;

	protected SmartFluidTankBehaviour tank;
	protected BeltProcessingBehaviour beltProcessing;
	public FilteringBehaviour filtering;

	private int cycleTicker;
	private boolean running;

	public SquidPrinterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		processingTicks = -1;
		cycleTicker = 0;
		running = false;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, TANK_CAPACITY);
		behaviours.add(tank);

		beltProcessing = new BeltProcessingBehaviour(this).whenItemEnters(this::onItemReceived)
			.whileItemHeld(this::whenItemHeld);
		behaviours.add(beltProcessing);

		filtering = new FilteringBehaviour(this, new SquidPrinterFilterSlot())
			.withPredicate(stack -> stack.isEmpty() || stack.is(Items.ENCHANTED_BOOK))
			.withCallback(stack -> notifyUpdate());
		behaviours.add(filtering);
	}

	@Override
	protected AABB createRenderBoundingBox() {
		return super.createRenderBoundingBox().expandTowards(0, -2, 0);
	}

	@Override
	public void tick() {
		super.tick();

		if (level == null)
			return;

		if (!level.isClientSide) {
			tickRunning();
		}

		if (processingTicks >= 0)
			processingTicks--;

		if (processingTicks >= 8 && level.isClientSide)
			spawnInkParticles();

		if (level.isClientSide && running && level.getGameTime() % 3 == 0)
			spawnAmbientInk();
	}

	private void tickRunning() {
		FluidStack stored = getFluid();
		boolean canRun = !stored.isEmpty() && stored.getFluid().isSame(Fluids.WATER) && stored.getAmount() >= CYCLE_WATER_COST;

		if (canRun) {
			cycleTicker++;
			if (cycleTicker >= CYCLE_TICKS) {
				cycleTicker = 0;
				tank.getPrimaryHandler()
					.drain(CYCLE_WATER_COST, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
				notifyUpdate();
			}
			if (!running) {
				running = true;
				notifyUpdate();
			}
		} else {
			cycleTicker = 0;
			if (running) {
				running = false;
				notifyUpdate();
			}
		}
	}

	protected ProcessingResult onItemReceived(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (handler.blockEntity.isVirtual())
			return PASS;
		if (!isApplicableInput(transported.stack))
			return PASS;
		if (!isReady())
			return HOLD;
		return HOLD;
	}

	protected ProcessingResult whenItemHeld(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (processingTicks != -1 && processingTicks != 5)
			return HOLD;
		if (!isApplicableInput(transported.stack))
			return PASS;
		if (!isReady())
			return HOLD;

		if (processingTicks == -1) {
			processingTicks = FILLING_TIME;
			notifyUpdate();
			return HOLD;
		}

		ItemStack out = produceCopy();
		if (!out.isEmpty()) {
			transported.clearFanProcessingData();
			List<TransportedItemStack> outList = new ArrayList<>();
			TransportedItemStack held = null;
			TransportedItemStack result = transported.copy();
			result.stack = out;
			ItemStack remaining = transported.stack.copy();
			remaining.shrink(1);
			if (!remaining.isEmpty()) {
				held = transported.copy();
				held.stack = remaining;
			}
			outList.add(result);
			handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(outList, held));
		}

		sendSplash = true;
		notifyUpdate();
		return HOLD;
	}

	private boolean isApplicableInput(ItemStack stack) {
		return stack.is(Items.BOOK);
	}

	public boolean isReady() {
		ItemStack template = filtering == null ? ItemStack.EMPTY : filtering.getFilter();
		if (template.isEmpty() || !template.is(Items.ENCHANTED_BOOK))
			return false;
		FluidStack stored = getFluid();
		return !stored.isEmpty() && stored.getFluid().isSame(Fluids.WATER) && stored.getAmount() >= CYCLE_WATER_COST;
	}

	private ItemStack produceCopy() {
		ItemStack template = filtering.getFilter();
		if (template.isEmpty() || !template.is(Items.ENCHANTED_BOOK))
			return ItemStack.EMPTY;
		return EnchantmentBookCopyItem.fromEnchantedBook(template, CBItems.ENCHANTMENT_BOOK_COPY.get());
	}

	public FluidStack getFluid() {
		return tank.getPrimaryHandler().getFluid();
	}

	public boolean isRunning() {
		return running;
	}

	public int getComparatorOutput() {
		FluidStack stored = getFluid();
		return stored.isEmpty() ? 0 : Math.max(1, (int) Math.round(stored.getAmount() * 14.0 / TANK_CAPACITY) + 1);
	}

	private void spawnInkParticles() {
		if (level == null)
			return;
		double centerX = worldPosition.getX() + 0.5d;
		double centerY = worldPosition.getY() - 0.5d;
		double centerZ = worldPosition.getZ() + 0.5d;
		for (int i = 0; i < 4; i++) {
			double dx = (level.random.nextDouble() - 0.5d) * 0.4d;
			double dz = (level.random.nextDouble() - 0.5d) * 0.4d;
			level.addParticle(ParticleTypes.SQUID_INK, centerX + dx, centerY, centerZ + dz, 0d, -0.05d, 0d);
		}
	}

	private void spawnAmbientInk() {
		if (level == null)
			return;
		double centerX = worldPosition.getX() + 0.5d;
		double centerY = worldPosition.getY() + 0.05d;
		double centerZ = worldPosition.getZ() + 0.5d;
		double dx = (level.random.nextDouble() - 0.5d) * 0.3d;
		double dz = (level.random.nextDouble() - 0.5d) * 0.3d;
		level.addParticle(ParticleTypes.SQUID_INK, centerX + dx, centerY, centerZ + dz, 0d, -0.04d, 0d);
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		compound.putInt("ProcessingTicks", processingTicks);
		compound.putInt("CycleTicker", cycleTicker);
		compound.putBoolean("Running", running);
		if (sendSplash && clientPacket) {
			compound.putBoolean("Splash", true);
			sendSplash = false;
		}
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		processingTicks = compound.getInt("ProcessingTicks");
		cycleTicker = compound.getInt("CycleTicker");
		running = compound.getBoolean("Running");
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER && side != Direction.DOWN)
			return tank.getCapability().cast();
		return super.getCapability(cap, side);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		return containedFluidTooltip(tooltip, isPlayerSneaking, getCapability(ForgeCapabilities.FLUID_HANDLER));
	}

	public void spawnSplashIfPending(ServerLevel level) {
		if (!sendSplash)
			return;
		sendSplash = false;
	}
}

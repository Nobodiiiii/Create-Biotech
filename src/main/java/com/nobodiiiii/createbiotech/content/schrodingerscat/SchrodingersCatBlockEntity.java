package com.nobodiiiii.createbiotech.content.schrodingerscat;

import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SchrodingersCatBlockEntity extends SmartBlockEntity {

	private static final Random RANDOM = new Random();
	private static final int DEFAULT_INTERVAL = 20;
	private static final int MAX_INTERVAL = 60 * 20 * 60;
	private static final int PULSE_DURATION = 2;

	private static final String SIGNAL_STRENGTH_TAG = "SignalStrength";
	private static final String TICK_COUNTER_TAG = "TickCounter";
	private static final String PULSE_TICKS_TAG = "PulseTicks";
	private static final int DETECTION_INTERVAL_NET_ID = 0;
	private static final int OUTPUT_MODE_NET_ID = 1;

	private DetectionIntervalValueBehaviour detectionIntervalValue;
	private OutputModeValueBehaviour outputModeValue;

	private int signalStrength = 15;
	private int tickCounter = 0;
	private int pulseTicks = 0;

	public SchrodingersCatBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SCHRODINGERS_CAT.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SchrodingersCatBlockEntity be) {
		be.tick();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		detectionIntervalValue =
			new DetectionIntervalValueBehaviour(this, new SchrodingersCatSideValueBoxTransform(true));
		detectionIntervalValue.between(1, MAX_INTERVAL);
		detectionIntervalValue.withFormatter(this::formatDetectionInterval);
		detectionIntervalValue.withCallback(this::onDetectionIntervalChanged);
		detectionIntervalValue.setValue(DEFAULT_INTERVAL);
		behaviours.add(detectionIntervalValue);

		outputModeValue = new OutputModeValueBehaviour(this, new SchrodingersCatSideValueBoxTransform(false));
		outputModeValue.withCallback(this::onOutputModeChanged);
		outputModeValue.setValue(SchrodingersCatOutputMode.SUSTAINED.ordinal());
		behaviours.add(outputModeValue);
	}

	@Override
	public void tick() {
		super.tick();

		if (level == null || level.isClientSide)
			return;

		int previousOutput = getOutputSignal();
		boolean dataChanged = false;

		if (pulseTicks > 0) {
			pulseTicks--;
			dataChanged = pulseTicks == 0;
		}

		tickCounter++;
		if (tickCounter >= getDetectionInterval()) {
			tickCounter = 0;
			signalStrength = RANDOM.nextBoolean() ? 15 : 0;
			pulseTicks = getOutputMode() == SchrodingersCatOutputMode.PULSE && signalStrength > 0 ? PULSE_DURATION : 0;
			dataChanged = true;
		}

		if (previousOutput != getOutputSignal()) {
			setChanged();
			sendData();
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
			return;
		}

		if (dataChanged) {
			setChanged();
			sendData();
		}
	}

	public int getOutputSignal() {
		if (getOutputMode() == SchrodingersCatOutputMode.PULSE)
			return pulseTicks > 0 ? 15 : 0;
		return signalStrength;
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		tag.putInt(SIGNAL_STRENGTH_TAG, signalStrength);
		tag.putInt(TICK_COUNTER_TAG, tickCounter);
		tag.putInt(PULSE_TICKS_TAG, pulseTicks);
		super.write(tag, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		signalStrength = tag.contains(SIGNAL_STRENGTH_TAG) ? tag.getInt(SIGNAL_STRENGTH_TAG) : 15;
		tickCounter = tag.getInt(TICK_COUNTER_TAG);
		pulseTicks = tag.getInt(PULSE_TICKS_TAG);
	}

	private int getDetectionInterval() {
		return detectionIntervalValue == null ? DEFAULT_INTERVAL : detectionIntervalValue.getValue();
	}

	private SchrodingersCatOutputMode getOutputMode() {
		return outputModeValue == null ? SchrodingersCatOutputMode.SUSTAINED : outputModeValue.get();
	}

	private void onDetectionIntervalChanged(int newInterval) {
		tickCounter = 0;
		setChanged();
		sendData();
	}

	private void onOutputModeChanged(int newValue) {
		pulseTicks = 0;
		setChanged();
		sendData();

		if (level != null && !level.isClientSide)
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
	}

	private String formatDetectionInterval(int value) {
		if (value < 60)
			return value + "t";
		if (value < 20 * 60)
			return value / 20 + "s";
		return value / 20 / 60 + "m";
	}

	private static class DetectionIntervalValueBehaviour extends ScrollValueBehaviour {

		private static final BehaviourType<DetectionIntervalValueBehaviour> TYPE = new BehaviourType<>();
		private static final String TAG = "DetectionInterval";

		public DetectionIntervalValueBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
			super(Component.translatable("create_biotech.schrodingers_cat.interval.label"), be, slot);
		}

		@Override
		public BehaviourType<?> getType() {
			return TYPE;
		}

		@Override
		public void write(CompoundTag nbt, boolean clientPacket) {
			nbt.putInt(TAG, value);
		}

		@Override
		public void read(CompoundTag nbt, boolean clientPacket) {
			if (nbt.contains(TAG))
				value = nbt.getInt(TAG);
		}

		@Override
		public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
			return new ValueSettingsBoard(label, 60, 10,
				CreateLang.translatedOptions("generic.unit", "ticks", "seconds", "minutes"),
				new ValueSettingsFormatter(this::formatSettings));
		}

		@Override
		public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
			int value = Math.max(1, valueSetting.value());
			int multiplier = switch (valueSetting.row()) {
			case 0 -> 1;
			case 1 -> 20;
			default -> 60 * 20;
			};
			if (!valueSetting.equals(getValueSettings()))
				playFeedbackSound(this);
			setValue(value * multiplier);
		}

		@Override
		public ValueSettings getValueSettings() {
			int row = 0;
			int value = this.value;

			if (value > 60 * 20) {
				value /= 60 * 20;
				row = 2;
			} else if (value > 60) {
				value /= 20;
				row = 1;
			}

			return new ValueSettings(row, value);
		}

		public MutableComponent formatSettings(ValueSettings settings) {
			int value = Math.max(1, settings.value());
			return Component.literal(switch (settings.row()) {
			case 0 -> value + "t";
			case 1 -> "0:" + (value < 10 ? "0" : "") + value;
			default -> value + ":00";
			});
		}

		@Override
		public String getClipboardKey() {
			return "CatTimings";
		}

		@Override
		public int netId() {
			return DETECTION_INTERVAL_NET_ID;
		}

	}

	private static class OutputModeValueBehaviour extends ScrollOptionBehaviour<SchrodingersCatOutputMode> {

		private static final BehaviourType<OutputModeValueBehaviour> TYPE = new BehaviourType<>();
		private static final String TAG = "OutputMode";

		public OutputModeValueBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
			super(SchrodingersCatOutputMode.class,
				Component.translatable("create_biotech.schrodingers_cat.output_mode.label"), be, slot);
		}

		@Override
		public BehaviourType<?> getType() {
			return TYPE;
		}

		@Override
		public void write(CompoundTag nbt, boolean clientPacket) {
			nbt.putInt(TAG, value);
		}

		@Override
		public void read(CompoundTag nbt, boolean clientPacket) {
			if (nbt.contains(TAG))
				value = nbt.getInt(TAG);
		}

		@Override
		public String getClipboardKey() {
			return "CatOutputMode";
		}

		@Override
		public int netId() {
			return OUTPUT_MODE_NET_ID;
		}

	}

	private enum SchrodingersCatOutputMode implements INamedIconOptions {
		SUSTAINED(AllIcons.I_ACTIVE),
		PULSE(AllIcons.I_MTD_REPLAY);

		private final AllIcons icon;
		private final String translationKey;

		SchrodingersCatOutputMode(AllIcons icon) {
			this.icon = icon;
			this.translationKey = CreateBiotech.MOD_ID + ".schrodingers_cat.output_mode." + Lang.asId(name());
		}

		@Override
		public AllIcons getIcon() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}
	}

	private static class SchrodingersCatSideValueBoxTransform extends ValueBoxTransform {

		private final boolean left;

		public SchrodingersCatSideValueBoxTransform(boolean left) {
			this.left = left;
		}

		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 5, 13.5),
				AngleHelper.horizontalAngle(getSide(state)), Axis.Y);
		}

		@Override
		public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
			TransformStack.of(ms)
				.rotateYDegrees(AngleHelper.horizontalAngle(getSide(state)) + 180);
		}

		private Direction getSide(BlockState state) {
			Direction front = state.getValue(SchrodingersCatBlock.FACING);
			return left ? front.getCounterClockWise() : front.getClockWise();
		}
	}
}

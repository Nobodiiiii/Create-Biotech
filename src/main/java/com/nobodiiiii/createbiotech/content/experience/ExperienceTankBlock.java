package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraftforge.common.util.ForgeSoundType;

public class ExperienceTankBlock extends Block implements IWrenchable, IBE<ExperienceTankBlockEntity> {
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

	public static final SoundType SILENCED_METAL =
		new ForgeSoundType(0.1F, 1.5F, () -> SoundEvents.METAL_BREAK, () -> SoundEvents.METAL_STEP,
			() -> SoundEvents.METAL_PLACE, () -> SoundEvents.METAL_HIT, () -> SoundEvents.METAL_FALL);

	public ExperienceTankBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(TOP, true)
			.setValue(BOTTOM, true)
			.setValue(SHAPE, Shape.WINDOW));
	}

	public static boolean isTank(BlockState state) {
		return state.getBlock() instanceof ExperienceTankBlock;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(TOP, BOTTOM, SHAPE);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		if (oldState.getBlock() == state.getBlock() || moved)
			return;
		withBlockEntityDo(level, pos, ExperienceTankBlockEntity::requestConnectivityUpdate);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (context.getLevel().isClientSide)
			return InteractionResult.SUCCESS;
		withBlockEntityDo(context.getLevel(), context.getClickedPos(), ExperienceTankBlockEntity::toggleWindows);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
			BlockEntity be = level.getBlockEntity(pos);
			if (!(be instanceof ExperienceTankBlockEntity tank)) {
				super.onRemove(state, level, pos, newState, isMoving);
				return;
			}
			level.removeBlockEntity(pos);
			ExperienceTankBlockEntity.splitTankAndInvalidate(tank, pos);
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		if (mirror == Mirror.NONE)
			return state;
		boolean x = mirror == Mirror.FRONT_BACK;
		return switch (state.getValue(SHAPE)) {
			case WINDOW_NE -> state.setValue(SHAPE, x ? Shape.WINDOW_NW : Shape.WINDOW_SE);
			case WINDOW_NW -> state.setValue(SHAPE, x ? Shape.WINDOW_NE : Shape.WINDOW_SW);
			case WINDOW_SE -> state.setValue(SHAPE, x ? Shape.WINDOW_SW : Shape.WINDOW_NE);
			case WINDOW_SW -> state.setValue(SHAPE, x ? Shape.WINDOW_SE : Shape.WINDOW_NW);
			default -> state;
		};
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		for (int i = 0; i < rotation.ordinal(); i++)
			state = rotateOnce(state);
		return state;
	}

	private BlockState rotateOnce(BlockState state) {
		return switch (state.getValue(SHAPE)) {
			case WINDOW_NE -> state.setValue(SHAPE, Shape.WINDOW_SE);
			case WINDOW_NW -> state.setValue(SHAPE, Shape.WINDOW_NE);
			case WINDOW_SE -> state.setValue(SHAPE, Shape.WINDOW_SW);
			case WINDOW_SW -> state.setValue(SHAPE, Shape.WINDOW_NW);
			default -> state;
		};
	}

	@Override
	public SoundType getSoundType(BlockState state, LevelReader world, BlockPos pos, Entity entity) {
		SoundType soundType = super.getSoundType(state, world, pos, entity);
		if (entity != null && entity.getPersistentData()
			.contains("SilenceTankSound"))
			return SILENCED_METAL;
		return soundType;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		ExperienceTankBlockEntity tank = ConnectivityHandler.partAt(getBlockEntityType(), level, pos);
		if (tank == null)
			return 0;
		float fill = tank.getFillState();
		return fill <= 0 ? 0 : Mth.clamp(Mth.ceil(fill * 15.0f), 1, 15);
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public Class<ExperienceTankBlockEntity> getBlockEntityClass() {
		return ExperienceTankBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ExperienceTankBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.EXPERIENCE_TANK.get();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state,
		BlockEntityType<S> type) {
		return type == getBlockEntityType() ? (BlockEntityTicker<S>) (BlockEntityTicker<ExperienceTankBlockEntity>) ExperienceTankBlockEntity::tick
			: null;
	}
}

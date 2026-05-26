package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.decoration.encasing.EncasableBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchableWithBracket;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class ExperiencePipeBlock extends PipeBlock
	implements SimpleWaterloggedBlock, IWrenchableWithBracket, IBE<ExperiencePipeBlockEntity>, EncasableBlock,
	TransformableBlock {
	private static final VoxelShape OCCLUSION_BOX = Block.box(4, 4, 4, 12, 12, 12);

	public ExperiencePipeBlock(Properties properties) {
		super(4 / 16f, properties);
		registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return tryRemoveBracket(context) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult ray) {
		ItemStack heldItem = player.getItemInHand(hand);
		InteractionResult result = tryEncase(state, world, pos, heldItem, player, hand, ray);
		return result.consumesAction() ? result : InteractionResult.PASS;
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		boolean blockTypeChanged = state.getBlock() != newState.getBlock();
		if (blockTypeChanged && !world.isClientSide)
			ExperiencePropagator.propagateChangedPipe(world, pos, state);
		if (state != newState && !isMoving)
			removeBracket(world, pos, true).ifPresent(stack -> Block.popResource(world, pos, stack));
		if (state.hasBlockEntity() && (blockTypeChanged || !newState.hasBlockEntity()))
			world.removeBlockEntity(pos);
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (!world.isClientSide && state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock, BlockPos neighborPos,
		boolean isMoving) {
		DebugPackets.sendNeighborsUpdatePacket(world, pos);
		Direction direction = ExperiencePropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos,
			isMoving);
		if (direction == null || !isOpenAt(state, direction))
			return;
		world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
		ExperiencePropagator.propagateChangedPipe(world, pos, state);
	}

	public static boolean isPipe(BlockState state) {
		return state.getBlock() instanceof ExperiencePipeBlock || state.getBlock() instanceof ExperienceEncasedPipeBlock;
	}

	public static boolean canConnectTo(BlockAndTintGetter world, BlockPos neighborPos, BlockState neighbor,
		Direction direction) {
		if (ExperiencePropagator.hasExperienceEndpoint(world, neighborPos, direction.getOpposite()))
			return true;
		ExperienceTransportBehaviour transport = BlockEntityBehaviour.get(world, neighborPos, ExperienceTransportBehaviour.TYPE);
		BracketedBlockEntityBehaviour bracket = BlockEntityBehaviour.get(world, neighborPos, BracketedBlockEntityBehaviour.TYPE);
		if (neighbor.getBlock() instanceof ExperiencePipeBlock)
			return bracket == null || !bracket.isBracketPresent()
				|| ExperiencePropagator.getStraightPipeAxis(neighbor) == direction.getAxis();
		if (transport == null)
			return false;
		return transport.canHaveFlowToward(neighbor, direction.getOpposite());
	}

	public static boolean shouldDrawRim(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction direction) {
		BlockPos offsetPos = pos.relative(direction);
		BlockState facingState = world.getBlockState(offsetPos);
		if (facingState.getBlock() instanceof ExperienceEncasedPipeBlock)
			return true;
		if (!isPipe(facingState))
			return true;
		return !canConnectTo(world, offsetPos, facingState, direction);
	}

	public static boolean shouldDrawCasing(BlockAndTintGetter world, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof ExperiencePipeBlock))
			return false;
		for (Direction.Axis axis : Iterate.axes) {
			int connections = 0;
			for (Direction direction : Iterate.directions)
				if (direction.getAxis() != axis && isOpenAt(state, direction))
					connections++;
			if (connections > 2)
				return true;
		}
		return false;
	}

	public static boolean isOpenAt(BlockState state, Direction direction) {
		return state.getValue(PROPERTY_BY_DIRECTION.get(direction));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
		return updateBlockState(defaultBlockState(), context.getNearestLookingDirection(), null, context.getLevel(),
			context.getClickedPos()).setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor world,
		BlockPos pos, BlockPos neighbourPos) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
		if (isOpenAt(state, direction) && neighbourState.hasProperty(BlockStateProperties.WATERLOGGED))
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return updateBlockState(state, direction, direction.getOpposite(), world, pos);
	}

	public BlockState updateBlockState(BlockState state, Direction preferredDirection, @Nullable Direction ignore,
		BlockAndTintGetter world, BlockPos pos) {
		BracketedBlockEntityBehaviour bracket = BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);
		if (bracket != null && bracket.isBracketPresent())
			return state;

		BlockState previousState = state;
		int previousSides = (int) Arrays.stream(Iterate.directions)
			.map(PROPERTY_BY_DIRECTION::get)
			.filter(previousState::getValue)
			.count();

		for (Direction direction : Iterate.directions)
			if (direction != ignore) {
				boolean shouldConnect = canConnectTo(world, pos.relative(direction), world.getBlockState(pos.relative(direction)),
					direction);
				state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), shouldConnect);
			}

		Direction connectedDirection = null;
		for (Direction direction : Iterate.directions) {
			if (!isOpenAt(state, direction))
				continue;
			if (connectedDirection != null)
				return state;
			connectedDirection = direction;
		}

		if (connectedDirection != null)
			return state.setValue(PROPERTY_BY_DIRECTION.get(connectedDirection.getOpposite()), true);
		if (previousSides == 2)
			return previousState;
		return state.setValue(PROPERTY_BY_DIRECTION.get(preferredDirection), true)
			.setValue(PROPERTY_BY_DIRECTION.get(preferredDirection.getOpposite()), true);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false)
			: Fluids.EMPTY.defaultFluidState();
	}

	@Override
	public Optional<ItemStack> removeBracket(BlockGetter world, BlockPos pos, boolean inOnReplacedContext) {
		BracketedBlockEntityBehaviour behaviour =
			BracketedBlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);
		if (behaviour == null)
			return Optional.empty();
		BlockState bracket = behaviour.removeBracket(inOnReplacedContext);
		return bracket == null ? Optional.empty() : Optional.of(new ItemStack(bracket.getBlock()));
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public Class<ExperiencePipeBlockEntity> getBlockEntityClass() {
		return ExperiencePipeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ExperiencePipeBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.EXPERIENCE_PIPE.get();
	}

	@Override
	public boolean supportsExternalFaceHiding(BlockState state) {
		return false;
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
		return OCCLUSION_BOX;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return ExperiencePipeBlockRotation.rotate(state, rotation);
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return ExperiencePipeBlockRotation.mirror(state, mirror);
	}

	@Override
	public BlockState transform(BlockState state, StructureTransform transform) {
		return ExperiencePipeBlockRotation.transform(state, transform);
	}
}

package com.nobodiiiii.createbiotech.content.experience.pipe;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.DOWN;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.UP;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;

import java.util.Map;
import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.block.IBE;

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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.ticks.TickPriority;

public class ExperienceEncasedPipeBlock extends Block
	implements IWrenchable, SpecialBlockItemRequirement, IBE<ExperiencePipeBlockEntity>, EncasedBlock,
	TransformableBlock {
	public static final Map<Direction, BooleanProperty> FACING_TO_PROPERTY_MAP = PipeBlock.PROPERTY_BY_DIRECTION;

	private final Supplier<Block> casing;

	public ExperienceEncasedPipeBlock(Properties properties, Supplier<Block> casing) {
		super(properties);
		this.casing = casing;
		registerDefaultState(defaultBlockState().setValue(NORTH, false)
			.setValue(SOUTH, false)
			.setValue(DOWN, false)
			.setValue(UP, false)
			.setValue(WEST, false)
			.setValue(EAST, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		boolean blockTypeChanged = state.getBlock() != newState.getBlock();
		if (blockTypeChanged && !world.isClientSide)
			ExperiencePropagator.propagateChangedPipe(world, pos, state);
		if (state.hasBlockEntity() && (blockTypeChanged || !newState.hasBlockEntity()))
			world.removeBlockEntity(pos);
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (!world.isClientSide && state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter world, BlockPos pos,
		Player player) {
		return CBItems.EXPERIENCE_PIPE.get().getDefaultInstance();
	}

	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock, BlockPos neighborPos,
		boolean isMoving) {
		DebugPackets.sendNeighborsUpdatePacket(world, pos);
		Direction direction = ExperiencePropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos,
			isMoving);
		if (direction == null || !state.getValue(FACING_TO_PROPERTY_MAP.get(direction)))
			return;
		world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
		ExperiencePropagator.propagateChangedPipe(world, pos, state);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (world.isClientSide)
			return InteractionResult.SUCCESS;

		context.getLevel().levelEvent(2001, pos, Block.getId(state));
		BlockState equivalentPipe = transferSixWayProperties(state, com.nobodiiiii.createbiotech.registry.CBBlocks.EXPERIENCE_PIPE.get().defaultBlockState());

		Direction firstFound = Direction.UP;
		for (Direction direction : Iterate.directions)
			if (state.getValue(FACING_TO_PROPERTY_MAP.get(direction))) {
				firstFound = direction;
				break;
			}

		ExperienceTransportBehaviour.cacheFlows(world, pos);
		world.setBlockAndUpdate(pos,
			com.nobodiiiii.createbiotech.registry.CBBlocks.EXPERIENCE_PIPE.get().updateBlockState(equivalentPipe,
				firstFound, null, world, pos));
		ExperienceTransportBehaviour.loadFlows(world, pos);
		return InteractionResult.SUCCESS;
	}

	public static BlockState transferSixWayProperties(BlockState from, BlockState to) {
		for (Direction direction : Iterate.directions) {
			BooleanProperty property = FACING_TO_PROPERTY_MAP.get(direction);
			to = to.setValue(property, from.getValue(property));
		}
		return to;
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, BlockEntity be) {
		return ItemRequirement.of(com.nobodiiiii.createbiotech.registry.CBBlocks.EXPERIENCE_PIPE.get().defaultBlockState(), be);
	}

	@Override
	public Class<ExperiencePipeBlockEntity> getBlockEntityClass() {
		return ExperiencePipeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ExperiencePipeBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.ENCASED_EXPERIENCE_PIPE.get();
	}

	@Override
	public Block getCasing() {
		return casing.get();
	}

	@Override
	public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem, Player player,
		InteractionHand hand, BlockHitResult ray) {
		ExperienceTransportBehaviour.cacheFlows(level, pos);
		level.setBlockAndUpdate(pos, transferSixWayProperties(state, defaultBlockState()));
		ExperienceTransportBehaviour.loadFlows(level, pos);
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

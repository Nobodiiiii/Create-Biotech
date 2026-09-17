package com.nobodiiiii.createbiotech.content.giantfrog;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.foundation.block.CBMultiBlockLifecycle;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GiantFrogBlock extends BaseEntityBlock implements CBMultiBlockLifecycle.Part {
	public static final MapCodec<GiantFrogBlock> CODEC = simpleCodec(GiantFrogBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final IntegerProperty X_OFFSET = IntegerProperty.create("x", 0, 2);
	public static final IntegerProperty Y_OFFSET = IntegerProperty.create("y", 0, 1);
	public static final IntegerProperty Z_OFFSET = IntegerProperty.create("z", 0, 2);

	public static final float FROG_SCALE = 4.0f;
	private static final double PIXELS_PER_BLOCK = 16.0d;
	private static final int OCCUPIED_WIDTH = 3;
	private static final int OCCUPIED_HEIGHT = 2;
	private static final int CENTER_OFFSET = OCCUPIED_WIDTH / 2;
	private static final int HORIZONTAL_DIRECTIONS = 4;
	private static final double FOOTPRINT_PIXELS = OCCUPIED_WIDTH * PIXELS_PER_BLOCK;
	private static final double MODEL_ORIGIN_X = FOOTPRINT_PIXELS / 2.0d;
	private static final double MODEL_ORIGIN_Y = 8.0d;
	private static final double MODEL_ORIGIN_Z = FOOTPRINT_PIXELS / 2.0d;
	private static final double FACING_OFFSET_CORRECTION = 12.0d;

	public static final double BODY_WIDTH = 28.0d / PIXELS_PER_BLOCK;
	public static final double BODY_LENGTH = 36.0d / PIXELS_PER_BLOCK;
	public static final double BODY_HEIGHT = 20.0d / PIXELS_PER_BLOCK;

	private static final PixelBox BODY_BOX = modelBox(-14.0d, -4.0d, -28.0d, 28.0d, 20.0d, 36.0d);
	private static final PixelBox[] COLLISION_BOXES = {
		BODY_BOX,
		modelBox(-20.0d, -8.0d, -26.0d, 8.0d, 12.0d, 12.0d),
		modelBox(12.0d, -8.0d, -26.0d, 8.0d, 12.0d, 12.0d),
		modelBox(-22.0d, -8.0d, -4.0d, 12.0d, 12.0d, 16.0d),
		modelBox(10.0d, -8.0d, -4.0d, 12.0d, 12.0d, 16.0d)
	};
	private static final VoxelShape[] COLLISION_SHAPES = makeCollisionShapes();
	private static final AABB BODY_BOUNDS = BODY_BOX.boundsForAllFacings();

	public GiantFrogBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(X_OFFSET, CENTER_OFFSET)
			.setValue(Y_OFFSET, 0)
			.setValue(Z_OFFSET, CENTER_OFFSET));
	}

	@Override
	protected MapCodec<? extends GiantFrogBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, X_OFFSET, Y_OFFSET, Z_OFFSET);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		if (!canPlaceAt(context.getLevel(), pos, context))
			return null;
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		// Placed on both sides like vanilla doors do: leaving the client with a lone
		// anchor makes the frog's shape pop in only once the server's updates arrive.
		BlockState partState = defaultBlockState().setValue(FACING, state.getValue(FACING));
		forEachOccupiedOffset((x, y, z) -> {
			if (x == CENTER_OFFSET && y == 0 && z == CENTER_OFFSET)
				return;
			level.setBlock(pos.offset(x - CENTER_OFFSET, y, z - CENTER_OFFSET),
				partState.setValue(X_OFFSET, x).setValue(Y_OFFSET, y).setValue(Z_OFFSET, z),
				Block.UPDATE_ALL);
		});

		if (level.isClientSide)
			return;
		Long index = stack.get(CBDataComponents.FROG_STOMACH_SPACE.get());
		if (index != null && level.getBlockEntity(pos) instanceof GiantFrogBlockEntity frog)
			frog.setSpaceIndex(index);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		// Catches parts that arrive without going through setPlacedBy, such as a
		// /setblock of a single leg, which would otherwise linger as a ghost block.
		scheduleStructureCheck(level, pos, state);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		// Deferred on purpose: validating here would run the 18-position scan on the
		// client too, force-load whichever chunk the far side of the frog sits in, and
		// repeat the whole scan once per neighbour update instead of once per tick.
		scheduleStructureCheck(level, pos, state);
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (isValidStructure(level, pos, state))
			return;
		removeStructure(level, pos, state);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// Survival breaks let the scheduled teardown destroy the anchor so its loot
		// table decides what drops. Creative has to take the anchor out itself,
		// without drops, exactly like vanilla's double blocks.
		if (!level.isClientSide && !isMain(state) && player.isCreative()) {
			BlockPos mainPos = getMainPos(pos, state);
			if (belongsTo(level, mainPos, mainPos))
				CBMultiBlockLifecycle.removeAnchorInCreative(level, mainPos, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.getBlock() == newState.getBlock()) {
			super.onRemove(state, level, pos, newState, isMoving);
			return;
		}

		if (isMain(state) && level.getBlockEntity(pos) instanceof GiantFrogBlockEntity frog)
			frog.dropBeltTransferItem();

		// Neighbour updates only reach the parts touching this one, so the rest of the
		// frog has to be told to re-check itself.
		scheduleStructureCheck(level, pos, state);

		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getBodyShape(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getBodyShape(state);
	}

	@Override
	public PushReaction getPistonPushReaction(BlockState state) {
		return PushReaction.BLOCK;
	}

	@Override
	public BlockPos getMultiBlockAnchor(BlockPos pos, BlockState state) {
		return getMainPos(pos, state);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return isMain(state) || isMouthInputPart(state) ? new GiantFrogBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		// Every position that gets a block entity gets a ticker: a SmartBlockEntity only
		// runs initialize() - which fires BlockEntityBehaviourEvent and starts its
		// behaviours - from its first tick, so an unticked mouth would keep a
		// DirectBeltInputBehaviour that never came up. The tick body itself returns
		// straight after the behaviour pass for anything but the anchor.
		if (!isMain(state) && !isMouthInputPart(state))
			return null;
		return createTickerHelper(type, CBBlockEntityTypes.GIANT_FROG.get(), GiantFrogBlockEntity::tick);
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		// Which parts drop is the loot table's call; all this adds is the stomach the
		// frog is bound to, which lives in the block entity rather than in a component.
		List<ItemStack> drops = super.getDrops(state, builder);
		if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof GiantFrogBlockEntity frog)
			drops.forEach(stack -> addSpaceIndex(stack, frog));
		return drops;
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
		Player player) {
		return createDropStack(level, getMainPos(pos, state));
	}

	public static AABB getBodyBounds(BlockPos pos) {
		return BODY_BOUNDS.move(pos);
	}

	public static AABB getBodyBounds(BlockPos pos, BlockState state) {
		Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
		return BODY_BOX.rotateTo(facing)
			.offsetAwayFrom(facing, FACING_OFFSET_CORRECTION)
			.toAabb(pos);
	}

	private static boolean canPlaceAt(Level level, BlockPos mainPos, BlockPlaceContext context) {
		if (mainPos.getY() + OCCUPIED_HEIGHT > level.getMaxBuildHeight())
			return false;

		for (int y = 0; y < OCCUPIED_HEIGHT; y++) {
			for (int x = 0; x < OCCUPIED_WIDTH; x++) {
				for (int z = 0; z < OCCUPIED_WIDTH; z++) {
					BlockPos partPos = mainPos.offset(x - CENTER_OFFSET, y, z - CENTER_OFFSET);
					if (!level.getWorldBorder().isWithinBounds(partPos))
						return false;
					if (!level.getBlockState(partPos).canBeReplaced(context))
						return false;
				}
			}
		}
		return true;
	}

	private static boolean isValidStructure(LevelReader level, BlockPos pos, BlockState state) {
		BlockPos mainPos = getMainPos(pos, state);
		Direction facing = state.getValue(FACING);

		for (int y = 0; y < OCCUPIED_HEIGHT; y++) {
			for (int x = 0; x < OCCUPIED_WIDTH; x++) {
				for (int z = 0; z < OCCUPIED_WIDTH; z++) {
					BlockPos partPos = mainPos.offset(x - CENTER_OFFSET, y, z - CENTER_OFFSET);
					// An unloaded neighbour chunk is not evidence of a broken frog, and
					// reading it would force it in from disk on a block-update path.
					if (!CBMultiBlockLifecycle.isLoaded(level, partPos))
						continue;
					BlockState partState = level.getBlockState(partPos);
					if (!(partState.getBlock() instanceof GiantFrogBlock))
						return false;
					if (partState.getValue(FACING) != facing
						|| partState.getValue(X_OFFSET) != x
						|| partState.getValue(Y_OFFSET) != y
						|| partState.getValue(Z_OFFSET) != z)
						return false;
				}
			}
		}
		return true;
	}

	/** Whether the frog part at {@code partPos}, if any, is part of the frog anchored at {@code mainPos}. */
	private static boolean belongsTo(LevelReader level, BlockPos partPos, BlockPos mainPos) {
		BlockState partState = level.getBlockState(partPos);
		return partState.getBlock() instanceof GiantFrogBlock && getMainPos(partPos, partState).equals(mainPos);
	}

	/**
	 * Asks every part of this frog to re-check itself next tick. The anchor's own check
	 * already covers the whole structure, so while it stands it speaks for all of them -
	 * which also collapses the eighteen updates a placement produces into one check.
	 * Once the anchor is gone the orphans have to clean themselves up individually.
	 */
	private void scheduleStructureCheck(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level.isClientSide())
			return;

		BlockPos mainPos = getMainPos(pos, state);
		if (CBMultiBlockLifecycle.isLoaded(level, mainPos) && belongsTo(level, mainPos, mainPos)) {
			CBMultiBlockLifecycle.scheduleValidation(level, mainPos, this);
			return;
		}

		forEachOccupiedOffset((x, y, z) -> {
			BlockPos partPos = mainPos.offset(x - CENTER_OFFSET, y, z - CENTER_OFFSET);
			if (CBMultiBlockLifecycle.isLoaded(level, partPos) && belongsTo(level, partPos, mainPos))
				CBMultiBlockLifecycle.scheduleValidation(level, partPos, this);
		});
	}

	/**
	 * Tears down every part of the frog anchored at this position. Only the anchor may
	 * drop anything, and it does so through {@code destroyBlock} so that its loot table
	 * - not this method - decides what the player gets. Runs from a scheduled tick, so
	 * a second part reaching the same conclusion this tick finds nothing left to do.
	 */
	private static void removeStructure(Level level, BlockPos pos, BlockState state) {
		BlockPos mainPos = getMainPos(pos, state);
		boolean anchorPresent = belongsTo(level, mainPos, mainPos);

		forEachOccupiedOffset((x, y, z) -> {
			BlockPos partPos = mainPos.offset(x - CENTER_OFFSET, y, z - CENTER_OFFSET);
			if (!partPos.equals(mainPos) && belongsTo(level, partPos, mainPos))
				CBMultiBlockLifecycle.removeSilently(level, partPos);
		});

		if (anchorPresent)
			level.destroyBlock(mainPos, true);
	}

	public static boolean isMain(BlockState state) {
		return state.getValue(X_OFFSET) == CENTER_OFFSET && state.getValue(Y_OFFSET) == 0
			&& state.getValue(Z_OFFSET) == CENTER_OFFSET;
	}

	public static boolean isMouthInputPart(BlockState state) {
		if (state.getValue(Y_OFFSET) != 0)
			return false;
		Direction facing = state.getValue(FACING);
		int x = state.getValue(X_OFFSET);
		int z = state.getValue(Z_OFFSET);
		return switch (facing) {
			case NORTH -> x == CENTER_OFFSET && z == 0;
			case SOUTH -> x == CENTER_OFFSET && z == OCCUPIED_WIDTH - 1;
			case WEST -> x == 0 && z == CENTER_OFFSET;
			case EAST -> x == OCCUPIED_WIDTH - 1 && z == CENTER_OFFSET;
			default -> false;
		};
	}

	public static BlockPos getMouthInputPos(BlockPos mainPos, BlockState state) {
		return mainPos.relative(state.getValue(FACING));
	}

	/** The air block one block beyond the front edge of the frog's mouth. */
	public static BlockPos getMouthExitPos(BlockPos mainPos, Direction facing) {
		return mainPos.relative(facing, CENTER_OFFSET + 1);
	}

	/** The air block one block beyond the rear edge of the frog. */
	public static BlockPos getTailExitPos(BlockPos mainPos, Direction facing) {
		return mainPos.relative(facing.getOpposite(), CENTER_OFFSET + 1);
	}

	private static ItemStack createDropStack(BlockGetter level, BlockPos mainPos) {
		ItemStack stack = new ItemStack(level.getBlockState(mainPos).getBlock());
		if (level.getBlockEntity(mainPos) instanceof GiantFrogBlockEntity frog)
			addSpaceIndex(stack, frog);
		return stack;
	}

	private static void addSpaceIndex(ItemStack stack, GiantFrogBlockEntity frog) {
		if (frog.hasSpace() && stack.getItem() instanceof GiantFrogItem)
			stack.set(CBDataComponents.FROG_STOMACH_SPACE.get(), frog.getSpaceIndex());
	}

	public static BlockPos getMainPos(BlockPos pos, BlockState state) {
		return pos.offset(CENTER_OFFSET - state.getValue(X_OFFSET), -state.getValue(Y_OFFSET),
			CENTER_OFFSET - state.getValue(Z_OFFSET));
	}

	private static VoxelShape getBodyShape(BlockState state) {
		return COLLISION_SHAPES[shapeIndex(state.getValue(FACING), state.getValue(X_OFFSET),
			state.getValue(Y_OFFSET), state.getValue(Z_OFFSET))];
	}

	private static VoxelShape[] makeCollisionShapes() {
		VoxelShape[] shapes = new VoxelShape[HORIZONTAL_DIRECTIONS * OCCUPIED_WIDTH * OCCUPIED_HEIGHT
			* OCCUPIED_WIDTH];
		for (Direction facing : Direction.Plane.HORIZONTAL)
			forEachOccupiedOffset((x, y, z) ->
				shapes[shapeIndex(facing, x, y, z)] = createCollisionShape(facing, x, y, z));
		return shapes;
	}

	private static VoxelShape createCollisionShape(Direction facing, int x, int y, int z) {
		VoxelShape shape = Shapes.empty();
		for (PixelBox box : COLLISION_BOXES) {
			VoxelShape part = createPartShape(box.rotateTo(facing).offsetAwayFrom(facing, FACING_OFFSET_CORRECTION),
				x, y, z);
			if (!part.isEmpty())
				shape = Shapes.or(shape, part);
		}
		return shape.optimize();
	}

	private static VoxelShape createPartShape(PixelBox box, int x, int y, int z) {
		double partX = x * PIXELS_PER_BLOCK;
		double partY = y * PIXELS_PER_BLOCK;
		double partZ = z * PIXELS_PER_BLOCK;
		double minX = clampMinPixel(box.minX - partX, x);
		double maxX = clampMaxPixel(box.maxX - partX, x);
		double minY = clampPixel(box.minY - partY);
		double maxY = clampPixel(box.maxY - partY);
		double minZ = clampMinPixel(box.minZ - partZ, z);
		double maxZ = clampMaxPixel(box.maxZ - partZ, z);
		if (minX >= maxX || minY >= maxY || minZ >= maxZ)
			return Shapes.empty();
		return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static double clampPixel(double pixels) {
		return Math.max(0.0d, Math.min(PIXELS_PER_BLOCK, pixels));
	}

	private static double clampMinPixel(double pixels, int offset) {
		return offset == 0 ? pixels : Math.max(0.0d, pixels);
	}

	private static double clampMaxPixel(double pixels, int offset) {
		return offset == OCCUPIED_WIDTH - 1 ? pixels : Math.min(PIXELS_PER_BLOCK, pixels);
	}

	private static PixelBox modelBox(double originX, double originY, double originZ, double sizeX, double sizeY,
		double sizeZ) {
		double minX = MODEL_ORIGIN_X + originX;
		double minY = MODEL_ORIGIN_Y + originY;
		double minZ = MODEL_ORIGIN_Z - originZ - sizeZ;
		return new PixelBox(minX, minY, minZ, minX + sizeX, minY + sizeY, MODEL_ORIGIN_Z - originZ);
	}

	private static int shapeIndex(Direction facing, int x, int y, int z) {
		return facing.get2DDataValue() * OCCUPIED_WIDTH * OCCUPIED_HEIGHT * OCCUPIED_WIDTH + shapeIndex(x, y, z);
	}

	private static int shapeIndex(int x, int y, int z) {
		return (y * OCCUPIED_WIDTH + z) * OCCUPIED_WIDTH + x;
	}

	private static void forEachOccupiedOffset(OffsetConsumer consumer) {
		for (int y = 0; y < OCCUPIED_HEIGHT; y++)
			for (int x = 0; x < OCCUPIED_WIDTH; x++)
				for (int z = 0; z < OCCUPIED_WIDTH; z++)
					consumer.accept(x, y, z);
	}

	@FunctionalInterface
	private interface OffsetConsumer {
		void accept(int x, int y, int z);
	}

	private record PixelBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		PixelBox rotateTo(Direction facing) {
			return switch (facing) {
				case NORTH -> new PixelBox(FOOTPRINT_PIXELS - maxX, minY, FOOTPRINT_PIXELS - maxZ,
					FOOTPRINT_PIXELS - minX, maxY, FOOTPRINT_PIXELS - minZ);
				case EAST -> new PixelBox(minZ, minY, FOOTPRINT_PIXELS - maxX, maxZ, maxY,
					FOOTPRINT_PIXELS - minX);
				case WEST -> new PixelBox(FOOTPRINT_PIXELS - maxZ, minY, minX, FOOTPRINT_PIXELS - minZ, maxY, maxX);
				default -> this;
			};
		}

		AABB boundsForAllFacings() {
			double minX = Double.POSITIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			double maxZ = Double.NEGATIVE_INFINITY;
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				PixelBox box = rotateTo(facing).offsetAwayFrom(facing, FACING_OFFSET_CORRECTION);
				minX = Math.min(minX, box.minX);
				minY = Math.min(minY, box.minY);
				minZ = Math.min(minZ, box.minZ);
				maxX = Math.max(maxX, box.maxX);
				maxY = Math.max(maxY, box.maxY);
				maxZ = Math.max(maxZ, box.maxZ);
			}
			return new AABB(minX / PIXELS_PER_BLOCK - CENTER_OFFSET, minY / PIXELS_PER_BLOCK,
				minZ / PIXELS_PER_BLOCK - CENTER_OFFSET, maxX / PIXELS_PER_BLOCK - CENTER_OFFSET,
				maxY / PIXELS_PER_BLOCK, maxZ / PIXELS_PER_BLOCK - CENTER_OFFSET);
		}

		PixelBox offsetAwayFrom(Direction facing, double pixels) {
			return switch (facing) {
				case NORTH -> new PixelBox(minX, minY, minZ + pixels, maxX, maxY, maxZ + pixels);
				case SOUTH -> new PixelBox(minX, minY, minZ - pixels, maxX, maxY, maxZ - pixels);
				case EAST -> new PixelBox(minX - pixels, minY, minZ, maxX - pixels, maxY, maxZ);
				case WEST -> new PixelBox(minX + pixels, minY, minZ, maxX + pixels, maxY, maxZ);
				default -> this;
			};
		}

		AABB toAabb(BlockPos pos) {
			return new AABB(pos.getX() + minX / PIXELS_PER_BLOCK - CENTER_OFFSET,
				pos.getY() + minY / PIXELS_PER_BLOCK,
				pos.getZ() + minZ / PIXELS_PER_BLOCK - CENTER_OFFSET,
				pos.getX() + maxX / PIXELS_PER_BLOCK - CENTER_OFFSET,
				pos.getY() + maxY / PIXELS_PER_BLOCK,
				pos.getZ() + maxZ / PIXELS_PER_BLOCK - CENTER_OFFSET);
		}
	}
}

package com.nobodiiiii.createbiotech.content.surgery;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

public class SurgicalKitBlock extends HorizontalDirectionalBlock
	implements IBE<SurgicalKitBlockEntity>, ProperWaterloggedBlock {
	public static final MapCodec<SurgicalKitBlock> CODEC = simpleCodec(SurgicalKitBlock::new);

	public SurgicalKitBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
	}

	@Override
	protected MapCodec<SurgicalKitBlock> codec() {
		return CODEC;
	}

	@Override
	public String getDescriptionId() {
		return Util.makeDescriptionId("item", BuiltInRegistries.BLOCK.getKey(this));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withWater(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		updateWater(level, state, pos);
		return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return AllShapes.TOOLBOX.get(state.getValue(FACING));
	}

	@Override
	public Class<SurgicalKitBlockEntity> getBlockEntityClass() {
		return SurgicalKitBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SurgicalKitBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.SURGICAL_KIT.get();
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		if (!level.isClientSide)
			withBlockEntityDo(level, pos, kit -> kit.storePlacedStack(stack));
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (!(level instanceof ServerLevel) || player instanceof FakePlayer)
			return;
		ItemStack pickedUp = getBlockEntityOptional(level, pos).map(SurgicalKitBlockEntity::copyStoredKit)
			.orElse(ItemStack.EMPTY);
		if (level.destroyBlock(pos, false) && !pickedUp.isEmpty())
			player.getInventory().placeItemBackInInventory(pickedUp);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SurgicalKitBlockEntity kit))
			return super.getDrops(state, params);
		ItemStack drop = kit.copyStoredKit();
		return drop.isEmpty() || super.getDrops(state, params).isEmpty() ? List.of() : List.of(drop);
	}
}

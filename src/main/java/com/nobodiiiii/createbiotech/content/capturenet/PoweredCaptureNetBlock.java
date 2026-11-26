package com.nobodiiiii.createbiotech.content.capturenet;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

public class PoweredCaptureNetBlock extends KineticBlock implements IBE<PoweredCaptureNetBlockEntity>, ICogWheel {

	public static final BooleanProperty CEILING = ArmBlock.CEILING;

	public PoweredCaptureNetBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(CEILING, false));
	}

	public static void storeMountedNet(ItemStack stack, ItemStack net, HolderLookup.Provider registries) {
		var data = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
		ArmCaptureBehaviour.writeNet(data, registries, net.copyWithCount(1));
		BlockItem.setBlockEntityData(stack, CBBlockEntityTypes.POWERED_CAPTURE_NET.get(), data);
	}

	public static @Nullable CaptureNetTier mountedNetTier(ItemStack stack) {
		CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (data == null)
			return null;
		var net = CBItemData.readOnlySnapshot(data).getCompound(ArmCaptureBehaviour.NET_TAG);
		ResourceLocation itemId = ResourceLocation.tryParse(net.getString("id"));
		return itemId != null && BuiltInRegistries.ITEM.get(itemId) instanceof CaptureNetItem item ? item.tier() : null;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(CEILING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(CEILING, context.getClickedFace() == Direction.DOWN);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(CEILING) ? AllShapes.MECHANICAL_ARM_CEILING : AllShapes.MECHANICAL_ARM;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	public Class<PoweredCaptureNetBlockEntity> getBlockEntityClass() {
		return PoweredCaptureNetBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PoweredCaptureNetBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.POWERED_CAPTURE_NET.get();
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof CaptureNetItem) || player instanceof FakePlayer
			|| !player.mayBuild() || !level.mayInteract(player, pos))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		PoweredCaptureNetBlockEntity capture = getBlockEntity(level, pos);
		if (capture == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (CaptureNetItem.isOccupied(stack)) {
			if (!level.isClientSide)
				player.displayClientMessage(Component.translatable("create_biotech.arm_capture.net_occupied"), true);
			return ItemInteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			ItemStack previous = capture.replaceNet(stack.split(1));
			if (!previous.isEmpty())
				player.getInventory().placeItemBackInInventory(previous);
			capture.notifyUpdate();
			server.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.2f);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
			|| !player.mayBuild() || !level.mayInteract(player, pos))
			return InteractionResult.PASS;
		if (level instanceof ServerLevel server) {
			PoweredCaptureNetBlockEntity capture = getBlockEntity(server, pos);
			if (capture == null || !PoweredCaptureNetConversion.restoreArm(server, capture, player))
				return InteractionResult.FAIL;
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}

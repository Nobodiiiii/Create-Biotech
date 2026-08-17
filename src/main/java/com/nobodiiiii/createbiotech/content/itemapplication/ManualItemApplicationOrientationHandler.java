package com.nobodiiiii.createbiotech.content.itemapplication;

import java.util.Optional;
import java.util.Set;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.foundation.utility.BlockHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.wrapper.RecipeWrapper;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ManualItemApplicationOrientationHandler {
	private static final Set<ResourceLocation> CUSTOM_CONVERSIONS = Set.of(
		CreateBiotech.asResource("explosion_proof_item_vault"),
		CreateBiotech.asResource("shulker_packager_manual_only")
	);

	private ManualItemApplicationOrientationHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.isCanceled())
			return;

		Player player = event.getEntity();
		if (!player.mayBuild())
			return;

		Level level = event.getLevel();
		ItemStack heldItem = event.getItemStack();
		BlockPos pos = event.getPos();
		BlockState blockState = level.getBlockState(pos);

		if (heldItem.isEmpty() || blockState.isAir())
			return;

		// Some placed blocks, such as burners, use their state to distinguish empty and filled item variants.
		ItemStack processedItem = blockState.getCloneItemStack(event.getHitVec(), level, pos, player);
		Optional<ManualApplicationRecipe> foundRecipe =
			findCreateBiotechManualApplicationRecipe(level, blockState, processedItem, heldItem);
		if (foundRecipe.isEmpty())
			return;

		ManualApplicationRecipe recipe = foundRecipe.get();
		BlockState transformedBlock = transformBlock(recipe, blockState, player, event.getHand(), event.getHitVec());
		if (transformedBlock.isAir())
			return;

		event.setCanceled(true);
		if (level.isClientSide) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			return;
		}

		boolean converted = applyRecipeInWorld(level, pos, transformedBlock, recipe, player, event.getHand(),
			heldItem);
		event.setCancellationResult(converted ? InteractionResult.SUCCESS : InteractionResult.FAIL);
	}

	private static Optional<ManualApplicationRecipe> findCreateBiotechManualApplicationRecipe(Level level,
		BlockState blockState, ItemStack processedItem, ItemStack heldItem) {
		RecipeType<Recipe<RecipeWrapper>> type = AllRecipeTypes.ITEM_APPLICATION.getType();
		return level.getRecipeManager()
			.getAllRecipesFor(type)
			.stream()
			.filter(recipe -> recipe instanceof ManualApplicationRecipe)
			.map(recipe -> (ManualApplicationRecipe) recipe)
			.filter(recipe -> CreateBiotech.MOD_ID.equals(recipe.getId()
				.getNamespace()))
			.filter(recipe -> !CUSTOM_CONVERSIONS.contains(recipe.getId()))
			.filter(recipe -> recipe.testBlock(blockState) || recipe.getProcessedItem()
				.test(processedItem))
			.filter(recipe -> recipe.getRequiredHeldItem()
				.test(heldItem))
			.findFirst();
	}

	private static BlockState transformBlock(ManualApplicationRecipe recipe, BlockState sourceState, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (recipe.getRollableResults()
			.isEmpty())
			return Blocks.AIR.defaultBlockState();

		ItemStack output = recipe.getRollableResults()
			.get(0)
			.rollOutput();
		if (!(output.getItem() instanceof BlockItem blockItem))
			return Blocks.AIR.defaultBlockState();

		BlockState targetState = blockItem.getBlock().defaultBlockState();
		if (sourceState.is(targetState.getBlock())) {
			// Preserve result-item placement state for same-block conversions, then restore only orientation.
			BlockPlaceContext placementContext = new BlockPlaceContext(player, hand, output, hitResult);
			BlockState placementState = blockItem.getBlock().getStateForPlacement(placementContext);
			if (placementState != null)
				targetState = placementState;
			targetState = copyDirectionalProperties(sourceState, targetState);
		} else {
			targetState = BlockHelper.copyProperties(sourceState, targetState);
		}
		return applyDirectionalContext(targetState, sourceState, player);
	}

	private static BlockState copyDirectionalProperties(BlockState sourceState, BlockState targetState) {
		targetState = BlockHelper.copyProperty(BlockStateProperties.FACING, sourceState, targetState);
		targetState = BlockHelper.copyProperty(BlockStateProperties.HORIZONTAL_FACING, sourceState, targetState);
		targetState = BlockHelper.copyProperty(BlockStateProperties.AXIS, sourceState, targetState);
		return BlockHelper.copyProperty(BlockStateProperties.HORIZONTAL_AXIS, sourceState, targetState);
	}

	private static BlockState applyDirectionalContext(BlockState targetState, BlockState sourceState, Player player) {
		targetState = applyFacing(targetState, sourceState, player, BlockStateProperties.FACING, false);
		targetState = applyFacing(targetState, sourceState, player, BlockStateProperties.HORIZONTAL_FACING, true);
		targetState = applyAxis(targetState, sourceState, player, BlockStateProperties.AXIS, false);
		return applyAxis(targetState, sourceState, player, BlockStateProperties.HORIZONTAL_AXIS, true);
	}

	private static BlockState applyFacing(BlockState targetState, BlockState sourceState, Player player,
		DirectionProperty property, boolean horizontalOnly) {
		if (!targetState.hasProperty(property) || sourceState.hasProperty(property))
			return targetState;

		Direction facing = sourceFacing(sourceState, player, horizontalOnly);
		if (facing == null)
			facing = playerPlacementFacing(player, horizontalOnly);
		if (horizontalOnly && facing.getAxis() == Direction.Axis.Y)
			facing = player.getDirection()
				.getOpposite();
		if (!property.getPossibleValues()
			.contains(facing))
			return targetState;
		return targetState.setValue(property, facing);
	}

	private static BlockState applyAxis(BlockState targetState, BlockState sourceState, Player player,
		EnumProperty<Direction.Axis> property, boolean horizontalOnly) {
		if (!targetState.hasProperty(property) || sourceState.hasProperty(property))
			return targetState;

		Direction.Axis axis = sourceAxis(sourceState);
		if (axis == null) {
			Direction facing = sourceFacing(sourceState, player, false);
			axis = facing == null ? playerFacing(player, horizontalOnly)
				.getAxis() : facing.getAxis();
		}
		if (horizontalOnly && axis == Direction.Axis.Y)
			axis = player.getDirection()
				.getAxis();
		if (!property.getPossibleValues()
			.contains(axis))
			return targetState;
		return targetState.setValue(property, axis);
	}

	private static Direction sourceFacing(BlockState sourceState, Player player, boolean horizontalOnly) {
		if (sourceState.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			return sourceState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		if (sourceState.hasProperty(BlockStateProperties.FACING)) {
			Direction facing = sourceState.getValue(BlockStateProperties.FACING);
			if (!horizontalOnly || facing.getAxis() != Direction.Axis.Y)
				return facing;
		}

		Direction.Axis axis = sourceAxis(sourceState);
		if (axis == null || horizontalOnly && axis == Direction.Axis.Y)
			return null;
		Direction hint = playerFacing(player, horizontalOnly);
		return Direction.fromAxisAndDirection(axis, hint.getAxisDirection());
	}

	private static Direction.Axis sourceAxis(BlockState sourceState) {
		if (sourceState.hasProperty(BlockStateProperties.AXIS))
			return sourceState.getValue(BlockStateProperties.AXIS);
		if (sourceState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
			return sourceState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
		return null;
	}

	private static Direction playerFacing(Player player, boolean horizontalOnly) {
		if (horizontalOnly)
			return player.getDirection();
		return Direction.getNearest(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z);
	}

	private static Direction playerPlacementFacing(Player player, boolean horizontalOnly) {
		if (horizontalOnly)
			return player.getDirection()
				.getOpposite();
		return playerFacing(player, false).getOpposite();
	}

	private static boolean applyRecipeInWorld(Level level, BlockPos pos, BlockState transformedBlock,
		ManualApplicationRecipe recipe, Player player, InteractionHand hand, ItemStack heldItem) {
		level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 1, 1.45f);
		level.destroyBlock(pos, false);
		if (!level.setBlock(pos, transformedBlock, Block.UPDATE_ALL))
			return false;

		transformedBlock.getBlock()
			.setPlacedBy(level, pos, transformedBlock, player, heldItem);
		recipe.rollResults()
			.forEach(stack -> Block.popResource(level, pos, stack));
		consumeHeldItem(recipe, player, hand, heldItem);
		return true;
	}

	private static void consumeHeldItem(ManualApplicationRecipe recipe, Player player, InteractionHand hand,
		ItemStack heldItem) {
		boolean keepHeld = recipe.shouldKeepHeldItem() || player.isCreative();
		if (isUnbreakable(heldItem) || keepHeld)
			return;

		if (heldItem.getMaxDamage() > 0) {
			heldItem.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
			return;
		}

		ItemStack leftover = heldItem.getCraftingRemainingItem();
		heldItem.shrink(1);
		if (leftover.isEmpty())
			return;
		if (heldItem.isEmpty()) {
			player.setItemInHand(hand, leftover);
			return;
		}
		if (!player.getInventory()
			.add(leftover))
			player.drop(leftover, false);
	}

	private static boolean isUnbreakable(ItemStack stack) {
		return stack.hasTag() && stack.getTag()
			.getBoolean("Unbreakable");
	}
}

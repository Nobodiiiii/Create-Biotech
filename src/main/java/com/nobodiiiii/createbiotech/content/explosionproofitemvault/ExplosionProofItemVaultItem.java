package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ExplosionProofItemVaultItem extends BlockItem {

	public ExplosionProofItemVaultItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		InteractionResult initialResult = super.place(context);
		if (!initialResult.consumesAction())
			return initialResult;

		tryMultiPlace(context);
		return initialResult;
	}

	@Override
	protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
		BlockState state) {
		MinecraftServer server = level.getServer();
		if (server == null)
			return false;

		CompoundTag nbt = stack.getTagElement("BlockEntityTag");
		if (nbt != null) {
			nbt.remove("Length");
			nbt.remove("Size");
			nbt.remove("Controller");
			nbt.remove("LastKnownPos");
		}

		return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
	}

	private void tryMultiPlace(BlockPlaceContext context) {
		Player player = context.getPlayer();
		if (player == null || player.isShiftKeyDown())
			return;

		Direction face = context.getClickedFace();
		ItemStack stack = context.getItemInHand();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockPos placedOnPos = pos.relative(face.getOpposite());
		BlockState placedOnState = level.getBlockState(placedOnPos);

		if (!ExplosionProofItemVaultBlock.isVault(placedOnState))
			return;
		if (SymmetryWandItem.presentInHotbar(player))
			return;

		ExplosionProofItemVaultBlockEntity vaultAt =
			ConnectivityHandler.partAt(CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(), level, placedOnPos);
		if (vaultAt == null)
			return;

		ExplosionProofItemVaultBlockEntity controllerBE =
			vaultAt.getControllerBE() instanceof ExplosionProofItemVaultBlockEntity controller ? controller : null;
		if (controllerBE == null)
			return;

		int width = controllerBE.getWidth();
		if (width == 1)
			return;

		Axis vaultBlockAxis = ExplosionProofItemVaultBlock.getVaultBlockAxis(placedOnState);
		if (vaultBlockAxis == null || face.getAxis() != vaultBlockAxis)
			return;

		int vaultsToPlace = 0;
		Direction vaultFacing = Direction.fromAxisAndDirection(vaultBlockAxis, AxisDirection.POSITIVE);
		BlockPos startPos = face == vaultFacing.getOpposite() ? controllerBE.getBlockPos()
			.relative(vaultFacing.getOpposite())
			: controllerBE.getBlockPos()
				.relative(vaultFacing, controllerBE.getHeight());

		if (VecHelper.getCoordinate(startPos, vaultBlockAxis) != VecHelper.getCoordinate(pos, vaultBlockAxis))
			return;

		for (int xOffset = 0; xOffset < width; xOffset++) {
			for (int zOffset = 0; zOffset < width; zOffset++) {
				BlockPos offsetPos = vaultBlockAxis == Axis.X ? startPos.offset(0, xOffset, zOffset)
					: startPos.offset(xOffset, zOffset, 0);
				BlockState blockState = level.getBlockState(offsetPos);
				if (ExplosionProofItemVaultBlock.isVault(blockState))
					continue;
				if (!blockState.canBeReplaced())
					return;
				vaultsToPlace++;
			}
		}

		if (!player.isCreative() && stack.getCount() < vaultsToPlace)
			return;

		for (int xOffset = 0; xOffset < width; xOffset++) {
			for (int zOffset = 0; zOffset < width; zOffset++) {
				BlockPos offsetPos = vaultBlockAxis == Axis.X ? startPos.offset(0, xOffset, zOffset)
					: startPos.offset(xOffset, zOffset, 0);
				BlockState blockState = level.getBlockState(offsetPos);
				if (ExplosionProofItemVaultBlock.isVault(blockState))
					continue;

				BlockPlaceContext placeContext = BlockPlaceContext.at(context, offsetPos, face);
				player.getPersistentData()
					.putBoolean("SilenceVaultSound", true);
				super.place(placeContext);
				player.getPersistentData()
					.remove("SilenceVaultSound");
			}
		}
	}
}

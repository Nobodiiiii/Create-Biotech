package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberVaultRole;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryWrapper;
import com.simibubi.create.foundation.utility.SameSizeCombinedInvWrapper;

import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

public class ExplosionProofItemVaultBlockEntity extends ItemVaultBlockEntity {

	private static final String BLAST_CHAMBER_CONTROLLER_TAG = "BlastChamberController";
	private static final String BLAST_CHAMBER_ROLE_TAG = "BlastChamberRole";

	private ScrollOptionBehaviour<CreeperBlastChamberVaultRole> chamberRoleSelection;
	@Nullable
	private BlockPos blastChamberController;
	@Nullable
	private CreeperBlastChamberVaultRole blastChamberRole;
	private boolean syncingChamberRoleSelection;

	public ExplosionProofItemVaultBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public ExplosionProofItemVaultBlockEntity(BlockPos pos, BlockState state) {
		this(CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		chamberRoleSelection = new ScrollOptionBehaviour<>(CreeperBlastChamberVaultRole.class,
			Component.translatable("create_biotech.creeper_blast_chamber.vault_role.label"), this,
			new CenteredSideValueBoxTransform());
		chamberRoleSelection.onlyActiveWhen(this::canConfigureBlastChamberRole);
		chamberRoleSelection.withCallback(this::onChamberRoleSelectionChanged);
		behaviours.add(chamberRoleSelection);
	}

	@Override
	public InventoryIdentifier getInvId() {
		initExplosionProofCapability();
		return invId;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (isItemHandlerCap(cap)) {
			initExplosionProofCapability();
			return itemCapability.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void removeController(boolean keepContents) {
		if (level.isClientSide())
			return;
		updateConnectivity = true;
		controller = null;
		radius = 1;
		length = 1;

		BlockState state = getBlockState();
		if (ExplosionProofItemVaultBlock.isVault(state)) {
			state = state.setValue(ItemVaultBlock.LARGE, false);
			getLevel().setBlock(worldPosition, state, 22);
		}

		itemCapability.invalidate();
		setChanged();
		sendData();
	}

	@Override
	public void notifyMultiUpdated() {
		BlockState state = getBlockState();
		if (ExplosionProofItemVaultBlock.isVault(state)) {
			level.setBlock(getBlockPos(), state.setValue(ItemVaultBlock.LARGE, radius > 2), 6);
		}
		itemCapability.invalidate();
		setChanged();
	}

	public void bindToBlastChamber(BlockPos chamberControllerPos, CreeperBlastChamberVaultRole role) {
		ExplosionProofItemVaultBlockEntity controller = getRoleController();
		if (controller == null)
			return;
		controller.forEachVaultPart(part -> part.setBlastChamberBinding(chamberControllerPos, role));
	}

	public void clearBlastChamberBinding(BlockPos chamberControllerPos) {
		ExplosionProofItemVaultBlockEntity controller = getRoleController();
		if (controller == null)
			return;
		controller.forEachVaultPart(part -> {
			if (Objects.equals(part.blastChamberController, chamberControllerPos))
				part.setBlastChamberBinding(null, null);
		});
	}

	private void initExplosionProofCapability() {
		if (itemCapability.isPresent())
			return;

		if (!isController()) {
			ItemVaultBlockEntity controllerBE = getControllerBE();
			if (!(controllerBE instanceof ExplosionProofItemVaultBlockEntity explosionProofController))
				return;

			explosionProofController.initExplosionProofCapability();
			itemCapability = explosionProofController.itemCapability;
			invId = explosionProofController.invId;
			return;
		}

		boolean alongZ = ExplosionProofItemVaultBlock.getVaultBlockAxis(getBlockState()) == Axis.Z;
		IItemHandlerModifiable[] invs = new IItemHandlerModifiable[length * radius * radius];
		for (int yOffset = 0; yOffset < length; yOffset++) {
			for (int xOffset = 0; xOffset < radius; xOffset++) {
				for (int zOffset = 0; zOffset < radius; zOffset++) {
					BlockPos vaultPos = alongZ ? worldPosition.offset(xOffset, zOffset, yOffset)
						: worldPosition.offset(yOffset, xOffset, zOffset);
					ItemVaultBlockEntity vaultAt =
						ConnectivityHandler.partAt(CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(), level, vaultPos);
					invs[yOffset * radius * radius + xOffset * radius + zOffset] =
						vaultAt != null ? vaultAt.getInventoryOfBlock() : new ItemStackHandler();
				}
			}
		}

		IItemHandler itemHandler = new VersionedInventoryWrapper(SameSizeCombinedInvWrapper.create(invs));
		itemCapability = LazyOptional.of(() -> itemHandler);

		BlockPos farCorner = alongZ ? worldPosition.offset(radius, radius, length)
			: worldPosition.offset(length, radius, radius);
		invId = new InventoryIdentifier.Bounds(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			worldPosition, farCorner));
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		if (blastChamberController != null)
			compound.putLong(BLAST_CHAMBER_CONTROLLER_TAG, blastChamberController.asLong());
		if (blastChamberRole != null)
			compound.putInt(BLAST_CHAMBER_ROLE_TAG, blastChamberRole.ordinal());
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		blastChamberController = compound.contains(BLAST_CHAMBER_CONTROLLER_TAG)
			? BlockPos.of(compound.getLong(BLAST_CHAMBER_CONTROLLER_TAG))
			: null;
		blastChamberRole = compound.contains(BLAST_CHAMBER_ROLE_TAG)
			? CreeperBlastChamberVaultRole.values()[compound.getInt(BLAST_CHAMBER_ROLE_TAG)]
			: null;
		syncRoleSelectionToBinding();
	}

	private boolean canConfigureBlastChamberRole() {
		return blastChamberController != null && blastChamberRole != null;
	}

	private void onChamberRoleSelectionChanged(int selection) {
		if (syncingChamberRoleSelection || level == null || level.isClientSide)
			return;

		ExplosionProofItemVaultBlockEntity controller = getRoleController();
		if (controller == null || controller.blastChamberController == null)
			return;

		if (!(level.getBlockEntity(controller.blastChamberController) instanceof CreeperBlastChamberBlockEntity chamber))
			return;

		chamber.configureVaultRole(controller.getBlockPos(), chamberRoleSelection.get());
	}

	@Nullable
	private ExplosionProofItemVaultBlockEntity getRoleController() {
		if (isController())
			return this;
		return getControllerBE() instanceof ExplosionProofItemVaultBlockEntity controller ? controller : null;
	}

	private void forEachVaultPart(java.util.function.Consumer<ExplosionProofItemVaultBlockEntity> consumer) {
		if (level == null)
			return;

		Axis axis = getMainConnectionAxis();
		BlockPos controllerPos = getBlockPos();
		for (int yOffset = 0; yOffset < length; yOffset++) {
			for (int xOffset = 0; xOffset < radius; xOffset++) {
				for (int zOffset = 0; zOffset < radius; zOffset++) {
					BlockPos vaultPos = axis == Axis.Z ? controllerPos.offset(xOffset, zOffset, yOffset)
						: controllerPos.offset(yOffset, xOffset, zOffset);
					ExplosionProofItemVaultBlockEntity vaultPart =
						ConnectivityHandler.partAt(CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(), level, vaultPos);
					if (vaultPart != null)
						consumer.accept(vaultPart);
				}
			}
		}
	}

	private void setBlastChamberBinding(@Nullable BlockPos chamberControllerPos,
		@Nullable CreeperBlastChamberVaultRole role) {
		boolean changed = !Objects.equals(blastChamberController, chamberControllerPos) || blastChamberRole != role;
		blastChamberController = chamberControllerPos;
		blastChamberRole = role;
		syncRoleSelectionToBinding();
		if (!changed)
			return;
		setChanged();
		sendData();
	}

	private void syncRoleSelectionToBinding() {
		if (chamberRoleSelection == null || blastChamberRole == null || chamberRoleSelection.get() == blastChamberRole)
			return;
		syncingChamberRoleSelection = true;
		chamberRoleSelection.setValue(blastChamberRole.ordinal());
		syncingChamberRoleSelection = false;
	}
}

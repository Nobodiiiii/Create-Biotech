package com.nobodiiiii.createbiotech.content.universaljoint;

import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointEndpointBlockEntity.EndpointAddress;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Two-click repair selection carried by a captured-slime cardboard box.
 */
public final class UniversalJointRepair {

	public static final String REPAIR_SELECTION_TAG = "UniversalJointRepair";

	private static final String ENDPOINT_TAG = "Endpoint";
	private static final String REVISION_TAG = "Generation";

	private UniversalJointRepair() {}

	public static boolean canRepairWith(ItemStack stack) {
		return CapturedEntityBoxItem.isBox(stack)
			&& stack.getCount() == 1
			&& CapturedEntityBoxHelper.containsEntityType(stack, EntityType.SLIME);
	}

	public static InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockEntity rawEndpoint = level.getBlockEntity(context.getClickedPos());
		if (!(rawEndpoint instanceof HalfShaftBlockEntity clicked))
			return InteractionResult.PASS;
		Player player = context.getPlayer();
		if (player == null
			|| !clicked.isAtExpectedOwnAddress()
			|| !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), context.getItemInHand())
			|| !canRepairWith(context.getItemInHand()))
			return InteractionResult.FAIL;
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		if (!(level instanceof ServerLevel serverLevel)
			|| !(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.FAIL;

		ItemStack box = context.getItemInHand();
		Selection selection = readSelection(box);
		if (selection == null) {
			writeSelection(box, clicked);
			player.displayClientMessage(
				Component.translatable(
					"create_biotech.universal_joint.repair.selected"),
				true);
			playSound(serverLevel, clicked.getWorldCenter(), 0.8f);
			return InteractionResult.SUCCESS;
		}
		if (selection.endpointId().equals(clicked.getEndpointId()))
			return failed(player);

		ServerLevel selectedLevel =
			serverLevel.getServer().getLevel(selection.cachedAddress().dimension());
		if (selectedLevel == null || selectedLevel != serverLevel)
			return failed(player);
		BlockEntity selectedRaw = UniversalJointEndpointBlockEntity.resolveLoaded(
			selectedLevel, selection.cachedAddress());
		if (!(selectedRaw instanceof HalfShaftBlockEntity selected)
			|| !selected.getEndpointId().equals(selection.endpointId())
			|| !selected.isAtExpectedOwnAddress()
			|| selected.getMoveRevision() < selection.minimumMoveRevision()
			|| selected == clicked
			|| selected.getWorldCenter().distanceToSqr(clicked.getWorldCenter())
				> Mth.square(
					UniversalJointBlockEntity.getStrainStartDistance()))
			return failed(player);

		Vec3 firstPosition = selected.getWorldCenter();
		Vec3 secondPosition = clicked.getWorldCenter();
		if (!UniversalJointTransactions.repairHalves(selected, clicked, box,
			serverPlayer))
			return failed(player);

		playSound(serverLevel, firstPosition, 1.0f);
		playSound(serverLevel, secondPosition, 1.0f);
		player.displayClientMessage(
			Component.translatable(
				"create_biotech.universal_joint.repair.success"),
			true);
		return InteractionResult.SUCCESS;
	}

	@Nullable
	public static Selection readSelection(ItemStack stack) {
		CompoundTag root = CBItemData.getReadOnly(stack);
		if (root == null
			|| !root.contains(REPAIR_SELECTION_TAG, Tag.TAG_COMPOUND))
			return null;
		CompoundTag tag = root.getCompound(REPAIR_SELECTION_TAG);
		if (!tag.hasUUID(ENDPOINT_TAG)
			|| !tag.contains(REVISION_TAG, Tag.TAG_LONG))
			return null;
		long revision = tag.getLong(REVISION_TAG);
		if (revision < 0)
			return null;
		EndpointAddress address = EndpointAddress.read(tag).orElse(null);
		return address == null ? null
			: new Selection(tag.getUUID(ENDPOINT_TAG), revision, address);
	}

	private static void writeSelection(ItemStack stack,
		HalfShaftBlockEntity endpoint) {
		CBItemData.edit(stack, root -> {
			CompoundTag tag = endpoint.getEndpointAddress().write();
			tag.putUUID(ENDPOINT_TAG, endpoint.getEndpointId());
			tag.putLong(REVISION_TAG, endpoint.getMoveRevision());
			root.put(REPAIR_SELECTION_TAG, tag);
		});
	}

	public static void clearSelection(ItemStack stack) {
		CBItemData.edit(stack, UniversalJointRepair::clearSelection);
	}

	public static void clearSelection(CompoundTag customData) {
		customData.remove(REPAIR_SELECTION_TAG);
	}

	private static InteractionResult failed(Player player) {
		player.displayClientMessage(
			Component.translatable(
				"create_biotech.universal_joint.repair.invalid"),
			true);
		return InteractionResult.FAIL;
	}

	private static void playSound(ServerLevel level, Vec3 worldPosition,
		float pitch) {
		level.playSound(null, worldPosition.x, worldPosition.y, worldPosition.z,
			SoundEvents.SLIME_JUMP, SoundSource.BLOCKS, 0.5f, pitch);
	}

	public record Selection(UUID endpointId, long minimumMoveRevision,
							EndpointAddress cachedAddress) {}
}

package com.nobodiiiii.createbiotech.content.universaljoint;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlockEntity.PeerReference;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Synchronous mutations of the two loaded blocks that form a universal joint.
 *
 * <p>This class deliberately contains no persistent journal, global endpoint index, save hook, or
 * chunk-loading lookup.</p>
 */
public final class UniversalJointTransactions {

	private UniversalJointTransactions() {}

	public static void dismantle(UniversalJointBlockEntity removed) {
		if (!(removed.getLevel() instanceof ServerLevel level))
			return;
		UniversalJointBlockEntity peer = removed.getLoadedLinkedJoint();
		boolean mutual = peer != null && removed.references(peer) && peer.references(removed);

		removed.clearLocalLink();
		removed.rebuildKineticsAfterTransaction();
		if (!mutual || peer.getLevel() != level)
			return;

		peer.beginControlledReplacement();
		peer.clearLocalLink();
		if (!level.destroyBlock(peer.getBlockPos(), false)) {
			peer.endControlledReplacement();
			peer.rebuildKineticsAfterTransaction();
		}
	}

	public static void requestFracture(UniversalJointBlockEntity requester) {
		if (!(requester.getLevel() instanceof ServerLevel level))
			return;
		UniversalJointBlockEntity peer = requester.getLoadedLinkedJoint();
		if (peer == null || peer.getLevel() != level
			|| !requester.isAtExpectedOwnAddress()
			|| !peer.isAtExpectedOwnAddress()
			|| !requester.references(peer) || !peer.references(requester))
			return;
		double distance = Math.sqrt(requester.distanceSquaredTo(peer));
		if (!Double.isFinite(distance)
			|| distance < UniversalJointBlockEntity.getElasticDisconnectRange())
			return;

		UniversalJointBlockEntity first =
			UniversalJointBlockEntity.isPrimaryEndpoint(requester, peer)
				? requester : peer;
		UniversalJointBlockEntity second = first == requester ? peer : requester;
		fractureLoadedPair(level, first, second);
	}

	private static void fractureLoadedPair(ServerLevel level,
		UniversalJointBlockEntity first, UniversalJointBlockEntity second) {
		EndpointSnapshot firstSnapshot = EndpointSnapshot.joint(first);
		EndpointSnapshot secondSnapshot = EndpointSnapshot.joint(second);
		MotionSample firstMotion = motionAt(first);
		MotionSample secondMotion = motionAt(second);

		first.beginControlledReplacement();
		second.beginControlledReplacement();

		HalfShaftBlockEntity firstHalf =
			replaceWithHalf(level, firstSnapshot);
		HalfShaftBlockEntity secondHalf =
			firstHalf == null ? null : replaceWithHalf(level, secondSnapshot);
		if (firstHalf == null || secondHalf == null) {
			UniversalJointBlockEntity restoredFirst =
				restoreJoint(level, firstSnapshot);
			UniversalJointBlockEntity restoredSecond =
				restoreJoint(level, secondSnapshot);
			finishJointRollback(restoredFirst, firstSnapshot,
				restoredSecond, secondSnapshot);
			return;
		}

		Vec3 position = firstMotion.position().add(secondMotion.position()).scale(0.5d);
		Vec3 velocity =
			firstMotion.velocity().add(secondMotion.velocity()).scale(0.5d / 20.0d);
		ItemEntity drop = new ItemEntity(level, position.x, position.y, position.z,
			new ItemStack(Items.SLIME_BALL));
		drop.setDeltaMovement(velocity);
		level.addFreshEntity(drop);
	}

	public static boolean repairHalves(HalfShaftBlockEntity first,
		HalfShaftBlockEntity second, ItemStack repairBox, ServerPlayer player) {
		if (!(first.getLevel() instanceof ServerLevel level)
			|| second.getLevel() != level || first == second || player.level() != level
			|| first.getEndpointId().equals(second.getEndpointId())
			|| !first.isAtExpectedOwnAddress()
			|| !second.isAtExpectedOwnAddress()
			|| !UniversalJointRepair.canRepairWith(repairBox))
			return false;
		if (first.getWorldCenter().distanceToSqr(second.getWorldCenter())
			> Mth.square(UniversalJointBlockEntity.getStrainStartDistance()))
			return false;

		EndpointSnapshot firstSnapshot = EndpointSnapshot.half(first);
		EndpointSnapshot secondSnapshot = EndpointSnapshot.half(second);
		first.beginControlledReplacement();
		second.beginControlledReplacement();

		UniversalJointBlockEntity firstJoint =
			replaceWithJoint(level, firstSnapshot);
		UniversalJointBlockEntity secondJoint =
			firstJoint == null ? null : replaceWithJoint(level, secondSnapshot);
		if (firstJoint == null || secondJoint == null
			|| !firstJoint.createMutualLink(secondJoint)) {
			restoreHalf(level, firstSnapshot);
			restoreHalf(level, secondSnapshot);
			return false;
		}

		CapturedEntityBoxHelper.replacePlayerStack(player, repairBox,
			CapturedEntityBoxHelper.createEmptyBox(repairBox));
		player.getInventory().setChanged();
		return true;
	}

	@Nullable
	private static HalfShaftBlockEntity replaceWithHalf(ServerLevel level,
		EndpointSnapshot snapshot) {
		if (!level.setBlock(snapshot.pos(), halfState(snapshot.state()),
			Block.UPDATE_ALL))
			return null;
		HalfShaftBlockEntity replacement =
			endpointAt(level, snapshot.pos(), HalfShaftBlockEntity.class);
		if (replacement != null)
			replacement.setEndpointIdentity(snapshot.endpointId(),
				snapshot.moveRevision());
		return replacement;
	}

	@Nullable
	private static UniversalJointBlockEntity replaceWithJoint(ServerLevel level,
		EndpointSnapshot snapshot) {
		if (!level.setBlock(snapshot.pos(), jointState(snapshot.state()),
			Block.UPDATE_ALL))
			return null;
		UniversalJointBlockEntity replacement =
			endpointAt(level, snapshot.pos(), UniversalJointBlockEntity.class);
		if (replacement != null)
			replacement.setEndpointIdentity(snapshot.endpointId(),
				snapshot.moveRevision());
		return replacement;
	}

	@Nullable
	private static UniversalJointBlockEntity restoreJoint(ServerLevel level,
		EndpointSnapshot snapshot) {
		BlockEntity current = level.getBlockEntity(snapshot.pos());
		UniversalJointEndpointBlockEntity currentEndpoint =
			current instanceof UniversalJointEndpointBlockEntity endpoint
				? endpoint : null;
		if (currentEndpoint != null)
			currentEndpoint.beginControlledReplacement();
		if (!level.getBlockState(snapshot.pos()).equals(snapshot.state())
			&& !level.setBlock(snapshot.pos(), snapshot.state(), Block.UPDATE_ALL)) {
			if (currentEndpoint != null)
				currentEndpoint.endControlledReplacement();
			return null;
		}
		UniversalJointBlockEntity restored =
			endpointAt(level, snapshot.pos(), UniversalJointBlockEntity.class);
		if (restored == null) {
			endControlledReplacementAt(level, snapshot.pos());
			return null;
		}
		restored.setEndpointIdentity(snapshot.endpointId(), snapshot.moveRevision());
		restored.endControlledReplacement();
		return restored;
	}

	private static void finishJointRollback(
		@Nullable UniversalJointBlockEntity first, EndpointSnapshot firstSnapshot,
		@Nullable UniversalJointBlockEntity second, EndpointSnapshot secondSnapshot) {
		if (first != null && second != null) {
			first.restoreLocalLink(firstSnapshot.peer());
			second.restoreLocalLink(secondSnapshot.peer());
			if (!first.references(second) || !second.references(first)) {
				first.clearLocalLink();
				second.clearLocalLink();
			}
		} else {
			if (first != null)
				first.clearLocalLink();
			if (second != null)
				second.clearLocalLink();
		}
		if (first != null)
			first.rebuildKineticsAfterTransaction();
		if (second != null)
			second.rebuildKineticsAfterTransaction();
	}

	@Nullable
	private static HalfShaftBlockEntity restoreHalf(ServerLevel level,
		EndpointSnapshot snapshot) {
		BlockEntity current = level.getBlockEntity(snapshot.pos());
		UniversalJointEndpointBlockEntity currentEndpoint =
			current instanceof UniversalJointEndpointBlockEntity endpoint
				? endpoint : null;
		if (currentEndpoint != null)
			currentEndpoint.beginControlledReplacement();
		if (!level.getBlockState(snapshot.pos()).equals(snapshot.state())
			&& !level.setBlock(snapshot.pos(), snapshot.state(), Block.UPDATE_ALL)) {
			if (currentEndpoint != null)
				currentEndpoint.endControlledReplacement();
			return null;
		}
		HalfShaftBlockEntity restored =
			endpointAt(level, snapshot.pos(), HalfShaftBlockEntity.class);
		if (restored == null) {
			endControlledReplacementAt(level, snapshot.pos());
			return null;
		}
		restored.setEndpointIdentity(snapshot.endpointId(), snapshot.moveRevision());
		restored.endControlledReplacement();
		return restored;
	}

	private static void endControlledReplacementAt(ServerLevel level, BlockPos pos) {
		BlockEntity current = level.getBlockEntity(pos);
		if (current instanceof UniversalJointEndpointBlockEntity endpoint)
			endpoint.endControlledReplacement();
	}

	private static BlockState halfState(BlockState previous) {
		return copyEndpointProperties(previous,
			CBBlocks.HALF_SHAFT.get().defaultBlockState());
	}

	private static BlockState jointState(BlockState previous) {
		return copyEndpointProperties(previous,
			CBBlocks.UNIVERSAL_JOINT.get().defaultBlockState());
	}

	private static BlockState copyEndpointProperties(BlockState source,
		BlockState destination) {
		Direction facing = source.hasProperty(BlockStateProperties.FACING)
			? source.getValue(BlockStateProperties.FACING) : Direction.NORTH;
		boolean waterlogged = source.hasProperty(BlockStateProperties.WATERLOGGED)
			&& source.getValue(BlockStateProperties.WATERLOGGED);
		return destination
			.setValue(BlockStateProperties.FACING, facing)
			.setValue(BlockStateProperties.WATERLOGGED, waterlogged);
	}

	private static MotionSample motionAt(
		UniversalJointEndpointBlockEntity endpoint) {
		Vec3 local = Vec3.atCenterOf(endpoint.getBlockPos());
		return new MotionSample(endpoint.getWorldCenter(),
			SubLevelCompat.getWorldVelocity(endpoint.getLevel(),
				endpoint.getBlockPos(), local));
	}

	@Nullable
	private static <T extends UniversalJointEndpointBlockEntity> T endpointAt(
		ServerLevel level, BlockPos pos, Class<T> type) {
		BlockEntity raw = level.getBlockEntity(pos);
		return type.isInstance(raw) ? type.cast(raw) : null;
	}

	private record EndpointSnapshot(BlockPos pos, BlockState state, java.util.UUID endpointId,
									long moveRevision, @Nullable PeerReference peer) {

		private static EndpointSnapshot joint(UniversalJointBlockEntity endpoint) {
			return new EndpointSnapshot(endpoint.getBlockPos().immutable(),
				endpoint.getBlockState(), endpoint.getEndpointId(),
				endpoint.getMoveRevision(), endpoint.snapshotPeerReference());
		}

		private static EndpointSnapshot half(HalfShaftBlockEntity endpoint) {
			return new EndpointSnapshot(endpoint.getBlockPos().immutable(),
				endpoint.getBlockState(), endpoint.getEndpointId(),
				endpoint.getMoveRevision(), null);
		}
	}

	private record MotionSample(Vec3 position, Vec3 velocity) {}
}

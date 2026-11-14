package com.nobodiiiii.createbiotech.content.shulkerpackager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.CreateLang;

import dev.ryanhcode.sable.companion.SubLevelAccess;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public class ShulkerPackagerConnectionHandler {
	private static final int PLACEMENT_SNAPSHOT_LIFETIME = 20 * 10;
	private static final int MAX_PENDING_PLACEMENTS = 16;
	private static final int[][] OUTLINE_EDGES = {
		{0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
		{2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
	};

	static List<ShulkerPackagerTarget> currentSelection = new ArrayList<>();
	static List<ShulkerPackagerTarget> wrenchSelection = new ArrayList<>();
	static List<PlacementSelectionSnapshot> placementSnapshots = new ArrayList<>();
	static ItemStack currentItem;
	@Nullable
	static Level selectionLevel;
	static long lastBlockPos = -1;
	@Nullable
	static UUID lastBlockSubLevelId;
	@Nullable
	static Level lastBlockLevel;
	@Nullable
	static ShulkerPackagerBlockEntity lastPackager;
	static long lastInteractionPointRevision = -1;
	static Set<UUID> pendingPlacementResultNonces = new LinkedHashSet<>();

	private ShulkerPackagerConnectionHandler() {}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (Minecraft.getInstance().screen != null)
			return;
		tick();
	}

	@SubscribeEvent
	public static void rightClickingBlocksSelectsThem(PlayerInteractEvent.RightClickBlock event) {
		if (currentItem == null || event.getHand() != InteractionHand.MAIN_HAND)
			return;
		Level world = event.getLevel();
		if (!world.isClientSide)
			return;
		Player player = event.getEntity();
		if (player == null || player.isSpectator()
			|| !player.getMainHandItem().is(CBItems.SHULKER_PACKAGER.get()))
			return;

		BlockPos pos = event.getPos();
		BlockState state = world.getBlockState(pos);
		if (!ShulkerPackagerArmInteractions.isSelectable(world, pos, state))
			return;

		UUID targetSubLevelId = SubLevelCompat.getSpaceId(world, pos);
		ShulkerPackagerTarget selected = getSelected(pos, targetSubLevelId);
		if (selected == null) {
			if (currentSelection.size() >= ShulkerPackagerBlockEntity.MAX_OUTPUTS) {
				createBiotechLang()
					.translate("shulker_packager.too_many_outputs", ShulkerPackagerBlockEntity.MAX_OUTPUTS)
					.style(ChatFormatting.RED)
					.sendStatus(player);
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.FAIL);
				return;
			}
			ShulkerPackagerTarget point = ShulkerPackagerTarget.selected(world, pos);
			if (point == null)
				return;
			selected = point;
			put(point);
		}

		if (player != null) {
			createBiotechLang()
				.translate("shulker_packager.store_package_to", CreateLang.blockName(state)
					.style(ChatFormatting.WHITE))
				.color(0xDDC166)
				.sendStatus(player);
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}

	@SubscribeEvent
	public static void leftClickingBlocksDeselectsThem(InputEvent.InteractionKeyMappingTriggered event) {
		if (!event.isAttack())
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.level == null || minecraft.player == null)
			return;
		if (!minecraft.player.getMainHandItem().is(CBItems.SHULKER_PACKAGER.get()))
			return;
		if (!(minecraft.hitResult instanceof BlockHitResult hit))
			return;
		BlockPos pos = hit.getBlockPos();
		if (!ShulkerPackagerArmInteractions.isSelectable(minecraft.level, pos, minecraft.level.getBlockState(pos)))
			return;

		remove(pos, SubLevelCompat.getSpaceId(minecraft.level, pos));
		// The item cannot attack selectable outputs. Cancel before Minecraft enters
		// startDestroyBlock so Sable cannot turn this UI click into a sublevel impulse.
		event.setSwingHand(false);
		event.setCanceled(true);
	}

	public static void flushSettings(BlockPos pos, @Nullable UUID expectedSubLevelId, UUID requestNonce,
		int placementSequence) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			clearActiveSelection();
			return;
		}

		List<ShulkerPackagerTarget> addressedTargets = new ArrayList<>();
		List<ShulkerPackagerTarget> capturedSelection = takePlacementSelection(level, placementSequence);
		if (capturedSelection == null)
			capturedSelection = List.of();
		for (ShulkerPackagerTarget selected : capturedSelection)
			addressedTargets.add(selected.forAnchor(expectedSubLevelId));

		if (pendingPlacementResultNonces.size() >= MAX_PENDING_PLACEMENTS) {
			Iterator<UUID> iterator = pendingPlacementResultNonces.iterator();
			if (iterator.hasNext()) {
				iterator.next();
				iterator.remove();
			}
		}
		pendingPlacementResultNonces.add(requestNonce);
		CBPackets.sendToServer(new ShulkerPackagerPlacementPacket(addressedTargets, pos, expectedSubLevelId,
			requestNonce));
	}

	/** Only a server acknowledgement is allowed to report a completed binding. */
	public static void handlePlacementResult(UUID requestNonce, boolean accepted, int requestedOutputs,
		int acceptedOutputs) {
		if (!pendingPlacementResultNonces.remove(requestNonce))
			return;

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null)
			return;
		if (!accepted) {
			createBiotechLang()
				.translate("shulker_packager.configuration_rejected")
				.style(ChatFormatting.RED)
				.sendStatus(player);
			return;
		}

		int rejectedOutputs = Math.max(0, requestedOutputs - acceptedOutputs);
		if (rejectedOutputs > 0)
			createBiotechLang()
				.translate("shulker_packager.invalid_outputs_removed", rejectedOutputs)
				.style(ChatFormatting.RED)
				.sendStatus(player);
		if (acceptedOutputs > 0)
			createBiotechLang()
				.translate("shulker_packager.output_summary", acceptedOutputs)
				.style(ChatFormatting.WHITE)
				.sendStatus(player);
	}

	private static void clearActiveSelection() {
		currentSelection.clear();
		currentItem = null;
		selectionLevel = null;
	}

	/** Freezes the exact selection used by a successful client-predicted placement. */
	public static void capturePlacementSelection(Level level, InteractionHand hand, ItemStack placedStack,
		int placementSequence) {
		prunePlacementSnapshots(level);
		if (placementSnapshots.size() >= MAX_PENDING_PLACEMENTS)
			placementSnapshots.remove(0);

		boolean ownsSelection = hand == InteractionHand.MAIN_HAND && selectionLevel == level
			&& currentItem == placedStack;
		List<ShulkerPackagerTarget> capturedSelection = ownsSelection
			? List.copyOf(currentSelection)
			: List.of();
		placementSnapshots.add(new PlacementSelectionSnapshot(level, placementSequence,
			level.getGameTime() + PLACEMENT_SNAPSHOT_LIFETIME, capturedSelection));

		if (ownsSelection)
			currentSelection.clear();
		else if (hand == InteractionHand.MAIN_HAND)
			clearActiveSelection();
	}

	@Nullable
	private static List<ShulkerPackagerTarget> takePlacementSelection(Level level, int placementSequence) {
		prunePlacementSnapshots(level);
		for (Iterator<PlacementSelectionSnapshot> iterator = placementSnapshots.iterator(); iterator.hasNext();) {
			PlacementSelectionSnapshot snapshot = iterator.next();
			if (snapshot.level() != level || snapshot.placementSequence() != placementSequence)
				continue;
			iterator.remove();
			return snapshot.selection();
		}
		return null;
	}

	private static void prunePlacementSnapshots(Level currentLevel) {
		placementSnapshots.removeIf(snapshot -> snapshot.level() != currentLevel
			|| snapshot.expiresAt() < currentLevel.getGameTime());
	}

	public static void tick() {
		Player player = Minecraft.getInstance().player;
		if (player == null)
			return;
		prunePlacementSnapshots(player.level());

		ItemStack heldItemMainhand = player.getMainHandItem();
		if (!heldItemMainhand.is(CBItems.SHULKER_PACKAGER.get())) {
			clearActiveSelection();
		} else {
			if (heldItemMainhand != currentItem || selectionLevel != player.level())
				currentSelection.clear();
			currentItem = heldItemMainhand;
			selectionLevel = player.level();
			drawOutlines(currentSelection);
		}

		checkForWrench(heldItemMainhand);
	}

	private static void checkForWrench(ItemStack heldItem) {
		if (!AllItems.WRENCH.isIn(heldItem)) {
			resetWrenchCache();
			return;
		}

		HitResult objectMouseOver = Minecraft.getInstance().hitResult;
		if (!(objectMouseOver instanceof BlockHitResult result)) {
			resetWrenchCache();
			return;
		}

		BlockPos pos = result.getBlockPos();
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			resetWrenchCache();
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof ShulkerPackagerBlockEntity packager)) {
			resetWrenchCache();
			return;
		}

		UUID subLevelId = SubLevelCompat.getSpaceId(level, pos);
		List<ShulkerPackagerTarget> packagerOutputs = packager.getOutputs();
		if (lastBlockPos == -1 || lastBlockPos != pos.asLong()
			|| !Objects.equals(lastBlockSubLevelId, subLevelId)
			|| lastBlockLevel != level
			|| lastPackager != packager
			|| lastInteractionPointRevision != packager.getInteractionPointRevision()) {
			wrenchSelection.clear();
			wrenchSelection.addAll(packagerOutputs);
			lastBlockPos = pos.asLong();
			lastBlockSubLevelId = subLevelId;
			lastBlockLevel = level;
			lastPackager = packager;
			lastInteractionPointRevision = packager.getInteractionPointRevision();
		}

		drawOutlines(wrenchSelection);
	}

	private static void resetWrenchCache() {
		lastBlockPos = -1;
		lastBlockSubLevelId = null;
		lastBlockLevel = null;
		lastPackager = null;
		lastInteractionPointRevision = -1;
		wrenchSelection.clear();
	}

	private static void drawOutlines(Collection<ShulkerPackagerTarget> selection) {
		for (ShulkerPackagerTarget point : selection) {
			if (!point.isValid())
				continue;

			Level level = point.getLevel();
			BlockPos pos = point.getPos();
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getShape(level, pos);
			if (shape.isEmpty())
				continue;

			int color = point.getMode()
				.getColor();
			drawOutline(point, shape.bounds()
				.move(pos), color, AnimationTickHolder.getPartialTicks());
		}
	}

	private static void drawOutline(ShulkerPackagerTarget point, AABB localBounds, int color, float partialTick) {
		SubLevelAccess subLevel = SubLevelCompat.getContaining(point.getLevel(), point.getPos());
		Vec3[] corners = new Vec3[8];
		for (int i = 0; i < corners.length; i++) {
			Vec3 localCorner = new Vec3((i & 1) == 0 ? localBounds.minX : localBounds.maxX,
				(i & 2) == 0 ? localBounds.minY : localBounds.maxY,
				(i & 4) == 0 ? localBounds.minZ : localBounds.maxZ);
			corners[i] = SubLevelCompat.toRenderWorld(subLevel, localCorner, partialTick);
		}

		for (int i = 0; i < OUTLINE_EDGES.length; i++) {
			int[] edge = OUTLINE_EDGES[i];
			Outliner.getInstance()
				.showLine(new OutlineEdge(point, i), corners[edge[0]], corners[edge[1]])
				.colored(color)
				.lineWidth(1 / 16f);
		}
	}

	private static void put(ShulkerPackagerTarget point) {
		currentSelection.add(point);
	}

	private static ShulkerPackagerTarget remove(BlockPos pos, @Nullable UUID subLevelId) {
		ShulkerPackagerTarget result = getSelected(pos, subLevelId);
		if (result != null)
			currentSelection.remove(result);
		return result;
	}

	private static ShulkerPackagerTarget getSelected(BlockPos pos, @Nullable UUID subLevelId) {
		for (ShulkerPackagerTarget point : currentSelection)
			if (point.getPos()
				.equals(pos) && Objects.equals(point.targetSubLevelId(), subLevelId))
				return point;
		return null;
	}

	private static LangBuilder createBiotechLang() {
		return new LangBuilder(CreateBiotech.MOD_ID);
	}

	private record OutlineEdge(ShulkerPackagerTarget target, int edge) {}

	private record PlacementSelectionSnapshot(Level level, int placementSequence, long expiresAt,
		List<ShulkerPackagerTarget> selection) {}
}

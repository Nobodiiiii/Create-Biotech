package com.nobodiiiii.createbiotech.content.shulkerpackager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ShulkerPackagerConnectionHandler {

	static List<ArmInteractionPoint> currentSelection = new ArrayList<>();
	static ItemStack currentItem;
	static long lastBlockPos = -1;

	private ShulkerPackagerConnectionHandler() {}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;
		if (Minecraft.getInstance().screen != null)
			return;
		tick();
	}

	@SubscribeEvent
	public static void rightClickingBlocksSelectsThem(PlayerInteractEvent.RightClickBlock event) {
		if (currentItem == null)
			return;
		Level world = event.getLevel();
		if (!world.isClientSide)
			return;
		Player player = event.getEntity();
		if (player != null && player.isSpectator())
			return;

		BlockPos pos = event.getPos();
		BlockState state = world.getBlockState(pos);
		if (!ShulkerPackagerArmInteractions.isSelectable(state))
			return;

		ArmInteractionPoint selected = getSelected(pos);
		if (selected == null) {
			ArmInteractionPoint point = ArmInteractionPoint.create(world, pos, state);
			if (point == null)
				return;
			selected = point;
			put(point);
		}

		if (ShulkerPackagerArmInteractions.canBeInput(selected))
			selected.cycleMode();

		if (player != null) {
			Mode mode = selected.getMode();
			CreateLang.builder()
				.translate(mode.getTranslationKey(), CreateLang.blockName(state)
					.style(ChatFormatting.WHITE))
				.color(mode.getColor())
				.sendStatus(player);
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}

	@SubscribeEvent
	public static void leftClickingBlocksDeselectsThem(PlayerInteractEvent.LeftClickBlock event) {
		if (currentItem == null || !event.getLevel().isClientSide)
			return;
		if (remove(event.getPos()) != null) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.SUCCESS);
		}
	}

	public static void flushSettings(BlockPos pos) {
		if (currentSelection == null)
			return;

		int removed = 0;
		for (Iterator<ArmInteractionPoint> iterator = currentSelection.iterator(); iterator.hasNext();) {
			ArmInteractionPoint point = iterator.next();
			if (point.getPos()
				.closerThan(pos, getConnectionRange()))
				continue;
			iterator.remove();
			removed++;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		if (removed > 0) {
			CreateLang.builder()
				.translate("mechanical_arm.points_outside_range", removed)
				.style(ChatFormatting.RED)
				.sendStatus(player);
		} else {
			int inputs = 0;
			int outputs = 0;
			for (ArmInteractionPoint armInteractionPoint : currentSelection)
				if (armInteractionPoint.getMode() == Mode.DEPOSIT)
					outputs++;
				else
					inputs++;
			if (inputs + outputs > 0)
				CreateLang.builder()
					.translate("mechanical_arm.summary", inputs, outputs)
					.style(ChatFormatting.WHITE)
					.sendStatus(player);
		}

		CBPackets.sendToServer(new ShulkerPackagerPlacementPacket(currentSelection, pos));
		currentSelection.clear();
		currentItem = null;
		lastBlockPos = -1;
	}

	public static void tick() {
		Player player = Minecraft.getInstance().player;
		if (player == null)
			return;

		ItemStack heldItemMainhand = player.getMainHandItem();
		if (!heldItemMainhand.is(CBItems.SHULKER_PACKAGER.get())) {
			currentItem = null;
		} else {
			if (heldItemMainhand != currentItem) {
				currentSelection.clear();
				currentItem = heldItemMainhand;
			}
			drawOutlines(currentSelection);
		}

		checkForWrench(heldItemMainhand);
	}

	private static void checkForWrench(ItemStack heldItem) {
		if (!AllItems.WRENCH.isIn(heldItem))
			return;

		HitResult objectMouseOver = Minecraft.getInstance().hitResult;
		if (!(objectMouseOver instanceof BlockHitResult result))
			return;

		BlockPos pos = result.getBlockPos();
		BlockEntity be = Minecraft.getInstance().level.getBlockEntity(pos);
		if (!(be instanceof ShulkerPackagerBlockEntity packager)) {
			lastBlockPos = -1;
			currentSelection.clear();
			return;
		}

		if (lastBlockPos == -1 || lastBlockPos != pos.asLong()) {
			currentSelection.clear();
			packager.getInputs()
				.forEach(ShulkerPackagerConnectionHandler::put);
			packager.getOutputs()
				.forEach(ShulkerPackagerConnectionHandler::put);
			lastBlockPos = pos.asLong();
		}

		drawOutlines(currentSelection);
	}

	private static void drawOutlines(Collection<ArmInteractionPoint> selection) {
		for (Iterator<ArmInteractionPoint> iterator = selection.iterator(); iterator.hasNext();) {
			ArmInteractionPoint point = iterator.next();

			if (!point.isValid()) {
				iterator.remove();
				continue;
			}

			Level level = point.getLevel();
			BlockPos pos = point.getPos();
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getShape(level, pos);
			if (shape.isEmpty())
				continue;

			int color = point.getMode()
				.getColor();
			Outliner.getInstance().showAABB(point, shape.bounds()
					.move(pos))
				.colored(color)
				.lineWidth(1 / 16f);
		}
	}

	private static void put(ArmInteractionPoint point) {
		currentSelection.add(point);
	}

	private static ArmInteractionPoint remove(BlockPos pos) {
		ArmInteractionPoint result = getSelected(pos);
		if (result != null)
			currentSelection.remove(result);
		return result;
	}

	private static ArmInteractionPoint getSelected(BlockPos pos) {
		for (ArmInteractionPoint point : currentSelection)
			if (point.getPos()
				.equals(pos))
				return point;
		return null;
	}

	private static int getConnectionRange() {
		return CBConfigs.COMMON.shulkerPackager.connectionRange.get();
	}
}

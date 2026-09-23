package com.nobodiiiii.createbiotech.content.honeycombgauge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Plans one JEI recipe against the actual four-slot Create panels on the selected face. */
public final class HoneycombGaugeRecipePlacement {
	public static final int MAX_RECIPE_SLOTS = 32;
	private static final double EPSILON = 0.001;
	private static final int MAX_INPUTS = 9;

	private HoneycombGaugeRecipePlacement() {}

	public record Ingredient(ItemStack stack, int amount) {}
	private record Slot(BlockPos pos, PanelSlot panel, BlockState state, double x, boolean existing) {}
	private record Node(Ingredient ingredient, Slot slot) {}
	public record Plan(List<Node> inputs, List<Node> outputs, int newGauges, String error) {
		public boolean ready() { return error == null; }
	}

	public static Plan plan(Player player, BlockPos origin, Direction facing, Direction up,
		List<Ingredient> rawInputs, List<Ingredient> rawOutputs) {
		return plan(player, origin, facing, up, rawInputs, rawOutputs,
			HoneycombGaugeClusterScanner.scan(player.level(), origin, facing));
	}

	public static Plan plan(Player player, BlockPos origin, Direction facing, Direction up,
		List<Ingredient> rawInputs, List<Ingredient> rawOutputs,
		HoneycombGaugeClusterScanner.Snapshot snapshot) {
		List<Ingredient> inputs = combine(rawInputs);
		List<Ingredient> outputs = combine(rawOutputs);
		if (inputs.isEmpty() || outputs.isEmpty() || inputs.size() > MAX_INPUTS
			|| rawInputs.size() + rawOutputs.size() > MAX_RECIPE_SLOTS
			|| inputs.stream().anyMatch(entry -> entry.amount() > 4096)
			|| outputs.stream().anyMatch(entry -> entry.amount() > 4096)
			|| inputs.stream().anyMatch(entry -> entry.stack().getItem() instanceof FilterItem)
			|| outputs.stream().anyMatch(entry -> entry.stack().getItem() instanceof FilterItem))
			return error("unsupported");
		Level level = player.level();
		if (snapshot.limited() || snapshot.honeycombs().isEmpty())
			return error("space");

		Direction right = HoneycombGaugeClusterBlock.nextWorkspaceUp(facing, up);
		List<Slot> slots = new ArrayList<>();
		Set<BlockPos> supports = new LinkedHashSet<>(snapshot.honeycombs());
		for (BlockPos support : supports) {
			BlockPos pos = support.relative(facing);
			if (!level.isLoaded(pos) || !player.mayInteract(level, pos)
				|| !player.mayUseItemAt(pos, facing, AllBlocks.FACTORY_GAUGE.asStack()))
				continue;
			BlockState worldState = level.getBlockState(pos);
			boolean existing = worldState.getBlock() instanceof FactoryPanelBlock
				&& FactoryPanelBlock.connectedDirection(worldState) == facing;
			if (!existing && !worldState.isAir())
				continue;
			BlockState state = existing ? worldState : placementState(player, support, facing);
			if (state == null || FactoryPanelBlock.connectedDirection(state) != facing
				|| !state.canSurvive(level, pos))
				continue;
			FactoryPanelBlockEntity be = existing && level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity panel
				? panel : null;
			if (existing && be == null)
				continue;
			for (PanelSlot panel : PanelSlot.values()) {
				FactoryPanelBehaviour behaviour = be == null ? null : be.panels.get(panel);
				Vec3 offset = slotCenter(state, panel).add(Vec3.atLowerCornerOf(pos));
				double x = offset.x * right.getStepX() + offset.y * right.getStepY()
					+ offset.z * right.getStepZ();
				slots.add(new Slot(pos, panel, state, x, behaviour != null && behaviour.isActive()));
			}
		}
		if (slots.isEmpty())
			return error("space");
		double min = slots.stream().mapToDouble(Slot::x).min().orElse(0);
		double max = slots.stream().mapToDouble(Slot::x).max().orElse(0);
		double middle = (min + max) / 2;
		List<Node> inputNodes = new ArrayList<>();
		List<Node> outputNodes = new ArrayList<>();
		Set<Slot> used = new HashSet<>();
		for (Ingredient ingredient : inputs) {
			if (misplacedExisting(slots, ingredient, true, middle, level)
				&& outputs.stream().noneMatch(other -> ItemStack.isSameItemSameComponents(
					other.stack(), ingredient.stack())))
				return error("layout");
			Slot slot = choose(slots, used, ingredient, true, middle, level);
			if (slot == null)
				return error("space");
			used.add(slot);
			inputNodes.add(new Node(ingredient, slot));
		}
		for (Ingredient ingredient : outputs) {
			if (misplacedExisting(slots, ingredient, false, middle, level)
				&& inputs.stream().noneMatch(other -> ItemStack.isSameItemSameComponents(
					other.stack(), ingredient.stack())))
				return error("layout");
			Slot slot = choose(slots, used, ingredient, false, middle, level);
			if (slot == null)
				return error("space");
			used.add(slot);
			outputNodes.add(new Node(ingredient, slot));
		}
		for (Node output : outputNodes) {
			FactoryPanelBehaviour existingOutput = behaviour(level, output.slot());
			int connections = existingOutput == null ? 0 : existingOutput.targetedBy.size();
			for (Node input : inputNodes) {
				if (input.slot().x() + EPSILON >= output.slot().x())
					return error("layout");
				if (!input.slot().state().setValue(FactoryPanelBlock.WATERLOGGED, false)
					.setValue(FactoryPanelBlock.POWERED, false)
					.equals(output.slot().state().setValue(FactoryPanelBlock.WATERLOGGED, false)
						.setValue(FactoryPanelBlock.POWERED, false)))
					return error("orientation");
				if (!input.slot().pos().subtract(output.slot().pos()).closerThan(BlockPos.ZERO, 16))
					return error("distance");
				FactoryPanelBehaviour existingInput = behaviour(level, input.slot());
				if (existingInput == null || existingOutput == null
					|| !existingOutput.targetedBy.containsKey(existingInput.getPanelPosition()))
					connections++;
			}
			if (connections > MAX_INPUTS || existingOutput != null && existingOutput.panelBE().restocker)
				return error("connections");
		}
		int count = (int) used.stream().filter(slot -> !slot.existing()).count();
		if (!player.isCreative() && availableGauges(player) < count)
			return error("gauges");
		return new Plan(List.copyOf(inputNodes), List.copyOf(outputNodes), count, null);
	}

	private static Plan error(String reason) {
		return new Plan(List.of(), List.of(), 0, reason);
	}

	private static List<Ingredient> combine(List<Ingredient> entries) {
		List<Ingredient> result = new ArrayList<>();
		for (Ingredient entry : entries) {
			if (entry.stack().isEmpty() || entry.amount() <= 0 || entry.amount() > 4096)
				continue;
			int index = -1;
			for (int i = 0; i < result.size(); i++)
				if (ItemStack.isSameItemSameComponents(result.get(i).stack(), entry.stack())) {
					index = i;
					break;
				}
			if (index < 0)
				result.add(new Ingredient(entry.stack().copyWithCount(1), entry.amount()));
			else {
				Ingredient old = result.get(index);
				result.set(index, new Ingredient(old.stack(), old.amount() + entry.amount()));
			}
		}
		return result;
	}

	private static Slot choose(List<Slot> slots, Set<Slot> used, Ingredient ingredient, boolean input,
		double middle, Level level) {
		return slots.stream().filter(slot -> !used.contains(slot))
			.filter(slot -> input ? slot.x() < middle - EPSILON : slot.x() > middle + EPSILON)
			.filter(slot -> !slot.existing() || matches(behaviour(level, slot), ingredient.stack()))
			.sorted(Comparator.<Slot>comparingInt(slot -> slot.existing() ? 0 : 1)
				.thenComparingDouble(slot -> Math.abs(slot.x() - middle)))
			.findFirst().orElse(null);
	}

	private static boolean misplacedExisting(List<Slot> slots, Ingredient ingredient, boolean input,
		double middle, Level level) {
		boolean found = false;
		for (Slot slot : slots) {
			if (!slot.existing() || !matches(behaviour(level, slot), ingredient.stack()))
				continue;
			found = true;
			if (input ? slot.x() < middle - EPSILON : slot.x() > middle + EPSILON)
				return false;
		}
		return found;
	}

	private static boolean matches(FactoryPanelBehaviour panel, ItemStack stack) {
		return panel != null && ItemStack.isSameItemSameComponents(panel.getFilter(), stack);
	}

	private static FactoryPanelBehaviour behaviour(Level level, Slot slot) {
		if (!(level.getBlockEntity(slot.pos()) instanceof FactoryPanelBlockEntity be))
			return null;
		FactoryPanelBehaviour panel = be.panels.get(slot.panel());
		return panel != null && panel.isActive() ? panel : null;
	}

	private static BlockState placementState(Player player, BlockPos support, Direction face) {
		Vec3 hit = Vec3.atCenterOf(support).add(face.getStepX() * .5,
			face.getStepY() * .5, face.getStepZ() * .5);
		BlockPlaceContext context = new BlockPlaceContext(player.level(), player, InteractionHand.MAIN_HAND,
			AllBlocks.FACTORY_GAUGE.asStack(), new BlockHitResult(hit, face, support, false));
		return AllBlocks.FACTORY_GAUGE.get().getStateForPlacement(context);
	}

	private static Vec3 slotCenter(BlockState state, PanelSlot slot) {
		Vec3 vec = new Vec3(.25 + slot.xOffset * .5, 1.5 / 16f, .25 + slot.yOffset * .5);
		vec = VecHelper.rotateCentered(vec, 180, Direction.Axis.Y);
		vec = VecHelper.rotateCentered(vec, Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(state) + 90,
			Direction.Axis.X);
		return VecHelper.rotateCentered(vec, Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state),
			Direction.Axis.Y);
	}

	private static int availableGauges(Player player) {
		int count = 0;
		for (ItemStack stack : player.getInventory().items)
			if (AllBlocks.FACTORY_GAUGE.isIn(stack) && FactoryPanelBlockItem.isTuned(stack))
				count += stack.getCount();
		return count;
	}

	public static boolean place(ServerPlayer player, Plan plan) {
		if (!plan.ready())
			return false;
		Level level = player.level();
		List<Node> nodes = new ArrayList<>(plan.inputs());
		nodes.addAll(plan.outputs());
		List<Slot> installed = new ArrayList<>();
		Set<BlockPos> createdBlocks = new HashSet<>();
		List<ItemStack> consumed = new ArrayList<>();
		for (Node node : nodes) {
			Slot slot = node.slot();
			if (slot.existing())
				continue;
			if (level.getBlockState(slot.pos()).isAir()) {
				if (!level.setBlock(slot.pos(), slot.state(), 3)) {
					rollback(player, installed, createdBlocks, consumed);
					return false;
				}
				createdBlocks.add(slot.pos());
			}
			if (!(level.getBlockEntity(slot.pos()) instanceof FactoryPanelBlockEntity be)) {
				rollback(player, installed, createdBlocks, consumed);
				return false;
			}
			FactoryPanelBehaviour panel = be.panels.get(slot.panel());
			if (panel == null || panel.isActive()) {
				rollback(player, installed, createdBlocks, consumed);
				return false;
			}
			ItemStack source = firstGauge(player);
			if (!panel.isActive() && !be.addPanel(slot.panel(),
				LogisticallyLinkedBlockItem.networkFromStack(
					FactoryPanelBlockItem.fixCtrlCopiedStack(source.copy())))) {
				rollback(player, installed, createdBlocks, consumed);
				return false;
			}
			installed.add(slot);
			if (!panel.setFilter(node.ingredient().stack())) {
				rollback(player, installed, createdBlocks, consumed);
				return false;
			}
			panel.count = node.ingredient().amount();
			panel.upTo = true;
			be.setChanged();
			be.redraw = true;
			if (!player.isCreative()) {
				consumed.add(source.copyWithCount(1));
				source.shrink(1);
			}
		}
		for (Node node : nodes)
			if (behaviour(level, node.slot()) == null) {
				rollback(player, installed, createdBlocks, consumed);
				return false;
			}
		for (Node output : plan.outputs()) {
			FactoryPanelBehaviour target = behaviour(level, output.slot());
			target.recipeOutput = output.ingredient().amount();
			for (Node input : plan.inputs()) {
				FactoryPanelBehaviour source = behaviour(level, input.slot());
				if (!target.targetedBy.containsKey(source.getPanelPosition()))
					target.addConnection(source.getPanelPosition());
				FactoryPanelConnection connection = target.targetedBy.get(source.getPanelPosition());
				if (connection != null)
					connection.amount = input.ingredient().amount();
			}
			target.panelBE().setChanged();
			target.panelBE().notifyUpdate();
		}
		player.getInventory().setChanged();
		return true;
	}

	private static void rollback(ServerPlayer player, List<Slot> installed,
		Set<BlockPos> createdBlocks, List<ItemStack> consumed) {
		Level level = player.level();
		for (int i = installed.size() - 1; i >= 0; i--) {
			Slot slot = installed.get(i);
			if (level.getBlockEntity(slot.pos()) instanceof FactoryPanelBlockEntity be)
				be.removePanel(slot.panel());
		}
		for (BlockPos pos : createdBlocks)
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		for (ItemStack stack : consumed)
			player.getInventory().placeItemBackInInventory(stack);
		player.getInventory().setChanged();
	}

	private static ItemStack firstGauge(Player player) {
		for (ItemStack stack : player.getInventory().items)
			if (AllBlocks.FACTORY_GAUGE.isIn(stack) && FactoryPanelBlockItem.isTuned(stack)
				&& !stack.isEmpty())
				return stack;
		return AllBlocks.FACTORY_GAUGE.asStack();
	}

	public static Component message(String error) {
		return Component.translatable("create_biotech.honeycomb_gauge_cluster.jei." + error);
	}
}

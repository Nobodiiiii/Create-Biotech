package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.Action;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.EditResult;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.ProgramState;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.ProgramUpdate;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Snapshot;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ComputerProgramMenu extends AbstractContainerMenu {
	private final ComputerBlockEntity computer;
	private final ServerPlayer viewer;
	private ProgramState programState;
	@Nullable
	private ProgramUpdate editResponse;
	private Snapshot snapshot = Snapshot.EMPTY;
	private long sentVersion = -1;
	private int updateCooldown;

	public ComputerProgramMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		super(CBMenuTypes.COMPUTER_PROGRAM.get(), id);
		computer = null;
		viewer = null;
		programState = new ProgramState(buffer);
		addInventorySamples(inventory);
	}

	private ComputerProgramMenu(int id, Inventory inventory, ServerPlayer viewer, ComputerBlockEntity computer, ProgramState programState) {
		super(CBMenuTypes.COMPUTER_PROGRAM.get(), id);
		this.viewer = viewer;
		this.computer = computer;
		this.programState = programState;
		addInventorySamples(inventory);
	}

	static void open(ServerPlayer player, ComputerBlockEntity computer) {
		ProgramState state = programsOf(computer);
		player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ComputerProgramMenu(id, inventory, player, computer, state),
			computer.getBlockState().getBlock().getName()), state::write);
	}

	private void addInventorySamples(Inventory inventory) {
		// Sync the player's inventory while this menu is open. The screen renders the slots in its inventory tab.
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			addSlot(new Slot(inventory, i, 0, 0) {
				@Override public boolean isActive() { return false; }
			});
		}
	}

	private static ProgramState programsOf(ComputerBlockEntity computer) {
		return new ProgramState(computer.programRevision(), computer.program(), computer.restartProgram(), computer.library());
	}

	public ProgramState programState() { return programState; }
	public Snapshot snapshot() { return snapshot; }

	void receive(Snapshot snapshot) { this.snapshot = snapshot; }

	void receive(ProgramUpdate update) {
		programState = update.state();
		if (update.result() != EditResult.SYNC)
			editResponse = update;
	}

	@Nullable
	public ProgramUpdate takeEditResponse() {
		ProgramUpdate response = editResponse;
		editResponse = null;
		return response;
	}

	void apply(ServerPlayer player, Action action, long expectedRevision, Optional<ComputerProgram> program) {
		if (player != viewer || !stillValid(player))
			return;
		boolean mayEdit = player.level().mayInteract(player, computer.getBlockPos());
		if (action == Action.STOP) {
			if (mayEdit) {
				computer.stopProgram();
				updateCooldown = 0;
			}
			return;
		}
		if (!mayEdit || program.isEmpty()) {
			sendPrograms(programsOf(computer), EditResult.REJECTED);
			return;
		}
		if (expectedRevision != computer.programRevision()) {
			sendPrograms(programsOf(computer), EditResult.CONFLICT);
			return;
		}
		ComputerProgram submitted = program.orElseThrow();
		if (submitted.firstUnavailable(computer.library()) != null) {
			sendPrograms(programsOf(computer), EditResult.LIBRARY_LOCKED);
			return;
		}
		if (action == Action.SAVE)
			computer.saveProgram(submitted);
		else
			computer.run(submitted, player.getUUID());
		sendPrograms(programsOf(computer), EditResult.ACCEPTED);
		updateCooldown = 0;
	}

	private void sendPrograms(ProgramState state, EditResult result) {
		programState = state;
		CBPackets.sendToPlayer(new ProgramUpdate(containerId, result, state), viewer);
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (computer == null)
			return;
		if (updateCooldown > 0) {
			updateCooldown--;
			return;
		}
		ProgramState programs = programsOf(computer);
		if (!programs.equals(programState))
			sendPrograms(programs, EditResult.SYNC);
		long version = computer.syncVersion();
		if (sentVersion != version) {
			sentVersion = version;
			updateCooldown = 3;
			CBPackets.sendToPlayer(new ComputerProgramPacket.StatusUpdate(containerId, computer.snapshot()), viewer);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		if (computer == null)
			return true;
		Level level = player.level();
		BlockPos pos = computer.getBlockPos();
		return !player.isSpectator() && player == viewer && !computer.isRemoved() && computer.getLevel() == level
			&& level.isLoaded(pos) && player.canInteractWithBlock(pos, 1)
			&& level.getBlockEntity(pos) == computer && ComputerBlock.hasPair(level, pos, computer.getBlockState())
			&& computer.mayEdit(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		// The inventory slots only provide samples for the editor.
	}
}

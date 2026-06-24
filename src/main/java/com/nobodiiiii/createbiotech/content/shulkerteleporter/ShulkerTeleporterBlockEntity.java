package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ShulkerTeleporterBlockEntity extends BlockEntity implements MenuProvider {

	public static final int CLOSE_TICKS = 80;
	public static final int SEALED_HOLD_TICKS = 10;
	public static final int ARRIVAL_COOLDOWN_TICKS = 80;
	private static final int MAX_ADDRESS_LENGTH = 32;
	private static final Map<ResourceKey<Level>, Map<String, Set<BlockPos>>> ADDRESS_INDEX = new HashMap<>();

	private String ownAddress = "";
	private String targetAddress = "";
	private int closingTicks;
	private int previousClosingTicks;
	private int sealedHoldTicks;
	private boolean closing;
	private final Map<UUID, Integer> arrivalCooldowns = new HashMap<>();

	public ShulkerTeleporterBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SHULKER_TELEPORTER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ShulkerTeleporterBlockEntity be) {
		be.tick();
	}

	public void tick() {
		previousClosingTicks = closingTicks;

		if (level == null)
			return;

		if (level.isClientSide) {
			if (closing && closingTicks < CLOSE_TICKS)
				closingTicks++;
			else if (!closing && closingTicks > 0)
				closingTicks--;
			return;
		}

		registerAddress();
		tickArrivalCooldowns();

		List<ServerPlayer> playersInside = level.getEntitiesOfClass(ServerPlayer.class, getTeleportArea(),
			this::canTeleportPlayer);
		boolean shouldClose = !playersInside.isEmpty() && hasUsableTarget();

		if (shouldClose) {
			if (!closing) {
				closing = true;
				sendBlockUpdate();
			}
			if (closingTicks < CLOSE_TICKS)
				closingTicks++;
			if (closingTicks >= CLOSE_TICKS && ++sealedHoldTicks >= SEALED_HOLD_TICKS) {
				ServerPlayer player = playersInside.get(0);
				if (teleport(player)) {
					closing = false;
					closingTicks = 0;
					sealedHoldTicks = 0;
					sendBlockUpdate();
				}
			}
			return;
		}

		if (closing || closingTicks > 0) {
			closing = false;
			closingTicks = Math.max(0, closingTicks - 2);
			sealedHoldTicks = 0;
			sendBlockUpdate();
		}
	}

	public void sendToMenu(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(worldPosition);
		buffer.writeUtf(ownAddress, MAX_ADDRESS_LENGTH);
		buffer.writeUtf(targetAddress, MAX_ADDRESS_LENGTH);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.create_biotech.shulker_teleporter");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new ShulkerTeleporterMenu(id, inventory, this);
	}

	public boolean canPlayerUse(Player player) {
		return level != null && level.getBlockEntity(worldPosition) == this
			&& player.distanceToSqr(worldPosition.getX() + 0.5d, worldPosition.getY() + 1.0d,
				worldPosition.getZ() + 0.5d) <= 64.0d;
	}

	public String getOwnAddress() {
		return ownAddress;
	}

	public String getTargetAddress() {
		return targetAddress;
	}

	public void setAddresses(String ownAddress, String targetAddress) {
		unregisterAddress();
		this.ownAddress = normalizeAddress(ownAddress);
		this.targetAddress = normalizeAddress(targetAddress);
		registerAddress();
		setChanged();
		sendBlockUpdate();
	}

	public float getClosingProgress(float partialTicks) {
		return Mth.clamp(Mth.lerp(partialTicks, previousClosingTicks, closingTicks) / (float) CLOSE_TICKS, 0.0f,
			1.0f);
	}

	public boolean isClosing() {
		return closing;
	}

	public AABB getTeleportArea() {
		return new AABB(worldPosition.getX() + 0.18d, worldPosition.getY(), worldPosition.getZ() + 0.18d,
			worldPosition.getX() + 0.82d, worldPosition.getY() + 2.85d, worldPosition.getZ() + 0.82d);
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).expandTowards(0, 3, 0);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		registerAddress();
	}

	@Override
	public void setRemoved() {
		unregisterAddress();
		super.setRemoved();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putString("OwnAddress", ownAddress);
		tag.putString("TargetAddress", targetAddress);
		tag.putInt("ClosingTicks", closingTicks);
		tag.putInt("SealedHoldTicks", sealedHoldTicks);
		tag.putBoolean("Closing", closing);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		ownAddress = normalizeAddress(tag.getString("OwnAddress"));
		targetAddress = normalizeAddress(tag.getString("TargetAddress"));
		closingTicks = Mth.clamp(tag.getInt("ClosingTicks"), 0, CLOSE_TICKS);
		previousClosingTicks = closingTicks;
		sealedHoldTicks = Mth.clamp(tag.getInt("SealedHoldTicks"), 0, SEALED_HOLD_TICKS);
		closing = tag.getBoolean("Closing");
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		load(tag);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	static String normalizeAddress(String address) {
		if (address == null)
			return "";
		String trimmed = address.trim();
		if (trimmed.length() > MAX_ADDRESS_LENGTH)
			trimmed = trimmed.substring(0, MAX_ADDRESS_LENGTH);
		return trimmed.toLowerCase(Locale.ROOT);
	}

	private boolean hasUsableTarget() {
		return !targetAddress.isBlank() && findTarget() != null;
	}

	private boolean canTeleportPlayer(ServerPlayer player) {
		if (player.isSpectator() || !player.isAlive())
			return false;
		return !arrivalCooldowns.containsKey(player.getUUID());
	}

	private boolean teleport(ServerPlayer player) {
		ShulkerTeleporterBlockEntity target = findTarget();
		if (target == null || !(target.level instanceof ServerLevel targetLevel))
			return false;

		target.markArrivalCooldown(player.getUUID());
		player.teleportTo(targetLevel, target.worldPosition.getX() + 0.5d, target.worldPosition.getY() + 0.05d,
			target.worldPosition.getZ() + 0.5d, player.getYRot(), player.getXRot());
		player.resetFallDistance();
		return true;
	}

	private void markArrivalCooldown(UUID uuid) {
		arrivalCooldowns.put(uuid, ARRIVAL_COOLDOWN_TICKS);
	}

	private void tickArrivalCooldowns() {
		Iterator<Map.Entry<UUID, Integer>> iterator = arrivalCooldowns.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int remaining = entry.getValue() - 1;
			if (remaining <= 0)
				iterator.remove();
			else
				entry.setValue(remaining);
		}
	}

	@Nullable
	private ShulkerTeleporterBlockEntity findTarget() {
		if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null)
			return null;

		for (ServerLevel candidateLevel : serverLevel.getServer().getAllLevels()) {
			Map<String, Set<BlockPos>> byAddress = ADDRESS_INDEX.get(candidateLevel.dimension());
			if (byAddress == null)
				continue;
			Set<BlockPos> positions = byAddress.get(targetAddress);
			if (positions == null)
				continue;

			Iterator<BlockPos> iterator = positions.iterator();
			while (iterator.hasNext()) {
				BlockPos targetPos = iterator.next();
				BlockEntity blockEntity = candidateLevel.getBlockEntity(targetPos);
				if (!(blockEntity instanceof ShulkerTeleporterBlockEntity target)) {
					iterator.remove();
					continue;
				}
				if (target == this)
					continue;
				if (!targetAddress.equals(target.ownAddress)) {
					iterator.remove();
					continue;
				}
				return target;
			}
		}
		return null;
	}

	private void registerAddress() {
		if (level == null || level.isClientSide || ownAddress.isBlank())
			return;
		ADDRESS_INDEX.computeIfAbsent(level.dimension(), key -> new HashMap<>())
			.computeIfAbsent(ownAddress, key -> new HashSet<>())
			.add(worldPosition);
	}

	private void unregisterAddress() {
		if (level == null || level.isClientSide || ownAddress.isBlank())
			return;
		Map<String, Set<BlockPos>> byAddress = ADDRESS_INDEX.get(level.dimension());
		if (byAddress == null)
			return;
		Set<BlockPos> positions = byAddress.get(ownAddress);
		if (positions == null)
			return;
		positions.remove(worldPosition);
		if (positions.isEmpty())
			byAddress.remove(ownAddress);
	}

	private void sendBlockUpdate() {
		if (level == null)
			return;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}
}

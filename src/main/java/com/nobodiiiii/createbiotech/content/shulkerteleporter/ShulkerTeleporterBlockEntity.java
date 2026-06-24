package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ShulkerTeleporterBlockEntity extends KineticBlockEntity implements MenuProvider {

	public static final int CLOSE_TICKS = 80;
	public static final int SEALED_HOLD_TICKS = 10;
	public static final int ARRIVAL_COOLDOWN_TICKS = 80;
	public static final float TOP_SHELL_OPEN_Y = -1.0f;
	public static final float TOP_SHELL_CLOSED_Y = -2.0f;
	private static final AABB TELEPORT_TRIGGER_AREA = new AABB(1.0d / 16.0d, 0.0d, 1.0d / 16.0d,
		15.0d / 16.0d, 0.25d, 15.0d / 16.0d);
	private static final int MAX_ADDRESS_LENGTH = 32;

	private String ownAddress = "";
	private String targetAddress = "";
	private float closingTicks;
	private float previousClosingTicks;
	private int sealedHoldTicks;
	private boolean closing;
	private final Map<UUID, Integer> arrivalCooldowns = new HashMap<>();

	public ShulkerTeleporterBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SHULKER_TELEPORTER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ShulkerTeleporterBlockEntity be) {
		be.tick();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	public void tick() {
		super.tick();
		previousClosingTicks = closingTicks;

		if (level == null)
			return;

		if (level.isClientSide) {
			float animationStep = getAnimationStep();
			if (closing && closingTicks < CLOSE_TICKS)
				closingTicks = Math.min(CLOSE_TICKS, closingTicks + animationStep);
			else if (!closing && closingTicks > 0)
				closingTicks = Math.max(0, closingTicks - animationStep);
			return;
		}

		tickArrivalCooldowns();

		List<Entity> entitiesInside = level.getEntitiesOfClass(Entity.class, getTeleportArea(),
			this::canTeleportEntity);
		boolean shouldClose = !entitiesInside.isEmpty() && hasUsableTarget() && Math.abs(getSpeed()) > 0;

		if (shouldClose) {
			float animationStep = getAnimationStep();
			if (!closing) {
				closing = true;
				sendBlockUpdate();
			}
			if (closingTicks < CLOSE_TICKS)
				closingTicks = Math.min(CLOSE_TICKS, closingTicks + animationStep);
			if (closingTicks >= CLOSE_TICKS) {
				if (sealedHoldTicks < SEALED_HOLD_TICKS)
					sealedHoldTicks++;
				if (sealedHoldTicks >= SEALED_HOLD_TICKS) {
					if (teleport(entitiesInside)) {
						closing = false;
						sealedHoldTicks = 0;
						sendBlockUpdate();
					}
				}
			}
			return;
		}

		if (closing || closingTicks > 0) {
			closing = false;
			closingTicks = Math.max(0, closingTicks - getAnimationStep());
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
		BlockPos bottom = getBottomPos();
		return level != null && level.getBlockEntity(worldPosition) == this
			&& player.distanceToSqr(bottom.getX() + 0.5d, bottom.getY() + 1.0d, bottom.getZ() + 0.5d) <= 64.0d;
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

	public float getTopShellYOffset(float partialTicks) {
		float progress = getClosingProgress(partialTicks);
		return Mth.lerp(progress, TOP_SHELL_OPEN_Y, TOP_SHELL_CLOSED_Y);
	}

	public double getTopShellTopY(float partialTicks) {
		return worldPosition.getY() + getTopShellYOffset(partialTicks) + 1.0d;
	}

	public boolean isClosing() {
		return closing;
	}

	private float getAnimationStep() {
		float speed = Math.abs(getSpeed());
		if (speed <= 0)
			return 1.0f;
		return Mth.clamp(speed / 64.0f, 0.25f, 4.0f);
	}

	public AABB getTeleportArea() {
		return TELEPORT_TRIGGER_AREA.move(getBottomPos());
	}

	public boolean isEntityInTeleportArea(Entity entity) {
		return entity.isAlive() && !entity.isSpectator() && getTeleportArea().intersects(entity.getBoundingBox());
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(getBottomPos()).expandTowards(0, 3, 0);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		registerAddress();
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		tag.putString("OwnAddress", ownAddress);
		tag.putString("TargetAddress", targetAddress);
		tag.putFloat("ClosingTicks", closingTicks);
		tag.putInt("SealedHoldTicks", sealedHoldTicks);
		tag.putBoolean("Closing", closing);
		super.write(tag, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		ownAddress = normalizeAddress(tag.getString("OwnAddress"));
		targetAddress = normalizeAddress(tag.getString("TargetAddress"));
		closingTicks = Mth.clamp(tag.getFloat("ClosingTicks"), 0, CLOSE_TICKS);
		previousClosingTicks = closingTicks;
		sealedHoldTicks = Mth.clamp(tag.getInt("SealedHoldTicks"), 0, SEALED_HOLD_TICKS);
		closing = tag.getBoolean("Closing");
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
		if (targetAddress.isBlank() || !(level instanceof ServerLevel serverLevel))
			return false;
		return ShulkerTeleporterSavedData.get(serverLevel.getServer())
			.hasTarget(targetAddress, getSavedLocation());
	}

	private boolean canTeleportEntity(Entity entity) {
		if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity))
			return false;
		if (!isEntityInTeleportArea(entity))
			return false;
		return !arrivalCooldowns.containsKey(entity.getUUID());
	}

	private boolean teleport(List<Entity> entities) {
		if (entities.isEmpty())
			return false;

		ShulkerTeleporterBlockEntity target = findOpenTarget(entities.get(0).getId());
		if (target == null || !(target.level instanceof ServerLevel targetLevel))
			return false;

		BlockPos targetBottom = target.getBottomPos();
		boolean teleportedAny = false;
		for (Entity entity : entities) {
			entity.resetFallDistance();
			boolean teleported = entity.teleportTo(targetLevel, targetBottom.getX() + 0.5d,
				targetBottom.getY() + 1.0d / 16.0d, targetBottom.getZ() + 0.5d,
				Set.<RelativeMovement>of(), entity.getYRot(), entity.getXRot());
			if (!teleported)
				continue;
			target.markArrivalCooldown(entity.getUUID());
			teleportedAny = true;
		}
		return teleportedAny;
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
	private ShulkerTeleporterBlockEntity findOpenTarget(int ticketOwner) {
		if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null)
			return null;

		ShulkerTeleporterSavedData savedData = ShulkerTeleporterSavedData.get(serverLevel.getServer());
		for (ShulkerTeleporterSavedData.Location location : savedData.getTargets(targetAddress,
			getSavedLocation())) {
			ServerLevel candidateLevel = serverLevel.getServer().getLevel(location.dimension());
			if (candidateLevel == null) {
				savedData.unregister(location);
				continue;
			}

			ChunkPos targetChunk = new ChunkPos(location.pos());
			// This ticket expires after five ticks and is only refreshed while an entity is waiting to teleport.
			candidateLevel.getChunkSource()
				.addRegionTicket(TicketType.POST_TELEPORT, targetChunk, 1, ticketOwner);
			BlockEntity blockEntity = candidateLevel.getBlockEntity(location.pos());
			if (!(blockEntity instanceof ShulkerTeleporterBlockEntity target)) {
				savedData.unregister(location);
				continue;
			}
			if (!targetAddress.equals(target.ownAddress)) {
				savedData.register(location, target.ownAddress);
				continue;
			}
			if (!target.canReceiveTeleport())
				continue;
			return target;
		}
		return null;
	}

	private boolean canReceiveTeleport() {
		if (!isFullyOpen() || level == null)
			return false;
		return level.getEntitiesOfClass(Entity.class, getTeleportArea(), this::blocksIncomingTeleport)
			.isEmpty();
	}

	private boolean blocksIncomingTeleport(Entity entity) {
		return entity.isAlive() && !entity.isSpectator()
			&& (entity instanceof LivingEntity || entity instanceof ItemEntity);
	}

	private boolean isFullyOpen() {
		return !closing && closingTicks <= 0;
	}

	private void registerAddress() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		ShulkerTeleporterSavedData.get(serverLevel.getServer())
			.register(getSavedLocation(), ownAddress);
	}

	public void unregisterAddress() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		ShulkerTeleporterSavedData.get(serverLevel.getServer())
			.unregister(getSavedLocation());
	}

	private ShulkerTeleporterSavedData.Location getSavedLocation() {
		return new ShulkerTeleporterSavedData.Location(level.dimension(), worldPosition.immutable());
	}

	private void sendBlockUpdate() {
		if (level == null)
			return;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	public BlockPos getBottomPos() {
		return worldPosition.below(ShulkerTeleporterBlock.TOP);
	}
}

package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.Vec3;

public class ShulkerTeleporterBlockEntity extends KineticBlockEntity implements MenuProvider {

	public static final int CLOSE_TICKS = 80;
	public static final int SEALED_HOLD_TICKS = 10;
	public static final int DEFAULT_ARRIVAL_COOLDOWN_TICKS = 80;
	public static final float TOP_SHELL_OPEN_Y = -1.0f;
	public static final float TOP_SHELL_CLOSED_Y = -2.0f;
	public static final int MAX_ADDRESS_LENGTH = 32;
	public static final int MAX_CANDIDATE_ADDRESSES = 64;
	private static final AABB TELEPORT_TRIGGER_AREA = new AABB(1.0d / 16.0d, 0.0d, 1.0d / 16.0d,
		15.0d / 16.0d, 0.25d, 15.0d / 16.0d);
	private static final Map<Level, Set<BlockPos>> CLIENT_ACTIVE_TELEPORTERS = new WeakHashMap<>();

	private String ownAddress = "";
	private String targetAddress = "";
	private final List<String> candidateAddresses = new ArrayList<>();
	private float closingTicks;
	private float previousClosingTicks;
	private int sealedHoldTicks;
	private boolean closing;
	private final Map<UUID, Integer> arrivalCooldowns = new HashMap<>();
	@Nullable
	private ShulkerTeleporterSavedData.Location registeredLocation;

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
			updateClientActiveRegistration();
			return;
		}

		tickArrivalCooldowns();

		List<Entity> entitiesInside = level.getEntitiesOfClass(Entity.class, getWorldTeleportArea(),
			this::canTeleportEntity);
		int maximumEntities = CBConfigs.SERVER.shulkerTeleporter.maxEntitiesPerTeleport.get();
		if (maximumEntities > 0 && entitiesInside.size() > maximumEntities)
			entitiesInside = new ArrayList<>(entitiesInside.subList(0, maximumEntities));
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
			boolean wasClosing = closing;
			closing = false;
			closingTicks = Math.max(0, closingTicks - getAnimationStep());
			sealedHoldTicks = 0;
			if (wasClosing || closingTicks <= 0)
				sendBlockUpdate();
			else
				setChanged();
		}
	}

	@Override
	public void sendToMenu(RegistryFriendlyByteBuf buffer) {
		buffer.writeBlockPos(worldPosition);
		buffer.writeUtf(ownAddress, MAX_ADDRESS_LENGTH);
		buffer.writeUtf(targetAddress, MAX_ADDRESS_LENGTH);
		buffer.writeVarInt(candidateAddresses.size());
		for (String candidateAddress : candidateAddresses)
			buffer.writeUtf(candidateAddress, MAX_ADDRESS_LENGTH);
		UUID subLevelId = getSubLevelId();
		buffer.writeBoolean(subLevelId != null);
		if (subLevelId != null)
			buffer.writeUUID(subLevelId);
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
			&& SubLevelCompat.canEntityInteractWith(level, worldPosition, player)
			&& SubLevelCompat.distanceSquared(level, player.position(),
				new Vec3(bottom.getX() + 0.5d, bottom.getY() + 1.0d, bottom.getZ() + 0.5d)) <= 64.0d;
	}

	public String getOwnAddress() {
		return ownAddress;
	}

	public String getTargetAddress() {
		return targetAddress;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (super.addToGoggleTooltip(tooltip, isPlayerSneaking))
			tooltip.add(Component.empty());
		CreateLang.builder()
			.add(Component.translatable("create_biotech.shulker_teleporter.target_address"))
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip);
		CreateLang.builder()
			.add(targetAddress.isEmpty()
				? Component.translatable("create_biotech.shulker_teleporter.target_address.unset")
				: Component.literal(targetAddress))
			.style(targetAddress.isEmpty() ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA)
			.forGoggles(tooltip, 1);
		return true;
	}

	public List<String> getCandidateAddresses() {
		return List.copyOf(candidateAddresses);
	}

	@Nullable
	public UUID getSubLevelId() {
		return level == null ? null : SubLevelCompat.getSpaceId(level, worldPosition);
	}

	public void setAddresses(String ownAddress, String targetAddress) {
		setConfiguration(ownAddress, targetAddress, candidateAddresses);
	}

	public void setConfiguration(String ownAddress, String targetAddress, List<String> candidateAddresses) {
		unregisterAddress();
		this.ownAddress = normalizeAddress(ownAddress);
		this.targetAddress = normalizeAddress(targetAddress);
		this.candidateAddresses.clear();
		this.candidateAddresses.addAll(normalizeCandidateAddresses(candidateAddresses));
		registerAddress();
		setChanged();
		sendBlockUpdate();
		if (level != null && !level.isClientSide)
			for (int part = ShulkerTeleporterBlock.BOTTOM; part <= ShulkerTeleporterBlock.TOP; part++)
				DisplayLinkBlock.notifyGatherers(level, getBottomPos().above(part));
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

	public AABB getWorldTeleportArea() {
		return level == null ? getTeleportArea()
			: SubLevelCompat.toWorldBounds(level, worldPosition, getTeleportArea());
	}

	public boolean isEntityInTeleportArea(Entity entity) {
		Vec3 localEntityPosition = level == null ? entity.position()
			: SubLevelCompat.toLocal(level, worldPosition, entity.position());
		return level != null && entity.isAlive() && !entity.isSpectator()
			&& SubLevelCompat.canEntityInteractWith(level, worldPosition, entity)
			&& getTeleportArea().contains(localEntityPosition);
	}

	@Override
	protected AABB createRenderBoundingBox() {
		return new AABB(getBottomPos()).expandTowards(0, 3, 0);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		registerAddress();
		updateClientActiveRegistration();
	}

	@Override
	public void onChunkUnloaded() {
		unregisterClientActiveTeleporter();
		if (registeredLocation != null && registeredLocation.subLevelId() != null)
			unregisterAddress();
		super.onChunkUnloaded();
	}

	@Override
	public void invalidate() {
		unregisterClientActiveTeleporter();
		super.invalidate();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		tag.putString("OwnAddress", ownAddress);
		tag.putString("TargetAddress", targetAddress);
		ListTag candidateTags = new ListTag();
		for (String candidateAddress : candidateAddresses)
			candidateTags.add(StringTag.valueOf(candidateAddress));
		tag.put("CandidateAddresses", candidateTags);
		tag.putFloat("ClosingTicks", closingTicks);
		tag.putInt("SealedHoldTicks", sealedHoldTicks);
		tag.putBoolean("Closing", closing);
		super.write(tag, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		ownAddress = normalizeAddress(tag.getString("OwnAddress"));
		targetAddress = normalizeAddress(tag.getString("TargetAddress"));
		candidateAddresses.clear();
		ListTag candidateTags = tag.getList("CandidateAddresses", Tag.TAG_STRING);
		List<String> loadedCandidates = new ArrayList<>(candidateTags.size());
		for (Tag candidateTag : candidateTags)
			loadedCandidates.add(candidateTag.getAsString());
		candidateAddresses.addAll(normalizeCandidateAddresses(loadedCandidates));
		closingTicks = Mth.clamp(tag.getFloat("ClosingTicks"), 0, CLOSE_TICKS);
		previousClosingTicks = closingTicks;
		sealedHoldTicks = Mth.clamp(tag.getInt("SealedHoldTicks"), 0, SEALED_HOLD_TICKS);
		closing = tag.getBoolean("Closing");
		updateClientActiveRegistration();
	}

	static Iterable<BlockPos> activeClientTeleporters(Level level) {
		Set<BlockPos> positions = CLIENT_ACTIVE_TELEPORTERS.get(level);
		return positions == null ? List.of() : positions;
	}

	static void clearActiveClientTeleporters(Level level) {
		CLIENT_ACTIVE_TELEPORTERS.remove(level);
	}

	private void updateClientActiveRegistration() {
		if (level == null || !level.isClientSide)
			return;
		if (closingTicks > 0.0f || previousClosingTicks > 0.0f) {
			CLIENT_ACTIVE_TELEPORTERS.computeIfAbsent(level, ignored -> new java.util.HashSet<>())
				.add(worldPosition.immutable());
			return;
		}
		unregisterClientActiveTeleporter();
	}

	private void unregisterClientActiveTeleporter() {
		if (level == null || !level.isClientSide)
			return;
		Set<BlockPos> positions = CLIENT_ACTIVE_TELEPORTERS.get(level);
		if (positions == null)
			return;
		positions.remove(worldPosition);
		if (positions.isEmpty())
			CLIENT_ACTIVE_TELEPORTERS.remove(level);
	}

	static String normalizeAddress(String address) {
		if (address == null)
			return "";
		String trimmed = address.trim();
		if (trimmed.length() > MAX_ADDRESS_LENGTH)
			trimmed = trimmed.substring(0, MAX_ADDRESS_LENGTH);
		return trimmed.toLowerCase(Locale.ROOT);
	}

	static List<String> normalizeCandidateAddresses(List<String> addresses) {
		LinkedHashSet<String> normalizedAddresses = new LinkedHashSet<>();
		if (addresses != null)
			for (String address : addresses) {
				String normalizedAddress = normalizeAddress(address);
				if (normalizedAddress.isBlank())
					continue;
				normalizedAddresses.add(normalizedAddress);
				if (normalizedAddresses.size() >= MAX_CANDIDATE_ADDRESSES)
					break;
			}
		return new ArrayList<>(normalizedAddresses);
	}

	private boolean hasUsableTarget() {
		if (targetAddress.isBlank() || !(level instanceof ServerLevel serverLevel))
			return false;
		ShulkerTeleporterSavedData.Location source = getSavedLocation();
		return ShulkerTeleporterSavedData.get(serverLevel.getServer()).getTargets(targetAddress, source)
			.stream().anyMatch(target -> isTargetAllowed(source, target)
				&& isTargetAvailableWithoutTeleport(serverLevel, target));
	}

	private boolean canTeleportEntity(Entity entity) {
		if (entity instanceof Player) {
			if (!CBConfigs.SERVER.shulkerTeleporter.allowPlayers.get())
				return false;
		} else if (entity instanceof ItemEntity) {
			if (!CBConfigs.SERVER.shulkerTeleporter.allowItems.get())
				return false;
		} else if (entity instanceof LivingEntity) {
			if (!CBConfigs.SERVER.shulkerTeleporter.allowMobs.get())
				return false;
		} else {
			return false;
		}
		if (!isEntityInTeleportArea(entity))
			return false;
		return !arrivalCooldowns.containsKey(entity.getUUID());
	}

	private boolean teleport(List<Entity> entities) {
		if (entities.isEmpty())
			return false;
		if (!(level instanceof ServerLevel sourceLevel))
			return false;

		ShulkerTeleporterBlockEntity target = findOpenTarget(entities.get(0).getId());
		if (target == null || !(target.level instanceof ServerLevel targetLevel))
			return false;

		BlockPos targetBottom = target.getBottomPos();
		Vec3 targetWorldPos = SubLevelCompat.toWorld(targetLevel,
			new Vec3(targetBottom.getX() + 0.5d, targetBottom.getY() + 1.0d / 16.0d,
				targetBottom.getZ() + 0.5d));
		boolean teleportedAny = false;
		for (Entity entity : entities) {
			entity.resetFallDistance();
			boolean teleported = entity.teleportTo(targetLevel, targetWorldPos.x, targetWorldPos.y, targetWorldPos.z,
				Set.<RelativeMovement>of(), entity.getYRot(), entity.getXRot());
			if (!teleported)
				continue;
			target.markArrivalCooldown(entity.getUUID());
			if (entity instanceof ServerPlayer player)
				CBAdvancements.award(player, CBAdvancements.SHULKER_TELEPORTER);
			teleportedAny = true;
		}
		if (teleportedAny) {
			playTeleportEffects(sourceLevel, getBottomPos());
			playTeleportEffects(targetLevel, targetBottom);
		}
		return teleportedAny;
	}

	private static void playTeleportEffects(ServerLevel level, BlockPos bottom) {
		Vec3 worldPos = SubLevelCompat.toWorld(level,
			new Vec3(bottom.getX() + 0.5d, bottom.getY() + 1.0d, bottom.getZ() + 0.5d));
		double x = worldPos.x;
		double y = worldPos.y;
		double z = worldPos.z;
		level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.PORTAL, x, y, z, 32, 0.45d, 1.0d, 0.45d, 0.2d);
	}

	private void markArrivalCooldown(UUID uuid) {
		int cooldown = CBConfigs.SERVER.shulkerTeleporter.arrivalCooldownTicks.get();
		if (cooldown > 0)
			arrivalCooldowns.put(uuid, cooldown);
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
		ShulkerTeleporterSavedData.Location source = getSavedLocation();
		for (ShulkerTeleporterSavedData.Location location : savedData.getTargets(targetAddress, source)) {
			if (!isTargetAllowed(source, location))
				continue;
			ServerLevel candidateLevel = serverLevel.getServer().getLevel(location.dimension());
			if (candidateLevel == null) {
				savedData.unregister(location);
				continue;
			}
			if (!SubLevelCompat.matchesSpace(candidateLevel, location.pos(), location.subLevelId())) {
				savedData.unregister(location);
				continue;
			}

			if (location.subLevelId() == null) {
				ChunkPos targetChunk = new ChunkPos(location.pos());
				if (!CBConfigs.SERVER.shulkerTeleporter.allowDestinationChunkLoading.get()
					&& !candidateLevel.isLoaded(location.pos()))
					continue;
				if (CBConfigs.SERVER.shulkerTeleporter.allowDestinationChunkLoading.get()) {
					// Static-world endpoints can be loaded with a vanilla ticket. Sublevels own their plot lifecycle.
					candidateLevel.getChunkSource()
						.addRegionTicket(TicketType.POST_TELEPORT, targetChunk, 1, ticketOwner);
				}
			} else if (!candidateLevel.isLoaded(location.pos())) {
				continue;
			}
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

	private static boolean isTargetAllowed(ShulkerTeleporterSavedData.Location source,
		ShulkerTeleporterSavedData.Location target) {
		if (!source.dimension().equals(target.dimension())
			&& !CBConfigs.SERVER.shulkerTeleporter.allowCrossDimension.get())
			return false;
		if (!source.dimension().equals(target.dimension())
			|| !Objects.equals(source.subLevelId(), target.subLevelId()))
			return true;
		double maximum = CBConfigs.SERVER.shulkerTeleporter.maxSameSpaceDistance.get();
		return maximum <= 0 || source.pos().distSqr(target.pos()) <= maximum * maximum;
	}

	private static boolean isTargetAvailableWithoutTeleport(ServerLevel sourceLevel,
		ShulkerTeleporterSavedData.Location target) {
		ServerLevel targetLevel = sourceLevel.getServer().getLevel(target.dimension());
		if (targetLevel == null)
			return false;
		if (target.subLevelId() != null)
			return targetLevel.isLoaded(target.pos());
		return CBConfigs.SERVER.shulkerTeleporter.allowDestinationChunkLoading.get()
			|| targetLevel.isLoaded(target.pos());
	}

	private boolean canReceiveTeleport() {
		if (!isFullyOpen() || level == null)
			return false;
		return level.getEntitiesOfClass(Entity.class, getWorldTeleportArea(), this::blocksIncomingTeleport)
			.isEmpty();
	}

	private boolean blocksIncomingTeleport(Entity entity) {
		return isEntityInTeleportArea(entity)
			&& (entity instanceof LivingEntity || entity instanceof ItemEntity);
	}

	private boolean isFullyOpen() {
		return !closing && closingTicks <= 0;
	}

	private void registerAddress() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		ShulkerTeleporterSavedData savedData = ShulkerTeleporterSavedData.get(serverLevel.getServer());
		ShulkerTeleporterSavedData.Location currentLocation = getSavedLocation();
		if (registeredLocation != null && !registeredLocation.equals(currentLocation))
			savedData.unregister(registeredLocation);
		savedData.register(currentLocation, ownAddress);
		registeredLocation = currentLocation;
	}

	public void unregisterAddress() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		ShulkerTeleporterSavedData.Location location = registeredLocation == null ? getSavedLocation() : registeredLocation;
		ShulkerTeleporterSavedData.get(serverLevel.getServer())
			.unregister(location);
		registeredLocation = null;
	}

	private ShulkerTeleporterSavedData.Location getSavedLocation() {
		return new ShulkerTeleporterSavedData.Location(level.dimension(),
			SubLevelCompat.getSpaceId(level, worldPosition), worldPosition.immutable());
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

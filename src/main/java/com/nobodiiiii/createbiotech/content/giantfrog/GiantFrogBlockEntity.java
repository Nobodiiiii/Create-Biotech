package com.nobodiiiii.createbiotech.content.giantfrog;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.nobodiiiii.createbiotech.content.frogportal.FrogDigestiveTractBehaviour;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachDimensions;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachSavedData;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachSpace;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimearmor.SlimeArmorHandler;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GiantFrogBlockEntity extends SmartBlockEntity {
	private static final int EAT_INTERVAL = 20;
	private static final int TONGUE_ANIMATION_TICKS = 20;
	private static final int CATCH_ANIMATION_TICKS = 6;
	private static final int EAT_FINISH_TICKS = 10;
	private static final double CAPTURE_BOX_SIZE = 2.0d;
	private static final double TONGUE_PULL_SPEED = 0.75d;
	private static final double BELT_CONNECTION_FORWARD_DISTANCE = 1.5d;
	/** Covers the legs, the extended tongue and the belt handoff, none of which are in the body box. */
	private static final double RENDER_BOUNDS_PADDING = BELT_CONNECTION_FORWARD_DISTANCE;
	private static final double BELT_ITEM_Y = 15.0d / 16.0d;
	private static final double BELT_HANDOFF_DISTANCE = 0.26d;
	private static final double BELT_TONGUE_TRANSFER_DISTANCE = 0.5d;
	private static final double BELT_TRANSFER_DISTANCE = BELT_HANDOFF_DISTANCE + BELT_TONGUE_TRANSFER_DISTANCE;
	private static final int DEFAULT_BELT_TRANSFER_TICKS = 8;
	private static final int MIN_BELT_TRANSFER_TICKS = 4;
	private static final int MAX_BELT_TRANSFER_TICKS = 20;

	private int eatCooldown;
	private int tongueAnimationTicks;
	private int tongueAnimationAge;
	private boolean hasSpace;
	private long spaceIndex = -1L;
	private boolean frogLocationRecorded;
	private PendingEat pendingEat;
	private TransportedItemStack beltTransferItem;
	private int beltTransferAge;
	private int previousBeltTransferAge;
	private int beltTransferDuration = DEFAULT_BELT_TRANSFER_TICKS;

	public GiantFrogBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.GIANT_FROG.get(), pos, state);
		eatCooldown = levelRandomOffset(pos);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		if (GiantFrogBlock.isMouthInputPart(getBlockState())) {
			behaviours.add(new DirectBeltInputBehaviour(this).onlyInsertWhen(this::canAcceptBeltInput)
				.considerOccupiedWhen(this::isBeltTransferOccupied)
				.setInsertionHandler(this::handleBeltInsertion));
		}
	}

	@Override
	protected AABB createRenderBoundingBox() {
		// The default single-block box would cull the frog as soon as its anchor left
		// the frustum, even though the model covers three blocks. Padding covers the
		// legs, the tongue and the item riding the handoff in front of the mouth.
		if (!GiantFrogBlock.isMain(getBlockState()))
			return super.createRenderBoundingBox();
		return GiantFrogBlock.getBodyBounds(worldPosition, getBlockState())
			.inflate(RENDER_BOUNDS_PADDING);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, GiantFrogBlockEntity be) {
		be.tickSmartBlockEntity();
		if (!GiantFrogBlock.isMain(state))
			return;
		if (level.isClientSide) {
			be.tickClientAnimation();
			return;
		}
		if (be.hasSpace && !be.frogLocationRecorded && level instanceof ServerLevel serverLevel)
			be.recordFrogLocation(serverLevel.getServer());

		be.tickServerBeltTransfer();
		if (be.hasActiveBeltTransfer())
			return;

		if (be.pendingEat != null) {
			be.tickPendingEat(level, pos, state);
			return;
		}

		if (be.eatCooldown > 0) {
			be.eatCooldown--;
			return;
		}

		be.eatCooldown = EAT_INTERVAL;
		if (be.tryStartEatingPlayer(level, pos, state))
			return;
		be.tryStartEatingSmallSlime(level, pos, state);
	}

	private boolean tryStartEatingPlayer(Level level, BlockPos pos, BlockState state) {
		Player player = findSlimeDisguisedPlayer(level, pos, state);
		if (player == null)
			return false;
		if (!(level instanceof ServerLevel serverLevel))
			return false;

		pendingEat = PendingEat.player(player, level.dimension());
		playTongueEffects(serverLevel, pos);
		return true;
	}

	private void tickPendingEat(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			pendingEat = null;
			return;
		}

		Entity target = findPendingTarget(serverLevel, pendingEat);
		if (pendingEat.phase == PendingEatPhase.CATCH) {
			if (target == null || !target.isAlive()) {
				pendingEat = null;
				return;
			}

			pullTargetTowardMouth(target, pos, state);
			pendingEat.age++;
			if (pendingEat.age < CATCH_ANIMATION_TICKS)
				return;

			PendingEat eat = pendingEat;
			eat.phase = PendingEatPhase.EAT;
			eat.age = 0;
			playEatSound(serverLevel, pos);
			finishPendingEat(serverLevel, eat, target);
			return;
		}

		pendingEat.age++;
		if (pendingEat.age >= EAT_FINISH_TICKS)
			pendingEat = null;
	}

	private boolean tryStartEatingSmallSlime(Level level, BlockPos pos, BlockState state) {
		Slime slime = findSmallSlime(level, pos, state);
		if (slime == null)
			return false;
		if (!(level instanceof ServerLevel serverLevel))
			return false;

		pendingEat = PendingEat.smallSlime(slime);
		playTongueEffects(serverLevel, pos);
		return true;
	}

	private void finishPendingEat(ServerLevel level, PendingEat eat, Entity target) {
		switch (eat.type) {
			case SMALL_SLIME -> finishEatingSmallSlime(level, target);
			case SLIME_ARMORED_PLAYER -> {
				if (target instanceof ServerPlayer player)
					finishEatingPlayer(level, player, eat);
			}
		}
	}

	private void finishEatingSmallSlime(ServerLevel level, Entity target) {
		if (!(target instanceof Slime slime) || !slime.isAlive())
			return;

		Vec3 dropPos = slime.position();
		slime.discard();
		level.addFreshEntity(new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, new ItemStack(Items.SLIME_BALL)));
	}

	private void finishEatingPlayer(ServerLevel level, ServerPlayer player, PendingEat eat) {
		if (eat.returnDimension == null || eat.returnPos == null)
			return;

		MinecraftServer server = level.getServer();
		ServerLevel frogLevel = server.getLevel(FrogStomachDimensions.FROG_STOMACH);
		if (frogLevel == null)
			return;

		long index = ensureRoom(server, frogLevel);
		FrogStomachSavedData.get(server)
			.setReturn(player.getUUID(), index, eat.returnDimension, eat.returnPos);

		Entity changed = player.changeDimension(FrogDigestiveTractBehaviour.transitionTo(frogLevel, player,
			FrogStomachSpace.mouthPortalCenter(index), FrogStomachSpace.mouthEntryVelocity()));
		if (changed != null)
			changed.setPortalCooldown();
	}

	private static Entity findPendingTarget(ServerLevel level, PendingEat eat) {
		if (eat.type == PendingEatType.SLIME_ARMORED_PLAYER)
			return level.getServer()
				.getPlayerList()
				.getPlayer(eat.uuid);
		return level.getEntity(eat.uuid);
	}

	private static void pullTargetTowardMouth(Entity target, BlockPos pos, BlockState state) {
		Vec3 direction = target.position().vectorTo(getMouthPos(pos, state));
		target.setDeltaMovement(direction.lengthSqr() < 1.0E-7d
			? Vec3.ZERO
			: direction.normalize()
				.scale(TONGUE_PULL_SPEED));
		target.hasImpulse = true;
		target.hurtMarked = true;
	}

	private void playTongueEffects(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.FROG_TONGUE, SoundSource.NEUTRAL, 1.0f,
			0.9f + level.random.nextFloat() * 0.2f);
		if (level instanceof ServerLevel serverLevel)
			CBPackets.sendToTrackingChunk(new GiantFrogEatPacket(pos), serverLevel, pos);
	}

	private void playEatSound(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.FROG_EAT, SoundSource.NEUTRAL, 1.0f,
			0.9f + level.random.nextFloat() * 0.2f);
	}

	public void startEatAnimation() {
		startTongueAnimation();
	}

	public void startTongueAnimation() {
		tongueAnimationTicks = TONGUE_ANIMATION_TICKS;
		tongueAnimationAge = 0;
	}

	public boolean isTongueAnimating() {
		return tongueAnimationTicks > 0;
	}

	public boolean isMouthHeldOpenByBelt() {
		if (level == null || !GiantFrogBlock.isMain(getBlockState()))
			return false;
		Direction facing = getFacing(getBlockState());
		BlockPos beltPos = GiantFrogBlock.getMouthInputPos(worldPosition, getBlockState())
			.relative(facing);
		Direction requiredMovement = facing.getOpposite();
		return isBeltMovingToward(level, beltPos, requiredMovement);
	}

	public float getTongueAnimationAge(float partialTicks) {
		return isTongueAnimating() ? tongueAnimationAge + partialTicks : 0.0f;
	}

	public ItemStack getBeltTransferStack() {
		return beltTransferItem == null ? ItemStack.EMPTY : beltTransferItem.stack;
	}

	public TransportedItemStack getBeltTransferItem() {
		return beltTransferItem;
	}

	public float getBeltTransferProgress(float partialTicks) {
		if (!hasActiveBeltTransfer())
			return 0.0f;
		float age = Mth.lerp(partialTicks, (float) previousBeltTransferAge, (float) beltTransferAge);
		return Mth.clamp(age / Math.max(1, beltTransferDuration), 0.0f, 1.0f);
	}

	private void tickClientAnimation() {
		tickClientBeltTransfer();
		if (tongueAnimationTicks > 0) {
			tongueAnimationTicks--;
			tongueAnimationAge++;
		}
	}

	public long ensureRoom(MinecraftServer server, ServerLevel frogLevel) {
		if (!hasSpace) {
			spaceIndex = FrogStomachSavedData.get(server).allocateSpace();
			hasSpace = true;
			FrogStomachSpace.buildRoom(frogLevel, spaceIndex,
				!getBlockState().is(CBBlocks.GIANT_FROG_FACTORY.get()));
			setChanged();
		} else if (!FrogStomachSpace.isBuilt(frogLevel, spaceIndex)) {
			FrogStomachSpace.buildRoom(frogLevel, spaceIndex, false);
		}
		recordFrogLocation(server);
		return spaceIndex;
	}

	public boolean hasSpace() {
		return hasSpace;
	}

	public long getSpaceIndex() {
		return spaceIndex;
	}

	public void setSpaceIndex(long index) {
		spaceIndex = index;
		hasSpace = true;
		setChanged();
		if (level instanceof ServerLevel serverLevel)
			recordFrogLocation(serverLevel.getServer());
	}

	private void recordFrogLocation(MinecraftServer server) {
		if (!hasSpace || !(level instanceof ServerLevel serverLevel) || !GiantFrogBlock.isMain(getBlockState()))
			return;
		FrogStomachSavedData.get(server)
			.setFrogLocation(spaceIndex, serverLevel.dimension(), worldPosition, getFacing(getBlockState()));
		frogLocationRecorded = true;
	}

	private void tickSmartBlockEntity() {
		super.tick();
	}

	private boolean canAcceptBeltInput(Direction side) {
		if (!GiantFrogBlock.isMouthInputPart(getBlockState()) || side != getFacing(getBlockState()).getOpposite())
			return false;
		GiantFrogBlockEntity mainFrog = getMainFrog();
		return mainFrog != null && !mainFrog.hasActiveBeltTransfer() && mainFrog.pendingEat == null;
	}

	private boolean isBeltTransferOccupied(Direction side) {
		if (!GiantFrogBlock.isMouthInputPart(getBlockState()) || side != getFacing(getBlockState()).getOpposite())
			return false;
		GiantFrogBlockEntity mainFrog = getMainFrog();
		return mainFrog == null || mainFrog.hasActiveBeltTransfer() || mainFrog.pendingEat != null;
	}

	private ItemStack handleBeltInsertion(TransportedItemStack transported, Direction side, boolean simulate) {
		GiantFrogBlockEntity mainFrog = getMainFrog();
		if (mainFrog == null || mainFrog.hasActiveBeltTransfer())
			return transported.stack;
		if (simulate)
			return ItemStack.EMPTY;

		mainFrog.beginBeltTransfer(transported, getBeltTransferDuration());
		return ItemStack.EMPTY;
	}

	private boolean hasActiveBeltTransfer() {
		return beltTransferItem != null && !beltTransferItem.stack.isEmpty();
	}

	private void beginBeltTransfer(TransportedItemStack transported, int duration) {
		beltTransferItem = transported.copy();
		beltTransferAge = 0;
		previousBeltTransferAge = 0;
		beltTransferDuration = Mth.clamp(duration, MIN_BELT_TRANSFER_TICKS, MAX_BELT_TRANSFER_TICKS);
		setChanged();
		sendData();
	}

	private void tickServerBeltTransfer() {
		if (!hasActiveBeltTransfer())
			return;

		previousBeltTransferAge = beltTransferAge;
		beltTransferAge++;
		setChanged();
		if (beltTransferAge < beltTransferDuration)
			return;

		ItemStack transferred = beltTransferItem.stack.copy();
		clearBeltTransfer();
		ItemStack remainder = insertItemIntoStomach(transferred, false);
		if (!remainder.isEmpty())
			dropBeltTransferRemainder(remainder);
		setChanged();
		sendData();
	}

	public void dropBeltTransferItem() {
		if (!hasActiveBeltTransfer())
			return;

		ItemStack transferred = beltTransferItem.stack.copy();
		Vec3 dropPos = getBeltTransferWorldPos();
		clearBeltTransfer();
		dropItemAtBeltTransfer(transferred, dropPos);
		setChanged();
		sendData();
	}

	private void tickClientBeltTransfer() {
		if (!hasActiveBeltTransfer())
			return;
		previousBeltTransferAge = beltTransferAge;
		if (beltTransferAge < beltTransferDuration) {
			beltTransferAge++;
			return;
		}
		clearBeltTransfer();
	}

	private void clearBeltTransfer() {
		beltTransferItem = null;
		beltTransferAge = 0;
		previousBeltTransferAge = 0;
		beltTransferDuration = DEFAULT_BELT_TRANSFER_TICKS;
	}

	private int getBeltTransferDuration() {
		if (level == null)
			return DEFAULT_BELT_TRANSFER_TICKS;

		Direction facing = getFacing(getBlockState());
		float speed = getBeltMovementSpeed(level, worldPosition.relative(facing));
		if (speed <= 1.0E-4f)
			return DEFAULT_BELT_TRANSFER_TICKS;

		return Mth.clamp((int) Math.ceil(BELT_TRANSFER_DISTANCE / speed), MIN_BELT_TRANSFER_TICKS,
			MAX_BELT_TRANSFER_TICKS);
	}

	private static float getBeltMovementSpeed(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof BeltBlockEntity belt)
			return Math.abs(belt.getDirectionAwareBeltMovementSpeed());
		if (be instanceof SlimeBeltBlockEntity belt)
			return Math.abs(belt.getDirectionAwareBeltMovementSpeed());
		if (be instanceof MagmaBeltBlockEntity belt)
			return Math.abs(belt.getDirectionAwareBeltMovementSpeed());
		return 0.0f;
	}

	private void dropBeltTransferRemainder(ItemStack stack) {
		BlockPos mouthPos = GiantFrogBlock.getMouthInputPos(worldPosition, getBlockState());
		Vec3 dropPos = Vec3.atBottomCenterOf(mouthPos)
			.add(0.0d, BELT_ITEM_Y, 0.0d);
		dropItemAtBeltTransfer(stack, dropPos);
	}

	private void dropItemAtBeltTransfer(ItemStack stack, Vec3 dropPos) {
		if (!(level instanceof ServerLevel serverLevel) || stack.isEmpty())
			return;

		ItemEntity item = new ItemEntity(serverLevel, dropPos.x, dropPos.y, dropPos.z, stack.copy());
		item.setDeltaMovement(Vec3.atLowerCornerOf(getFacing(getBlockState()).getOpposite()
			.getNormal())
			.scale(0.05d));
		item.setDefaultPickUpDelay();
		serverLevel.addFreshEntity(item);
	}

	private Vec3 getBeltTransferWorldPos() {
		Direction facing = getFacing(getBlockState());
		Vec3 outward = Vec3.atLowerCornerOf(facing.getNormal());
		Vec3 sideways = Vec3.atLowerCornerOf(facing.getClockWise()
			.getNormal());
		double progress = Mth.clamp((double) beltTransferAge / Math.max(1, beltTransferDuration), 0.0d, 1.0d);
		double sideOffset = beltTransferItem == null ? 0.0d : beltTransferItem.sideOffset;
		return Vec3.atLowerCornerOf(worldPosition)
			.add(0.5d, BELT_ITEM_Y, 0.5d)
			.add(outward.scale(BELT_CONNECTION_FORWARD_DISTANCE + BELT_HANDOFF_DISTANCE))
			.add(outward.scale(-BELT_TRANSFER_DISTANCE * progress))
			.add(sideways.scale(sideOffset));
	}

	private ItemStack insertItemIntoStomach(ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		if (simulate)
			return ItemStack.EMPTY;
		if (!(level instanceof ServerLevel serverLevel))
			return stack;

		MinecraftServer server = serverLevel.getServer();
		ServerLevel frogLevel = server.getLevel(FrogStomachDimensions.FROG_STOMACH);
		if (frogLevel == null)
			return stack;

		long index = ensureRoom(server, frogLevel);
		Vec3 entryPos = FrogStomachSpace.mouthPortalCenter(index);
		ItemEntity item = new ItemEntity(frogLevel, entryPos.x, entryPos.y, entryPos.z, stack.copy());
		item.setDeltaMovement(FrogStomachSpace.mouthEntryVelocity());
		item.setPortalCooldown();
		item.setDefaultPickUpDelay();
		if (!frogLevel.addFreshEntity(item))
			return stack;

		return ItemStack.EMPTY;
	}

	private GiantFrogBlockEntity getMainFrog() {
		if (level == null)
			return null;
		BlockPos mainPos = GiantFrogBlock.getMainPos(worldPosition, getBlockState());
		if (level.getBlockEntity(mainPos) instanceof GiantFrogBlockEntity frog
			&& GiantFrogBlock.isMain(frog.getBlockState()))
			return frog;
		return null;
	}

	private static Slime findSmallSlime(Level level, BlockPos pos, BlockState state) {
		AABB bounds = getCaptureBounds(pos, state);
		Vec3 frogCenter = getCaptureCenter(bounds);
		List<Slime> slimes = level.getEntitiesOfClass(Slime.class, bounds, GiantFrogBlockEntity::canEat);
		return slimes.stream()
			.min(Comparator.comparingDouble(slime -> slime.distanceToSqr(frogCenter)))
			.orElse(null);
	}

	private static boolean canEat(Slime slime) {
		return slime.isAlive() && slime.getSize() == 1;
	}

	private static Player findSlimeDisguisedPlayer(Level level, BlockPos pos, BlockState state) {
		AABB bounds = getCaptureBounds(pos, state);
		Vec3 frogCenter = getCaptureCenter(bounds);
		List<Player> players = level.getEntitiesOfClass(Player.class, bounds, GiantFrogBlockEntity::canEatPlayer);
		return players.stream()
			.min(Comparator.comparingDouble(player -> player.distanceToSqr(frogCenter)))
			.orElse(null);
	}

	private static boolean canEatPlayer(Player player) {
		return player.isAlive() && !player.isSpectator() && player.canUsePortal(false)
			&& SlimeArmorHandler.testForSlimeStealth(player);
	}

	private static AABB getCaptureBounds(BlockPos pos, BlockState state) {
		AABB body = GiantFrogBlock.getBodyBounds(pos, state);
		Direction facing = getFacing(state);
		double centerX = (body.minX + body.maxX) * 0.5d;
		double centerZ = (body.minZ + body.maxZ) * 0.5d;
		double minY = body.minY;
		double maxY = minY + CAPTURE_BOX_SIZE;
		double halfSize = CAPTURE_BOX_SIZE / 2.0d;

		return switch (facing) {
			case NORTH -> new AABB(centerX - halfSize, minY, body.minZ - CAPTURE_BOX_SIZE,
				centerX + halfSize, maxY, body.minZ);
			case SOUTH -> new AABB(centerX - halfSize, minY, body.maxZ,
				centerX + halfSize, maxY, body.maxZ + CAPTURE_BOX_SIZE);
			case WEST -> new AABB(body.minX - CAPTURE_BOX_SIZE, minY, centerZ - halfSize,
				body.minX, maxY, centerZ + halfSize);
			case EAST -> new AABB(body.maxX, minY, centerZ - halfSize,
				body.maxX + CAPTURE_BOX_SIZE, maxY, centerZ + halfSize);
			default -> body;
		};
	}

	private static Vec3 getCaptureCenter(AABB bounds) {
		return new Vec3((bounds.minX + bounds.maxX) * 0.5d, (bounds.minY + bounds.maxY) * 0.5d,
			(bounds.minZ + bounds.maxZ) * 0.5d);
	}

	private static Vec3 getMouthPos(BlockPos pos, BlockState state) {
		AABB body = GiantFrogBlock.getBodyBounds(pos, state);
		Direction facing = getFacing(state);
		double centerX = (body.minX + body.maxX) * 0.5d;
		double centerZ = (body.minZ + body.maxZ) * 0.5d;
		double mouthY = body.minY + Math.min(0.75d, (body.maxY - body.minY) * 0.65d);

		return switch (facing) {
			case NORTH -> new Vec3(centerX, mouthY, body.minZ);
			case SOUTH -> new Vec3(centerX, mouthY, body.maxZ);
			case WEST -> new Vec3(body.minX, mouthY, centerZ);
			case EAST -> new Vec3(body.maxX, mouthY, centerZ);
			default -> body.getCenter();
		};
	}

	private static Direction getFacing(BlockState state) {
		return state.hasProperty(GiantFrogBlock.FACING) ? state.getValue(GiantFrogBlock.FACING) : Direction.NORTH;
	}

	private static boolean isBeltMovingToward(Level level, BlockPos pos, Direction movement) {
		if (level.getBlockEntity(pos) instanceof BeltBlockEntity belt)
			return belt.getSpeed() != 0 && belt.getMovementFacing() == movement;
		if (level.getBlockEntity(pos) instanceof SlimeBeltBlockEntity belt)
			return belt.getSpeed() != 0 && belt.getMovementFacing() == movement;
		if (level.getBlockEntity(pos) instanceof MagmaBeltBlockEntity belt)
			return belt.getSpeed() != 0 && belt.getMovementFacing() == movement;
		return false;
	}

	private static int levelRandomOffset(BlockPos pos) {
		return Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13, EAT_INTERVAL);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (!GiantFrogBlock.isMain(getBlockState()))
			return;
		tag.putBoolean("HasSpace", hasSpace);
		tag.putLong("SpaceIndex", spaceIndex);
		if (hasActiveBeltTransfer()) {
			tag.put("BeltTransferItem", beltTransferItem.serializeNBT(registries));
			tag.putInt("BeltTransferAge", beltTransferAge);
			tag.putInt("BeltTransferDuration", beltTransferDuration);
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (!GiantFrogBlock.isMain(getBlockState()))
			return;
		hasSpace = tag.getBoolean("HasSpace");
		spaceIndex = tag.getLong("SpaceIndex");
		frogLocationRecorded = false;
		if (tag.contains("BeltTransferItem")) {
			beltTransferItem = TransportedItemStack.read(tag.getCompound("BeltTransferItem"), registries);
			beltTransferAge = tag.getInt("BeltTransferAge");
			previousBeltTransferAge = beltTransferAge;
			beltTransferDuration = tag.contains("BeltTransferDuration")
				? Mth.clamp(tag.getInt("BeltTransferDuration"), MIN_BELT_TRANSFER_TICKS, MAX_BELT_TRANSFER_TICKS)
				: DEFAULT_BELT_TRANSFER_TICKS;
			if (!hasActiveBeltTransfer())
				clearBeltTransfer();
			return;
		}
		clearBeltTransfer();
	}

	private enum PendingEatType {
		SMALL_SLIME,
		SLIME_ARMORED_PLAYER
	}

	private enum PendingEatPhase {
		CATCH,
		EAT
	}

	private static class PendingEat {
		private final UUID uuid;
		private final PendingEatType type;
		private final ResourceKey<Level> returnDimension;
		private final Vec3 returnPos;
		private PendingEatPhase phase = PendingEatPhase.CATCH;
		private int age;

		private PendingEat(UUID uuid, PendingEatType type, ResourceKey<Level> returnDimension, Vec3 returnPos) {
			this.uuid = uuid;
			this.type = type;
			this.returnDimension = returnDimension;
			this.returnPos = returnPos;
		}

		private static PendingEat smallSlime(Slime slime) {
			return new PendingEat(slime.getUUID(), PendingEatType.SMALL_SLIME, null, null);
		}

		private static PendingEat player(Player player, ResourceKey<Level> returnDimension) {
			return new PendingEat(player.getUUID(), PendingEatType.SLIME_ARMORED_PLAYER, returnDimension,
				player.position());
		}
	}
}

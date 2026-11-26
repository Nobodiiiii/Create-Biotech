package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxHandler;
import com.nobodiiiii.createbiotech.content.cardboardbox.LargeCardboardBoxHandler;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

final class ArmCaptureBehaviour extends BlockEntityBehaviour {

	private static final BehaviourType<ArmCaptureBehaviour> TYPE = new BehaviourType<>();
	static final String NET_TAG = "CreateBiotechCaptureNet";
	private static final String MOTION_TAG = "CreateBiotechCaptureMotion";
	private static final int SCAN_INTERVAL = 3;
	private static final float SWEEP_SOUND_RPM_THRESHOLD = 128;

	private final PoweredCaptureNetBlockEntity arm;
	private ItemStack net = ItemStack.EMPTY;
	private int trackedMobId = -1;
	private @Nullable ArmCapturePose.Swing swing;
	private long swingStart;
	private float swingProgress;
	private float poseProgress;
	private boolean contactPending;
	private float serverYaw;
	private @Nullable TrackingTurn trackingTurn;
	private long nextScanTick;

	ArmCaptureBehaviour(PoweredCaptureNetBlockEntity arm) {
		super(arm);
		this.arm = arm;
	}

	ItemStack getNet() {
		return net;
	}

	ItemStack replaceNet(ItemStack replacement) {
		ItemStack previous = net;
		net = replacement;
		clearMotion();
		nextScanTick = 0;
		return previous;
	}

	private @Nullable Vec3 getTrackingPoint() {
		return trackedMobId >= 0 && getWorld().getEntity(trackedMobId) instanceof Mob mob && mob.isAlive()
			? mob.getBoundingBox().getCenter() : null;
	}

	boolean isSwinging() {
		return swing != null;
	}

	float getMovementStep() {
		return ArmCapturePose.movementStep(arm.getSpeed());
	}

	ArmCapturePose getClientPose(float previousYaw) {
		return swing == null ? trackingPose(previousYaw, getMovementStep()) : swing.sample(poseProgress);
	}

	private ArmCapturePose trackingPose(float previousYaw, float step) {
		boolean ceiling = arm.getBlockState().getValue(PoweredCaptureNetBlock.CEILING);
		Vec3 trackingPoint = getTrackingPoint();
		ArmCapturePose ready = ArmCapturePose.ready(getPos(), ceiling, previousYaw, trackingPoint);
		if (trackingPoint == null) {
			trackingTurn = null;
			return ready;
		}
		if (trackingTurn == null || trackingTurn.mobId != trackedMobId)
			trackingTurn = new TrackingTurn(trackedMobId, previousYaw);
		float yaw = trackingTurn.advance(previousYaw, ready.yaw(), step);
		return new ArmCapturePose(yaw, ready.shoulder(), ready.elbow(), ready.head(), ready.extension());
	}

	private static final class TrackingTurn {
		private final int mobId;
		private final float startYaw;
		private float progress;

		private TrackingTurn(int mobId, float startYaw) {
			this.mobId = mobId;
			this.startYaw = startYaw;
		}

		private float advance(float previousYaw, float targetYaw, float step) {
			progress = Math.min(1, progress + step);
			if (Mth.equal(previousYaw, targetYaw))
				return previousYaw;
			float facing = AngleHelper.angleLerp(progress, startYaw, targetYaw);
			// The bearing can change as the mob moves. Limit that correction to Create's maximum turn per tick.
			return Mth.approachDegrees(previousYaw, facing, 180 * step);
		}
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	@Override
	public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		net = ItemStack.parseOptional(registries, tag.getCompound(NET_TAG));
		if (!clientPacket || !tag.contains(MOTION_TAG)) {
			clearMotion();
			return;
		}
		CompoundTag motion = tag.getCompound(MOTION_TAG);
		trackedMobId = motion.contains("Mob") ? motion.getInt("Mob") : -1;
		if (!motion.contains("Progress")) {
			swing = null;
			contactPending = false;
			return;
		}
		long start = motion.getLong("Start");
		float progress = Mth.clamp(motion.getFloat("Progress"), -1, 1);
		if (swing != null && swingStart == start) {
			if (progress <= swingProgress)
				return;
			// A late client should reach the contact pose on its next tick.
			if (swingProgress < ArmCapturePose.CONTACT_AT && progress >= ArmCapturePose.CONTACT_AT)
				progress = Math.max(swingProgress, ArmCapturePose.CONTACT_AT - getMovementStep());
		} else {
			Vec3 point = new Vec3(motion.getDouble("X"), motion.getDouble("Y"), motion.getDouble("Z"));
			boolean ceiling = arm.getBlockState().getValue(PoweredCaptureNetBlock.CEILING);
			swing = ArmCapturePose.planSwing(getPos(), ceiling, motion.getFloat("StartYaw"), point);
			swingStart = start;
			trackingTurn = null;
			contactPending = false;
		}
		swingProgress = progress;
		poseProgress = progress;
	}

	@Override
	public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		writeNet(tag, registries, net);
		if (!clientPacket || (trackedMobId < 0 && swing == null))
			return;
		CompoundTag motion = new CompoundTag();
		if (trackedMobId >= 0)
			motion.putInt("Mob", trackedMobId);
		if (swing != null) {
			Vec3 point = swing.point();
			motion.putDouble("X", point.x);
			motion.putDouble("Y", point.y);
			motion.putDouble("Z", point.z);
			motion.putLong("Start", swingStart);
			motion.putFloat("StartYaw", swing.startYaw());
			motion.putFloat("Progress", swingProgress);
		}
		tag.put(MOTION_TAG, motion);
	}

	static void writeNet(CompoundTag tag, HolderLookup.Provider registries, ItemStack net) {
		if (!net.isEmpty())
			tag.put(NET_TAG, net.save(registries));
	}

	@Override
	public void destroy() {
		if (getWorld() instanceof ServerLevel level)
			Block.popResource(level, getPos(), net);
		net = ItemStack.EMPTY;
		clearMotion();
	}

	@Override
	public void tick() {
		Level world = getWorld();
		if (world == null || arm.isVirtual() || !(net.getItem() instanceof CaptureNetItem captureNet))
			return;
		float step = getMovementStep();
		if (!(world instanceof ServerLevel level)) {
			if (swing != null)
				advanceSwing(step);
			return;
		}

		long now = level.getGameTime();
		if (swing == null && now < nextScanTick) {
			if (step > 0 && trackedMobId >= 0)
				serverYaw = trackingPose(serverYaw, step).yaw();
			return;
		}
		if (step == 0 || level.hasNeighborSignal(getPos())) {
			nextScanTick = now + SCAN_INTERVAL;
			if (clearMotion())
				arm.sendData();
			return;
		}

		boolean changed = false;
		if (swing != null) {
			ArmCapturePose.Swing current = swing;
			float previous = swingProgress;
			boolean contact = advanceSwing(step);
			serverYaw = current.yawAt(swingProgress);
			if (previous < ArmCapturePose.LOWERING_AT && swingProgress >= ArmCapturePose.LOWERING_AT
				&& Math.abs(arm.getSpeed()) > SWEEP_SOUND_RPM_THRESHOLD)
				level.playSound(null, getPos(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.5f, 1.3f);
			if (contact && canCatch(level))
				capture(level, captureNet, current.strikeAt(getPos()));
			if (swing != current || swingProgress < 1)
				return;
			swing = null;
			changed = true;
		}
		nextScanTick = now + SCAN_INTERVAL;
		changed |= scan(level, captureNet, now);
		if (swing == null && trackedMobId >= 0)
			serverYaw = trackingPose(serverYaw, step).yaw();
		if (changed)
			arm.sendData();
	}

	private boolean advanceSwing(float step) {
		// The last tick supplies the contact pose for client interpolation. Catch as the mouth arrives.
		boolean contact = contactPending;
		float previous = swingProgress;
		swingProgress = Math.min(1, swingProgress + step);
		contactPending = previous < ArmCapturePose.CONTACT_AT && swingProgress >= ArmCapturePose.CONTACT_AT;
		poseProgress = contactPending ? ArmCapturePose.CONTACT_AT : swingProgress;
		return contact;
	}

	private boolean clearMotion() {
		boolean changed = trackedMobId >= 0 || swing != null;
		trackedMobId = -1;
		trackingTurn = null;
		swing = null;
		swingProgress = 0;
		poseProgress = 0;
		contactPending = false;
		return changed;
	}

	private boolean canCatch(ServerLevel level) {
		return !CaptureNetItem.isOccupied(net) && !CaptureNetItem.isEscapeCoolingDown(net, level);
	}

	private boolean scan(ServerLevel level, CaptureNetItem captureNet, long now) {
		Vec3 center = Vec3.atCenterOf(getPos());
		int reach = captureRange();
		double trackingReach = reach + 1.0;
		AABB searchArea = new AABB(center, center).inflate(trackingReach);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, searchArea, Mob::isAlive);
		Mob nearest = findNearest(mobs, center, trackingReach);
		int mobId = nearest == null ? -1 : nearest.getId();
		boolean changed = trackedMobId != mobId;
		trackedMobId = mobId;
		if (nearest == null || !isWithinReach(nearest, reach) || !canCatch(level))
			return changed;

		BioPackagerBlockEntity packager = getReadyPackager(level);
		BoxSupply boxes = packager == null ? null : BoxSupply.find(packager.getAttachedInventory());
		if (boxes == null)
			return changed;
		Vec3 origin = captureOrigin();
		boolean ceiling = arm.getBlockState().getValue(PoweredCaptureNetBlock.CEILING);
		mobs.sort(Comparator.comparingDouble(mob -> mob.getBoundingBox().getCenter().distanceToSqr(center)));
		for (Mob mob : mobs) {
			if (!isWithinReach(mob, reach) || !captureNet.canCapture(mob) || boxes.slotFor(mob) < 0
				|| !hasClearPath(level, origin, mob))
				continue;
			ArmCapturePose.Swing plan = ArmCapturePose.planSwing(getPos(), ceiling, serverYaw, mob.getBoundingBox());
			if (plan == null)
				continue;
			swing = plan;
			swingStart = now;
			swingProgress = poseProgress = plan.initialProgress();
			contactPending = false;
			trackingTurn = null;
			return true;
		}
		return changed;
	}

	private boolean isWithinReach(Mob mob, int reach) {
		return getPos().closerToCenterThan(mob.getBoundingBox().getCenter(), reach);
	}

	private static int captureRange() {
		int configured = CBConfigs.SERVER.poweredCaptureNet.captureRange.get();
		return configured == 0 ? ArmBlockEntity.getRange() : configured;
	}

	private static @Nullable Mob findNearest(List<Mob> mobs, Vec3 center, double reach) {
		Mob nearest = null;
		double reachSquared = Mth.square(reach);
		double bestDistance = Double.POSITIVE_INFINITY;
		for (Mob mob : mobs) {
			double distance = mob.getBoundingBox().getCenter().distanceToSqr(center);
			if (distance <= reachSquared && distance < bestDistance) {
				nearest = mob;
				bestDistance = distance;
			}
		}
		return nearest;
	}

	private void capture(ServerLevel level, CaptureNetItem captureNet, ArmCapturePose.Strike strike) {
		BioPackagerBlockEntity packager = getReadyPackager(level);
		BoxSupply boxes = packager == null ? null : BoxSupply.find(packager.getAttachedInventory());
		if (boxes == null)
			return;
		Vec3 origin = captureOrigin();
		int reach = captureRange();
		double margin = CBConfigs.SERVER.poweredCaptureNet.captureMargin.get();
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, strike.searchArea().inflate(margin),
			mob -> mob.isAlive() && isWithinReach(mob, reach) && touchesNet(strike, mob.getBoundingBox(), margin));
		mobs.sort(Comparator.comparingDouble(mob -> mob.getBoundingBox().getCenter().distanceToSqr(strike.center())));
		ItemStack equippedNet = net;
		for (Mob mob : mobs) {
			if (net != equippedNet || arm.isRemoved() || !packager.canStartPacking()
				|| packager.getAttachedInventory() != boxes.inventory())
				return;
			int slot = boxes.slotFor(mob);
			if (slot >= 0 && mob.level() == level && isWithinReach(mob, reach) && captureNet.canCapture(mob)
				&& touchesNet(strike, mob.getBoundingBox(), margin) && hasClearPath(level, origin, mob)
				&& pack(level, packager, boxes, slot, mob, equippedNet, origin))
				return;
		}
	}

	private static boolean touchesNet(ArmCapturePose.Strike strike, AABB bounds, double margin) {
		return strike.intersects(bounds.inflate(margin));
	}

	private boolean pack(ServerLevel level, BioPackagerBlockEntity packager, BoxSupply boxes, int slot, Mob mob,
		ItemStack equippedNet, Vec3 origin) {
		ItemStack emptyBox = boxes.inventory().extractItem(slot, 1, false);
		if (emptyBox.isEmpty())
			return false;
		boolean correctSize = slot == boxes.smallSlot() ? CapturedEntityBoxHelper.isEmptySmallBox(emptyBox)
			: CapturedEntityBoxHelper.isEmptyLargeBox(emptyBox);
		ItemStack filledBox = correctSize ? CapturedEntityBoxHelper.createCaptureBox(emptyBox) : ItemStack.EMPTY;
		if (!CapturedEntityBoxHelper.captureEntity(filledBox, mob) || !mob.isAlive() || mob.level() != level || arm.isRemoved()
			|| net != equippedNet || !packager.startPacking(filledBox)) {
			refundBox(level, packager, boxes.inventory(), slot, emptyBox);
			return false;
		}

		Vec3 caughtAt = mob.getBoundingBox().getCenter();
		level.playSound(null, caughtAt.x, caughtAt.y, caughtAt.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
		mob.discard();
		if (CBConfigs.SERVER.poweredCaptureNet.consumeDurability.get())
			CaptureNetItem.damageNet(equippedNet, 1, level, null, origin);
		if (equippedNet.isEmpty())
			clearMotion();
		arm.notifyUpdate();
		return true;
	}

	private static void refundBox(ServerLevel level, BioPackagerBlockEntity packager, IItemHandler inventory,
		int slot, ItemStack box) {
		if (box.isEmpty())
			return;
		if (!packager.isRemoved() && packager.getAttachedInventory() == inventory) {
			box = inventory.insertItem(slot, box, false);
			box = ItemHandlerHelper.insertItemStacked(inventory, box, false);
		}
		if (!box.isEmpty()) {
			Vec3 pos = Vec3.atCenterOf(packager.getBlockPos());
			Containers.dropItemStack(level, pos.x, pos.y, pos.z, box);
		}
	}

	private @Nullable BioPackagerBlockEntity getReadyPackager(ServerLevel level) {
		return level.getBlockEntity(getPos().below()) instanceof BioPackagerBlockEntity packager && packager.canStartPacking()
			? packager : null;
	}

	private Vec3 captureOrigin() {
		return ArmCapturePose.jointOrigin(getPos(), arm.getBlockState().getValue(PoweredCaptureNetBlock.CEILING));
	}

	private boolean hasClearPath(ServerLevel level, Vec3 origin, Mob mob) {
		Vec3 destination = mob.getBoundingBox().getCenter();
		if (!level.getWorldBorder().isWithinBounds(destination))
			return false;
		BlockPos armPos = getPos();
		return BlockGetter.traverseBlocks(origin, destination, level, (world, pos) -> {
			if (pos.equals(armPos))
				return null;
			LevelChunk chunk = world.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getZ()));
			if (chunk == null)
				return false;
			return chunk.getBlockState(pos).getCollisionShape(world, pos).clip(origin, destination, pos) == null
				? null : false;
		}, world -> true);
	}

	private record BoxSupply(IItemHandler inventory, int smallSlot, int largeSlot) {

		static @Nullable BoxSupply find(@Nullable IItemHandler inventory) {
			if (inventory == null)
				return null;
			int small = -1;
			int large = -1;
			for (int slot = 0; slot < inventory.getSlots() && (small < 0 || large < 0); slot++) {
				ItemStack stack = inventory.getStackInSlot(slot);
				if (small < 0 && CapturedEntityBoxHelper.isEmptySmallBox(stack)
					&& !inventory.extractItem(slot, 1, true).isEmpty())
					small = slot;
				else if (large < 0 && CapturedEntityBoxHelper.isEmptyLargeBox(stack)
					&& !inventory.extractItem(slot, 1, true).isEmpty())
					large = slot;
			}
			return small < 0 && large < 0 ? null : new BoxSupply(inventory, small, large);
		}

		int slotFor(Mob mob) {
			if (smallSlot >= 0 && CardboardBoxHandler.isSmallMobType(mob))
				return smallSlot;
			return largeSlot >= 0 && LargeCardboardBoxHandler.canLargeBoxCapture(mob) ? largeSlot : -1;
		}
	}
}

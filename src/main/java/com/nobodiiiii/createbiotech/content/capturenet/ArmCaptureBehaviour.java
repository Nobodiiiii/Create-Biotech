package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxHandler;
import com.nobodiiiii.createbiotech.content.cardboardbox.LargeCardboardBoxHandler;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class ArmCaptureBehaviour extends BlockEntityBehaviour {

	private static final BehaviourType<ArmCaptureBehaviour> TYPE = new BehaviourType<>();
	private static final String NET_TAG = "CreateBiotechCaptureNet";
	private static final String MOTION_TAG = "CreateBiotechCaptureMotion";
	private static final int CAPTURE_RADIUS = 2;
	private static final int TRACKING_RADIUS = 3;
	private static final int SCAN_INTERVAL = 3;

	private final ArmBlockEntity arm;
	private ItemStack net = ItemStack.EMPTY;
	private int trackedMobId = -1;
	private @Nullable Vec3 swingPoint;
	private long swingStart;
	private int swingDuration;
	private @Nullable ArmCapturePose.Swing clientSwing;
	private boolean contacted;
	private long nextScanTick;

	private ArmCaptureBehaviour(ArmBlockEntity arm) {
		super(arm);
		this.arm = arm;
	}

	public static @Nullable ArmCaptureBehaviour get(ArmBlockEntity arm) {
		return arm.getBehaviour(TYPE);
	}

	public static boolean hasNet(ArmBlockEntity arm) {
		ArmCaptureBehaviour capture = get(arm);
		return capture != null && capture.net.getItem() instanceof CaptureNetItem;
	}

	public ItemStack getNet() {
		return net;
	}

	private @Nullable Vec3 getTrackingPoint() {
		return trackedMobId >= 0 && getWorld().getEntity(trackedMobId) instanceof Mob mob && mob.isAlive()
			? mob.getBoundingBox().getCenter() : null;
	}

	public boolean isSwinging() {
		return swingPoint != null;
	}

	public ArmCapturePose getClientPose(float previousYaw) {
		boolean ceiling = arm.getBlockState().getValue(ArmBlock.CEILING);
		if (swingPoint == null) {
			clientSwing = null;
			return ArmCapturePose.ready(getPos(), ceiling, previousYaw, getTrackingPoint());
		}
		if (clientSwing == null || !clientSwing.matches(swingPoint, ceiling))
			clientSwing = ArmCapturePose.planSwing(getPos(), ceiling, previousYaw, swingPoint);
		float progress = (getWorld().getGameTime() - swingStart) / (float) swingDuration;
		return clientSwing.sample(previousYaw, progress);
	}

	@SubscribeEvent
	public static void attachToArm(BlockEntityBehaviourEvent event) {
		event.forType(AllBlockEntityTypes.MECHANICAL_ARM.get(), arm -> {
			if (arm.getBehaviour(TYPE) == null)
				event.attach(new ArmCaptureBehaviour(arm));
		});
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void useNetOnArm(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		ItemStack held = event.getItemStack();
		boolean installing = held.getItem() instanceof CaptureNetItem;
		boolean removing = held.isEmpty() && event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown();
		if ((!installing && !removing) || player.isSpectator() || !player.mayBuild()
			|| event.getUseBlock() == TriState.FALSE || installing && event.getUseItem() == TriState.FALSE
			|| !(event.getLevel().getBlockEntity(event.getPos()) instanceof ArmBlockEntity arm)
			|| arm.getType() != AllBlockEntityTypes.MECHANICAL_ARM.get())
			return;

		ArmCaptureBehaviour capture = arm.getBehaviour(TYPE);
		if (removing && (capture == null || capture.net.isEmpty()))
			return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
		if (!(event.getLevel() instanceof ServerLevel level) || !level.mayInteract(player, event.getPos()))
			return;

		if (CaptureNetItem.isOccupied(held)) {
			player.displayClientMessage(Component.translatable("create_biotech.arm_capture.net_occupied"), true);
			return;
		}
		if (capture == null) {
			capture = new ArmCaptureBehaviour(arm);
			arm.attachBehaviourLate(capture);
		}
		ItemStack previous = capture.net;
		capture.net = installing ? held.split(1) : ItemStack.EMPTY;
		capture.clearMotion();
		capture.nextScanTick = 0;
		arm.notifyUpdate();
		if (!previous.isEmpty())
			player.getInventory().placeItemBackInInventory(previous);
		if (installing)
			player.displayClientMessage(Component.translatable("create_biotech.arm_capture.net_installed",
				capture.net.getHoverName()), true);
		level.playSound(null, event.getPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.2f);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	@Override
	public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		net = ItemStack.parseOptional(registries, tag.getCompound(NET_TAG));
		clearMotion();
		if (!clientPacket || !tag.contains(MOTION_TAG))
			return;
		CompoundTag motion = tag.getCompound(MOTION_TAG);
		if (motion.contains("Mob"))
			trackedMobId = motion.getInt("Mob");
		if (motion.getInt("Duration") > 0) {
			swingPoint = new Vec3(motion.getDouble("X"), motion.getDouble("Y"), motion.getDouble("Z"));
			swingStart = motion.getLong("Start");
			swingDuration = motion.getInt("Duration");
		}
	}

	@Override
	public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		if (!net.isEmpty())
			tag.put(NET_TAG, net.save(registries));
		if (!clientPacket || (trackedMobId < 0 && swingPoint == null))
			return;
		CompoundTag motion = new CompoundTag();
		if (trackedMobId >= 0)
			motion.putInt("Mob", trackedMobId);
		if (swingPoint != null) {
			motion.putDouble("X", swingPoint.x);
			motion.putDouble("Y", swingPoint.y);
			motion.putDouble("Z", swingPoint.z);
			motion.putLong("Start", swingStart);
			motion.putInt("Duration", swingDuration);
		}
		tag.put(MOTION_TAG, motion);
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
		if (!(getWorld() instanceof ServerLevel level) || arm.isVirtual()
			|| !(net.getItem() instanceof CaptureNetItem captureNet))
			return;
		long now = level.getGameTime();
		if (swingPoint == null && now < nextScanTick)
			return;
		float speed = Math.abs(arm.getSpeed());
		if (speed == 0 || level.hasNeighborSignal(getPos()) || !((ArmCaptureAccess) arm).createBiotech$isCaptureArmIdle()) {
			nextScanTick = now + SCAN_INTERVAL;
			if (clearMotion())
				arm.sendData();
			return;
		}

		boolean changed = false;
		if (swingPoint != null) {
			long swingTicks = now - swingStart;
			if (swingTicks >= swingDuration) {
				swingPoint = null;
				changed = true;
			} else if (!contacted && swingTicks >= Mth.ceil(swingDuration * CaptureNetScoop.CONTACT)) {
				contacted = true;
				if (canCatch(level))
					capture(level, captureNet);
			}
		}
		if (!net.isEmpty() && now >= nextScanTick) {
			nextScanTick = now + SCAN_INTERVAL;
			changed |= scan(level, captureNet, now, speed);
		}
		if (changed)
			arm.sendData();
	}

	private boolean clearMotion() {
		boolean changed = trackedMobId >= 0 || swingPoint != null;
		trackedMobId = -1;
		swingPoint = null;
		return changed;
	}

	private boolean canCatch(ServerLevel level) {
		return !CaptureNetItem.isOccupied(net) && !CaptureNetItem.isEscapeCoolingDown(net, level);
	}

	private boolean scan(ServerLevel level, CaptureNetItem captureNet, long now, float speed) {
		Vec3 center = Vec3.atCenterOf(getPos());
		AABB trackingArea = new AABB(getPos()).inflate(TRACKING_RADIUS);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, trackingArea, Mob::isAlive);
		Mob nearest = findNearest(mobs, center, mob -> trackingArea.contains(mob.getBoundingBox().getCenter()));
		int mobId = nearest == null ? -1 : nearest.getId();
		boolean changed = trackedMobId != mobId;
		trackedMobId = mobId;
		if (nearest == null || swingPoint != null || !canCatch(level))
			return changed;

		BioPackagerBlockEntity packager = getReadyPackager(level);
		BoxSupply boxes = packager == null ? null : BoxSupply.find(packager.getAttachedInventory());
		if (boxes == null)
			return changed;
		AABB captureArea = new AABB(getPos()).inflate(CAPTURE_RADIUS);
		Vec3 origin = captureOrigin();
		Mob swingAt = findNearest(mobs, center, mob -> captureArea.contains(mob.getBoundingBox().getCenter())
			&& captureNet.canCapture(mob) && boxes.slotFor(mob) >= 0 && hasClearPath(level, origin, mob));
		if (swingAt == null)
			return changed;

		swingPoint = swingAt.getBoundingBox().getCenter();
		swingStart = now;
		swingDuration = Mth.clamp(Mth.ceil(1024 / Math.max(1, speed)), CaptureNetItem.ANIMATION_TICKS, 1024);
		contacted = false;
		level.playSound(null, getPos(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.5f, 1.3f);
		return true;
	}

	private static @Nullable Mob findNearest(List<Mob> mobs, Vec3 center, Predicate<Mob> filter) {
		Mob nearest = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (Mob mob : mobs) {
			double distance = mob.getBoundingBox().getCenter().distanceToSqr(center);
			if (distance < bestDistance && filter.test(mob)) {
				nearest = mob;
				bestDistance = distance;
			}
		}
		return nearest;
	}

	private void capture(ServerLevel level, CaptureNetItem captureNet) {
		BioPackagerBlockEntity packager = getReadyPackager(level);
		BoxSupply boxes = packager == null ? null : BoxSupply.find(packager.getAttachedInventory());
		if (boxes == null)
			return;
		Vec3 center = Vec3.atCenterOf(getPos());
		Vec3 origin = captureOrigin();
		AABB captureArea = new AABB(getPos()).inflate(CAPTURE_RADIUS);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, captureArea,
			mob -> mob.isAlive() && captureArea.contains(mob.getBoundingBox().getCenter()));
		mobs.sort(Comparator.comparingDouble(mob -> mob.getBoundingBox().getCenter().distanceToSqr(center)));
		ItemStack equippedNet = net;
		for (Mob mob : mobs) {
			if (net != equippedNet || arm.isRemoved() || !packager.canStartPacking()
				|| packager.getAttachedInventory() != boxes.inventory())
				return;
			int slot = boxes.slotFor(mob);
			if (slot >= 0 && mob.level() == level && captureNet.canCapture(mob) && hasClearPath(level, origin, mob)
				&& pack(level, packager, boxes, slot, mob, equippedNet, origin))
				return;
		}
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
		mob.discard();
		CaptureNetItem.damageNet(equippedNet, 1, level, null, origin);
		if (equippedNet.isEmpty())
			clearMotion();
		arm.notifyUpdate();
		level.playSound(null, mob.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
		level.sendParticles(ParticleTypes.POOF, caughtAt.x, caughtAt.y, caughtAt.z, 5, 0.2, 0.2, 0.2, 0.02);
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
		return ArmCapturePose.jointOrigin(getPos(), arm.getBlockState().getValue(ArmBlock.CEILING));
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

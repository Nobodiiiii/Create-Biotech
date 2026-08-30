package com.nobodiiiii.createbiotech.entity;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicBodyRotationControl;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicGroundNavigation;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicMoveControl;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** A real entity whose visible body and locomotion are supplied by a surgical assembly. */
public class SlimeBionicEntity extends PathfinderMob {
	private static final float MAX_COLLISION_WIDTH = 2.0f;
	private static final float MAX_COLLISION_HEIGHT = 8.0f;
	private static final float MULTIPART_THRESHOLD = 8.0f;
	private static final int MAX_HIT_PARTS = SurgicalAssembly.MAX_HITBOX_LIMBS + 1;
	private static final String ASSEMBLY_TAG = "SurgicalAssembly";
	private static final String SOURCE_FORM_TAG = "BionicSourceForm";
	private static final double DEFAULT_ATTACK_DISTANCE_SQR = 5.0d * 5.0d;
	private static final int ATTACK_EVENT_STRIDE = 4;
	private static final int ATTACK_EVENT_VARIANTS =
		SurgicalLimbType.SHOULDER.maxPerBody() * ATTACK_EVENT_STRIDE;
	private static final byte ATTACK_EVENT_BASE = -64;
	private static final ResourceLocation ANATOMICAL_ATTACK_MODIFIER_ID =
		CreateBiotech.asResource("anatomical_attack");
	private static final EntityDataAccessor<CompoundTag> ASSEMBLY = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.COMPOUND_TAG);
	@Nullable
	private CompoundTag cachedAssemblyData;
	@Nullable
	private SurgicalAssembly cachedAssembly;
	@Nullable
	private SurgicalAssembly clientBoundsAssembly;
	@Nullable
	private SurgicalAssembly.BodyBounds clientBodyBounds;
	@Nullable
	private SurgicalAssembly.HitboxGeometry clientHitboxGeometry;
	@Nullable
	private SurgicalAssembly reportedBoundsAssembly;
	@Nullable
	private SurgicalAssembly.BodyBounds reportedBodyBounds;
	@Nullable
	private SurgicalAssembly.HitboxGeometry reportedHitboxGeometry;
	private final SlimeBionicHitPart[] hitParts;
	private SlimeBionicHitPart[] registeredHitParts;
	private int attackAnimationTick;
	private int attackAnimationDuration = SlimeBionicAttackTiming.PLAYBACK_TICKS;
	private boolean attackAnimationLeft;
	private int attackAnimationArmSlot;
	private boolean attackAnimationWeapon;
	private int attackActionTick;
	private int attackActionDuration = SlimeBionicCombat.NORMAL_ATTACK_TICKS;
	private boolean attackActionLeft;
	private int attackActionArmSlot;
	private boolean attackActionWeapon;
	private boolean nextEmptyHandAttackLeft;
	private int nextRightArmSlot;
	private int nextLeftArmSlot;

	public SlimeBionicEntity(EntityType<? extends SlimeBionicEntity> type, Level level) {
		super(type, level);
		hitParts = new SlimeBionicHitPart[MAX_HIT_PARTS];
		for (int index = 0; index < hitParts.length; index++)
			hitParts[index] = new SlimeBionicHitPart(this);
		// Unknown/legacy assemblies keep the full reserve so a later client measurement can activate it.
		registeredHitParts = hitParts;
		// Match the Ender Dragon: reserve one consecutive id block and keep every cached part stable.
		setId(ENTITY_COUNTER.getAndAdd(hitParts.length + 1) + 1);
		// The bionic body begins in the same synced slime state used by ordinary mimics.
		// Loading a cured entity can still restore this value to false from its saved data.
		((SlimeMimicAccess) (Object) this).createBiotech$setSlimeMimic(true);
		moveControl = new SlimeBionicMoveControl(this);
		setPersistenceRequired();
		updateHitParts();
	}

	@Override
	public void setId(int id) {
		super.setId(id);
		if (hitParts != null)
			for (int index = 0; index < hitParts.length; index++)
				hitParts[index].setId(id + index + 1);
	}

	@Override
	public boolean isMultipartEntity() {
		return registeredHitParts != null && registeredHitParts.length > 0;
	}

	@Override
	public SlimeBionicHitPart[] getParts() {
		return registeredHitParts;
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
		return new ClientboundAddEntityPacket(this, entity, registeredHitParts.length);
	}

	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket packet) {
		super.recreateFromPacket(packet);
		setRegisteredHitPartCount(Mth.clamp(packet.getData(), 0, MAX_HIT_PARTS));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes()
			.add(Attributes.MAX_HEALTH, 400.0d)
			.add(Attributes.MOVEMENT_SPEED, SurgicalGait.VILLAGER_WALK_SPEED)
			.add(Attributes.ATTACK_DAMAGE, 3.0d)
			.add(Attributes.ARMOR, 2.0d)
			.add(Attributes.FOLLOW_RANGE, 35.0d)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.15d);
	}

	/** Mirrors {@code Zombie}'s goal set so a stitched body already behaves like something alive. */
	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new BionicAttackGoal(this, 1.0d, false));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0d));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, false));
	}

	@Override
	protected BodyRotationControl createBodyControl() {
		return new SlimeBionicBodyRotationControl(this);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new SlimeBionicGroundNavigation(this, level);
	}

	@Override
	protected void updateWalkAnimation(float movement) {
		if (getLocomotionLegCount() < 2) {
			walkAnimation.update(0.0f, 0.4f);
			return;
		}
		// Vanilla clamps movement * 4 to 1, so speeds above roughly 0.25 blocks/tick cannot raise
		// cadence. Preserve the full configured bionic range and let the renderer cap swing angle.
		float animationSpeed = Math.min(movement * 4.0f, SurgicalGait.MAX_WALK_ANIMATION_SPEED);
		walkAnimation.update(animationSpeed, 0.4f);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ASSEMBLY, new CompoundTag());
	}

	public void setAssembly(SurgicalAssembly assembly) {
		CompoundTag encoded = assembly.save();
		entityData.set(ASSEMBLY, encoded);
		cachedAssemblyData = encoded;
		cachedAssembly = assembly;
		configureHitPartRegistration(assembly);
		clientBoundsAssembly = null;
		clientBodyBounds = null;
		clientHitboxGeometry = null;
		reportedBoundsAssembly = null;
		reportedBodyBounds = null;
		reportedHitboxGeometry = null;
		refreshMovementSpeed(assembly);
		refreshDimensions();
		updateHitParts();
	}

	/** Applies the leg-length curve to the authoritative movement attribute. */
	private void refreshMovementSpeed(SurgicalAssembly assembly) {
		if (level().isClientSide)
			return;
		SurgicalAssembly.BodyBounds bounds = assembly.bodyBounds();
		double speed = SurgicalGait.movementSpeed(bounds == null ? 0.0d : bounds.legLength());
		var movement = getAttribute(Attributes.MOVEMENT_SPEED);
		if (movement != null && movement.getBaseValue() != speed)
			movement.setBaseValue(speed);
	}

	@Nullable
	public SurgicalAssembly getAssembly() {
		CompoundTag encoded = entityData.get(ASSEMBLY);
		if (encoded != cachedAssemblyData) {
			cachedAssemblyData = encoded;
			cachedAssembly = SurgicalAssembly.load(encoded);
		}
		return cachedAssembly;
	}

	/** Hip joints are authoritative leg roots; a knee only articulates the hip chain that owns it. */
	public int getLocomotionLegCount() {
		SurgicalAssembly assembly = getAssembly();
		if (assembly == null)
			return 0;
		int legs = 0;
		for (SurgicalAssembly.Limb limb : assembly.effectiveLimbs())
			if (limb.type() == SurgicalLimbType.HIP)
				legs++;
		return legs;
	}

	/** Applies the renderer's exact visible envelope on the client, including slime-shell inflation. */
	public void setClientBodyGeometry(SurgicalAssembly assembly, SurgicalAssembly.BodyBounds bounds,
		SurgicalAssembly.HitboxGeometry hitboxGeometry) {
		if (!level().isClientSide || assembly == null || bounds == null || hitboxGeometry == null
			|| getAssembly() != assembly)
			return;
		if (clientBoundsAssembly == assembly && bounds.equals(clientBodyBounds)
			&& hitboxGeometry.equals(clientHitboxGeometry))
			return;
		clientBoundsAssembly = assembly;
		clientBodyBounds = bounds;
		clientHitboxGeometry = hitboxGeometry;
		refreshDimensions();
		updateHitParts();
		if ((!bounds.equals(assembly.bodyBounds()) || !hitboxGeometry.equals(assembly.hitboxGeometry()))
			&& (reportedBoundsAssembly != assembly || !bounds.equals(reportedBodyBounds)
				|| !hitboxGeometry.equals(reportedHitboxGeometry))) {
			reportedBoundsAssembly = assembly;
			reportedBodyBounds = bounds;
			reportedHitboxGeometry = hitboxGeometry;
			CBPackets.sendToServer(new SlimeBionicBodyBoundsPacket(getId(), bounds, hitboxGeometry));
		}
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		SurgicalAssembly.BodyBounds bounds = activeBodyBounds();
		if (bounds == null)
			return super.getDefaultDimensions(pose);
		// Vanilla mobs use one centred, yaw-independent square footprint whose side is the body's
		// lateral width. Their fore-aft model depth is deliberately not promoted to collision width.
		float width = Math.min(bounds.width(), MAX_COLLISION_WIDTH);
		float height = Math.min(bounds.minY() + bounds.height(), MAX_COLLISION_HEIGHT);
		float eyeHeight = Mth.clamp(bounds.minY() + bounds.height() * 0.85f, 0.0f, height);
		return EntityDimensions.fixed(width, height).withEyeHeight(eyeHeight);
	}

	@Nullable
	SurgicalAssembly.BodyBounds activeBodyBounds() {
		SurgicalAssembly assembly = getAssembly();
		return level().isClientSide && clientBoundsAssembly == assembly
			? clientBodyBounds : assembly == null ? null : assembly.bodyBounds();
	}

	@Override
	public boolean isWithinMeleeAttackRange(LivingEntity target) {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		if (geometry == null)
			return distanceToSqr(target) <= DEFAULT_ATTACK_DISTANCE_SQR;
		AABB targetBounds = target.getBoundingBox();
		for (SurgicalAssembly.ArmAttackGeometry arm : geometry.arms())
			if (SlimeBionicCombat.withinStartEnvelope(targetBounds, position(), arm))
				return true;
		return false;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (attackAnimationTick > 0)
			attackAnimationTick--;
		if (attackActionTick > 0)
			attackActionTick--;
	}

	/** Selects and snapshots one arm before starting its gameplay and presentation clocks. */
	private AttackStart beginAttack() {
		attackActionWeapon = hasAttackWeapon();
		boolean preferredLeft;
		if (attackActionWeapon) {
			preferredLeft = preferredAttackLeft(true);
		} else {
			preferredLeft = nextEmptyHandAttackLeft;
			nextEmptyHandAttackLeft = !nextEmptyHandAttackLeft;
		}
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		attackActionLeft = geometry != null && !geometry.hasArms(preferredLeft)
			&& geometry.hasArms(!preferredLeft) ? !preferredLeft : preferredLeft;
		int armCount = geometry == null ? 0 : geometry.armCount(attackActionLeft);
		if (armCount > 0) {
			int nextSlot = attackActionLeft ? nextLeftArmSlot++ : nextRightArmSlot++;
			attackActionArmSlot = Math.floorMod(nextSlot, armCount);
		} else {
			attackActionArmSlot = 0;
		}
		SurgicalAssembly.ArmAttackGeometry arm = geometry == null ? null
			: geometry.arm(attackActionLeft, attackActionArmSlot);
		SurgicalCombatCalibration.ArmCombatStats stats = SurgicalCombatCalibration.stats(arm);
		int attackInterval = stats.attackInterval();
		attackActionDuration = SlimeBionicCombat.duration(attackInterval);
		attackActionTick = attackActionDuration;

		attackAnimationDuration = SlimeBionicAttackTiming.playbackTicks(attackInterval);
		attackAnimationTick = attackAnimationDuration;
		attackAnimationWeapon = attackActionWeapon;
		attackAnimationLeft = attackActionLeft;
		attackAnimationArmSlot = attackActionArmSlot;
		int encoded = attackActionArmSlot * ATTACK_EVENT_STRIDE
			+ (attackActionWeapon ? 2 : 0) + (attackActionLeft ? 1 : 0);
		level().broadcastEntityEvent(this, (byte) (ATTACK_EVENT_BASE + encoded));
		return new AttackStart(attackActionDuration, attackInterval, stats.damageMultiplier(), arm);
	}

	@Nullable
	private SurgicalAssembly.HitboxGeometry activeHitboxGeometry() {
		SurgicalAssembly assembly = getAssembly();
		return level().isClientSide && clientBoundsAssembly == assembly
			? clientHitboxGeometry : assembly == null ? null : assembly.hitboxGeometry();
	}

	@Override
	public void tick() {
		super.tick();
		updateHitParts();
	}

	private void updateHitParts() {
		if (hitParts == null || registeredHitParts.length == 0)
			return;
		SurgicalAssembly.HitboxGeometry geometry = activeHitboxGeometry();
		List<SurgicalAssembly.VisualBounds> bounds = geometry == null ? List.of()
			: geometry.partBounds(MULTIPART_THRESHOLD);
		if (bounds.size() > registeredHitParts.length)
			bounds = List.of(geometry.overall());
		for (int index = 0; index < registeredHitParts.length; index++)
			registeredHitParts[index].setHitBounds(index < bounds.size() ? worldBounds(bounds.get(index)) : null);
	}

	/** Chooses the cached subset before level tracking starts; tracked part maps cannot grow later. */
	private void configureHitPartRegistration(SurgicalAssembly assembly) {
		if (isAddedToLevel())
			return;
		SurgicalAssembly.HitboxGeometry geometry = assembly == null ? null : assembly.hitboxGeometry();
		if (geometry != null)
			setRegisteredHitPartCount(geometry.partBounds(MULTIPART_THRESHOLD).size());
	}

	private void setRegisteredHitPartCount(int count) {
		registeredHitParts = count >= hitParts.length ? hitParts
			: Arrays.copyOf(hitParts, Mth.clamp(count, 0, hitParts.length));
	}

	private AABB worldBounds(SurgicalAssembly.VisualBounds bounds) {
		float angle = -yBodyRot * Mth.DEG_TO_RAD;
		double minX = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (int xSide = 0; xSide < 2; xSide++)
			for (int zSide = 0; zSide < 2; zSide++) {
				Vec3 rotated = new Vec3(xSide == 0 ? bounds.minX() : bounds.maxX(), 0.0d,
					zSide == 0 ? bounds.minZ() : bounds.maxZ()).yRot(angle);
				minX = Math.min(minX, rotated.x);
				minZ = Math.min(minZ, rotated.z);
				maxX = Math.max(maxX, rotated.x);
				maxZ = Math.max(maxZ, rotated.z);
			}
		return new AABB(getX() + minX, getY() + bounds.minY(), getZ() + minZ,
			getX() + maxX, getY() + bounds.maxY(), getZ() + maxZ);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		SurgicalAssembly.HitboxGeometry geometry = activeHitboxGeometry();
		return geometry == null ? super.getBoundingBoxForCulling()
			: getBoundingBox().minmax(worldBounds(geometry.overall()));
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		if (super.shouldRenderAtSqrDistance(distance))
			return true;
		SurgicalAssembly.HitboxGeometry geometry = activeHitboxGeometry();
		if (geometry == null)
			return false;
		double physicalSize = Math.max(getBoundingBox().getSize(), 1.0e-6d);
		double visualSize = Math.max(worldBounds(geometry.overall()).getSize(), physicalSize);
		double scale = visualSize / physicalSize;
		return super.shouldRenderAtSqrDistance(distance / (scale * scale));
	}

	@Override
	public boolean isPickable() {
		SurgicalAssembly.HitboxGeometry geometry = activeHitboxGeometry();
		boolean multipartActive = registeredHitParts.length > 0 && geometry != null
			&& geometry.requiresMultipart(MULTIPART_THRESHOLD);
		return !multipartActive && super.isPickable();
	}

	/** Side the next attack will request before the renderer/geometry applies single-arm fallback. */
	private boolean preferredAttackLeft(boolean weapon) {
		if (!weapon)
			return nextEmptyHandAttackLeft;
		boolean mainHand = isAttackWeapon(getMainHandItem());
		HumanoidArm arm = mainHand ? getMainArm() : getMainArm().getOpposite();
		return arm == HumanoidArm.LEFT;
	}

	/** Applies one logical attack hit without touching its independent presentation clock. */
	private boolean performAttackDamage(Entity target, double damageMultiplier) {
		var attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage == null || Math.abs(damageMultiplier - 1.0d) < 1.0e-8d)
			return super.doHurtTarget(target);
		attackDamage.removeModifier(ANATOMICAL_ATTACK_MODIFIER_ID);
		attackDamage.addTransientModifier(new AttributeModifier(ANATOMICAL_ATTACK_MODIFIER_ID,
			damageMultiplier - 1.0d, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		try {
			return super.doHurtTarget(target);
		} finally {
			attackDamage.removeModifier(ANATOMICAL_ATTACK_MODIFIER_ID);
		}
	}

	private boolean attackSectorIntersects(LivingEntity target,
		SurgicalAssembly.ArmAttackGeometry arm) {
		return SlimeBionicCombat.intersects(target.getBoundingBox(), position(), yBodyRot, arm);
	}

	private record AttackStart(int actionDuration, int attackInterval, double damageMultiplier,
		@Nullable SurgicalAssembly.ArmAttackGeometry arm) {}

	@Override
	public void handleEntityEvent(byte id) {
		int encoded = id - ATTACK_EVENT_BASE;
		if (encoded >= 0 && encoded < ATTACK_EVENT_VARIANTS) {
			attackActionArmSlot = encoded / ATTACK_EVENT_STRIDE;
			attackActionLeft = (encoded & 1) != 0;
			attackActionWeapon = (encoded & 2) != 0;
			SurgicalAssembly assembly = getAssembly();
			SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
			SurgicalAssembly.ArmAttackGeometry arm = geometry == null ? null
				: geometry.arm(attackActionLeft, attackActionArmSlot);
			int attackInterval = SurgicalCombatCalibration.stats(arm).attackInterval();
			attackActionDuration = SlimeBionicCombat.duration(attackInterval);
			attackActionTick = attackActionDuration;
			attackAnimationDuration = SlimeBionicAttackTiming.playbackTicks(attackInterval);
			attackAnimationTick = attackAnimationDuration;
			attackAnimationLeft = attackActionLeft;
			attackAnimationArmSlot = attackActionArmSlot;
			attackAnimationWeapon = attackActionWeapon;
		} else
			super.handleEntityEvent(id);
	}

	public int getAttackActionTick() {
		return attackActionTick;
	}

	public int getAttackActionDuration() {
		return attackActionDuration;
	}

	public boolean isAttackActionLeft() {
		return attackActionLeft;
	}

	public int getAttackActionArmSlot() {
		return attackActionArmSlot;
	}

	public int getAttackAnimationTick() {
		return attackAnimationTick;
	}

	public int getAttackAnimationDuration() {
		return attackAnimationDuration;
	}

	public boolean isAttackAnimationLeft() {
		return attackAnimationLeft;
	}

	public int getAttackAnimationArmSlot() {
		return attackAnimationArmSlot;
	}

	public boolean isAttackAnimationWeapon() {
		return attackAnimationWeapon;
	}

	public boolean hasAttackWeapon() {
		return isAttackWeapon(getMainHandItem()) || isAttackWeapon(getOffhandItem());
	}

	public static boolean isAttackWeapon(ItemStack stack) {
		return stack != null && stack.is(Tags.Items.MELEE_WEAPON_TOOLS);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (ASSEMBLY.equals(key)) {
			clientBoundsAssembly = null;
			clientBodyBounds = null;
			clientHitboxGeometry = null;
			reportedBoundsAssembly = null;
			reportedBodyBounds = null;
			reportedHitboxGeometry = null;
			refreshDimensions();
			updateHitParts();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		SlimeMimicAccess slimeState = (SlimeMimicAccess) (Object) this;
		tag.putBoolean(SOURCE_FORM_TAG, !slimeState.createBiotech$isSlimeMimic());
		CompoundTag assembly = entityData.get(ASSEMBLY);
		if (!assembly.isEmpty())
			tag.put(ASSEMBLY_TAG, assembly.copy());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// Saves predating this flag always represented the original bionic slime form.
		boolean sourceForm = tag.contains(SOURCE_FORM_TAG, Tag.TAG_BYTE) && tag.getBoolean(SOURCE_FORM_TAG);
		((SlimeMimicAccess) (Object) this).createBiotech$setSlimeMimic(!sourceForm);
		// Bodies packed before the AI existed were saved with NoAI set; wake those up on load.
		setNoAi(false);
		if (tag.contains(ASSEMBLY_TAG, Tag.TAG_COMPOUND)) {
			SurgicalAssembly assembly = SurgicalAssembly.load(tag.getCompound(ASSEMBLY_TAG));
			if (assembly != null)
				setAssembly(assembly);
		}
	}

	/** {@code ZombieAttackGoal} verbatim; the vanilla class is bound to {@code Zombie}. */
	private static class BionicAttackGoal extends MeleeAttackGoal {
		private static final float ATTACK_TRACKING_DEGREES_PER_TICK = 15.0f;
		private static final double ATTACK_TRACKING_EPSILON_SQR = 1.0e-8d;

		private final SlimeBionicEntity bionic;
		private int raiseArmTicks;
		@Nullable
		private LivingEntity pendingAttackTarget;
		private int pendingAttackElapsed;
		private int pendingAttackDuration;
		private boolean pendingImpactApplied;
		private int anatomicalAttackCooldown;
		private int currentAttackInterval = SurgicalCombatCalibration.ZOMBIE_ATTACK_INTERVAL;
		private double pendingDamageMultiplier = 1.0d;
		@Nullable
		private SurgicalAssembly.ArmAttackGeometry pendingArmGeometry;

		private BionicAttackGoal(SlimeBionicEntity bionic, double speedModifier, boolean followingTargetEvenIfNotSeen) {
			super(bionic, speedModifier, followingTargetEvenIfNotSeen);
			this.bionic = bionic;
		}

		@Override
		public void start() {
			super.start();
			raiseArmTicks = 0;
			clearPendingAttack();
			anatomicalAttackCooldown = 0;
			currentAttackInterval = SurgicalCombatCalibration.ZOMBIE_ATTACK_INTERVAL;
		}

		@Override
		public void stop() {
			super.stop();
			clearPendingAttack();
			anatomicalAttackCooldown = 0;
			bionic.setAggressive(false);
		}

		@Override
		public boolean canContinueToUse() {
			return (pendingAttackTarget != null && pendingAttackTarget.isAlive())
				|| super.canContinueToUse();
		}

		@Override
		protected void checkAndPerformAttack(LivingEntity target) {
			if (pendingAttackTarget != null)
				return;
			if (!canPerformAttack(target))
				return;
			AttackStart attack = bionic.beginAttack();
			anatomicalAttackCooldown = attack.attackInterval();
			currentAttackInterval = attack.attackInterval();
			bionic.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			pendingAttackDuration = attack.actionDuration();
			pendingArmGeometry = attack.arm();
			pendingDamageMultiplier = attack.damageMultiplier();
			pendingAttackTarget = target;
			pendingAttackElapsed = 0;
			pendingImpactApplied = false;
			turnBodyToward(target);
		}

		private void advancePendingAttack() {
			pendingAttackElapsed++;
			LivingEntity target = pendingAttackTarget;
			if (target != null && target.isAlive())
				turnBodyToward(target);
			if (!pendingImpactApplied && target != null && target.isAlive()
				&& SlimeBionicCombat.isActiveTick(pendingAttackElapsed, pendingAttackDuration)) {
				boolean intersects = pendingArmGeometry != null
					? bionic.attackSectorIntersects(target, pendingArmGeometry)
					: bionic.isWithinMeleeAttackRange(target);
				if (intersects) {
					pendingImpactApplied = true;
					bionic.performAttackDamage(target, pendingDamageMultiplier);
				}
			}
			if (pendingAttackElapsed >= pendingAttackDuration)
				clearPendingAttack();
		}

		private void turnBodyToward(LivingEntity target) {
			if (pendingAttackElapsed >= SlimeBionicCombat.activeEndTick(pendingAttackDuration))
				return;
			double dx = target.getX() - bionic.getX();
			double dz = target.getZ() - bionic.getZ();
			if (dx * dx + dz * dz <= ATTACK_TRACKING_EPSILON_SQR)
				return;
			float targetYaw = (float) Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90.0f;
			float bodyYaw = Mth.approachDegrees(bionic.yBodyRot, targetYaw,
				ATTACK_TRACKING_DEGREES_PER_TICK);
			bionic.yBodyRot = bodyYaw;
			bionic.setYRot(bodyYaw);
		}

		private void clearPendingAttack() {
			pendingAttackTarget = null;
			pendingAttackElapsed = 0;
			pendingAttackDuration = 0;
			pendingImpactApplied = false;
			pendingArmGeometry = null;
			pendingDamageMultiplier = 1.0d;
		}

		@Override
		protected boolean isTimeToAttack() {
			return anatomicalAttackCooldown <= 0;
		}

		@Override
		protected int getTicksUntilNextAttack() {
			return anatomicalAttackCooldown;
		}

		@Override
		protected int getAttackInterval() {
			return currentAttackInterval;
		}

		@Override
		public void tick() {
			if (anatomicalAttackCooldown > 0)
				anatomicalAttackCooldown--;
			if (pendingAttackTarget != null)
				advancePendingAttack();
			super.tick();
			raiseArmTicks++;
			bionic.setAggressive(raiseArmTicks >= 5 && getTicksUntilNextAttack() < getAttackInterval() / 2);
		}
	}
}

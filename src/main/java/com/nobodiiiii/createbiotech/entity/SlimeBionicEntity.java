package com.nobodiiiii.createbiotech.entity;

import java.util.Arrays;
import java.util.List;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.HashMultimap;
import com.nobodiiiii.createbiotech.entity.trait.BionicVibrationSense;
import com.nobodiiiii.createbiotech.entity.trait.BionicDeterrence;
import com.nobodiiiii.createbiotech.entity.trait.BionicHunting;
import com.nobodiiiii.createbiotech.entity.trait.BionicDefenses;
import com.nobodiiiii.createbiotech.entity.trait.BionicBlockAttachment;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.entity.ai.BionicBreakDoorGoal;
import com.nobodiiiii.createbiotech.entity.ai.BionicPackAlertGoal;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeCompanionHandler;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicCarriedDrops;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.ai.BionicDisposition;
import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligence;
import com.nobodiiiii.createbiotech.entity.ai.BionicMind;
import com.nobodiiiii.createbiotech.entity.ai.BionicFlightMovement;
import com.nobodiiiii.createbiotech.entity.ai.BionicWallClimbing;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicBodyRotationControl;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicGroundNavigation;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicMoveControl;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.common.NeoForgeMod;

/** A real entity whose visible body and locomotion are supplied by a surgical assembly. */
public class SlimeBionicEntity extends PathfinderMob implements net.minecraft.world.entity.Saddleable,
	net.minecraft.world.entity.PlayerRideableJumping, net.minecraft.world.entity.HasCustomInventoryScreen,
	net.neoforged.neoforge.common.IShearable,
	net.minecraft.world.entity.monster.RangedAttackMob {
	private static final float MAX_COLLISION_WIDTH = 2.0f;
	private static final float MAX_COLLISION_HEIGHT = 8.0f;
	private static final float MULTIPART_THRESHOLD = 8.0f;
	private static final int MAX_HIT_PARTS = SurgicalAssembly.MAX_HITBOX_LIMBS + 2;
	private static final String ASSEMBLY_TAG = "SurgicalAssembly";
	private static final String SOURCE_FORM_TAG = "BionicSourceForm";
	private static final String MOISTURE_TAG = "BionicMoisture";
	private static final String DRY_AIR_TAG = "BionicDryAir";
	private static final String OWNER_TAG = "BionicOwner";
	private static final String TRUSTED_TAG = "BionicTrustedPlayer";
	private static final String SHELL_GUARD_TAG = "BionicShellGuard";
	private static final String ORDERED_TO_SIT_TAG = "BionicOrderedToSit";
	private static final int MAX_MOISTURE = 2400;
	private static final double BASE_KNOCKBACK_RESISTANCE = 0.15d;
	private static final double SWIMMING_MOVEMENT_SPEED = 1.0d;
	private static final double SWIMMING_IDLE_SPEED = 0.5d;
	private static final double SWIMMING_PURSUIT_SPEED = 0.6d;
	private static final ResourceLocation ANATOMICAL_ATTACK_MODIFIER_ID =
		CreateBiotech.asResource("anatomical_attack");
	private static final EntityDataAccessor<CompoundTag> ASSEMBLY = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.COMPOUND_TAG);
	private static final EntityDataAccessor<Optional<UUID>> OWNER_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Boolean> SITTING_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> CLIMBING_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.BOOLEAN);
	@Nullable
	private CompoundTag cachedAssemblyData;
	@Nullable
	private SurgicalAssembly cachedAssembly;
	private boolean installingAssembly;
	@Nullable
	private SurgicalAssembly traitAssembly;
	@Nullable
	private BionicTraitResolver.Revision traitRevision;
	private final BionicVibrationSense vibrationSense = new BionicVibrationSense(this);
	private BionicTraitSet bionicTraits = BionicTraitSet.EMPTY;
	/** The mind derived from {@link #mindTraits}, resolved together with that trait set. */
	@Nullable
	private BionicMind mind;
	@Nullable
	private BionicTraitSet mindTraits;
	private final BionicHunting hunting = new BionicHunting(this);
	private final BionicBlockAttachment blockAttachment = new BionicBlockAttachment(this);
	private static final EntityDataAccessor<Optional<BlockPos>> ATTACH_CELL_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Direction> ATTACH_FACE_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.DIRECTION);
	private final com.nobodiiiii.createbiotech.entity.trait.BionicEquipment equipment = new com.nobodiiiii.createbiotech.entity.trait.BionicEquipment(this);
	@Nullable private Vec3 interactionHit;
	private final com.nobodiiiii.createbiotech.entity.trait.BionicProduction production = new com.nobodiiiii.createbiotech.entity.trait.BionicProduction(this);
	private final com.nobodiiiii.createbiotech.entity.trait.BionicSpecialAttacks specialAttacks = new com.nobodiiiii.createbiotech.entity.trait.BionicSpecialAttacks(this);
	private static final EntityDataAccessor<CompoundTag> EQUIPMENT_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.COMPOUND_TAG);
	private static final EntityDataAccessor<Integer> PRODUCTION_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> POLLEN_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> SHELL_STATE_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> SPECIAL_STARTED = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> SPECIAL_ACTION = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<org.joml.Vector3f> SPECIAL_ORIGIN = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> SPECIAL_TARGET = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> GUARDIAN_ATTACK_DURATION = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private float riderJump;
	private int riderJumpCooldown;
	private boolean riderAscending;
	private int selfDestructFuse, previousSelfDestructFuse;
	public com.nobodiiiii.createbiotech.entity.trait.BionicEquipment getBionicEquipment() { return equipment; }
	public BionicBlockAttachment getBlockAttachment() { return blockAttachment; }
	@Nullable public BlockPos getBlockAttachmentCell() { return entityData.get(ATTACH_CELL_DATA).orElse(null); }
	public Direction getBlockAttachmentFace() { return entityData.get(ATTACH_FACE_DATA); }
	public void setBlockAttachment(@Nullable BlockPos cell, Direction face) {
		entityData.set(ATTACH_FACE_DATA, face);
		entityData.set(ATTACH_CELL_DATA, Optional.ofNullable(cell));
	}
	/** Asked dozens of times per tick by movement and rotation; the synced cell rejects first. */
	public boolean isBlockAttached() {
		return blockAttachment != null && getBlockAttachmentCell() != null && blockAttachment.canAttach();
	}
	public SurgicalCubeRotation blockAttachmentRotation() {
		return blockAttachment == null ? SurgicalCubeRotation.IDENTITY : blockAttachment.rotation();
	}
	/** The attachment quaternion owns the entire body orientation; AI and riders cannot turn it. */
	public void lockBlockAttachmentRotation() {
		if (!isBlockAttached()) return;
		super.setYRot(0);
		super.setXRot(0);
		yRotO = xRotO = yBodyRotO = yBodyRot = yHeadRotO = yHeadRot = 0;
	}
	@Override public void setYRot(float yaw) { super.setYRot(isBlockAttached() ? 0 : yaw); }
	@Override public void setXRot(float pitch) { super.setXRot(isBlockAttached() ? 0 : pitch); }
	@Override public void setYBodyRot(float yaw) { super.setYBodyRot(isBlockAttached() ? 0 : yaw); }
	@Override public void setYHeadRot(float yaw) { super.setYHeadRot(isBlockAttached() ? 0 : yaw); }
	/** Shared rest-frame transform for carrier effects, hit parts and the visible body. */
	public Vec3 bodyToWorld(Vec3 point) {
		return position().add(isBlockAttached() ? blockAttachmentRotation().rotate(point)
			: point.yRot(-yBodyRot * Mth.DEG_TO_RAD));
	}
	public Vec3 worldToBody(Vec3 point) {
		Vec3 relative = point.subtract(position());
		return isBlockAttached() ? BionicRiding.inverse(blockAttachmentRotation()).rotate(relative)
			: relative.yRot(yBodyRot * Mth.DEG_TO_RAD);
	}
	public com.nobodiiiii.createbiotech.entity.trait.BionicSpecialAttacks getSpecialAttacks() { return specialAttacks; }
	@Override
	public void setTarget(@Nullable LivingEntity target) {
		LivingEntity previous = getTarget();
		super.setTarget(target);
		// Read the accepted target after NeoForge's target-change event has run.
		if (!level().isClientSide && specialAttacks != null && getTarget() != null && getTarget() != previous)
			specialAttacks.onTargetAcquired();
	}
	@Override public void performRangedAttack(LivingEntity target, float power) { specialAttacks.performRangedAttack(target); }
	public CompoundTag getBionicEquipmentData() { return entityData.get(EQUIPMENT_DATA); }
	public void setBionicEquipmentData(CompoundTag value) { entityData.set(EQUIPMENT_DATA, value); }
	public void setBionicSitting(boolean value) { setOrderedToSit(value); }
	public int getProductionFlags() { return entityData.get(PRODUCTION_DATA); }
	/** Interpolated Creeper.getSwelling progress from this body's synchronized fuse. */
	public float getSelfDestructSwelling(float partialTick) {
		if (selfDestructFuse == 0 && previousSelfDestructFuse == 0) return 0;
		return Mth.lerp(partialTick, (float) previousSelfDestructFuse, (float) selfDestructFuse)
			/ (float) Math.max(1, getBionicTraits().parameter(BionicTrait.SELF_DESTRUCT, "fuse_ticks", 30) - 2);
	}
	public boolean isSheared() { return (getProductionFlags() & 1) != 0; }
	public boolean hasBionicPollen() { return entityData.get(POLLEN_DATA); }
	public void setBionicPollen(boolean value) { entityData.set(POLLEN_DATA, value); }
	public net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState getShellState() {
		return net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.values()[entityData.get(SHELL_STATE_DATA)];
	}
	public int getShellPeekSequence() { return shellPeekSequence; }
	public long getSpecialActionStartTick() { return entityData.get(SPECIAL_STARTED); }
	public int getSpecialActionTicks() { return (int) Math.max(0, level().getGameTime() - getSpecialActionStartTick()); }
	public void setProductionFlags(int flags) { entityData.set(PRODUCTION_DATA, flags); }
	public void markSelfDestructing() { dead = true; }
	public void finishSelfDestruct() { SlimeMimicCarriedDrops.dropCarriedItems(this); triggerOnDeathMobEffects(RemovalReason.KILLED); discard(); }
	public int getSpecialAction() { return entityData.get(SPECIAL_ACTION); }
	public void syncSpecialOrigin(Vec3 worldOrigin) {
		entityData.set(SPECIAL_ORIGIN, worldToBody(worldOrigin).toVector3f());
	}
	public Vec3 getSpecialOrigin() { return bodyToWorld(new Vec3(entityData.get(SPECIAL_ORIGIN))); }
	public Vec3 getSpecialOrigin(float partialTick) {
		Vec3 interpolatedPosition = new Vec3(Mth.lerp((double) partialTick, xOld, getX()),
			Mth.lerp((double) partialTick, yOld, getY()), Mth.lerp((double) partialTick, zOld, getZ()));
		Vec3 origin = new Vec3(entityData.get(SPECIAL_ORIGIN));
		return interpolatedPosition.add(isBlockAttached() ? blockAttachmentRotation().rotate(origin)
			: origin.yRot(-Mth.rotLerp(partialTick, yBodyRotO, yBodyRot) * Mth.DEG_TO_RAD));
	}
	public int getGuardianAttackDuration() { return entityData.get(GUARDIAN_ATTACK_DURATION); }
	public void syncGuardianAttackDuration(int ticks) { entityData.set(GUARDIAN_ATTACK_DURATION, Math.max(1, ticks)); }
	public int getSpecialTarget() { return entityData.get(SPECIAL_TARGET); }
	public void syncSpecialAction(int action, int target) {
		if (action != getSpecialAction() || target != getSpecialTarget()) entityData.set(SPECIAL_STARTED, level().getGameTime());
		entityData.set(SPECIAL_ACTION, action); entityData.set(SPECIAL_TARGET, target);
	}
	@Nullable public Vec3 lastHeardPosition(Entity target) { return vibrationSense.lastHeardPosition(target); }
	public void setRiderAscending(boolean value) { riderAscending = value; }
	public boolean isSelfDestructing() { return production != null && production.exploding(); }
	private boolean hasFlightLocomotion() {
		return !isSwimming() && (getBionicTraits().has(BionicTrait.WINGLESS_FLIGHT) || getBionicTraits().has(BionicTrait.WING_FLIGHT));
	}
	public boolean canReplaceBionicWeapon(ItemStack incoming, ItemStack current) { return canReplaceCurrentItem(incoming, current); }
	public void equipBionicLoot(EquipmentSlot slot, ItemStack stack) { setItemSlotAndDropWhenKilled(slot, stack); }
	@Override public ItemStack equipItemIfPossible(ItemStack stack) {
		return equipment == null ? ItemStack.EMPTY : equipment.equipLoot(stack);
	}
	private boolean evaluatingJumpPath;
	private boolean strongJumpEnabled;
	private boolean doorBreakingEnabled;
	private boolean wallClimbEnabled;
	private boolean bodyFlightEnabled;
	private boolean organSwimEnabled;
	private boolean lavaWalkEnabled;
	private float baseWaterPathMalus = Float.NaN;
	private int moisture = -1;
	private int dryAir = Integer.MIN_VALUE;
	private int shellGuardTicks;
	private int shellStateTicks;
	private int shellPeekSequence;
	private int shellPeekTicks;
	private boolean shellDangerWasAround;
	private net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState shellState = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.IDLE;
	public int teleportPursuitTicks;
	public boolean isBionicWing(SurgicalAssembly.ArmAttackGeometry arm) {
		var assembly = getAssembly();
		if (assembly == null || arm.sourceId() < 0) return false;
		var chain = assembly.rotatingGroup(arm.sourceId(), arm.cubeId());
		return chain.stream().anyMatch(getBionicTraits().wingMembers()::contains);
	}
	@Nullable
	private UUID ownerId;
	private boolean orderedToSit;
	private int lastOwnerHurtTimestamp;
	private int lastOwnerAttackTimestamp;
	@Nullable
	private SurgicalAssembly clientBoundsAssembly;
	@Nullable
	private SurgicalAssembly.BodyBounds clientBodyBounds;
	@Nullable
	private SurgicalAssembly.HitboxGeometry clientHitboxGeometry;
	private double clientBodyVolume = Double.NaN;
	private final SlimeBionicHitPart[] hitParts;
	private SlimeBionicHitPart[] registeredHitParts;
	private int attackAnimationTick;
	private int attackAnimationDuration = SlimeBionicAttackTiming.PLAYBACK_TICKS;
	private boolean attackAnimationLeft;
	private int attackAnimationArmSlot;
	private boolean attackAnimationWeapon;
	private int attackActionTick;
	private int attackActionSnapshotTick;
	private int attackActionInterval = SurgicalCombatCalibration.ZOMBIE_ATTACK_INTERVAL;
	private int attackActionDuration = SlimeBionicCombat.duration(attackActionInterval);
	private boolean attackActionLeft;
	private int attackActionArmSlot;
	private boolean attackActionWeapon;
	private boolean attackActionHasElbow;
	private int attackActionSequence;
	private float attackAimYaw;
	private float attackAimPitch;
	private float attackBodyYaw;
	private boolean combatFacingControlled;
	private float combatFacingYaw;

	public SlimeBionicEntity(EntityType<? extends SlimeBionicEntity> type, Level level) {
		super(type, level);
		hitParts = new SlimeBionicHitPart[MAX_HIT_PARTS];
		for (int index = 0; index < hitParts.length; index++)
			hitParts[index] = new SlimeBionicHitPart(this);
		// Reserve stable multipart ids before synchronized assembly data arrives.
		registeredHitParts = hitParts;
		// Match the Ender Dragon: reserve one consecutive id block and keep every cached part stable.
		setId(ENTITY_COUNTER.getAndAdd(hitParts.length + 1) + 1);
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
			.add(Attributes.MAX_HEALTH, SurgicalHealthCalibration.MAX_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, SurgicalGait.ZOMBIE_WALK_SPEED)
			.add(Attributes.ATTACK_DAMAGE, 3.0d)
			.add(Attributes.ARMOR, 0.0d)
			.add(Attributes.FLYING_SPEED, 0.0d)
			.add(Attributes.FOLLOW_RANGE, 35.0d)
			.add(Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE);
	}

	/** Disposition selects natural targets; assigned targets use the ordinary combat goals. */
	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new BionicFloatGoal(this));
		goalSelector.addGoal(1, new BionicSitGoal(this));
		goalSelector.addGoal(1, new Goal() {
			{ setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
			// A passive passenger must not suspend autonomous wandering.
			@Override public boolean canUse() { return hasControllingPassenger() || specialAttacks != null && specialAttacks.busy(); }
			@Override public boolean canContinueToUse() { return canUse(); }
			@Override public void start() { if (hasControllingPassenger() || specialAttacks.locksMovement()) getNavigation().stop(); }
			@Override public void tick() { if (hasControllingPassenger() || specialAttacks.locksMovement()) getNavigation().stop(); }
		});
		goalSelector.addGoal(2, new BionicBreakDoorGoal(this));
		goalSelector.addGoal(2, new BionicAttackGoal(this, 1.0d, false));
		goalSelector.addGoal(2, new RestrictSunGoal(this) {
			@Override public boolean canUse() { return shouldAvoidSun() && super.canUse(); }
		});
		goalSelector.addGoal(3, new BionicFleeSunGoal(this));
		goalSelector.addGoal(3, new com.nobodiiiii.createbiotech.entity.ai.BionicShoulderRidingGoal(this));
		goalSelector.addGoal(5, new BionicFollowOwnerGoal(this));
		goalSelector.addGoal(6, new Goal() {
			private Goal delegate;
			{ setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
			@Override public boolean canUse() {
				if (production == null) return false;
				if (delegate == null) delegate = production.eatGrassGoal();
				return delegate.canUse();
			}
			@Override public void start() { delegate.start(); }
			@Override public boolean canContinueToUse() { return delegate.canContinueToUse(); }
			@Override public void tick() { delegate.tick(); }
			@Override public void stop() { delegate.stop(); }
		});
		goalSelector.addGoal(6, new Goal() {
			private Goal delegate;
			{ setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
			@Override public boolean canUse() {
				if (production == null) return false;
				if (delegate == null) delegate = production.pollinationGoal();
				return delegate.canUse();
			}
			@Override public boolean canContinueToUse() { return delegate.canContinueToUse(); }
			@Override public void start() { delegate.start(); }
			@Override public boolean requiresUpdateEveryTick() { return true; }
			@Override public void tick() { delegate.tick(); }
			@Override public void stop() { delegate.stop(); }
		});
		goalSelector.addGoal(7, new BionicLandStrollGoal(this));
		goalSelector.addGoal(7, new BionicSwimStrollGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new BionicPackAlertGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
			@Override public boolean canUse() {
				return ownerId == null && getDisposition() == BionicDisposition.HOSTILE && super.canUse();
			}
		});
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
			target -> hunting != null && hunting.matches(target)) {
			// Only a hunting body can match anything, so every other body skips the area scan.
			@Override
			protected void findTarget() {
				if (getBionicTraits().has(BionicTrait.HUNTING_INSTINCT))
					super.findTarget();
				else
					target = null;
			}
		});
	}

	@Override public void ate() { super.ate(); if (production != null) production.ate(); }

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
		if (!isSwimming() && getLocomotionLegCount() < 2) {
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
		builder.define(OWNER_DATA, Optional.empty());
		builder.define(SITTING_DATA, false);
		builder.define(CLIMBING_DATA, false);
		builder.define(EQUIPMENT_DATA, new CompoundTag());
		builder.define(PRODUCTION_DATA, 0);
		builder.define(POLLEN_DATA, false);
		builder.define(SHELL_STATE_DATA, 0);
		builder.define(SPECIAL_STARTED, 0L);
		builder.define(SPECIAL_ACTION, 0);
		builder.define(SPECIAL_TARGET, 0);
		builder.define(SPECIAL_ORIGIN, new org.joml.Vector3f());
		builder.define(GUARDIAN_ATTACK_DURATION, 80);
		builder.define(ATTACH_CELL_DATA, Optional.empty());
		builder.define(ATTACH_FACE_DATA, Direction.DOWN);
	}

	private void setBionicOwner(@Nullable UUID value) {
		ownerId = value;
		if (value != null) setPersistenceRequired();
		if (!level().isClientSide)
			entityData.set(OWNER_DATA, Optional.ofNullable(value));
	}

	private void setOrderedToSit(boolean value) {
		orderedToSit = value;
		if (!level().isClientSide)
			entityData.set(SITTING_DATA, value);
	}

	public void setAssembly(SurgicalAssembly assembly) {
		if (assembly == null || !assembly.isReadyForEntity())
			throw new IllegalArgumentException("A bionic entity requires complete body geometry");
		CompoundTag encoded = assembly.save();
		cachedAssemblyData = encoded;
		cachedAssembly = assembly;
		// Publishing synchronously invokes onSyncedDataUpdated. The assembly is already
		// decoded here; do not load it again or refresh its geometry halfway through installation.
		installingAssembly = true;
		try {
			entityData.set(ASSEMBLY, encoded);
		} finally {
			installingAssembly = false;
			// An equal NBT value need not replace the data item's previous tag instance.
			cachedAssemblyData = entityData.get(ASSEMBLY);
		}
		invalidateTraits();
		configureHitPartRegistration(assembly);
		clientBoundsAssembly = null;
		clientBodyBounds = null;
		clientHitboxGeometry = null;
		clientBodyVolume = Double.NaN;
		refreshMaximumHealth(assembly);
		refreshMovementSpeed(assembly);
		getBionicTraits();
		refreshDimensions();
		updateHitParts();
	}

	/** Applies the packed union-volume calibration without healing an already damaged body. */
	private void refreshMaximumHealth(SurgicalAssembly assembly) {
		if (level().isClientSide)
			return;
		var maximumHealth = getAttribute(Attributes.MAX_HEALTH);
		if (maximumHealth == null)
			return;
		double calibrated = SurgicalHealthCalibration.maximumHealth(assembly.bodyVolume());
		if (maximumHealth.getBaseValue() != calibrated)
			maximumHealth.setBaseValue(calibrated);
		if (getHealth() > calibrated)
			setHealth((float) calibrated);
	}

	/** Aquatic speed has its own baseline; land movement retains the anatomical gait calibration. */
	private void refreshMovementSpeed(SurgicalAssembly assembly) {
		if (level().isClientSide)
			return;
		double speed = organSwimEnabled ? SWIMMING_MOVEMENT_SPEED
			: SurgicalGait.movementSpeed(assembly.bodyBounds());
		var movement = getAttribute(Attributes.MOVEMENT_SPEED);
		if (movement != null && movement.getBaseValue() != speed)
			movement.setBaseValue(speed);
	}

	@Nullable
	public SurgicalAssembly getAssembly() {
		CompoundTag encoded = entityData.get(ASSEMBLY);
		if (encoded != cachedAssemblyData) {
			cachedAssemblyData = encoded;
			SurgicalAssembly loaded = SurgicalAssembly.loadCached(encoded);
			cachedAssembly = loaded != null && loaded.isReadyForEntity() ? loaded : null;
			invalidateTraits();
		}
		return cachedAssembly;
	}

	/** Current head-derived disposition and intelligence classification. */
	public BionicMind getMind() {
		BionicTraitSet traits = getBionicTraits();
		// Derived once with the trait set it belongs to; only a query that arrives before that
		// set exists still works it out directly.
		return traits == mindTraits ? mind : BionicMind.fromTraits(getAssembly(), traits);
	}

	public BionicDisposition getDisposition() {
		return getMind().disposition();
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		if (!super.canAttack(target) || isAlliedTo(target))
			return false;
		if (orderedToSit)
			return false;
		// As in vanilla, natural target selection supplies disposition rules. Once a target has
		// been assigned, combat does not require it to be prey or the last creature to hurt us.
		return true;
	}

	public BionicIntelligence getIntelligence() {
		return getMind().intelligence();
	}

	/** One decision shared by every effect; invalidated by assembly, trait or anatomy changes. */
	public BionicTraitSet getBionicTraits() {
		// LivingEntity construction can call overridden methods before subclass fields initialize.
		if (bionicTraits == null)
			return BionicTraitSet.EMPTY;
		SurgicalAssembly assembly = getAssembly();
		BionicTraitResolver.Revision revision = BionicTraitResolver.revision();
		if (assembly != traitAssembly || !revision.equals(traitRevision)) {
			traitAssembly = assembly;
			traitRevision = revision;
			bionicTraits = BionicTraitResolver.resolve(assembly, level());
			mind = BionicMind.fromTraits(assembly, bionicTraits);
			mindTraits = bionicTraits;
			// Publish the result before refreshing effects that may query this entity again.
			refreshNaturalArmor(bionicTraits);
			refreshKnockbackResistance(bionicTraits);
			refreshFlyingSpeed(assembly, bionicTraits);
			refreshLocomotion(bionicTraits);
			if (!level().isClientSide) {
				double range = 35;
				for (BionicTrait trait : List.of(BionicTrait.HEAD_PROJECTILE, BionicTrait.GUARDIAN_BEAM))
					if (bionicTraits.has(trait)) for (var variant : bionicTraits.result(trait).subtraits().keySet())
						range = Math.max(range, variant.parameter("range", 16));
				getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(range);
			}
			if (!bionicTraits.has(BionicTrait.MOISTURE_DEPENDENT))
				moisture = -1;
			if (!bionicTraits.has(BionicTrait.DRY_SUFFOCATION))
				dryAir = Integer.MIN_VALUE;
			if (!level().isClientSide) {
				if (!bionicTraits.isTameable())
					setBionicOwner(null);
				if (!bionicTraits.has(BionicTrait.TAMEABLE))
					setOrderedToSit(false);
			}
		}
		return bionicTraits;
	}

	@Nullable
	public Player getBionicOwner() {
		return ownerId == null ? null : level().getPlayerByUUID(ownerId);
	}

	@Nullable
	private Player getCompanionPlayer() {
		return getBionicOwner();
	}

	public boolean isOrderedToSit() { return orderedToSit; }
	@Nullable public UUID getBionicOwnerId() { return ownerId; }

	@Override
	public boolean isAlliedTo(Entity other) {
		return ownerId != null && ownerId.equals(other.getUUID())
			|| ownerId != null && other instanceof SlimeBionicEntity bionic && ownerId.equals(bionic.getBionicOwnerId())
			|| ownerId != null && other instanceof net.minecraft.world.entity.TamableAnimal tameable && ownerId.equals(tameable.getOwnerUUID())
			|| ownerId != null && ownerId.equals(SlimeCompanionHandler.ownerOf(other))
			|| super.isAlliedTo(other);
	}

	@Override
	public void openCustomInventoryScreen(Player player) {
		equipment.openInventory(player, null);
	}

	@Override
	public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
		// Carry the click through Player/Mob's normal item, equipment and riding interaction order.
		interactionHit = hit;
		try {
			return player.interactOn(this, hand);
		} finally {
			interactionHit = null;
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		InteractionResult chestRemovalResult = equipment.removeChest(player, hand, interactionHit);
		if (chestRemovalResult.consumesAction()) return chestRemovalResult;
		if (player.isSecondaryUseActive() && equipment.openInventory(player, interactionHit))
			return InteractionResult.sidedSuccess(level().isClientSide);
		ItemStack held = player.getItemInHand(hand);
		InteractionResult productionResult = production.interact(player, hand);
		if (productionResult.consumesAction()) return productionResult;
		InteractionResult equipmentResult = equipment.interact(player, hand, interactionHit);
		if (equipmentResult.consumesAction()) return equipmentResult;
		BionicTraitSet traits = getBionicTraits();
		if (ownerId == null && held.is(Items.SLIME_BALL) && traits.isTameable()) {
			if (!level().isClientSide) {
				if (!player.getAbilities().instabuild)
					held.shrink(1);
				boolean tamed = random.nextDouble() < traits.tamingChance();
				if (tamed) {
					setBionicOwner(player.getUUID());
					setOrderedToSit(false);
					setTarget(null);
				}
				level().broadcastEntityEvent(this, (byte) (tamed ? 7 : 6));
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		InteractionResult ridingResult = equipment.ride(player, hand, interactionHit);
		if (ridingResult.consumesAction()) return ridingResult;
		if (!traits.has(BionicTrait.TAMEABLE))
			return super.mobInteract(player, hand);
		if (ownerId != null && ownerId.equals(player.getUUID())) {
			if (!level().isClientSide) {
				setOrderedToSit(!orderedToSit);
				if (orderedToSit) {
					setTarget(null);
					getNavigation().stop();
				}
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		return super.mobInteract(player, hand);
	}

	private void invalidateTraits() {
		traitAssembly = null;
		traitRevision = null;
		bionicTraits = BionicTraitSet.EMPTY;
	}

	private void refreshNaturalArmor(BionicTraitSet traits) {
		var armor = getAttribute(Attributes.ARMOR);
		if (armor != null && armor.getBaseValue() != traits.value(BionicTrait.NATURAL_ARMOR))
			armor.setBaseValue(traits.value(BionicTrait.NATURAL_ARMOR));
	}

	private void refreshKnockbackResistance(BionicTraitSet traits) {
		var resistance = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		double value = Mth.clamp(BASE_KNOCKBACK_RESISTANCE + traits.value(BionicTrait.KNOCKBACK_RESISTANCE), 0.0d, 1.0d);
		if (resistance != null && resistance.getBaseValue() != value)
			resistance.setBaseValue(value);
	}

	private void refreshFlyingSpeed(@Nullable SurgicalAssembly assembly, BionicTraitSet traits) {
		if (level().isClientSide)
			return;
		double speed = assembly == null ? 0.0d : SurgicalGait.flyingSpeed(traits.flightVolumeRatio(assembly));
		var flying = getAttribute(Attributes.FLYING_SPEED);
		if (flying != null && flying.getBaseValue() != speed)
			flying.setBaseValue(speed);
	}

	private void refreshLocomotion(BionicTraitSet traits) {
		if (level().isClientSide)
			return;
		boolean jump = traits.hasStrongJump();
		boolean doors = traits.has(BionicTrait.DOOR_HANDLING);
		boolean climb = traits.has(BionicTrait.WALL_CLIMB);
		if (strongJumpEnabled != jump || doorBreakingEnabled != doors || wallClimbEnabled != climb) {
			strongJumpEnabled = jump;
			doorBreakingEnabled = doors;
			wallClimbEnabled = climb;
			navigation.stop();
		}
		if (!climb)
			entityData.set(CLIMBING_DATA, false);
		boolean aquatic = traits.has(BionicTrait.SWIM_SPECIALIST);
		boolean lavaWalk = traits.has(BionicTrait.LAVA_WALK);
		if (lavaWalkEnabled != lavaWalk) {
			lavaWalkEnabled = lavaWalk;
			// Do not keep a route computed with the old lava/fire traversal costs.
			navigation.stop();
		}
		boolean swimming = shouldUseSwimmingLocomotion(aquatic);
		boolean enabled = !swimming && (traits.has(BionicTrait.WINGLESS_FLIGHT)
			|| traits.has(BionicTrait.WING_FLIGHT));
		if (Float.isNaN(baseWaterPathMalus))
			baseWaterPathMalus = getPathfindingMalus(PathType.WATER);
		// Random swim targets are chosen before path creation: amphibious navigation alone
		// sets this too late, leaving DefaultRandomPos to reject every water candidate.
		float waterPathMalus = traits.hasFullEffect(BionicTrait.WATER_SENSITIVE) ? -1.0f
			: aquatic ? 0.0f : baseWaterPathMalus;
		if (getPathfindingMalus(PathType.WATER) != waterPathMalus) {
			setPathfindingMalus(PathType.WATER, waterPathMalus);
			navigation.stop();
		}
		boolean changed = bodyFlightEnabled != enabled || organSwimEnabled != swimming;
		bodyFlightEnabled = enabled;
		organSwimEnabled = swimming;
		setSwimming(swimming);
		// Keep a land/water route across shoreline transitions; replace navigation only
		// when the capability changes, not whenever the body touches/leaves the water.
		PathNavigation nextNavigation = navigation;
		if (enabled && !(navigation instanceof FlyingPathNavigation)) {
			FlyingPathNavigation flying = new FlyingPathNavigation(this, level());
			flying.setCanFloat(true);
			flying.setCanOpenDoors(false);
			flying.setCanPassDoors(true);
			nextNavigation = flying;
		} else if (!enabled && aquatic && !(navigation instanceof AmphibiousPathNavigation)) {
			nextNavigation = new com.nobodiiiii.createbiotech.entity.ai.BionicAmphibiousNavigation(this, level());
		} else if (!enabled && !aquatic && !(navigation instanceof SlimeBionicGroundNavigation)) {
			nextNavigation = new SlimeBionicGroundNavigation(this, level());
			nextNavigation.setCanFloat(true);
		}
		if (nextNavigation != navigation) {
			navigation.stop();
			navigation = nextNavigation;
		}
		if (navigation instanceof GroundPathNavigation ground) {
			// Like Zombie.setCanBreakDoors, configure closed-door paths before the AI
			// requests a route, including after replacing the navigator. The breaking
			// goal checks difficulty and protection separately when the mob reaches a door.
			ground.setCanOpenDoors(doors);
			boolean canFloat = !traits.has(BionicTrait.WATER_SINKING);
			if (navigation.canFloat() != canFloat) {
				navigation.stop();
				navigation.setCanFloat(canFloat);
			}
		}
		if (!changed)
			return;
		setSpeed(0.0f);
		setXxa(0.0f);
		setYya(0.0f);
		setZza(0.0f);
		setJumping(false);
		navigation.setSpeedModifier(swimming
			? getTarget() != null ? SWIMMING_PURSUIT_SPEED : SWIMMING_IDLE_SPEED : 1.0d);
		if (enabled) {
			moveControl = new FlyingMoveControl(this, 10, true);
			lookControl = new LookControl(this);
		} else if (swimming) {
			moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1f, 0.5f, false);
			lookControl = new SmoothSwimmingLookControl(this, 20);
		} else {
			moveControl = new SlimeBionicMoveControl(this);
			lookControl = new LookControl(this);
		}
		if (!swimming)
			setXRot(0.0f);
		setNoGravity(enabled);
		SurgicalAssembly assembly = getAssembly();
		if (assembly != null)
			refreshMovementSpeed(assembly);
	}

	/**
	 * Water touching the outer hitbox is not a stable medium for a large assembled body. Keep the
	 * controller selected by the path's next node while crossing the shoreline, and require a
	 * meaningful water depth before returning to land locomotion.
	 */
	private boolean shouldUseSwimmingLocomotion(boolean aquatic) {
		if (!aquatic || !isAlive() || isPassenger())
			return false;
		if (!isInWaterOrBubble())
			return false;
		return isInStableWater() || pathLeadsThroughWater();
	}

	@SuppressWarnings("deprecation")
	private boolean isInStableWater() {
		double minimumDepth = organSwimEnabled ? 0.05d : 0.25d;
		return getFluidHeight(FluidTags.WATER) >= minimumDepth
			|| level().getBlockState(blockPosition()).is(Blocks.BUBBLE_COLUMN);
	}

	private boolean pathLeadsThroughWater() {
		Path currentPath = navigation == null ? null : navigation.getPath();
		if (currentPath == null || currentPath.isDone())
			return false;
		PathType nextType = currentPath.getNextNode().type;
		if (nextType == PathType.WATER || nextType == PathType.WATER_BORDER)
			return true;
		BlockPos target = currentPath.getTarget();
		return target != null && level().getFluidState(target).is(FluidTags.WATER);
	}

	@Override
	public void updateSwimming() {
		// Axolotl activity is water-based, not dependent on sprinting or submerged eyes.
		// Entity.baseTick calls this after updating fluids and before ticking the AI.
		if (!level().isClientSide)
			refreshLocomotion(getBionicTraits());
	}

	@Override
	public boolean isVisuallySwimming() {
		// Reuse the synchronized swimming flag and vanilla pose blending without changing
		// the assembled body's collision dimensions to a player-sized swimming box.
		return isSwimming();
	}

	@Override
	public boolean fireImmune() {
		return getBionicTraits().hasFullEffect(BionicTrait.FIRE_IMMUNE) || super.fireImmune();
	}

	@Override
	public boolean isSensitiveToWater() {
		return getBionicTraits().hasFullEffect(BionicTrait.WATER_SENSITIVE);
	}

	@Override
	public boolean canFreeze() {
		return !getBionicTraits().hasFullEffect(BionicTrait.FREEZE_IMMUNE) && super.canFreeze();
	}

	@Override
	public boolean isInvertedHealAndHarm() {
		return getBionicTraits().has(BionicTrait.INVERTED_HEALING);
	}

	private boolean shouldAvoidSun() {
		return getBionicTraits().has(BionicTrait.SUN_SENSITIVE);
	}

	@Override
	public boolean canDrownInFluidType(FluidType type) {
		return !(type == NeoForgeMod.WATER_TYPE.value()
			&& getBionicTraits().has(BionicTrait.WATER_BREATHING))
			&& !getBionicTraits().has(BionicTrait.NO_BREATHING)
			&& super.canDrownInFluidType(type);
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean canBeAffected(MobEffectInstance effect) {
		ResourceLocation effectId = effect.getEffect().unwrapKey()
			.map(key -> key.location()).orElse(null);
		return !getBionicTraits().isImmuneTo(effectId) && super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier,
		net.minecraft.world.damagesource.DamageSource source) {
		BionicTraitSet traits = getBionicTraits();
		if (traits.has(BionicTrait.FALL_DAMAGE_IMMUNE)
			|| traits.has(BionicTrait.WINGLESS_FLIGHT)
			|| traits.has(BionicTrait.AGILE_LANDING)
			|| traits.has(BionicTrait.WING_SLOW_FALL)
			|| bodyFlightEnabled)
			return false;
		if (traits.has(BionicTrait.FALL_REDUCTION)) {
			fallDistance = Math.max(0.0f, fallDistance - (float)
				traits.parameter(BionicTrait.FALL_REDUCTION,
					"safe_fall_distance", 3.0d));
			multiplier *= (float) traits.parameter(
				BionicTrait.FALL_REDUCTION, "damage_multiplier", 0.5d);
		}
		return super.causeFallDamage(fallDistance, multiplier, source);
	}

	@Override
	protected int calculateFallDamage(float fallDistance, float multiplier) {
		int damage = super.calculateFallDamage(fallDistance, multiplier);
		if (getBionicTraits().has(BionicTrait.FALL_REDUCTION))
			damage -= (int) getBionicTraits().parameter(
				BionicTrait.FALL_REDUCTION, "flat_reduction", 0.0d);
		return Math.max(0, damage);
	}

	@Override
	public float getPathfindingMalus(PathType type) {
		// Strider's traversal costs apply only while the leg trait is present. Keeping
		// them out of the malus map also preserves the original costs after surgery/reload.
		if ((type == PathType.LAVA || type == PathType.DANGER_FIRE || type == PathType.DAMAGE_FIRE)
			&& getBionicTraits().has(BionicTrait.LAVA_WALK))
			return 0.0f;
		return super.getPathfindingMalus(type);
	}

	@Override
	protected void checkFallDamage(double movementY, boolean onGround, BlockState state, BlockPos pos) {
		if (getBionicTraits().has(BionicTrait.LAVA_WALK)) {
			// Match Strider.checkFallDamage, including fluid contact before the landing check.
			checkInsideBlocks();
			if (isInLava()) {
				resetFallDistance();
				return;
			}
		}
		super.checkFallDamage(movementY, onGround, state, pos);
	}

	@Override
	public boolean canStandOnFluid(FluidState state) {
		return state.is(FluidTags.LAVA)
			&& getBionicTraits().has(BionicTrait.LAVA_WALK)
			|| super.canStandOnFluid(state);
	}

	@Override
	public boolean onClimbable() {
		return getBionicTraits().has(BionicTrait.WALL_CLIMB) ? isClimbing() : super.onClimbable();
	}

	public boolean isClimbing() {
		return entityData.get(CLIMBING_DATA);
	}

	public boolean canUseWallClimbing() {
		return getBionicTraits().has(BionicTrait.WALL_CLIMB) && !isSwimming() && !hasFlightLocomotion();
	}

	/** Airborne climbers need wall pressure even while a melee action is playing. */
	public boolean shouldPauseAttackMovement() {
		if (canUseWallClimbing() && isClimbing() && !onGround())
			return false;
		return !getIntelligence().pursuesDuringAttack();
	}

	@Override
	public void travel(Vec3 movement) {
		if (blockAttachment.locksMovement()) {
			setDeltaMovement(Vec3.ZERO);
			calculateEntityAnimation(false);
			return;
		}
		if (isSwimming())
			movement = movement.scale(1.35d);
		if (isShiftKeyDown() && getBionicTraits().has(BionicTrait.SHELL_DEFENSE)) {
			movement = Vec3.ZERO;
			setJumping(false);
			setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		}
		if (hasFlightLocomotion()) {
			if (isControlledByLocalInstance()) {
				Vec3 input;
				double speed;
				if (getControllingPassenger() instanceof Player) {
					input = movement.yRot(-getYRot() * Mth.DEG_TO_RAD);
					speed = getSpeed();
				} else {
					// FlyingMoveControl embeds speed in its input. Use only its active command
					// and waypoint direction so AI and riders apply the flight attribute once.
					var control = getMoveControl();
					input = movement.lengthSqr() > 0.0d
						? new Vec3(control.getWantedX(), control.getWantedY(), control.getWantedZ())
							.subtract(position()).normalize() : Vec3.ZERO;
					speed = getAttributeValue(Attributes.FLYING_SPEED) * control.getSpeedModifier();
				}
				setDeltaMovement(BionicFlightMovement.velocity(getDeltaMovement(), input, speed));
				move(MoverType.SELF, getDeltaMovement());
			}
			calculateEntityAnimation(true);
			return;
		}
		if (isSwimming() && isInWaterOrBubble() && isControlledByLocalInstance()) {
			// Pair swimming steering with aquatic travel (vanilla Axolotl.travel): land-mob
			// water gravity and collision jumps fight the controller's vertical input.
			moveRelative(getSpeed(), movement);
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.9d));
			calculateEntityAnimation(true);
			return;
		}
		super.travel(movement);
	}

	@Override
	public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
		if (state.is(Blocks.COBWEB)
			&& getBionicTraits().has(BionicTrait.WEB_ADAPTED))
			return;
		super.makeStuckInBlock(state, motionMultiplier);
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		BionicTraitSet traits = getBionicTraits();
		if (level().isClientSide || isInvulnerableTo(source) || !isAlive() || amount <= 0)
			return super.hurt(source, amount);
		if (BionicDefenses.avoids(this, source)) return false;
		if (traits.has(BionicTrait.ENDER_TELEPORT) && (source.is(DamageTypeTags.IS_PROJECTILE)
			|| source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.ThrownPotion)) {
			boolean waterDamage = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.ThrownPotion potion
				&& potion.getItem().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
					net.minecraft.world.item.alchemy.PotionContents.EMPTY).is(net.minecraft.world.item.alchemy.Potions.WATER)
				&& super.hurt(source, amount);
			return BionicDefenses.projectileTeleport(this) || waterDamage;
		}
		amount = BionicDefenses.damage(this, source, amount);
		if (isShiftKeyDown() && traits.has(BionicTrait.SHELL_DEFENSE))
			amount *= (float) (1.0d - traits.strength(BionicTrait.SHELL_DEFENSE));
		if (source.is(DamageTypeTags.IS_FIRE)) {
			double resistance = traits.strength(BionicTrait.FIRE_RESISTANCE);
			if (traits.hasFullEffect(BionicTrait.FIRE_IMMUNE))
				return false;
			amount *= (float) (1.0d - resistance);
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			double immunity = traits.strength(BionicTrait.FREEZE_RESISTANCE);
			if (traits.hasFullEffect(BionicTrait.FREEZE_IMMUNE))
				return false;
			amount *= (float) ((1.0d - immunity)
				* (1.0d + 4.0d * traits.strength(BionicTrait.FREEZE_VULNERABLE)));
		}
		if (traits.has(BionicTrait.CONTACT_RETALIATION) && !(getMoveControl().hasWanted() && !getNavigation().isDone())
			&& !source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS) && !source.is(DamageTypes.THORNS)
			&& source.getDirectEntity() instanceof LivingEntity attacker && attacker != this && !isAlliedTo(attacker))
			attacker.hurt(damageSources().thorns(this), 2.0f);
		boolean hurt = super.hurt(source, amount);
		if (hurt) BionicDefenses.afterHurt(this, source);
		if (hurt && traits.has(BionicTrait.SHELL_DEFENSE) && !isNoAi())
			shellGuardTicks = Math.clamp((int) traits.parameter(BionicTrait.SHELL_DEFENSE, "duration_ticks", 80), 0, 12000);

		return hurt;
	}

	@Override
	public ProjectileDeflection deflection(Projectile projectile) {
		if (getBionicTraits().has(BionicTrait.PROJECTILE_DEFLECTION)
			&& projectile.getType() != EntityType.BREEZE_WIND_CHARGE
			&& projectile.getType() != EntityType.WIND_CHARGE)
			return ProjectileDeflection.REVERSE;
		return super.deflection(projectile);
	}

	@Override
	protected float getJumpPower() {
		return super.getJumpPower() + (float) getBionicTraits().strongJumpBonus();
	}

	/** Only the path evaluator sees the two-block step budget; actual movement must jump. */
	public void setEvaluatingJumpPath(boolean evaluating) { evaluatingJumpPath = evaluating; }

	@Override
	public float maxUpStep() {
		return evaluatingJumpPath && getBionicTraits().hasStrongJump()
			? Math.max(2.0f, super.maxUpStep()) : super.maxUpStep();
	}

	/** Only rest-pose hips touching the ground can drive locomotion or leg animation. */
	public int getLocomotionLegCount() {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.BodyBounds bounds = assembly == null ? null : assembly.bodyBounds();
		return bounds == null ? 0 : bounds.groundedLegCount();
	}

	/** Whether the currently installed movement controller makes this body travel by hopping. */
	public boolean usesHopLocomotion() {
		return moveControl instanceof SlimeBionicMoveControl && getLocomotionLegCount() < 2;
	}

	/** Applies the renderer's exact visible envelope on the client, including slime-shell inflation. */
	public void setClientBodyGeometry(SurgicalAssembly assembly, SurgicalAssembly.BodyBounds bounds,
		SurgicalAssembly.HitboxGeometry hitboxGeometry, double bodyVolume) {
		if (!level().isClientSide || assembly == null || bounds == null || hitboxGeometry == null
			|| getAssembly() != assembly
			|| !SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry))
			return;
		if (clientBoundsAssembly == assembly && bounds.equals(clientBodyBounds)
			&& hitboxGeometry.equals(clientHitboxGeometry)
			&& Double.compare(bodyVolume, clientBodyVolume) == 0)
			return;
		clientBoundsAssembly = assembly;
		clientBodyBounds = bounds;
		clientHitboxGeometry = hitboxGeometry;
		clientBodyVolume = bodyVolume;
		refreshDimensions();
		updateHitParts();
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		SurgicalAssembly.BodyBounds bounds = authoritativeBodyBounds();
		if (bounds == null)
			return super.getDefaultDimensions(pose);
		// Vanilla mobs use one centred, yaw-independent square footprint whose side is the body's
		// lateral width. Their fore-aft model depth is deliberately not promoted to collision width.
		float width = Math.min(bounds.width(), MAX_COLLISION_WIDTH);
		float height = Math.min(bounds.minY() + bounds.height(), MAX_COLLISION_HEIGHT);
		return EntityDimensions.fixed(width, height).withEyeHeight(bounds.eyeHeight());
	}

	/**
	 * Collision, hit parts and picking must agree between the server and every client, so they read
	 * the bounds frozen in the assembly rather than whatever this client's model measures. A client
	 * running a replaced entity model therefore sees visuals and hitbox disagree, which is preferred
	 * over each client simulating its own physics for the same mob.
	 */
	@Nullable
	SurgicalAssembly.BodyBounds authoritativeBodyBounds() {
		SurgicalAssembly assembly = getAssembly();
		return assembly == null ? null : assembly.bodyBounds();
	}

	/** Render-only envelope: this client's own measurement when it has one, else the shared bounds. */
	@Nullable
	SurgicalAssembly.BodyBounds renderBodyBounds() {
		SurgicalAssembly assembly = getAssembly();
		return level().isClientSide && clientBoundsAssembly == assembly && clientBodyBounds != null
			? clientBodyBounds : authoritativeBodyBounds();
	}

	@Override
	public boolean isWithinMeleeAttackRange(LivingEntity target) {
		return withinCombatEnvelope(target.getBoundingBox());
	}

	private boolean withinCombatEnvelope(AABB targetBounds) {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		if (geometry == null || geometry.armCount() == 0)
			return SlimeBionicCombat.withinStartEnvelope(targetBounds, position(), combatBodyYaw(),
				SlimeBionicCombat.fallbackArm(getBbWidth(), getBbHeight()));
		for (SurgicalAssembly.ArmAttackGeometry arm : geometry.arms())
			if (!isBionicWing(arm) && SlimeBionicCombat.withinStartEnvelope(targetBounds, position(), combatBodyYaw(), arm))
				return true;
		return false;
	}

	private float combatBodyYaw() {
		return combatFacingControlled ? combatFacingYaw : getYRot();
	}

	/** Movement and visual body smoothing follow the server's combat facing, never the reverse. */
	public boolean applyCombatFacing() {
		if (isBlockAttached()) return false;
		if (!combatFacingControlled || isNoAi() || !isAlive())
			return false;
		setYRot(combatFacingYaw);
		yBodyRot = combatFacingYaw;
		return true;
	}

	@Override
	public void aiStep() {
		setCanPickUpLoot(equipment.mainHandArm() != null && !isNoAi() && !isOrderedToSit() && !isVehicle());
		super.aiStep();
		if (bodyFlightEnabled)
			applyCombatFacing();
		if (attackAnimationTick > 0)
			attackAnimationTick--;
		if (attackActionTick > 0)
			attackActionTick--;
	}

	/** Selects the best ready arm and snapshots gameplay and presentation state for one attack. */
	@Nullable
	private AttackStart beginAttack(LivingEntity target, long[] rightRecovery, long[] leftRecovery,
		@Nullable ArmUse lastArm) {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		ArmSelection selection = geometry == null || geometry.armCount() == 0 ? fallbackArm(target, rightRecovery[0])
			: selectArm(target, geometry, rightRecovery, leftRecovery, lastArm);
		if (selection == null)
			return null;

		ArmCandidate selected = selection.arm();
		SurgicalCombatCalibration.ArmCombatStats stats = SurgicalCombatCalibration.stats(
			geometry == null || geometry.armCount() == 0 ? null : selected.arm());
		int recovery = SurgicalCombatCalibration.armRecovery(stats);
		int interval = SurgicalCombatCalibration.attackInterval(stats, selection.cadenceArms());
		int actionDuration = startAttackAction(selected, interval);
		return new AttackStart(actionDuration,
			SlimeBionicCombat.contactStartTick(interval, selected.arm().hasElbow(), selected.weapon()),
			interval, recovery, stats.damageMultiplier(),
			selected.arm(), selected.aim(), selected.left(), selected.slot());
	}

	@Nullable
	private ArmSelection selectArm(LivingEntity target, SurgicalAssembly.AttackGeometry geometry,
		long[] rightRecovery, long[] leftRecovery, @Nullable ArmUse lastArm) {
		ArmCandidate best = null;
		int cadenceArms = 0;
		int fixedArmIndex = -1;
		if (getIntelligence() == BionicIntelligence.SIMPLE) {
			double highestDps = -1;
			double baseAttackDamage = getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
			var weaponArm = equipment.mainHandArm();
			ItemStack weapon = getMainHandItem();
			double armedAttackDamage = isAttackWeapon(weapon)
				? SurgicalCombatCalibration.attackDamageWithWeapon(baseAttackDamage, weapon) : baseAttackDamage;
			var arms = geometry.arms();
			for (int index = 0; index < arms.size(); index++) {
				var arm = arms.get(index);
				if (isBionicWing(arm)) continue;
				var stats = SurgicalCombatCalibration.stats(arm);
				double attackDamage = arm == weaponArm ? armedAttackDamage : baseAttackDamage;
				double dps = attackDamage * stats.damageMultiplier() * 20.0d / SurgicalCombatCalibration.armRecovery(stats);
				if (dps > highestDps) { highestDps = dps; fixedArmIndex = index; }
			}
		}
		long now = level().getGameTime();
		float bodyYaw = combatBodyYaw();
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			List<SurgicalAssembly.ArmAttackGeometry> arms = left ? geometry.left() : geometry.right();
			long[] recovery = left ? leftRecovery : rightRecovery;
			for (int slot = 0; slot < arms.size(); slot++) {
				// A simple mind waits for its favourite, even when another arm could hit now.
				int armIndex = left ? geometry.right().size() + slot : slot;
				if (fixedArmIndex >= 0 && armIndex != fixedArmIndex)
					continue;
				if (slot >= recovery.length || recovery[slot] > now)
					continue;
				SurgicalAssembly.ArmAttackGeometry arm = arms.get(slot);
				if (isBionicWing(arm)) continue;
				if (!SlimeBionicCombat.withinStartEnvelope(target.getBoundingBox(), position(), bodyYaw, arm)
					|| !hasAttackLineOfSight(target, arm, bodyYaw))
					continue;
				// Count coordination before posture filtering, preserving the existing attack cadence.
				if (SlimeBionicCombat.contributesToCadence(target.getBoundingBox(), position(), bodyYaw, arm))
					cadenceArms++;
				if (!SlimeBionicCombat.canStart(target.getBoundingBox(), position(), bodyYaw, arm))
					continue;
				Vec3 origin = SlimeBionicCombat.worldOrigin(position(), bodyYaw, arm);
				Vec3 aim = SlimeBionicCombat.constrainAim(
					SlimeBionicCombat.aimAt(target.getBoundingBox(), origin, bodyYaw), bodyYaw, arm);
				float relativeYaw = Mth.wrapDegrees(SlimeBionicCombat.yaw(aim) - bodyYaw);
				boolean weapon = armHasWeapon(left, slot);
				Vec3 localTarget = target.getBoundingBox().getCenter().subtract(position())
					.yRot(bodyYaw * Mth.DEG_TO_RAD);
				boolean crossesBody = left ? localTarget.x < -0.05d : localTarget.x > 0.05d;
				double score = Math.abs(relativeYaw)
					+ Math.abs(SlimeBionicCombat.pitch(aim)) * 0.35d
					+ SlimeBionicCombat.posturePreferencePenalty(aim, bodyYaw, arm)
					+ (crossesBody ? 30.0d : 0.0d)
					+ (lastArm != null && lastArm.left() == left && lastArm.slot() == slot ? 18.0d : 0.0d)
					- (weapon ? 12.0d : 0.0d) + slot * 1.0e-3d;
				ArmCandidate candidate = new ArmCandidate(left, slot, weapon, arm, aim, score);
				if (best == null || candidate.score() < best.score())
					best = candidate;
			}
		}
		return best == null ? null : new ArmSelection(best, Math.max(1, cadenceArms));
	}

	@Nullable
	private ArmSelection fallbackArm(LivingEntity target, long readyAt) {
		SurgicalAssembly.ArmAttackGeometry arm = SlimeBionicCombat.fallbackArm(getBbWidth(), getBbHeight());
		float bodyYaw = combatBodyYaw();
		if (readyAt > level().getGameTime()
			|| !SlimeBionicCombat.canStart(target.getBoundingBox(), position(), bodyYaw, arm)
			|| !hasAttackLineOfSight(target, arm, bodyYaw))
			return null;
		Vec3 aim = SlimeBionicCombat.aimAt(target.getBoundingBox(),
			SlimeBionicCombat.worldOrigin(position(), bodyYaw, arm), bodyYaw);
		return new ArmSelection(new ArmCandidate(false, 0, false, arm, aim, 0.0d), 1);
	}

	private int startAttackAction(ArmCandidate selected, int interval) {
		attackActionInterval = interval;
		attackActionLeft = selected.left();
		attackActionArmSlot = selected.slot();
		attackActionWeapon = selected.weapon();
		attackActionHasElbow = selected.arm().hasElbow();
		attackActionDuration = SlimeBionicCombat.duration(interval);
		attackActionTick = attackActionDuration;
		attackBodyYaw = combatBodyYaw();
		attackActionSequence++;
		setAttackAim(selected.aim());
		CBPackets.sendToTrackingEntity(SlimeBionicAttackActionPacket.start(this), this);
		return attackActionDuration;
	}

	private void setAttackAim(Vec3 aim) {
		attackAimYaw = SlimeBionicCombat.yaw(aim);
		attackAimPitch = SlimeBionicCombat.pitch(aim);
	}

	private void synchronizeAttackAim(Vec3 aim, float bodyYaw, int remainingTicks) {
		setAttackAim(aim);
		attackBodyYaw = bodyYaw;
		attackActionTick = remainingTicks;
		CBPackets.sendToTrackingEntity(SlimeBionicAttackActionPacket.aim(this), this);
	}

	/** Shared across server and clients; see {@link #authoritativeBodyBounds()}. */
	@Nullable
	private SurgicalAssembly.HitboxGeometry authoritativeHitboxGeometry() {
		SurgicalAssembly assembly = getAssembly();
		return assembly == null ? null : assembly.hitboxGeometry();
	}

	/** Render-only envelope: this client's own measurement when it has one. */
	@Nullable
	private SurgicalAssembly.HitboxGeometry renderHitboxGeometry() {
		SurgicalAssembly assembly = getAssembly();
		return level().isClientSide && clientBoundsAssembly == assembly && clientHitboxGeometry != null
			? clientHitboxGeometry : authoritativeHitboxGeometry();
	}

	@Override
	public void tick() {
		previousSelfDestructFuse = selfDestructFuse;
		selfDestructFuse = getProductionFlags() >> 2;
		blockAttachment.tick();
		if (!level().isClientSide) { hunting.tick(); specialAttacks.tick(); }
		if (riderJumpCooldown > 0) riderJumpCooldown--;
		if (!hasControllingPassenger()) { riderJump = 0; riderAscending = false; }
		super.tick();
		// Spider updates and synchronizes wall contact after movement, for the next tick.
		if (!level().isClientSide)
			entityData.set(CLIMBING_DATA, horizontalCollision && canUseWallClimbing());
		tickLavaWalking();
		if (getBionicTraits().has(BionicTrait.WING_SLOW_FALL) && !onGround() && !isInWaterOrBubble()
			&& !isInLava() && getDeltaMovement().y < 0) {
			setDeltaMovement(getDeltaMovement().multiply(1, getBionicTraits().parameter(
				BionicTrait.WING_SLOW_FALL, "fall_multiplier", 0.6d), 1));
			resetFallDistance();
		}
		if (!level().isClientSide) {
			BionicDefenses.tick(this);
			equipment.tick();
			production.tick();
		}
		if (!level().isClientSide) tickShellDefense();
		if (!level().isClientSide)
			refreshLocomotion(getBionicTraits());
		vibrationSense.tick();
		updateOwnerTarget();
		tickHeadRespiration();
		tickBodyEnvironment();
		lockBlockAttachmentRotation();
		if (isBlockAttached()) setBoundingBox(makeBoundingBox());
		updateHitParts();
	}

	/** Native armadillo roll states, triggered only by damage to the assembled body. */
	private void tickShellDefense() {
		var idle = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.IDLE;
		var rolling = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.ROLLING;
		var scared = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.SCARED;
		var unrolling = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.UNROLLING;
		boolean enabled = getBionicTraits().has(BionicTrait.SHELL_DEFENSE) && !isNoAi() && isAlive();
		boolean allowed = enabled && !isInLiquid() && !isLeashed() && !isPassenger() && !isVehicle();
		if (shellGuardTicks > 0) shellGuardTicks--;
		if (shellPeekTicks > 0) shellPeekTicks--;
		boolean dangerAround = shellGuardTicks > 75;
		if (dangerAround != shellDangerWasAround) shellPeekTicks = scared.animationDuration() + 100 + random.nextInt(301);
		shellDangerWasAround = dangerAround;
		if (shellState == scared && shellPeekTicks == 0 && dangerAround) {
			level().broadcastEntityEvent(this, (byte) 64);
			shellPeekTicks = scared.animationDuration() + 100 + random.nextInt(301);
		}
		var next = shellState;
		if (!allowed || shellGuardTicks == 0) next = idle;
		else if (shellState == idle) next = rolling;
		else if (shellState == rolling && shellStateTicks > rolling.animationDuration()) next = scared;
		else if (shellState == scared && shellGuardTicks < unrolling.animationDuration()) next = unrolling;
		else if (shellState == unrolling && shellGuardTicks > unrolling.animationDuration()) next = scared;
		if (next != shellState) {
			if (next == rolling) playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_ROLL);
			if (next == unrolling) playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_UNROLL_START);
			if (next == scared && shellState == rolling) playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_LAND);
			if (next == idle && shellState != idle) playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_UNROLL_FINISH);
			shellState = next; shellStateTicks = 0;
		} else shellStateTicks++;
		if (entityData.get(SHELL_STATE_DATA) != shellState.ordinal()) {
			entityData.set(SHELL_STATE_DATA, shellState.ordinal());
		}
		setShiftKeyDown(shellState != idle);
		if (isShiftKeyDown()) { getNavigation().stop(); setDeltaMovement(getDeltaMovement().multiply(0, 1, 0)); }
	}

	private void tickLavaWalking() {
		if (!getBionicTraits().has(BionicTrait.LAVA_WALK))
			return;
		// Vanilla Strider.floatStrider runs after super.tick on both sides. Use the
		// same half-block support shape as LiquidBlock, not the rendered fluid height.
		if (isInLava()) {
			if (CollisionContext.of(this).isAbove(LiquidBlock.STABLE_SHAPE, blockPosition(), true)
				&& !level().getFluidState(blockPosition().above()).is(FluidTags.LAVA)) {
				setOnGround(true);
			} else {
				setDeltaMovement(getDeltaMovement().scale(0.5d).add(0.0d, 0.05d, 0.0d));
			}
		}
		checkInsideBlocks();
	}

	private void updateOwnerTarget() {
		if (level().isClientSide || ownerId == null || orderedToSit
			|| !getBionicTraits().has(BionicTrait.TAMEABLE))
			return;
		Player owner = getBionicOwner();
		if (owner == null)
			return;
		LivingEntity candidate = null;
		if (owner.getLastHurtByMobTimestamp() != lastOwnerHurtTimestamp) {
			lastOwnerHurtTimestamp = owner.getLastHurtByMobTimestamp();
			candidate = owner.getLastHurtByMob();
		}
		if (candidate == null && owner.getLastHurtMobTimestamp() != lastOwnerAttackTimestamp) {
			lastOwnerAttackTimestamp = owner.getLastHurtMobTimestamp();
			candidate = owner.getLastHurtMob();
		}
		if (candidate != null && candidate.isAlive() && candidate != this
			&& !isAlliedTo(candidate)) {
			if (canAttack(candidate))
				setTarget(candidate);
		}
	}

	@Override
	public void updateDynamicGameEventListener(java.util.function.BiConsumer<net.minecraft.world.level.gameevent.DynamicGameEventListener<?>,
		net.minecraft.server.level.ServerLevel> consumer) {
		if (level() instanceof net.minecraft.server.level.ServerLevel server && vibrationSense != null)
			consumer.accept(vibrationSense.listener(), server);
	}



	private void tickHeadRespiration() {
		if (level().isClientSide || !isAlive())
			return;
		if (!getBionicTraits().has(BionicTrait.DRY_SUFFOCATION)) {
			dryAir = Integer.MIN_VALUE;
			return;
		}
		if (isInWaterOrBubble()) {
			dryAir = 300;
			return;
		}
		if (dryAir == Integer.MIN_VALUE) dryAir = 300;
		if (--dryAir == -20) { dryAir = 0; hurt(damageSources().drown(), 2.0f); }
	}

	private void tickBodyEnvironment() {
		if (level().isClientSide || !isAlive())
			return;
		BionicTraitSet traits = getBionicTraits();
		if (tickCount % 10 == 0 && traits.value(BionicTrait.PASSIVE_REGENERATION) > 0.0d
			&& getHealth() < getMaxHealth())
			heal((float) (traits.value(BionicTrait.PASSIVE_REGENERATION) * 0.5d));
		if (traits.has(BionicTrait.SUN_SENSITIVE) && isSunBurnTick()) {
			boolean burns = true;
			ItemStack headwear = getItemBySlot(EquipmentSlot.HEAD);
			if (!headwear.isEmpty()) {
				if (headwear.isDamageableItem()) {
					Item item = headwear.getItem();
					headwear.setDamageValue(headwear.getDamageValue() + random.nextInt(2));
					if (headwear.getDamageValue() >= headwear.getMaxDamage()) {
						onEquippedItemBroken(item, EquipmentSlot.HEAD);
						setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
					}
				}
				burns = false;
			}
			if (burns)
				igniteForSeconds(8.0f);
		}

		double heatSensitivity = traits.strength(BionicTrait.HEAT_SENSITIVE);
		if (heatSensitivity > 0.0d
			&& level().getBiome(blockPosition()).is(BiomeTags.SNOW_GOLEM_MELTS))
			hurt(damageSources().onFire(), (float) heatSensitivity);

		double moistureDependence = traits.strength(BionicTrait.MOISTURE_DEPENDENT);
		if (moistureDependence <= 0.0d) {
			moisture = -1;
			return;
		}
		if (isNoAi()) return;
		if (isInWaterRainOrBubble()) {
			moisture = MAX_MOISTURE;
			return;
		}
		if (moisture < 0) moisture = MAX_MOISTURE;
		moisture = Math.max(0, moisture - 1);
		if (moisture <= 0)
			hurt(damageSources().dryOut(), (float) moistureDependence);
	}

	private void updateHitParts() {
		if (hitParts == null || registeredHitParts.length == 0)
			return;
		SurgicalAssembly.HitboxGeometry geometry = authoritativeHitboxGeometry();
		List<SurgicalAssembly.VisualBounds> bounds = geometry == null ? List.of()
			: geometry.partBounds(MULTIPART_THRESHOLD);
		AABB equipmentBounds = equipment.interactionBounds();
		if (bounds.size() + (equipmentBounds == null ? 0 : 1) > registeredHitParts.length) {
			if (geometry == null) return;
			bounds = List.of(geometry.overall());
		}
		for (int index = 0; index < registeredHitParts.length; index++)
			registeredHitParts[index].setHitBounds(index < bounds.size() ? worldBounds(bounds.get(index))
				: index == bounds.size() ? equipmentBounds : null);
	}

	/** Chooses the cached subset before level tracking starts; tracked part maps cannot grow later. */
	private void configureHitPartRegistration(SurgicalAssembly assembly) {
		if (isAddedToLevel())
			return;
		SurgicalAssembly.HitboxGeometry geometry = assembly == null ? null : assembly.hitboxGeometry();
		if (geometry != null)
			setRegisteredHitPartCount(geometry.partBounds(MULTIPART_THRESHOLD).size()
				+ (assembly.attackGeometry() != null && !assembly.attackGeometry().equipmentBounds().isEmpty() ? 1 : 0));
	}

	private void setRegisteredHitPartCount(int count) {
		registeredHitParts = count >= hitParts.length ? hitParts
			: Arrays.copyOf(hitParts, Mth.clamp(count, 0, hitParts.length));
	}

	private AABB worldBounds(SurgicalAssembly.VisualBounds bounds) {
		if (isBlockAttached()) {
			AABB rotated = blockAttachment.bounds(bounds);
			if (rotated != null) return rotated.move(position());
		}
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (int xSide = 0; xSide < 2; xSide++)
			for (int ySide = 0; ySide < 2; ySide++)
			for (int zSide = 0; zSide < 2; zSide++) {
				Vec3 rotated = bodyToWorld(new Vec3(xSide == 0 ? bounds.minX() : bounds.maxX(),
					ySide == 0 ? bounds.minY() : bounds.maxY(), zSide == 0 ? bounds.minZ() : bounds.maxZ()));
				minX = Math.min(minX, rotated.x);
				minY = Math.min(minY, rotated.y);
				minZ = Math.min(minZ, rotated.z);
				maxX = Math.max(maxX, rotated.x);
				maxY = Math.max(maxY, rotated.y);
				maxZ = Math.max(maxZ, rotated.z);
			}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override protected AABB makeBoundingBox() {
		if (isBlockAttached()) {
			SurgicalAssembly.HitboxGeometry geometry = authoritativeHitboxGeometry();
			// Use the measured envelope, including its depth and offsets, rather than rotating
			// vanilla's upright square footprint. Hit parts use this same rest-to-world transform.
			if (geometry != null)
				return worldBounds(geometry.requiresMultipart(MULTIPART_THRESHOLD) ? geometry.body() : geometry.overall());
			return BionicRiding.rotateBounds(super.makeBoundingBox(), position(), blockAttachmentRotation());
		}
		return super.makeBoundingBox();
	}

	@Override public boolean isInWall() {
		if (!isBlockAttached()) return super.isInWall();
		if (noPhysics || isSleeping()) return false;
		// The eye origin and sampling plane share the same head frame as getEyePosition and F3+B.
		// Retain the normal suffocating-block predicate and LivingEntity damage tick.
		double width = getBbWidth() * 0.8;
		AABB eyes = BionicRiding.rotateBounds(AABB.ofSize(blockAttachment.eyePoint(),
			width, 1.0e-6, width), Vec3.ZERO, blockAttachmentRotation()).move(position());
		return BlockPos.betweenClosedStream(eyes).anyMatch(pos -> {
			var state = level().getBlockState(pos);
			return !state.isAir() && state.isSuffocating(level(), pos)
				&& net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
					state.getCollisionShape(level(), pos).move(pos.getX(), pos.getY(), pos.getZ()),
					net.minecraft.world.phys.shapes.Shapes.create(eyes),
					net.minecraft.world.phys.shapes.BooleanOp.AND);
		});
	}

	@Override public boolean isPushable() {
		return (blockAttachment == null || !blockAttachment.locksMovement()) && super.isPushable();
	}

	@Override public Vec3 getDeltaMovement() {
		return blockAttachment != null && blockAttachment.locksMovement() ? Vec3.ZERO : super.getDeltaMovement();
	}

	@Override public void setDeltaMovement(Vec3 velocity) {
		super.setDeltaMovement(blockAttachment != null && blockAttachment.locksMovement() ? Vec3.ZERO : velocity);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		SurgicalAssembly.HitboxGeometry geometry = renderHitboxGeometry();
		AABB bounds = geometry == null ? super.getBoundingBoxForCulling()
			: getBoundingBox().minmax(worldBounds(geometry.overall()));
		AABB accessories = equipment.interactionBounds();
		return accessories == null ? bounds : bounds.minmax(accessories);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		if (super.shouldRenderAtSqrDistance(distance))
			return true;
		SurgicalAssembly.HitboxGeometry geometry = renderHitboxGeometry();
		if (geometry == null)
			return false;
		double physicalSize = Math.max(getBoundingBox().getSize(), 1.0e-6d);
		double visualSize = Math.max(worldBounds(geometry.overall()).getSize(), physicalSize);
		double scale = visualSize / physicalSize;
		return super.shouldRenderAtSqrDistance(distance / (scale * scale));
	}

	@Override
	public boolean isPickable() {
		SurgicalAssembly.HitboxGeometry geometry = authoritativeHitboxGeometry();
		boolean multipartActive = registeredHitParts.length > 0 && geometry != null
			&& geometry.requiresMultipart(MULTIPART_THRESHOLD);
		return !multipartActive && super.isPickable();
	}

	private boolean armHasWeapon(boolean left, int slot) {
		SurgicalAssembly assembly = getAssembly();
		var mainHandArm = equipment.mainHandArm();
		return isAttackWeapon(getMainHandItem()) && assembly != null && assembly.attackGeometry() != null
			&& mainHandArm != null && mainHandArm == assembly.attackGeometry().arm(left, slot);
	}

	@Override
	public HumanoidArm getMainArm() {
		HumanoidArm preferred = super.getMainArm();
		if (equipment == null) return preferred;
		SurgicalAssembly assembly = getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return preferred;
		var geometry = assembly.attackGeometry();
		var arms = preferred == HumanoidArm.LEFT ? geometry.left() : geometry.right();
		if (arms.stream().anyMatch(equipment::canUseWeapon)) return preferred;
		var other = preferred == HumanoidArm.LEFT ? geometry.right() : geometry.left();
		return other.stream().anyMatch(equipment::canUseWeapon)
			? preferred.getOpposite() : preferred;
	}

	/** Applies one logical attack hit without touching its independent presentation clock. */
	private boolean performAttackDamage(Entity target, double damageMultiplier) {
		boolean replaceMainHand = !attackUsesMainHand();
		HashMultimap<Holder<Attribute>, AttributeModifier> mainHandModifiers = replaceMainHand
			? combatModifiers(getMainHandItem()) : null;
		HashMultimap<Holder<Attribute>, AttributeModifier> selectedModifiers = replaceMainHand
			? combatModifiers(selectedAttackWeapon()) : null;
		if (replaceMainHand) {
			getAttributes().removeAttributeModifiers(mainHandModifiers);
			getAttributes().addTransientAttributeModifiers(selectedModifiers);
		}
		var attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
		try {
			if (attackDamage == null || Math.abs(damageMultiplier - 1.0d) < 1.0e-8d)
				return doSelectedAttack(target);
			attackDamage.removeModifier(ANATOMICAL_ATTACK_MODIFIER_ID);
			attackDamage.addTransientModifier(new AttributeModifier(ANATOMICAL_ATTACK_MODIFIER_ID,
				damageMultiplier - 1.0d, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			try {
				return doSelectedAttack(target);
			} finally {
				attackDamage.removeModifier(ANATOMICAL_ATTACK_MODIFIER_ID);
			}
		} finally {
			if (replaceMainHand) {
				getAttributes().removeAttributeModifiers(selectedModifiers);
				getAttributes().addTransientAttributeModifiers(mainHandModifiers);
			}
		}
	}

	@Override
	protected float getKnockback(Entity target, net.minecraft.world.damagesource.DamageSource source) {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		SurgicalAssembly.ArmAttackGeometry arm = geometry == null ? null : geometry.arm(attackActionLeft, attackActionArmSlot);
		double inherited = arm == null || arm.sourceId() < 0 ? 0 : getBionicTraits().armValue(
			BionicTrait.ATTACK_KNOCKBACK, assembly.rotatingGroup(arm.sourceId(), arm.cubeId()));
		return super.getKnockback(target, source) + (float) inherited;
	}

	private boolean doSelectedAttack(Entity target) {
		var selectedAssembly = getAssembly();
		var selectedGeometry = selectedAssembly == null ? null : selectedAssembly.attackGeometry();
		var selectedArm = selectedGeometry == null ? null : selectedGeometry.arm(attackActionLeft, attackActionArmSlot);
		if (selectedArm != null && isBionicWing(selectedArm)) return false;
		boolean hit = super.doHurtTarget(target);
		specialAttacks.afterMelee();
		if (!hit || level().isClientSide || !(target instanceof LivingEntity victim))
			return hit;
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		SurgicalAssembly.ArmAttackGeometry arm = geometry == null ? null
			: geometry.arm(attackActionLeft, attackActionArmSlot);
		if (arm == null || arm.sourceId() < 0)
			return hit;
		List<SurgicalAssembly.CombinationMember> attackingGroup =
			assembly.rotatingGroup(arm.sourceId(), arm.cubeId());
		BionicTraitSet traits = getBionicTraits();
		if (attackingGroup.stream().anyMatch(traits.members(BionicTrait.LAUNCH_STRIKE)::contains)) {
			double resistance = Math.max(0, 1 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			victim.setDeltaMovement(victim.getDeltaMovement().add(0,
				traits.parameter(BionicTrait.LAUNCH_STRIKE, "vertical_boost", 0.4d) * resistance, 0));
			victim.hurtMarked = true;
		}
		for (var entry : traits.result(BionicTrait.EFFECT_ATTACK).attackEffects().entrySet()) {
			var effect = entry.getKey();
			if (effect.unarmedOnly() && attackActionWeapon
				|| attackingGroup.stream().noneMatch(entry.getValue()::contains)
				|| random.nextDouble() >= effect.chance())
				continue;
			net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect())
				.ifPresent(holder -> victim.addEffect(new MobEffectInstance(holder,
					effect.durationTicks(), effect.amplifier()), this));
		}
		return hit;
	}

	private boolean attackUsesMainHand() {
		return attackActionWeapon && armHasWeapon(attackActionLeft, attackActionArmSlot);
	}

	private ItemStack selectedAttackWeapon() {
		return attackUsesMainHand() ? getMainHandItem() : ItemStack.EMPTY;
	}

	private static HashMultimap<Holder<Attribute>, AttributeModifier> combatModifiers(ItemStack stack) {
		HashMultimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
		if (stack == null || stack.isEmpty())
			return modifiers;
		stack.forEachModifier(EquipmentSlot.MAINHAND, modifiers::put);
		return modifiers;
	}

	@Override
	public ItemStack getWeaponItem() {
		return attackActionTick > 0 ? selectedAttackWeapon() : super.getWeaponItem();
	}

	private boolean hasAttackLineOfSight(LivingEntity target, SurgicalAssembly.ArmAttackGeometry arm,
		float bodyYaw) {
		Vec3 origin = SlimeBionicCombat.worldOrigin(position(), bodyYaw, arm);
		Vec3 targetPoint = SlimeBionicCombat.aimPoint(target.getBoundingBox(), origin);
		if (origin.distanceToSqr(targetPoint) < 1.0e-8d)
			return true;
		return level().clip(new ClipContext(origin, targetPoint, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
	}

	private record AttackStart(int actionDuration, int contactStartTick,
		int globalAttackInterval, int armRecovery,
		double damageMultiplier, SurgicalAssembly.ArmAttackGeometry arm, Vec3 aim,
		boolean left, int slot) {}

	private record ArmCandidate(boolean left, int slot, boolean weapon,
		SurgicalAssembly.ArmAttackGeometry arm, Vec3 aim, double score) {}

	private record ArmSelection(ArmCandidate arm, int cadenceArms) {}

	private record ArmUse(boolean left, int slot) {}

	public int getAttackActionTick() {
		return attackActionTick;
	}

	public int getAttackActionDuration() {
		return attackActionDuration;
	}

	/** Client ticks must not colour a still-tracking aim as contact before the server locks it. */
	public boolean isAttackPreviewContact() {
		int contactStart = getAttackActionContactStartTick();
		int synchronizedElapsed = attackActionDuration - attackActionSnapshotTick;
		int localElapsed = attackActionDuration - attackActionTick;
		return attackActionTick > 0 && synchronizedElapsed >= contactStart
			&& SlimeBionicCombat.isContactTick(localElapsed, contactStart);
	}

	public int getAttackActionContactStartTick() {
		return SlimeBionicCombat.contactStartTick(attackActionInterval,
			attackActionHasElbow, attackActionWeapon);
	}

	public int getAttackActionInterval() {
		return attackActionInterval;
	}

	public float getAttackBodyYaw() {
		return attackBodyYaw;
	}

	public boolean isAttackActionLeft() {
		return attackActionLeft;
	}

	public int getAttackActionArmSlot() {
		return attackActionArmSlot;
	}

	public boolean isAttackActionWeapon() {
		return attackActionWeapon;
	}

	public boolean hasAttackActionElbow() {
		return attackActionHasElbow;
	}

	public int getAttackActionSequence() {
		return attackActionSequence;
	}

	public float getAttackAimYaw() {
		return attackAimYaw;
	}

	public float getAttackAimPitch() {
		return attackAimPitch;
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

	/** Applies a clientbound snapshot; tracking, aim-lock and cancellation never restart the animation. */
	public void applyAttackAction(int sequence, boolean restart, boolean left, int slot,
		boolean weapon, boolean hasElbow, int interval, int remainingTicks,
		float aimYaw, float aimPitch, float bodyYaw) {
		if (!level().isClientSide || !Float.isFinite(aimYaw) || !Float.isFinite(aimPitch)
			|| !Float.isFinite(bodyYaw))
			return;
		if (!restart) {
			if (sequence != attackActionSequence)
				return;
			attackAimYaw = Mth.wrapDegrees(aimYaw);
			attackAimPitch = Mth.clamp(aimPitch, SlimeBionicCombat.MIN_AIM_PITCH_DEGREES,
				SlimeBionicCombat.MAX_AIM_PITCH_DEGREES);
			attackBodyYaw = Mth.wrapDegrees(bodyYaw);
			attackActionTick = Mth.clamp(remainingTicks, 0, attackActionDuration);
			attackActionSnapshotTick = attackActionTick;
			return;
		}
		attackActionSequence = sequence;
		attackActionInterval = Mth.clamp(interval, SurgicalCombatCalibration.MIN_GLOBAL_ATTACK_INTERVAL,
			SurgicalCombatCalibration.MAX_GLOBAL_ATTACK_INTERVAL);
		attackActionLeft = left;
		attackActionArmSlot = Mth.clamp(slot, 0, SurgicalLimbType.SHOULDER.maxPerBody() - 1);
		attackActionWeapon = weapon;
		attackActionHasElbow = hasElbow;
		attackActionDuration = SlimeBionicCombat.duration(attackActionInterval);
		attackActionTick = Mth.clamp(remainingTicks, 0, attackActionDuration);
		attackActionSnapshotTick = attackActionTick;
		attackAnimationDuration = SlimeBionicAttackTiming.playbackTicks(attackActionInterval);
		attackAnimationTick = attackAnimationDuration;
		attackAnimationLeft = attackActionLeft;
		attackAnimationArmSlot = attackActionArmSlot;
		attackAnimationWeapon = attackActionWeapon;
		attackAimYaw = Mth.wrapDegrees(aimYaw);
		attackAimPitch = Mth.clamp(aimPitch, SlimeBionicCombat.MIN_AIM_PITCH_DEGREES,
			SlimeBionicCombat.MAX_AIM_PITCH_DEGREES);
		attackBodyYaw = Mth.wrapDegrees(bodyYaw);
	}

	public boolean hasAttackWeapon() {
		return equipment.mainHandArm() != null && isAttackWeapon(getMainHandItem());
	}

	public static boolean isAttackWeapon(ItemStack stack) {
		return stack != null && !stack.isEmpty() && (stack.is(Tags.Items.MELEE_WEAPON_TOOLS)
			|| stack.getAttributeModifiers().modifiers().stream().anyMatch(entry ->
				entry.slot().test(EquipmentSlot.MAINHAND) && entry.attribute().equals(Attributes.ATTACK_DAMAGE)
					&& entry.modifier().amount() > 0));
	}

	@Override
	public void handleEntityEvent(byte event) {
		if ((event == 6 || event == 7) && level().isClientSide) {
			var particle = event == 7 ? ParticleTypes.HEART : ParticleTypes.SMOKE;
			for (int i = 0; i < 7; i++) {
				double dx = random.nextGaussian() * 0.02;
				double dy = random.nextGaussian() * 0.02;
				double dz = random.nextGaussian() * 0.02;
				level().addParticle(particle, getRandomX(1.0), getRandomY() + 0.5,
					getRandomZ(1.0), dx, dy, dz);
			}
		} else if (event == 64 && level().isClientSide) {
			shellPeekSequence++;
		} else {
			super.handleEntityEvent(event);
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (ATTACH_CELL_DATA.equals(key) || ATTACH_FACE_DATA.equals(key)) {
			lockBlockAttachmentRotation();
			setBoundingBox(makeBoundingBox());
			updateHitParts();
		}
		if (OWNER_DATA.equals(key))
			ownerId = entityData.get(OWNER_DATA).orElse(null);
		if (SITTING_DATA.equals(key))
			orderedToSit = entityData.get(SITTING_DATA);
		if (ASSEMBLY.equals(key) && !installingAssembly) {
			invalidateTraits();
			clientBoundsAssembly = null;
			clientBodyBounds = null;
			clientHitboxGeometry = null;
			clientBodyVolume = Double.NaN;
			refreshDimensions();
			updateHitParts();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		hunting.save(tag);
		equipment.save(tag);
		production.save(tag);
		specialAttacks.save(tag);
		blockAttachment.save(tag);
		SlimeMimicAccess slimeState = (SlimeMimicAccess) (Object) this;
		tag.putBoolean(SOURCE_FORM_TAG, !slimeState.createBiotech$isSlimeMimic());
		CompoundTag assembly = entityData.get(ASSEMBLY);
		if (!assembly.isEmpty())
			tag.put(ASSEMBLY_TAG, assembly.copy());
		if (moisture >= 0)
			tag.putInt(MOISTURE_TAG, moisture);
		if (dryAir != Integer.MIN_VALUE)
			tag.putInt(DRY_AIR_TAG, dryAir);
		if (ownerId != null)
			tag.putUUID(OWNER_TAG, ownerId);
		if (shellGuardTicks > 0)
			tag.putInt(SHELL_GUARD_TAG, shellGuardTicks);
		tag.putString("BionicShellState", shellState.getSerializedName());
		tag.putBoolean(ORDERED_TO_SIT_TAG, orderedToSit);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		hunting.load(tag);
		specialAttacks.load(tag);
		if (!tag.contains(SOURCE_FORM_TAG, Tag.TAG_BYTE)
			|| !tag.contains(ASSEMBLY_TAG, Tag.TAG_COMPOUND))
			return;
		SurgicalAssembly assembly = SurgicalAssembly.loadCached(tag.getCompound(ASSEMBLY_TAG));
		if (assembly == null || !assembly.isReadyForEntity())
			return;
		boolean sourceForm = tag.getBoolean(SOURCE_FORM_TAG);
		((SlimeMimicAccess) (Object) this).createBiotech$setSlimeMimic(!sourceForm);
		setAssembly(assembly);
		blockAttachment.load(tag);
		equipment.load(tag);
		production.load(tag);
		if (tag.contains(MOISTURE_TAG, Tag.TAG_ANY_NUMERIC))
			moisture = Mth.clamp(tag.getInt(MOISTURE_TAG), 0, MAX_MOISTURE);
		if (tag.contains(DRY_AIR_TAG, Tag.TAG_ANY_NUMERIC))
			dryAir = Mth.clamp(tag.getInt(DRY_AIR_TAG), -19, 300);
		setBionicOwner(tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG)
			: tag.hasUUID(TRUSTED_TAG) ? tag.getUUID(TRUSTED_TAG) : null);
		shellGuardTicks = Mth.clamp(tag.getInt(SHELL_GUARD_TAG), 0, 12000);
		shellState = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.fromName(tag.getString("BionicShellState"));
		shellStateTicks = 0;
		setOrderedToSit(tag.getBoolean(ORDERED_TO_SIT_TAG) && getBionicTraits().has(BionicTrait.TAMEABLE));
		if (!getBionicTraits().isTameable())
			setBionicOwner(null);
	}

	@Override public boolean isSaddleable() { return isAlive() && getBionicTraits().has(BionicTrait.RIDEABLE); }
	@Override public boolean isSaddled() { return equipment != null && equipment.saddled(); }
	@Override public void equipSaddle(ItemStack stack, @Nullable net.minecraft.sounds.SoundSource source) {
		if (!isSaddleable()) return;
		equipment.equipSaddle(stack);
		if (source != null) level().playSound(null, this, net.minecraft.sounds.SoundEvents.HORSE_SADDLE, source, 1, 1);
	}
	@Override protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && !isOrderedToSit() && isSaddled() && isSaddleable()
			&& equipment.canSeat(passenger)
			&& BionicRiding.pose(this, passenger, 1, yBodyRot) != null
			&& BionicRiding.clear(passenger, BionicRiding.pose(this, passenger, 1, yBodyRot).bounds(passenger));
	}
	@Override protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		equipment.passengerAdded(passenger);
	}
	@Override protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		equipment.passengerRemoved(passenger);
	}
	@Override @Nullable public LivingEntity getControllingPassenger() {
		return isSaddled() && isSaddleable() && ownerId != null && getFirstPassenger() instanceof Player player
			&& equipment.canSeat(player) ? player : null;
	}
	@Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		var pose = BionicRiding.pose(this, passenger, 1, yBodyRot);
		return pose == null ? super.getPassengerAttachmentPoint(passenger, dimensions, scale)
			: pose.anchor().subtract(position());
	}
	@Override protected void positionRider(Entity passenger, Entity.MoveFunction move) {
		var pose = BionicRiding.pose(this, passenger, 1, yBodyRot);
		if (pose == null) { super.positionRider(passenger, move); return; }
		Vec3 feet = pose.feet(passenger);
		move.accept(passenger, feet.x, feet.y, feet.z);
		passenger.setBoundingBox(pose.bounds(passenger));
	}
	@Override public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
		if (blockAttachment != null && blockAttachment.locksMovement()) {
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		if (!isVehicle() || noPhysics) {
			super.move(type, movement);
			return;
		}
		Vec3 initialPosition = position();
		Vec3 clipped = BionicRiding.clipMovement(this, movement);
		super.move(type, clipped);
		boolean blockedX = !Mth.equal(movement.x, clipped.x);
		boolean blockedY = movement.y != clipped.y;
		boolean blockedZ = !Mth.equal(movement.z, clipped.z);
		if (!blockedX && !blockedY && !blockedZ) return;
		// Entity.move only sees the already clipped vector. Preserve rider collisions too,
		// otherwise a rider touching the floor leaves the mount airborne and unable to hop.
		if (blockedX || blockedZ) {
			horizontalCollision = true;
			minorHorizontalCollision = false;
		}
		if (blockedY) {
			verticalCollision = true;
			if (movement.y < 0 && !onGround()) {
				verticalCollisionBelow = true;
				setOnGroundWithMovement(true, position().subtract(initialPosition));
				BlockPos support = getOnPosLegacy();
				checkFallDamage(0, true, level().getBlockState(support), support);
			}
		}
		Vec3 velocity = getDeltaMovement();
		setDeltaMovement(blockedX ? 0 : velocity.x, blockedY ? 0 : velocity.y, blockedZ ? 0 : velocity.z);
	}
	@Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		for (int y = 0; y <= (int) getBbHeight() + 1; y++) for (int radius = 1; radius <= 3; radius++)
			for (int[] offset : net.minecraft.world.entity.vehicle.DismountHelper.offsetsForDirection(getDirection())) {
				BlockPos pos = blockPosition().offset(offset[0] * radius, y, offset[1] * radius);
				if (!level().hasChunkAt(pos) || !level().getFluidState(pos).isEmpty()) continue;
				Vec3 safe = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
				if (safe != null && !getBoundingBox().intersects(passenger.getDimensions(Pose.STANDING).makeBoundingBox(safe))) return safe;
			}
		return super.getDismountLocationForPassenger(passenger);
	}
	@Override protected Vec3 getRiddenInput(Player player, Vec3 movement) {
		double forward = player.zza > 0 ? player.zza : player.zza * 0.25;
		double vertical = hasFlightLocomotion() || isSwimming() ? -Math.sin(player.getXRot() * Mth.DEG_TO_RAD) * forward
			+ (riderAscending ? 0.8 : 0) : 0;
		return new Vec3(player.xxa * 0.5, vertical,
			hasFlightLocomotion() || isSwimming() ? forward * Math.cos(player.getXRot() * Mth.DEG_TO_RAD) : forward);
	}
	@Override protected float getRiddenSpeed(Player player) {
		if (hasFlightLocomotion())
			return (float) getAttributeValue(Attributes.FLYING_SPEED);
		return (float) getAttributeValue(Attributes.MOVEMENT_SPEED)
			+ (equipment.camel() && player.isSprinting() ? 0.1f : 0);
	}
	@Override protected void tickRidden(Player player, Vec3 input) {
		if (isBlockAttached()) {
			lockBlockAttachmentRotation();
			return;
		}
		super.tickRidden(player, input);
		setYRot(BionicRiding.safeYaw(this, player.getYRot()));
		yBodyRot = getYRot();
		setXRot(BionicRiding.safePitch(this, player.getXRot() * 0.5f));
		yRotO = yBodyRot = yHeadRot = getYRot();
		if (!isControlledByLocalInstance()) return;
		if (riderJump > 0 && onGround()) {
			if (equipment.camel() && !getBionicTraits().hasStrongJump()) {
				Vec3 look = player.getLookAngle();
				setDeltaMovement(getDeltaMovement().add(look.x * 1.5 * riderJump, 0.4 * riderJump, look.z * 1.5 * riderJump));
			} else setDeltaMovement(getDeltaMovement().x, getJumpPower() * riderJump, getDeltaMovement().z);
			hasImpulse = true; riderJump = 0;
		} else if (!hasFlightLocomotion() && onGround() && input.horizontalDistanceSqr() > 0
			&& getLocomotionLegCount() < 2) jumpFromGround();
	}
	@Override public boolean canJump() { return hasControllingPassenger() && !hasFlightLocomotion() && !isSwimming()
		&& (equipment.jumpingDonor() || getBionicTraits().hasStrongJump()); }
	@Override public int getJumpCooldown() { return riderJumpCooldown; }
	@Override public void onPlayerJump(int strength) {
		if (!canJump() || riderJumpCooldown > 0) return;
		riderJump = strength >= 90 ? 1 : 0.4f + 0.4f * Mth.clamp(strength, 0, 90) / 90;
		if (equipment.camel() && !getBionicTraits().hasStrongJump()) riderJumpCooldown = 55;
	}
	@Override public void handleStartJump(int strength) { onPlayerJump(strength); }
	@Override public void handleStopJump() {}
	@Override protected void dropEquipment() { super.dropEquipment(); equipment.drop(); }
	@Override public boolean isShearable(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
		return production.shearable();
	}
	@Override public List<ItemStack> onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
		return production.shear();
	}
	private static final class BionicFloatGoal extends FloatGoal {
		private final SlimeBionicEntity bionic;

		private BionicFloatGoal(SlimeBionicEntity bionic) {
			super(bionic);
			this.bionic = bionic;
		}

		@Override public boolean canUse() {
			// Strider buoyancy handles lava without FloatGoal's repeated jump requests.
			if (bionic.isInLava() && bionic.getBionicTraits().has(BionicTrait.LAVA_WALK))
				return false;
			// Sinking bodies use vanilla water gravity instead of jumping to the surface.
			// Aquatic and flying controllers retain their own vertical movement.
			if (bionic.isInWater() && !bionic.isInLava()
				&& bionic.getBionicTraits().has(BionicTrait.WATER_SINKING))
				return false;
			// Swimming already steers vertically; keep fluid escape for other bodies.
			return (!bionic.organSwimEnabled || bionic.isInLava()) && super.canUse();
		}
	}

	/** Skeleton-style shade seeking, also usable by assembled bodies with non-ground navigation. */
	private static final class BionicFleeSunGoal extends FleeSunGoal {
		private final SlimeBionicEntity bionic;

		private BionicFleeSunGoal(SlimeBionicEntity bionic) {
			super(bionic, 1.0d);
			this.bionic = bionic;
		}

		private boolean needsShade() {
			return bionic.shouldAvoidSun() && bionic.level().isDay()
				&& bionic.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
				&& !bionic.isOrderedToSit() && !bionic.isVehicle() && !bionic.isPassenger()
				&& bionic.getTarget() == null;
		}

		@Override public boolean canUse() {
			// Seek cover before ignition; combat, sitting and rider control retain priority.
			return needsShade() && bionic.level().canSeeSky(bionic.blockPosition()) && setWantedPos();
		}

		@Override public boolean canContinueToUse() {
			return needsShade() && super.canContinueToUse();
		}

		@Override @Nullable protected Vec3 getHidePos() {
			for (int attempt = 0; attempt < 10; attempt++) {
				BlockPos pos = bionic.blockPosition().offset(bionic.random.nextInt(20) - 10,
					bionic.random.nextInt(6) - 3, bionic.random.nextInt(20) - 10);
				Vec3 target = Vec3.atBottomCenterOf(pos);
				// PathfinderMob has no Monster light preference; check shade and room for this body.
				if (!bionic.level().canSeeSky(pos) && bionic.getNavigation().isStableDestination(pos)
					&& bionic.level().noCollision(bionic, bionic.getBoundingBox().move(target.subtract(bionic.position()))))
					return target;
			}
			return null;
		}
	}

	private static final class BionicSitGoal extends Goal {
		private final SlimeBionicEntity bionic;

		private BionicSitGoal(SlimeBionicEntity bionic) {
			this.bionic = bionic;
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override public boolean canUse() {
			return bionic.orderedToSit && bionic.getBionicTraits().has(BionicTrait.TAMEABLE)
				&& bionic.onGround() && !bionic.isInWaterOrBubble();
		}
		@Override public boolean canContinueToUse() { return canUse(); }
		@Override public void start() { bionic.getNavigation().stop(); }
		@Override public void stop() { bionic.getNavigation().stop(); }
	}

	private static final class BionicLandStrollGoal extends WaterAvoidingRandomStrollGoal {
		private final SlimeBionicEntity bionic;
		private BionicLandStrollGoal(SlimeBionicEntity bionic) {
			super(bionic, 1.0d);
			this.bionic = bionic;
		}
		@Override public boolean canUse() {
			return !bionic.isSwimming() && super.canUse();
		}
		@Override public boolean canContinueToUse() {
			return !bionic.isSwimming() && super.canContinueToUse();
		}
	}

	private static final class BionicSwimStrollGoal extends RandomStrollGoal {
		private static final int[][] DISTANCES = {{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};
		private final SlimeBionicEntity bionic;
		private BionicSwimStrollGoal(SlimeBionicEntity bionic) {
			// Unlike land wandering, water activity must not stop after noActionTime >= 100.
			super(bionic, SWIMMING_IDLE_SPEED, 20, false);
			this.bionic = bionic;
		}
		@Override public boolean canUse() {
			return bionic.isSwimming() && super.canUse();
		}
		@Override public boolean canContinueToUse() {
			return bionic.isSwimming() && super.canContinueToUse();
		}
		@Nullable
		@Override protected Vec3 getPosition() {
			// AxolotlAi uses RandomStroll.swim: choose nearby water, then extend that
			// direction in tiers and retain the last water point before reaching shore.
			Vec3 target = null;
			for (int[] distance : DISTANCES) {
				Vec3 candidate = target == null
					? BehaviorUtils.getRandomSwimmablePos(bionic, distance[0], distance[1])
					: bionic.position().add(target.subtract(bionic.position()).normalize()
						.multiply(distance[0], distance[1], distance[0]));
				if (candidate == null || !bionic.level().getFluidState(BlockPos.containing(candidate))
					.is(FluidTags.WATER))
					break;
				target = candidate;
			}
			return target;
		}
	}

	private static final class BionicFollowOwnerGoal extends Goal {
		private final SlimeBionicEntity bionic;
		@Nullable private Player owner;
		private int recalculate;

		private BionicFollowOwnerGoal(SlimeBionicEntity bionic) {
			this.bionic = bionic;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override public boolean canUse() {
			if (bionic.orderedToSit || bionic.ownerId == null || !bionic.getBionicTraits().has(BionicTrait.TAMEABLE))
				return false;
			owner = bionic.getCompanionPlayer();
			return owner != null && !owner.isSpectator() && bionic.distanceToSqr(owner) > 100.0d;
		}
		@Override public boolean canContinueToUse() {
			return owner != null && owner.isAlive() && !bionic.orderedToSit
				&& (bionic.ownerId != null && bionic.getBionicTraits().has(BionicTrait.TAMEABLE))
				&& bionic.distanceToSqr(owner) > 4.0d && (!bionic.getNavigation().isDone()
					|| BionicWallClimbing.isInProgress(bionic.getNavigation()));
		}
		@Override public void stop() { owner = null; bionic.getNavigation().stop(); }
		@Override public void tick() {
			if (owner == null)
				return;
			bionic.getLookControl().setLookAt(owner, 10.0f, bionic.getMaxHeadXRot());
			if (--recalculate <= 0) {
				recalculate = adjustedTickDelay(10);
				if (bionic.distanceToSqr(owner) >= 144 && !bionic.isLeashed() && !bionic.isPassenger()) {
					for (int attempt = 0; attempt < 10; attempt++) {
						BlockPos pos = owner.blockPosition().offset(bionic.random.nextInt(7) - 3,
							bionic.random.nextInt(3) - 1, bionic.random.nextInt(7) - 3);
						if (Math.abs(pos.getX() - owner.getBlockX()) < 2 && Math.abs(pos.getZ() - owner.getBlockZ()) < 2) continue;
						if (net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(bionic, pos) == PathType.WALKABLE
							&& !(bionic.level().getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
							&& bionic.level().noCollision(bionic, bionic.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(bionic.position())))) {
							bionic.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, bionic.getYRot(), bionic.getXRot());
							bionic.getNavigation().stop(); break;
						}
					}
				} else bionic.getNavigation().moveTo(owner, bionic.isSwimming() ? SWIMMING_PURSUIT_SPEED : 1.2d);
			}
		}
	}

	/** Vanilla pursuit with independent, directional melee actions and persistent recovery deadlines. */
	private static class BionicAttackGoal extends MeleeAttackGoal {
		private final SlimeBionicEntity bionic;
		private final double pursuitSpeed;
		private final long[] rightArmRecovery = new long[SurgicalLimbType.SHOULDER.maxPerBody()];
		private final long[] leftArmRecovery = new long[SurgicalLimbType.SHOULDER.maxPerBody()];
		private int raiseArmTicks;
		private long nextAttackTick;
		private int currentAttackInterval = SurgicalCombatCalibration.ZOMBIE_ATTACK_INTERVAL;
		@Nullable
		private LivingEntity pendingAttackTarget;
		private long pendingAttackStartedAt;
		private int pendingAttackDuration;
		private int pendingContactStartTick;
		private boolean pendingImpactApplied;
		private double pendingDamageMultiplier = 1.0d;
		@Nullable
		private SurgicalAssembly.ArmAttackGeometry pendingArmGeometry;
		@Nullable
		private Vec3 pendingAim;
		private float pendingBodyYaw;
		private boolean pendingAimSynchronized;
		@Nullable
		private ArmUse lastArm;

		private BionicAttackGoal(SlimeBionicEntity bionic, double speedModifier, boolean followingTargetEvenIfNotSeen) {
			super(bionic, speedModifier, followingTargetEvenIfNotSeen);
			this.bionic = bionic;
			pursuitSpeed = speedModifier;
		}

		@Override
		public boolean canUse() {
			return validTarget(bionic.getTarget()) && (bionic.vibrationSense.lastHeardPosition(bionic.getTarget()) != null
				|| super.canUse() || bionic.canUseWallClimbing());
		}

		@Override
		public void start() {
			super.start();
			// MeleeAttackGoal can have no cached path (or one from a previous attempt)
			// when climbing is what allowed this goal to start.
			if (bionic.canUseWallClimbing() && bionic.getTarget() != null)
				bionic.getNavigation().moveTo(bionic.getTarget(), pursuitSpeed);
			raiseArmTicks = 0;
			clearPendingAttack();
			// Deadlines deliberately survive Goal restarts and target changes.
			bionic.combatFacingControlled = false;
		}

		@Override
		public void stop() {
			super.stop();
			clearPendingAttack();
			bionic.combatFacingControlled = false;
			bionic.setAggressive(false);
		}

		private boolean validTarget(@Nullable LivingEntity target) {
			return target != null && bionic.isAlive() && !bionic.isNoAi() && target.isAlive()
				&& !bionic.isVehicle() && !bionic.specialAttacks.busy()
				&& target.level() == bionic.level() && bionic.canAttack(target)
				&& !bionic.isAlliedTo(target) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = bionic.getTarget();
			return validTarget(target) && bionic.isWithinRestriction(target.blockPosition())
				&& (pendingAttackTarget == target || bionic.isWithinMeleeAttackRange(target)
					|| bionic.vibrationSense.lastHeardPosition(target) != null
					// SpiderAttackGoal keeps pursuing a valid target after its ground path ends.
					|| bionic.canUseWallClimbing() || super.canContinueToUse());
		}

		@Override
		protected void checkAndPerformAttack(LivingEntity target) {
			// Vanilla canPerformAttack() requires eye-to-eye sensing before our complete-AABB arm
			// checks run. Use only its timing/range portions; per-arm block visibility is checked from
			// the shoulder to the nearest target-box point in beginAttack() and again on contact.
			if (pendingAttackTarget != null || !validTarget(target) || !isTimeToAttack()
				|| !bionic.isWithinMeleeAttackRange(target))
				return;
			AttackStart attack = bionic.beginAttack(target, rightArmRecovery, leftArmRecovery, lastArm);
			if (attack == null)
				return;
			long now = bionic.level().getGameTime();
			nextAttackTick = now + attack.globalAttackInterval();
			currentAttackInterval = attack.globalAttackInterval();
			bionic.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			pendingAttackDuration = attack.actionDuration();
			pendingContactStartTick = attack.contactStartTick();
			pendingArmGeometry = attack.arm();
			pendingAim = attack.aim();
			pendingBodyYaw = bionic.combatBodyYaw();
			pendingDamageMultiplier = attack.damageMultiplier();
			pendingAttackTarget = target;
			pendingAttackStartedAt = now;
			pendingImpactApplied = false;
			pendingAimSynchronized = false;
			long[] recovery = attack.left() ? leftArmRecovery : rightArmRecovery;
			recovery[attack.slot()] = now + attack.armRecovery();
			lastArm = new ArmUse(attack.left(), attack.slot());
		}

		private void advancePendingAttack() {
			LivingEntity target = pendingAttackTarget;
			if (!validTarget(target) || target != bionic.getTarget()
				|| pendingArmGeometry == null || pendingAim == null) {
				clearPendingAttack();
				return;
			}
			long elapsed = bionic.level().getGameTime() - pendingAttackStartedAt;
			if (elapsed < 0 || elapsed >= pendingAttackDuration) {
				clearPendingAttack();
				return;
			}
			int pendingAttackElapsed = (int) elapsed;
			int activeStart = pendingContactStartTick;
			// The final preparation tick commits both the aim and shoulder orientation. Neither
			// navigation nor model/body smoothing can sweep the strike around after this point.
			if (pendingAttackElapsed < activeStart) {
				turnBodyToward(target);
				pendingBodyYaw = bionic.combatBodyYaw();
				trackAim(target);
				bionic.synchronizeAttackAim(pendingAim, pendingBodyYaw, pendingAttackDuration - pendingAttackElapsed);
			}
			if (!pendingAimSynchronized && pendingAttackElapsed >= activeStart) {
				pendingAimSynchronized = true;
				bionic.synchronizeAttackAim(pendingAim, pendingBodyYaw, pendingAttackDuration - pendingAttackElapsed);
			}
			if (!pendingImpactApplied && SlimeBionicCombat.isContactTick(pendingAttackElapsed,
				pendingContactStartTick)
				&& SlimeBionicCombat.intersects(target.getBoundingBox(), bionic.position(), pendingBodyYaw,
					pendingAim, pendingArmGeometry)
				&& bionic.hasAttackLineOfSight(target, pendingArmGeometry, pendingBodyYaw)) {
				// Contact consumes the swing even when a shield, hurt immunity or an event blocks damage.
				pendingImpactApplied = true;
				bionic.performAttackDamage(target, pendingDamageMultiplier);
			}
		}

		private void turnBodyToward(LivingEntity target) {
			float current = bionic.combatBodyYaw();
			Vec3 direction = target.getBoundingBox().getCenter().subtract(bionic.position());
			float desired = direction.horizontalDistanceSqr() < 1.0e-8d ? current : SlimeBionicCombat.yaw(direction);
			bionic.combatFacingYaw = Mth.approachDegrees(current, desired,
				SlimeBionicCombat.BODY_TURN_DEGREES);
			bionic.combatFacingControlled = true;
			bionic.applyCombatFacing();
		}

		private void trackAim(LivingEntity target) {
			Vec3 origin = SlimeBionicCombat.worldOrigin(bionic.position(), pendingBodyYaw, pendingArmGeometry);
			Vec3 desired = SlimeBionicCombat.constrainAim(
				SlimeBionicCombat.aimAt(target.getBoundingBox(), origin, pendingBodyYaw), pendingBodyYaw, pendingArmGeometry);
			pendingAim = SlimeBionicCombat.constrainAim(SlimeBionicCombat.approachAim(
				pendingAim, desired, SlimeBionicCombat.AIM_TRACKING_DEGREES), pendingBodyYaw, pendingArmGeometry);
		}

		private void clearPendingAttack() {
			boolean wasPending = pendingAttackTarget != null;
			pendingAttackTarget = null;
			pendingAttackStartedAt = 0L;
			pendingAttackDuration = 0;
			pendingContactStartTick = 0;
			pendingImpactApplied = false;
			pendingArmGeometry = null;
			pendingAim = null;
			pendingAimSynchronized = false;
			pendingDamageMultiplier = 1.0d;
			bionic.attackActionTick = 0;
			if (wasPending)
				CBPackets.sendToTrackingEntity(SlimeBionicAttackActionPacket.aim(bionic), bionic);
		}

		@Override
		protected boolean isTimeToAttack() {
			return bionic.level().getGameTime() >= nextAttackTick;
		}

		@Override
		protected int getTicksUntilNextAttack() {
			return (int) Math.max(0L, nextAttackTick - bionic.level().getGameTime());
		}

		@Override
		protected int getAttackInterval() {
			return currentAttackInterval;
		}

		@Override
		public void tick() {
			LivingEntity target = bionic.getTarget();
			if (!validTarget(target)) {
				clearPendingAttack();
				bionic.combatFacingControlled = false;
				bionic.getNavigation().stop();
				return;
			}
			if (bionic.getBionicTraits().has(BionicTrait.VIBRATION_SENSE) && !bionic.hasLineOfSight(target)) {
				clearPendingAttack();
				bionic.combatFacingControlled = false;
				Vec3 heard = bionic.vibrationSense.lastHeardPosition(target);
				if (heard != null) {
					bionic.getLookControl().setLookAt(heard.x, heard.y, heard.z);
					bionic.getNavigation().moveTo(heard.x, heard.y, heard.z,
						bionic.isSwimming() ? SWIMMING_PURSUIT_SPEED : 1.0d);
				} else bionic.getNavigation().stop();
				return;
			}
			if (pendingAttackTarget != null && pendingAttackTarget != target)
				clearPendingAttack();
			if (pendingAttackTarget != null) {
				advancePendingAttack();
			} else if (bionic.withinCombatEnvelope(target.getBoundingBox().inflate(0.5d))) {
				turnBodyToward(target);
			} else {
				bionic.combatFacingControlled = false;
			}
			super.tick();
			if (bionic.isSwimming())
				bionic.getNavigation().setSpeedModifier(SWIMMING_PURSUIT_SPEED);
			if (pendingAttackTarget != null && bionic.shouldPauseAttackMovement())
				bionic.getNavigation().stop();
			raiseArmTicks++;
			bionic.setAggressive(pendingAttackTarget != null
				|| raiseArmTicks >= 5 && getTicksUntilNextAttack() < getAttackInterval() / 2);
		}
	}
}

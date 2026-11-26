package com.nobodiiiii.createbiotech.entity;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.network.chat.Component;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import com.nobodiiiii.createbiotech.entity.trait.BionicGameplayRuntime;
import com.nobodiiiii.createbiotech.api.bionic.BionicGameplayEvent;
import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;

import java.util.List;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.trait.BionicVibrationSense;
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
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.ai.BionicDisposition;
import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligence;
import com.nobodiiiii.createbiotech.entity.ai.BionicMind;
import com.nobodiiiii.createbiotech.entity.ai.BionicFlightMovement;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicBodyRotationControl;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicGroundNavigation;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicMoveControl;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.entity.trait.BionicBehaviorRunner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/** A real entity whose visible body and locomotion are supplied by a surgical assembly. */
public class SlimeBionicEntity extends PathfinderMob implements net.minecraft.world.entity.Saddleable,
	net.minecraft.world.entity.PlayerRideableJumping, net.minecraft.world.entity.HasCustomInventoryScreen,
	net.neoforged.neoforge.common.IShearable,
	net.minecraft.world.entity.monster.RangedAttackMob, IEntityWithComplexSpawn {
	private static final float MAX_COLLISION_WIDTH = 2.0f;
	private static final float MAX_COLLISION_HEIGHT = 8.0f;
	static final float MULTIPART_THRESHOLD = 8.0f;
	private static final int MAX_HIT_PARTS = SurgicalAssembly.MAX_HITBOX_LIMBS + 2;
	private static final String ASSEMBLY_TAG = "SurgicalAssembly";
	public static final String PENDING_GROWTH_TAG = "PendingDevourGrowth";
	public static final String QUEUED_PREY_TAG = "QueuedDevourPrey";
	private static final String SOURCE_FORM_TAG = "BionicSourceForm";
	private static final String MOISTURE_TAG = "BionicMoisture";
	private static final String DRY_AIR_TAG = "BionicDryAir";
	private static final String OWNER_TAG = "BionicOwner";
	private static final String TRUSTED_TAG = "BionicTrustedPlayer";
	private static final String SHELL_GUARD_TAG = "BionicShellGuard";
	private static final String ORDERED_TO_SIT_TAG = "BionicOrderedToSit";
	static final int MAX_MOISTURE = 2400;
	static final double SWIMMING_IDLE_SPEED = 0.5d;
	static final double SWIMMING_PURSUIT_SPEED = 0.6d;
	static final ResourceLocation ANATOMICAL_ATTACK_MODIFIER_ID =
		CreateBiotech.asResource("anatomical_attack");
	private static final EntityDataAccessor<CompoundTag> ASSEMBLY = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.COMPOUND_TAG);
	private static final EntityDataAccessor<CompoundTag> PENDING_GROWTH = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.COMPOUND_TAG);
	private static final EntityDataAccessor<Integer> GROWTH_OBSERVER = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.INT);
	@Nullable private CompoundTag pendingGrowthData;
	@Nullable private SurgicalAssembly pendingGrowth;
	@Nullable private DevourGrowthQueue growthQueue;
	private long observerSince;

	private static final EntityDataAccessor<Optional<UUID>> OWNER_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	static final EntityDataAccessor<Optional<UUID>> DEVOURED_PLAYER = SynchedEntityData.defineId(
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
	long baseStatsRevision = Long.MIN_VALUE;
	private long traitStatsRevision = Long.MIN_VALUE;
	final BionicVibrationSense vibrationSense = new BionicVibrationSense(this);
	private final BionicBehaviorRunner extensionBehaviors = new BionicBehaviorRunner(this);
	private BionicTraitSet bionicTraits = BionicTraitSet.EMPTY;
	/** The mind derived from {@link #mindTraits}, resolved together with that trait set. */
	@Nullable
	private BionicMind mind;
	@Nullable
	private BionicTraitSet mindTraits;
	private final BionicHunting hunting = new BionicHunting(this);
	final BionicBlockAttachment blockAttachment = new BionicBlockAttachment(this);
	private static final EntityDataAccessor<Optional<BlockPos>> ATTACH_CELL_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Direction> ATTACH_FACE_DATA = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.DIRECTION);
	final com.nobodiiiii.createbiotech.entity.trait.BionicEquipment equipment = new com.nobodiiiii.createbiotech.entity.trait.BionicEquipment(this);
	@Nullable private Vec3 interactionHit;
	private final com.nobodiiiii.createbiotech.entity.trait.BionicProduction production = new com.nobodiiiii.createbiotech.entity.trait.BionicProduction(this);
	final com.nobodiiiii.createbiotech.entity.trait.BionicSpecialAttacks specialAttacks = new com.nobodiiiii.createbiotech.entity.trait.BionicSpecialAttacks(this);
	private static final EntityDataAccessor<CompoundTag> EQUIPMENT_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.COMPOUND_TAG);
	private static final EntityDataAccessor<Integer> PRODUCTION_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> POLLEN_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.BOOLEAN);
	static final EntityDataAccessor<Integer> SHELL_STATE_DATA = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> SPECIAL_STARTED = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> SPECIAL_ACTION = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<org.joml.Vector3f> SPECIAL_ORIGIN = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> SPECIAL_TARGET = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> GUARDIAN_ATTACK_DURATION = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	private float riderJump;
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
		if (target != null && DevourAttackGoal.protectedPrey(target) && (wildSlimeSize() > 0 || DevourAttackGoal.size(this) > 0)) target = null;
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
	boolean devourGroundAttack;
	private int walkingJumpDelay;
	private int replacedDevourerId;
	DevourAttackGoal devour;
	WildDevourerSlime wildSlime;
	private static final EntityDataAccessor<Integer> WILD_SLIME_SIZE = SynchedEntityData.defineId(SlimeBionicEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> DEVOUR_CARRIER = SynchedEntityData.defineId(
		SlimeBionicEntity.class, EntityDataSerializers.INT);

	boolean organSwimEnabled;
	private boolean lavaWalkEnabled;
	private float baseWaterPathMalus = Float.NaN;
	int moisture = -1;
	int dryAir = Integer.MIN_VALUE;
	int shellGuardTicks;
	int shellStateTicks;
	private int shellPeekSequence;
	int shellPeekTicks;
	boolean shellDangerWasAround;
	net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState shellState = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.IDLE;
	public int teleportPursuitTicks;
	public boolean isBionicWing(SurgicalAssembly.ArmAttackGeometry arm) {
		var assembly = getAssembly();
		if (assembly == null || arm.sourceId() < 0) return false;
		var chain = assembly.rotatingGroup(arm.sourceId(), arm.cubeId());
		return chain.stream().anyMatch(getBionicTraits().wingMembers()::contains);
	}
	@Nullable
	UUID ownerId;
	boolean orderedToSit;
	int lastOwnerHurtTimestamp;
	int lastOwnerAttackTimestamp;
	@Nullable
	SurgicalAssembly clientBoundsAssembly;
	@Nullable
	SurgicalAssembly.BodyBounds clientBodyBounds;
	@Nullable
	SurgicalAssembly.HitboxGeometry clientHitboxGeometry;
	double clientBodyVolume = Double.NaN;
	final SlimeBionicHitPart[] hitParts;
	SlimeBionicHitPart[] registeredHitParts;
	private int attackAnimationTick;
	private int attackAnimationDuration = SlimeBionicAttackTiming.PLAYBACK_TICKS;
	private boolean attackAnimationLeft;
	private int attackAnimationArmSlot;
	private boolean attackAnimationWeapon;
	int attackActionTick;
	private int attackActionSnapshotTick;
	int attackActionInterval = SurgicalCombatCalibration.ZOMBIE_ATTACK_INTERVAL;
	int attackActionDuration = SlimeBionicCombat.duration(attackActionInterval);
	boolean attackActionLeft;
	int attackActionArmSlot;
	boolean attackActionWeapon;
	boolean attackActionHasElbow;
	int attackActionSequence;
	float attackAimYaw;
	float attackAimPitch;
	float attackBodyYaw;
	boolean combatFacingControlled;
	float combatFacingYaw;

	public SlimeBionicEntity(EntityType<? extends SlimeBionicEntity> type, Level level) {
		super(type, level);
		if (wildSlime == null) wildSlime = new WildDevourerSlime(this); // Client mobs do not register AI goals.
		hitParts = new SlimeBionicHitPart[MAX_HIT_PARTS];
		for (int index = 0; index < hitParts.length; index++)
			hitParts[index] = new SlimeBionicHitPart(this);
		// Reserve stable multipart ids before synchronized assembly data arrives.
		registeredHitParts = hitParts;
		// Match the Ender Dragon: reserve one consecutive id block and keep every cached part stable.
		setId(ENTITY_COUNTER.getAndAdd(hitParts.length + 1) + 1);
		moveControl = new SlimeBionicMoveControl(this);
		setPersistenceRequired();
		BionicBodyGeometry.updateHitParts(this);
	}

	@Override
	public int getMaxHeadXRot() { return wildSlimeSize() > 0 ? 0 : super.getMaxHeadXRot(); }

	@Override
	public void jumpFromGround() {
		if (wildSlimeSize() == 0) {
			super.jumpFromGround();
			return;
		}
		var motion = getDeltaMovement();
		setDeltaMovement(motion.x, getJumpPower(), motion.z);
		hasImpulse = true;
	}

	@Override
	protected boolean shouldDespawnInPeaceful() { return wildSlimeSize() > 0 || super.shouldDespawnInPeaceful(); }

	@Override
	public MobCategory getClassification(boolean forSpawnCount) {
		return wildSlimeSize() > 0 ? MobCategory.MONSTER : super.getClassification(forSpawnCount);
	}

	@Override
	protected Component getTypeName() {
		if (wildSlimeSize() > 0) return CBEntityTypes.DEVOURER.get().getDescription();
		if (entityData != null && !SlimeMimicHandler.isSlimeMimic(this))
			return Component.translatable("entity.create_biotech.slime_bionic.source_form");
		return super.getTypeName();
	}

	@Override
	public SoundSource getSoundSource() {
		return wildSlimeSize() > 0 ? SoundSource.HOSTILE : super.getSoundSource();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return wildSlimeSize() == 0 ? super.getHurtSound(source) : wildSlimeSize() == 1 ? SoundEvents.SLIME_HURT_SMALL : SoundEvents.SLIME_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return wildSlimeSize() == 0 ? super.getDeathSound() : wildSlimeSize() == 1 ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.SLIME_DEATH;
	}

	@Override
	protected float getSoundVolume() { return wildSlimeSize() > 0 ? 0.4f * wildSlimeSize() : super.getSoundVolume(); }

	public int wildSlimeSize() { return entityData == null ? 0 : entityData.get(WILD_SLIME_SIZE); }

	public double baseMaximumHealth(SurgicalAssembly assembly, BionicTraitSet traits) {
		if (Double.isFinite(traits.calibratedHealth())) return traits.calibratedHealth();
		int size = wildSlimeSize();
		return size > 0 ? Math.min(DevourerEntity.maxHealthForSize(size), BionicStat.MAX_HEALTH.maximum())
			: SurgicalHealthCalibration.maximumHealth(assembly.bodyVolume(), level());
	}

	public boolean isTinySlimeBody() {
		int wildSize = wildSlimeSize();
		if (wildSize > 0) return wildSize <= 1;
		SurgicalAssembly assembly = getAssembly();
		return assembly == null || assembly.equivalentSlimeSize() <= 1.0d;
	}

	public double baseGroundSpeed() {
		int wildSize = wildSlimeSize();
		if (wildSize > 0) return 0.2f + 0.1f * wildSize;
		SurgicalAssembly assembly = getAssembly();
		return assembly == null ? SurgicalGait.movementSpeed(null, level())
			: SurgicalGait.movementSpeed(assembly.bodyBounds(), assembly.equivalentSlimeSize(), level());
	}

	public double baseMeleeDamage() {
		int wildSize = wildSlimeSize();
		if (wildSize > 0) return wildSize;
		SurgicalAssembly assembly = getAssembly();
		double contactSize = assembly != null && usesContactAttack() ? assembly.equivalentSlimeSize() : -1.0d;
		return BionicStats.evaluate(BionicStat.ATTACK_DAMAGE, level(), java.util.Map.of(
			"volume", assembly == null ? 0.0d : assembly.bodyVolume(), "slime_size", contactSize));
	}

	public boolean usesWildSlimeMovement() { return wildSlime != null && wildSlime.active(); }

	/** Walking and devouring both advance this delay while they control a grounded body. */
	public boolean waitForWalkingJump() {
		if (walkingJumpDelay <= 0) return false;
		walkingJumpDelay--;
		return true;
	}

	public void resetWalkingJumpDelay(boolean fast) {
		int delay = 10 + getRandom().nextInt(20);
		walkingJumpDelay = fast ? delay / 3 : delay;
	}

	void resetDevourJumpDelay() {
		int wildSize = wildSlimeSize();
		if (wildSize > 0 || getLocomotionLegCount() < 2)
			resetWalkingJumpDelay(wildSize > 0 ? wildSize > 1 : isAggressive() && !isTinySlimeBody());
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
		BionicBodyGeometry.setRegisteredHitPartCount(this, Mth.clamp(packet.getData(), 0, MAX_HIT_PARTS));
	}

	void inheritDevourer(DevourerEntity previous) {
		walkingJumpDelay = previous.walkingJumpDelay();
		replacedDevourerId = previous.getId();
	}

	@Override
	public void sendPairingData(ServerPlayer player, Consumer<CustomPacketPayload> bundleBuilder) {
		if (replacedDevourerId != 0 && tickCount == 0) super.sendPairingData(player, bundleBuilder);
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(replacedDevourerId);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buffer) {
		if (!(level().getEntity(buffer.readVarInt()) instanceof DevourerEntity previous)) return;
		// The old body can still be interpolating its landing when the new body spawns.
		Vec3 serverPosition = getPositionCodec().getBase();
		setPos(previous.position());
		setOldPosAndRot();
		lerpTo(serverPosition.x, serverPosition.y, serverPosition.z, getYRot(), getXRot(), 3);
	}

	void checkBionicInsideBlocks() {
		checkInsideBlocks();
	}

	boolean isBionicSunBurnTick() {
		return isSunBurnTick();
	}

	AABB bionicAttackBoundingBox() {
		return getAttackBoundingBox();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes()
			.add(Attributes.MAX_HEALTH, SurgicalHealthCalibration.MAX_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, SurgicalGait.ZOMBIE_WALK_SPEED)
			.add(Attributes.ATTACK_DAMAGE, 3.0d)
			.add(Attributes.ARMOR, 0.0d)
			.add(Attributes.FLYING_SPEED, 0.0d)
			.add(Attributes.FOLLOW_RANGE, 35.0d)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.0d);
	}

	/** Disposition selects natural targets; assigned targets use the ordinary combat goals. */
	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new BionicGoalSet.BionicFloatGoal(this));
		goalSelector.addGoal(1, new BionicGoalSet.BionicSitGoal(this));
		goalSelector.addGoal(1, new Goal() {
			{ setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
			// A passive passenger must not suspend autonomous wandering.
			@Override public boolean canUse() { return hasControllingPassenger() || specialAttacks != null && specialAttacks.busy(); }
			@Override public boolean canContinueToUse() { return canUse(); }
			@Override public void start() { if (hasControllingPassenger() || specialAttacks.locksMovement()) getNavigation().stop(); }
			@Override public void tick() { if (hasControllingPassenger() || specialAttacks.locksMovement()) getNavigation().stop(); }
		});
		devour = new DevourAttackGoal(this);
		goalSelector.addGoal(1, devour);
		wildSlime = new WildDevourerSlime(this);
		goalSelector.addGoal(2, wildSlime);
		goalSelector.addGoal(2, new BionicBreakDoorGoal(this));
		goalSelector.addGoal(2, new BionicGoalSet.BionicAttackGoal(this, 1.0d, false));
		goalSelector.addGoal(2, new RestrictSunGoal(this) {
			@Override public boolean canUse() { return shouldAvoidSun() && super.canUse(); }
		});
		goalSelector.addGoal(3, new BionicGoalSet.BionicFleeSunGoal(this));
		goalSelector.addGoal(3, new com.nobodiiiii.createbiotech.entity.ai.BionicShoulderRidingGoal(this));
		goalSelector.addGoal(5, new BionicGoalSet.BionicFollowOwnerGoal(this));
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
		goalSelector.addGoal(7, new BionicGoalSet.BionicLandStrollGoal(this));
		goalSelector.addGoal(7, new BionicLowFlightGoal(this));
		goalSelector.addGoal(7, new BionicGoalSet.BionicSwimStrollGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new BionicPackAlertGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
			target -> DevourAttackGoal.isPrey(this, target, DevourAttackGoal.size(this))) {
			@Override public boolean canUse() {
				return !isOrderedToSit() && DevourAttackGoal.size(SlimeBionicEntity.this) > 0 && super.canUse();
			}
		});
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
		if (!isSwimming() && BionicLocomotionPolicy.getLocomotionLegCount(this) < 2) {
			walkAnimation.update(0.0f, 0.4f);
			return;
		}
		// Vanilla clamps movement * 4 to 1, so speeds above roughly 0.25 blocks/tick cannot raise
		// cadence. Preserve the full configured bionic range and let the renderer cap swing angle.
		float animationSpeed = Math.min(movement * 4.0f, SurgicalGait.maximumWalkAnimationSpeed(level()));
		walkAnimation.update(animationSpeed, 0.4f);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ASSEMBLY, new CompoundTag());
		builder.define(PENDING_GROWTH, new CompoundTag());
		builder.define(GROWTH_OBSERVER, -1);
		builder.define(WILD_SLIME_SIZE, 0);
		builder.define(OWNER_DATA, Optional.empty());
		builder.define(DEVOURED_PLAYER, Optional.empty());
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
		builder.define(DEVOUR_CARRIER, -1);
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

	/** A hidden snapshot used by the native-model measurement job. */
	@Nullable
	public SurgicalAssembly getPendingGrowth() {
		var tag = entityData.get(PENDING_GROWTH);
		if (tag != pendingGrowthData) {
			pendingGrowthData = tag;
			pendingGrowth = tag.isEmpty() ? null : SurgicalAssembly.loadCached(tag);
		}
		return pendingGrowth;
	}

	@Nullable
	public SurgicalAssembly getAssemblyForPlacement() {
		var pending = getPendingGrowth();
		var base = pending == null ? getAssembly() : pending;
		return base == null || growthQueue == null ? base : growthQueue.forPlacement(base);
	}

	boolean hasDevourCapacity() {
		var base = getPendingGrowth();
		if (base == null) base = getAssembly();
		return base != null && DevourGrowthQueue.hasCapacity(base, growthQueue == null ? 0 : growthQueue.size());
	}

	boolean queueGrowth(DevourGrowthQueue.Meal meal) {
		if (!hasDevourCapacity()) return false;
		if (growthQueue == null) growthQueue = new DevourGrowthQueue();
		growthQueue.add(meal);
		return true;
	}

	void setPendingGrowth(SurgicalAssembly growth, CompoundTag encoded) {
		entityData.set(PENDING_GROWTH, encoded);
		pendingGrowthData = entityData.get(PENDING_GROWTH);
		pendingGrowth = growth;
		if (level() instanceof ServerLevel level && isAddedToLevel()) selectGrowthObserver(level);
	}

	public int growthObserver() { return entityData.get(GROWTH_OBSERVER); }

	/** One tracking player supplies the renderer. Rotate after a timeout if another player is available. */
	private void selectGrowthObserver(ServerLevel level) {
		var watchers = level.getChunkSource().chunkMap.getPlayersWatching(this);
		int current = growthObserver();
		if (watchers.stream().anyMatch(p -> p.getId() == current)
			&& (watchers.size() == 1 || level.getGameTime() - observerSince < 1200)) return;
		int next = watchers.stream().mapToInt(Entity::getId).filter(id -> id > current).min()
			.orElseGet(() -> watchers.stream().mapToInt(Entity::getId).min().orElse(-1));
		entityData.set(GROWTH_OBSERVER, next);
		observerSince = level.getGameTime();
	}

	public void setAssembly(SurgicalAssembly assembly) {
		installAssembly(assembly, null, null);
	}

	public void installAssembly(SurgicalAssembly assembly, @Nullable CompoundTag preparedData, @Nullable BionicTraitSet preparedTraits) {
		installAssembly(assembly, preparedData, preparedTraits, false);
	}

	public void installDevouredAssembly(SurgicalAssembly assembly, CompoundTag preparedData, BionicTraitSet preparedTraits) {
		installAssembly(assembly, preparedData, preparedTraits, true);
	}

	/** Publish the complete body and its matching prepared trait set in the same server tick. */
	private void installAssembly(SurgicalAssembly assembly, @Nullable CompoundTag preparedData,
		@Nullable BionicTraitSet preparedTraits, boolean devouredGrowth) {
		if (assembly == null || !assembly.isReadyForEntity())
			throw new IllegalArgumentException("A bionic entity requires complete body geometry");
		boolean healForGrowth = devouredGrowth && !level().isClientSide;
		float previousMaximum = healForGrowth ? getMaxHealth() : 0;
		if (getPersistentData().getBoolean("WildDevourer")) {
			int size = Mth.clamp(assembly.sources().getFirst().profile().stableInt("Size") + 1, 1, 127);
			entityData.set(WILD_SLIME_SIZE, size);
			xpReward = size;
			if (!level().isClientSide && !hasCustomName()) setCustomName(getTypeName());
		}
		if (growthQueue != null) growthQueue.invalidate();
		if (!entityData.get(PENDING_GROWTH).isEmpty()) {
			entityData.set(PENDING_GROWTH, new CompoundTag());
			entityData.set(GROWTH_OBSERVER, -1);
		}
		CompoundTag encoded = preparedData == null ? assembly.save() : preparedData;
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
		BionicBodyGeometry.configureHitPartRegistration(this, assembly);
		clientBoundsAssembly = null;
		clientBodyBounds = null;
		clientHitboxGeometry = null;
		clientBodyVolume = Double.NaN;
		if (preparedTraits != null) {
			traitStatsRevision = BionicStats.revision(level());
			traitRevision = BionicTraitResolver.revision(level());
			traitAssembly = assembly;
			bionicTraits = preparedTraits;
			applyTraitEffects(assembly);
		}
		BionicAttributeRefresh.refreshBaseStats(this, assembly);
		getBionicTraits();
		refreshDimensions();
		BionicBodyGeometry.updateHitParts(this);
		if (healForGrowth) {
			float gainedHealth = getMaxHealth() - previousMaximum;
			if (gainedHealth > 0) heal(gainedHealth);
		}
		BionicGameplayRuntime.post(BionicGameplayRuntime.event(BionicGameplayEvent.Kind.ASSEMBLY, this,
			com.nobodiiiii.createbiotech.CreateBiotech.asResource("assembly")).after());
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
		if (!super.canAttack(target) || isAlliedTo(target) || DevouredPlayer.isInside(this, target))
			return false;
		if (orderedToSit || wildSlimeSize() > 0 && !DevourAttackGoal.isPrey(this, target, DevourAttackGoal.size(this)))
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
		BionicTraitResolver.Revision revision = BionicTraitResolver.revision(level());
		long statsRevision = BionicStats.revision(level());
		if (assembly != traitAssembly || !revision.equals(traitRevision) || statsRevision != traitStatsRevision) {
			traitStatsRevision = statsRevision;
			traitAssembly = assembly;
			traitRevision = revision;
			bionicTraits = BionicTraitResolver.resolve(assembly, level(), wildSlimeSize() > 0);
			applyTraitEffects(assembly);
		}
		return bionicTraits;
	}

	private void applyTraitEffects(@Nullable SurgicalAssembly assembly) {
		mind = BionicMind.fromTraits(assembly, bionicTraits);
		mindTraits = bionicTraits;
		// Publish the result before refreshing effects that may query this entity again.
		if (assembly != null) BionicAttributeRefresh.refreshMaximumHealth(this, assembly, bionicTraits);
		BionicAttributeRefresh.refreshNaturalArmor(this, bionicTraits);
		BionicAttributeRefresh.refreshKnockbackResistance(this, bionicTraits);
		BionicLocomotionPolicy.refreshFlyingSpeed(this, assembly, bionicTraits);
		refreshLocomotion(bionicTraits);
		if (!level().isClientSide) {
			// A trait reload can turn the last attacking arm into a wing, or back into an arm.
			if (assembly != null) getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(baseMeleeDamage());
			double range = BionicAttributeRefresh.anatomicalBaseStat(this, BionicStat.FOLLOW_RANGE);
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

	@Nullable
	public Player getBionicOwner() {
		return ownerId == null ? null : level().getPlayerByUUID(ownerId);
	}

	@Nullable
	Player getCompanionPlayer() {
		return getBionicOwner();
	}

	public boolean isOrderedToSit() { return orderedToSit; }
	@Nullable public UUID getBionicOwnerId() { return ownerId; }

	@Override
	public boolean isAlliedTo(Entity other) {
		return DevourAttackGoal.protectedPrey(other) && (wildSlimeSize() > 0 || DevourAttackGoal.size(this) > 0)
			|| ownerId != null && ownerId.equals(other.getUUID())
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
		var event = BionicGameplayRuntime.post(BionicGameplayRuntime.event(BionicGameplayEvent.Kind.INTERACT, this,
			com.nobodiiiii.createbiotech.CreateBiotech.asResource("interact")).interaction(player, hand, player.getItemInHand(hand)));
		if (event.isCancelled()) return InteractionResult.CONSUME;
		InteractionResult result = event.getResult() == InteractionResult.PASS ? biotechInteract(player, hand) : event.getResult();
		// Player.interactOn falls back to the held item's handler even for FAIL.
		// A cancelled transaction must consume the click to prevent fallback saddle/shears actions.
		if (result == InteractionResult.FAIL) return InteractionResult.CONSUME;
		if (result.consumesAction()) { event.setResult(result); BionicGameplayRuntime.post(event.after()); }
		return result;
	}
	private InteractionResult biotechInteract(Player player, InteractionHand hand) {
		InteractionResult customResult = BionicGameplayRuntime.interact(this, player, hand);
		if (customResult != InteractionResult.PASS) return customResult;
		InteractionResult chestRemovalResult = equipment.removeChest(player, hand, interactionHit);
		if (chestRemovalResult != InteractionResult.PASS) return chestRemovalResult;
		if (player.isSecondaryUseActive() && equipment.openInventory(player, interactionHit))
			return InteractionResult.sidedSuccess(level().isClientSide);
		ItemStack held = player.getItemInHand(hand);
		InteractionResult productionResult = production.interact(player, hand);
		if (productionResult != InteractionResult.PASS) return productionResult;
		InteractionResult equipmentResult = equipment.interact(player, hand, interactionHit);
		if (equipmentResult != InteractionResult.PASS) return equipmentResult;
		InteractionResult shearingResult = production.interactShears(player, hand);
		if (shearingResult != InteractionResult.PASS) return shearingResult;
		BionicTraitSet traits = getBionicTraits();
		if (ownerId == null && BionicGameplayRuntime.tamingItem(this, held) && traits.isTameable()) {
			if (!level().isClientSide) {
				var tameEvent = BionicGameplayRuntime.post(BionicGameplayRuntime.event(BionicGameplayEvent.Kind.TAME, this,
					com.nobodiiiii.createbiotech.CreateBiotech.asResource("tame")).interaction(player, hand, held).amount(BionicGameplayRuntime.tamingChance(this, held)));
				if (tameEvent.isCancelled()) return InteractionResult.FAIL;
				if (!player.getAbilities().instabuild) held.shrink(1);
				boolean tamed = random.nextDouble() < Math.min(1, tameEvent.getAmount());
				if (tamed) {
					setBionicOwner(player.getUUID());
					setOrderedToSit(false);
					setTarget(null);
				}
				level().broadcastEntityEvent(this, (byte) (tamed ? 7 : 6));
				tameEvent.setAmount(tamed ? 1 : 0); BionicGameplayRuntime.post(tameEvent.after());
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

	/** An inherited special can yield to a crush within its launch range. */
	public boolean preferDevourToSpecial(@Nullable LivingEntity target) {
		if (devour == null) return false;
		if (devourGroundAttack) return devour.isCrushing();
		return target != null && DevourAttackGoal.size(this) > 0
			&& devour.replaceContact(target, devourAttackDamage());
	}

	public boolean isDevourGroundAttack() { return devourGroundAttack; }

	void setDevourGroundAttack(boolean active) {
		if (active == devourGroundAttack) return;
		devourGroundAttack = active;
		refreshLocomotion(getBionicTraits());
	}

	void refreshLocomotion(BionicTraitSet traits) {
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
		boolean swimming = BionicLocomotionPolicy.shouldUseSwimmingLocomotion(this, aquatic);
		boolean enabled = !devourGroundAttack && !swimming && (traits.has(BionicTrait.WINGLESS_FLIGHT)
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
		if (getAssembly() != null)
			BionicLocomotionPolicy.refreshMovementSpeed(this);
	}

	@Override
	public void updateSwimming() {
		BionicLocomotionPolicy.updateSwimming(this);
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

	boolean shouldAvoidSun() {
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
		return !getBionicTraits().isImmuneTo(effectId)
			&& (extensionBehaviors == null || extensionBehaviors.allowsEffect(effectId)) && super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier,
		net.minecraft.world.damagesource.DamageSource source) {
		BionicTraitSet traits = getBionicTraits();
		if (traits.has(BionicTrait.FALL_DAMAGE_IMMUNE)
			|| traits.has(BionicTrait.WINGLESS_FLIGHT)
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
		return getBionicTraits().has(BionicTrait.WALL_CLIMB) && !isSwimming() && !BionicLocomotionPolicy.hasFlightLocomotion(this);
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
		if (BionicLocomotionPolicy.hasFlightLocomotion(this)) {
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
		if (extensionBehaviors != null) {
			amount = extensionBehaviors.incomingDamage(source, amount);
			if (amount <= 0) return false;
		}
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
		return BionicLocomotionPolicy.getLocomotionLegCount(this);
	}

	/** Whether the currently installed movement controller makes this body travel by hopping. */
	public boolean usesHopLocomotion() {
		return BionicLocomotionPolicy.usesHopLocomotion(this);
	}

	/** Applies the renderer's exact visible envelope on the client, including slime-shell inflation. */
	public void setClientBodyGeometry(SurgicalAssembly assembly, SurgicalAssembly.BodyBounds bounds,
		SurgicalAssembly.HitboxGeometry hitboxGeometry, double bodyVolume) {
		BionicBodyGeometry.setClientBodyGeometry(this, assembly, bounds, hitboxGeometry, bodyVolume);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		if (wildSlimeSize() > 0) return EntityType.SLIME.getDimensions().scale(wildSlimeSize());
		SurgicalAssembly.BodyBounds bounds = BionicBodyGeometry.authoritativeBodyBounds(this);
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
		return BionicBodyGeometry.authoritativeBodyBounds(this);
	}

	/** Render-only envelope: this client's own measurement when it has one, else the shared bounds. */
	@Nullable
	SurgicalAssembly.BodyBounds renderBodyBounds() {
		return BionicBodyGeometry.renderBodyBounds(this);
	}

	@Override
	public boolean isWithinMeleeAttackRange(LivingEntity target) {
		return BionicMeleeController.usesContactAttack(this) ? super.isWithinMeleeAttackRange(target)
			: BionicMeleeController.withinCombatEnvelope(this, target.getBoundingBox());
	}

	/** A body with no attacking arms, including wing-only bodies, fights by slime-like contact. */
	public boolean usesContactAttack() {
		return BionicMeleeController.usesContactAttack(this);
	}

	@Override
	public void push(Entity other) {
		if (devour != null && devour.pressing(other)) return;
		super.push(other);
		if (wildSlimeSize() == 0 || other instanceof net.minecraft.world.entity.animal.IronGolem) dealContactDamage(other);
	}

	@Override
	protected void doPush(Entity other) {
		if (devour != null && devour.pressing(other)) return;
		super.doPush(other);
		if (wildSlimeSize() == 0 || !(other instanceof Player) && !(other instanceof net.minecraft.world.entity.animal.IronGolem))
			dealContactDamage(other);
	}

	@Override
	public void playerTouch(Player player) {
		super.playerTouch(player);
		dealContactDamage(player);
	}

	/** Slime.dealDamage: immediate contact damage, with repeated hits governed by hurt immunity. */
	private void dealContactDamage(Entity other) {
		if (!(other instanceof LivingEntity target) || wildSlimeSize() == 0 && target != getTarget()
			|| !(level() instanceof ServerLevel serverLevel) || !isEffectiveAi()
			|| !BionicMeleeController.usesContactAttack(this) || !BionicMeleeController.isValidMeleeTarget(this, target)
			|| !super.isWithinMeleeAttackRange(target) || !hasLineOfSight(target))
			return;
		int devourSize = DevourAttackGoal.size(this);
		boolean prey = DevourAttackGoal.isPrey(this, target, devourSize);
		boolean canDevour = devourSize > 0 && DevourGrowth.canDevour(this);
		float damage = (float) getBionicTraits().slimeContactDamage(prey && canDevour
			? devourCarrierDamage() : getAttributeValue(Attributes.ATTACK_DAMAGE));
		if (prey && (canDevour || target instanceof Player) && devour.replaceContact(target, damage)) return;
		if (isTinySlimeBody() && (!prey || canDevour)) return;
		var source = damageSources().mobAttack(this);
		if (target.hurt(source, damage)) {
			playSound(SoundEvents.SLIME_ATTACK, 1.0f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
			EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
			if (canDevour && target.isAlive()) afterDevourHit(target);
			else specialAttacks.afterMelee();
		}
	}

	/** Movement and visual body smoothing follow the server's combat facing, never the reverse. */
	public boolean applyCombatFacing() {
		return BionicMeleeController.applyCombatFacing(this);
	}

	@Override
	public void aiStep() {
		setCanPickUpLoot(equipment.mainHandArm() != null && !isNoAi() && !isOrderedToSit() && !isVehicle());
		super.aiStep();
		if (bodyFlightEnabled)
			BionicMeleeController.applyCombatFacing(this);
		if (attackAnimationTick > 0)
			attackAnimationTick--;
		if (attackActionTick > 0)
			attackActionTick--;
	}

	/** Selects the best ready arm and snapshots gameplay and presentation state for one attack. */
	@Nullable
	AttackStart beginAttack(LivingEntity target, long[] rightRecovery, long[] leftRecovery,
		@Nullable ArmUse lastArm) {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		if (geometry == null || BionicMeleeController.usesContactAttack(this))
			return null;
		if (DevourAttackGoal.size(this) > 0 && devour.replaceContact(target, devourAttackDamage())) return null;
		ArmSelection selection = BionicMeleeController.selectArm(this, target, geometry, rightRecovery, leftRecovery, lastArm);
		if (selection == null)
			return null;

		ArmCandidate selected = selection.arm();
		SurgicalCombatCalibration.ArmCombatStats stats = SurgicalCombatCalibration.stats(selected.arm(), level());
		int recovery = SurgicalCombatCalibration.armRecovery(stats, level());
		int interval = SurgicalCombatCalibration.attackInterval(stats, selection.cadenceArms(), level());
		int actionDuration = BionicMeleeController.startAttackAction(this, selected, interval);
		return new AttackStart(actionDuration,
			SlimeBionicCombat.contactStartTick(interval, selected.arm().hasElbow(), selected.weapon()),
			interval, recovery, getBionicTraits().armDamageMultiplier(selected.arm(), stats.damageMultiplier()),
			selected.arm(), selected.aim(), selected.left(), selected.slot());
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (growthQueue != null) growthQueue.invalidate();
		if (extensionBehaviors != null && !level().isClientSide) extensionBehaviors.clear();
		super.remove(reason);
	}

	@Override
	public void tick() {
		if (level() instanceof ServerLevel level && isAlive()) {
			if (growthQueue != null && growthQueue.tick(this)) growthQueue = null;
			if (!entityData.get(PENDING_GROWTH).isEmpty() && tickCount % 20 == 0) selectGrowthObserver(level);
		}
		if (!level().isClientSide && baseStatsRevision != BionicStats.revision(level())) {
			SurgicalAssembly assembly = getAssembly();
			if (assembly != null) BionicAttributeRefresh.refreshBaseStats(this, assembly);
		}
		previousSelfDestructFuse = selfDestructFuse;
		selfDestructFuse = getProductionFlags() >> 2;
		blockAttachment.tick();
		if (!level().isClientSide) { hunting.tick(); specialAttacks.tick(); }
		if (!hasControllingPassenger()) { riderJump = 0; riderAscending = false; }
		super.tick();
		if (wildSlimeSize() > 0) wildSlime.animate(wildSlimeSize());
		// Spider updates and synchronizes wall contact after movement, for the next tick.
		if (!level().isClientSide)
			entityData.set(CLIMBING_DATA, horizontalCollision && canUseWallClimbing());
		BionicEnvironment.tickLavaWalking(this);
		if (!devourGroundAttack && getBionicTraits().has(BionicTrait.WING_SLOW_FALL) && !onGround() && !isInWaterOrBubble()
			&& !isInLava() && getDeltaMovement().y < 0) {
			setDeltaMovement(getDeltaMovement().multiply(1, getBionicTraits().parameter(
				BionicTrait.WING_SLOW_FALL, "fall_multiplier", 0.6d), 1));
			resetFallDistance();
		}
		if (!level().isClientSide) {
			BionicDefenses.tick(this);
			extensionBehaviors.tick();
			equipment.tick();
			production.tick();
		}
		if (!level().isClientSide) BionicEnvironment.tickShellDefense(this);
		if (!level().isClientSide)
			refreshLocomotion(getBionicTraits());
		vibrationSense.tick();
		BionicEnvironment.updateOwnerTarget(this);
		BionicEnvironment.tickHeadRespiration(this);
		BionicEnvironment.tickBodyEnvironment(this);
		lockBlockAttachmentRotation();
		if (isBlockAttached()) setBoundingBox(makeBoundingBox());
		BionicBodyGeometry.updateHitParts(this);
		if (devour != null) devour.swallowedPlayer.tick();
	}

	@Override
	public void updateDynamicGameEventListener(java.util.function.BiConsumer<net.minecraft.world.level.gameevent.DynamicGameEventListener<?>,
		net.minecraft.server.level.ServerLevel> consumer) {
		BionicEnvironment.updateDynamicGameEventListener(this, consumer);
	}

	@Override protected AABB makeBoundingBox() {
		if (isBlockAttached()) {
			SurgicalAssembly.HitboxGeometry geometry = BionicMeleeController.authoritativeHitboxGeometry(this);
			// Use the measured envelope, including its depth and offsets, rather than rotating
			// vanilla's upright square footprint. Hit parts use this same rest-to-world transform.
			if (geometry != null)
				return BionicBodyGeometry.worldBounds(this, geometry.requiresMultipart(MULTIPART_THRESHOLD) ? geometry.body() : geometry.overall());
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
		SurgicalAssembly.HitboxGeometry geometry = BionicMeleeController.renderHitboxGeometry(this);
		AABB bounds = geometry == null ? super.getBoundingBoxForCulling()
			: getBoundingBox().minmax(BionicBodyGeometry.worldBounds(this, geometry.overall()));
		AABB accessories = equipment.interactionBounds();
		return accessories == null ? bounds : bounds.minmax(accessories);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		if (super.shouldRenderAtSqrDistance(distance))
			return true;
		SurgicalAssembly.HitboxGeometry geometry = BionicMeleeController.renderHitboxGeometry(this);
		if (geometry == null)
			return false;
		double physicalSize = Math.max(getBoundingBox().getSize(), 1.0e-6d);
		double visualSize = Math.max(BionicBodyGeometry.worldBounds(this, geometry.overall()).getSize(), physicalSize);
		double scale = visualSize / physicalSize;
		return super.shouldRenderAtSqrDistance(distance / (scale * scale));
	}

	@Override
	public boolean isPickable() {
		SurgicalAssembly.HitboxGeometry geometry = BionicMeleeController.authoritativeHitboxGeometry(this);
		boolean multipartActive = registeredHitParts.length > 0 && geometry != null
			&& geometry.requiresMultipart(MULTIPART_THRESHOLD);
		return !multipartActive && super.isPickable();
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

	@Override
	protected float getKnockback(Entity target, net.minecraft.world.damagesource.DamageSource source) {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		SurgicalAssembly.ArmAttackGeometry arm = geometry == null ? null : geometry.arm(attackActionLeft, attackActionArmSlot);
		double inherited = arm == null || arm.sourceId() < 0 ? 0 : getBionicTraits().armValue(
			BionicTrait.ATTACK_KNOCKBACK, assembly.rotatingGroup(arm.sourceId(), arm.cubeId()));
		return super.getKnockback(target, source) + (float) inherited;
	}

	/** The correction follows the entire installed shoulder/elbow chain, including mixed donors. */
	public double armDamageOffset(@Nullable SurgicalAssembly.ArmAttackGeometry arm) {
		return BionicMeleeController.armDamageOffset(this, arm);
	}

	boolean doSelectedAttack(Entity target) {
		var selectedAssembly = getAssembly();
		var selectedGeometry = selectedAssembly == null ? null : selectedAssembly.attackGeometry();
		var selectedArm = selectedGeometry == null ? null : selectedGeometry.arm(attackActionLeft, attackActionArmSlot);
		if (selectedArm != null && isBionicWing(selectedArm)) return false;
		boolean hit;
		if (target instanceof LivingEntity victim && level() instanceof ServerLevel level
			&& DevourAttackGoal.isPrey(this, victim, DevourAttackGoal.size(this)) && DevourGrowth.canDevour(this)) {
			hit = hitWithDevouringArm(level, victim);
		} else {
			hit = super.doHurtTarget(target);
		}
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
		applyMeleeTraits(victim, attackingGroup, attackActionWeapon);
		return hit;
	}

	/** Mob's melee hit, with a finisher decision after applying the selected arm and weapon's damage. */
	private boolean hitWithDevouringArm(ServerLevel level, LivingEntity target) {
		var source = damageSources().mobAttack(this);
		float damage = EnchantmentHelper.modifyDamage(level, getWeaponItem(), target, source,
			(float) getAttributeValue(Attributes.ATTACK_DAMAGE));
		if (devour.replaceContact(target, damage) || !target.hurt(source, damage)) return false;

		float knockback = getKnockback(target, source);
		if (knockback > 0) {
			target.knockback(knockback * 0.5, Mth.sin(getYRot() * Mth.DEG_TO_RAD), -Mth.cos(getYRot() * Mth.DEG_TO_RAD));
			setDeltaMovement(getDeltaMovement().multiply(0.6, 1, 0.6));
		}
		EnchantmentHelper.doPostAttackEffects(level, target, source);
		setLastHurtMob(target);
		playAttackSound();
		return true;
	}

	private double devourCarrierDamage() {
		return Math.max(0, getAttributeValue(Attributes.ATTACK_DAMAGE)
			+ getBionicTraits().devourDamageOffset());
	}

	float devourAttackDamage() {
		double damage = devourCarrierDamage();
		return (float) (usesContactAttack() ? getBionicTraits().slimeContactDamage(damage) : damage);
	}

	void afterDevourHit(LivingEntity victim) {
		specialAttacks.afterMelee();
		var members = List.copyOf(getBionicTraits().members(BionicTrait.DEVOUR));
		double knockback = getBionicTraits().armValue(BionicTrait.ATTACK_KNOCKBACK, members);
		if (knockback > 0) victim.knockback(knockback * 0.5, getX() - victim.getX(), getZ() - victim.getZ());
		applyMeleeTraits(victim, members, false);
	}

	private void applyMeleeTraits(LivingEntity victim, List<SurgicalAssembly.CombinationMember> attackingGroup, boolean armed) {
		extensionBehaviors.melee(victim, attackingGroup);
		BionicTraitSet traits = getBionicTraits();
		if (attackingGroup.stream().anyMatch(traits.members(BionicTrait.LAUNCH_STRIKE)::contains)) {
			double resistance = Math.max(0, 1 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			victim.setDeltaMovement(victim.getDeltaMovement().add(0,
				traits.parameter(BionicTrait.LAUNCH_STRIKE, "vertical_boost", 0.4d) * resistance, 0));
			victim.hurtMarked = true;
		}
		for (var entry : traits.result(BionicTrait.EFFECT_ATTACK).attackEffects().entrySet()) {
			var effect = entry.getKey();
			if (effect.unarmedOnly() && armed
				|| attackingGroup.stream().noneMatch(entry.getValue()::contains)
				|| random.nextDouble() >= effect.chance())
				continue;
			net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect())
				.ifPresent(holder -> victim.addEffect(new MobEffectInstance(holder,
					effect.durationTicks(), effect.amplifier()), this));
		}
	}

	@Override
	public ItemStack getWeaponItem() {
		return attackActionTick > 0 ? BionicMeleeController.selectedAttackWeapon(this) : super.getWeaponItem();
	}

	record AttackStart(int actionDuration, int contactStartTick,
		int globalAttackInterval, int armRecovery,
		double damageMultiplier, SurgicalAssembly.ArmAttackGeometry arm, Vec3 aim,
		boolean left, int slot) {}

	record ArmCandidate(boolean left, int slot, boolean weapon,
		SurgicalAssembly.ArmAttackGeometry arm, Vec3 aim, double score) {}

	record ArmSelection(ArmCandidate arm, int cadenceArms) {}

	record ArmUse(boolean left, int slot) {}

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
		attackActionInterval = Mth.clamp(interval, 1, 1200);
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
		if ((PENDING_GROWTH.equals(key) || GROWTH_OBSERVER.equals(key)) && level().isClientSide && isAddedToLevel())
			com.nobodiiiii.createbiotech.content.surgery.client.DevouredBodyMeasurements.schedule(this);
		if (WILD_SLIME_SIZE.equals(key)) {
			invalidateTraits();
			refreshDimensions();
		}
		if (ATTACH_CELL_DATA.equals(key) || ATTACH_FACE_DATA.equals(key)) {
			lockBlockAttachmentRotation();
			setBoundingBox(makeBoundingBox());
			BionicBodyGeometry.updateHitParts(this);
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
			BionicBodyGeometry.updateHitParts(this);
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
		var pending = entityData.get(PENDING_GROWTH);
		if (!pending.isEmpty()) tag.put(PENDING_GROWTH_TAG, pending.copy());
		if (growthQueue != null) tag.put(QUEUED_PREY_TAG, growthQueue.save());
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
		SurgicalAssembly pending = tag.contains(PENDING_GROWTH_TAG, Tag.TAG_COMPOUND)
			? SurgicalAssembly.loadCached(tag.getCompound(PENDING_GROWTH_TAG)) : null;
		if (pending == null && assembly.sources().stream().anyMatch(s -> s.profile().needsPreyTopology())) {
			// Older development saves kept the render proxies in the installed body.
			pending = assembly;
			assembly = DevourGrowth.installedPrefix(assembly);
		}
		double savedHealthBase = getAttributeBaseValue(Attributes.MAX_HEALTH);
		float savedMaxHealth = getMaxHealth();
		float savedHealth = getHealth();
		setAssembly(assembly);
		int size = wildSlimeSize();
		if (size > 0 && savedMaxHealth < getMaxHealth() && tag.contains("Health", Tag.TAG_ANY_NUMERIC)
			&& savedHealthBase == Math.max(1, size * size + getBionicTraits().value(BionicTrait.HEALTH_OFFSET)))
			setHealth(getMaxHealth() * (savedHealth / savedMaxHealth));
		if (pending != null && pending.isReadyForEntity()) setPendingGrowth(pending, pending.save());
		growthQueue = tag.contains(QUEUED_PREY_TAG, Tag.TAG_LIST)
			? DevourGrowthQueue.load(tag.getList(QUEUED_PREY_TAG, Tag.TAG_COMPOUND)) : null;
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
		if (source != null) level().playSound(null, this, net.minecraft.sounds.SoundEvents.HORSE_SADDLE, source, 0.5f, 1);
	}
	@Override protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && entityData.get(DEVOURED_PLAYER).isEmpty()
			&& !isOrderedToSit() && isSaddled() && isSaddleable()
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
		if (devour != null) devour.swallowedPlayer.onDismount(passenger);
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
		if (DevouredPlayer.positionInside(this, passenger, move)) return;
		var pose = BionicRiding.pose(this, passenger, 1, yBodyRot);
		if (pose == null) { super.positionRider(passenger, move); return; }
		Vec3 feet = pose.feet(passenger);
		move.accept(passenger, feet.x, feet.y, feet.z);
		passenger.setBoundingBox(pose.bounds(passenger));
	}
	@SuppressWarnings("deprecation") // Keep Minecraft 1.21.1's legacy landing-position semantics.
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
		Vec3 safe = BionicRiding.safeDismount(this, passenger);
		return safe != null ? safe : super.getDismountLocationForPassenger(passenger);
	}
	@Override protected Vec3 getRiddenInput(Player player, Vec3 movement) {
		double forward = player.zza > 0 ? player.zza : player.zza * 0.25;
		double vertical = BionicLocomotionPolicy.hasFlightLocomotion(this) || isSwimming() ? -Math.sin(player.getXRot() * Mth.DEG_TO_RAD) * forward
			+ (riderAscending ? 0.8 : 0) : 0;
		return new Vec3(player.xxa * 0.5, vertical,
			BionicLocomotionPolicy.hasFlightLocomotion(this) || isSwimming() ? forward * Math.cos(player.getXRot() * Mth.DEG_TO_RAD) : forward);
	}
	@Override protected float getRiddenSpeed(Player player) {
		if (BionicLocomotionPolicy.hasFlightLocomotion(this))
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
			setDeltaMovement(getDeltaMovement().x, getJumpPower() * riderJump, getDeltaMovement().z);
			hasImpulse = true; riderJump = 0;
		} else if (!BionicLocomotionPolicy.hasFlightLocomotion(this) && onGround() && input.horizontalDistanceSqr() > 0
			&& BionicLocomotionPolicy.getLocomotionLegCount(this) < 2) jumpFromGround();
	}
	@Override public boolean canJump() { return hasControllingPassenger() && !BionicLocomotionPolicy.hasFlightLocomotion(this) && !isSwimming()
		&& (equipment.jumpingDonor() || getBionicTraits().hasStrongJump()); }
	@Override public void onPlayerJump(int strength) {
		if (!canJump()) return;
		riderJump = strength >= 90 ? 1 : 0.4f + 0.4f * Mth.clamp(strength, 0, 90) / 90;
	}
	@Override public void handleStartJump(int strength) { onPlayerJump(strength); }
	@Override public void handleStopJump() {}
	@Override public boolean isShearable(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
		return production.shearable();
	}
	@Override public List<ItemStack> onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
		return production.shear();
	}
}

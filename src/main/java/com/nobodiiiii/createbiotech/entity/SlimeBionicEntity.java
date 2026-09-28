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
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeCompanionHandler;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.ai.BionicDisposition;
import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligence;
import com.nobodiiiii.createbiotech.entity.ai.BionicMind;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicBodyRotationControl;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicGroundNavigation;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicMoveControl;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.common.NeoForgeMod;

/** A real entity whose visible body and locomotion are supplied by a surgical assembly. */
public class SlimeBionicEntity extends PathfinderMob {
	private static final float MAX_COLLISION_WIDTH = 2.0f;
	private static final float MAX_COLLISION_HEIGHT = 8.0f;
	private static final float MULTIPART_THRESHOLD = 8.0f;
	private static final int MAX_HIT_PARTS = SurgicalAssembly.MAX_HITBOX_LIMBS + 1;
	private static final String ASSEMBLY_TAG = "SurgicalAssembly";
	private static final String SOURCE_FORM_TAG = "BionicSourceForm";
	private static final String MOISTURE_TAG = "BionicMoisture";
	private static final String DRY_AIR_TAG = "BionicDryAir";
	private static final String OWNER_TAG = "BionicOwner";
	private static final String TRUSTED_TAG = "BionicTrustedPlayer";
	private static final String SHELL_GUARD_TAG = "BionicShellGuard";
	private static final String ACTIVE_SPINES_TAG = "BionicActiveSpines";
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
	@Nullable
	private CompoundTag cachedAssemblyData;
	@Nullable
	private SurgicalAssembly cachedAssembly;
	@Nullable
	private SurgicalAssembly traitAssembly;
	@Nullable
	private BionicTraitResolver.Revision traitRevision;
	private final BionicVibrationSense vibrationSense = new BionicVibrationSense(this);
	private BionicTraitSet bionicTraits = BionicTraitSet.EMPTY;
	private boolean bodyFlightEnabled;
	private boolean organSwimEnabled;
	private float baseWaterPathMalus = Float.NaN;
	private int moisture = -1;
	private int dryAir = -1;
	private int shellGuardTicks;
	private int activeSpinesTicks;
	@Nullable
	private UUID ownerId;
	private boolean orderedToSit;
	@Nullable
	private LivingEntity ownerAssignedTarget;
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
			.add(Attributes.FLYING_SPEED, SurgicalGait.ZOMBIE_WALK_SPEED)
			.add(Attributes.FOLLOW_RANGE, 35.0d)
			.add(Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE);
	}

	/** Target acquisition and retaliation both respect the head-derived disposition in canAttack. */
	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new BionicFloatGoal(this));
		goalSelector.addGoal(1, new BionicSitGoal(this));
		goalSelector.addGoal(2, new BionicAttackGoal(this, 1.0d, false));
		goalSelector.addGoal(5, new BionicFollowOwnerGoal(this));
		goalSelector.addGoal(7, new BionicLandStrollGoal(this));
		goalSelector.addGoal(7, new BionicSwimStrollGoal(this));
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
		entityData.set(ASSEMBLY, encoded);
		cachedAssemblyData = encoded;
		cachedAssembly = assembly;
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
			SurgicalAssembly loaded = SurgicalAssembly.load(encoded);
			cachedAssembly = loaded != null && loaded.isReadyForEntity() ? loaded : null;
			invalidateTraits();
		}
		return cachedAssembly;
	}

	/** Current head-derived disposition and intelligence classification. */
	public BionicMind getMind() {
		return BionicMind.resolve(getAssembly(), level());
	}

	public BionicDisposition getDisposition() {
		return getMind().disposition();
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		if (!super.canAttack(target))
			return false;
		if (shellGuardTicks > 0 && getBionicTraits().has(BionicTrait.SHELL_DEFENSE)
			|| ownerId != null && ownerId.equals(target.getUUID())
			|| orderedToSit)
			return false;
		if (ownerId != null)
			return target == ownerAssignedTarget || target == getLastHurtByMob();
		// Shared by target goals and the pending arm strike's contact checks, so a changed head or
		// data-pack classification also stops an attack that was already in progress.
		return switch (getDisposition()) {
			case FRIENDLY -> false;
			case NEUTRAL -> target == getLastHurtByMob();
			case HOSTILE -> true;
		};
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
			// Publish the result before refreshing effects that may query this entity again.
			refreshNaturalArmor(bionicTraits);
			refreshKnockbackResistance(bionicTraits);
			refreshLocomotion(bionicTraits);
			if (!bionicTraits.has(BionicTrait.MOISTURE_DEPENDENT))
				moisture = -1;
			if (!bionicTraits.has(BionicTrait.DRY_SUFFOCATION))
				dryAir = -1;
			if (!level().isClientSide) {
				if (!bionicTraits.has(BionicTrait.TAMEABLE)) {
					setBionicOwner(null);
					setOrderedToSit(false);
				}
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
			|| ownerId != null && ownerId.equals(SlimeCompanionHandler.ownerOf(other))
			|| super.isAlliedTo(other);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!getBionicTraits().has(BionicTrait.TAMEABLE))
			return super.mobInteract(player, hand);
		if (ownerId == null && held.is(Items.SLIME_BALL)) {
			if (!level().isClientSide) {
				if (!player.getAbilities().instabuild)
					held.shrink(1);
				setBionicOwner(player.getUUID());
				setOrderedToSit(false);
				setTarget(null);
			}
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		if (ownerId != null && ownerId.equals(player.getUUID()) && held.isEmpty()) {
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

	private void refreshLocomotion(BionicTraitSet traits) {
		if (level().isClientSide)
			return;
		boolean aquatic = traits.has(BionicTrait.SWIM_SPECIALIST);
		boolean swimming = shouldUseSwimmingLocomotion(aquatic);
		boolean enabled = !swimming && (traits.has(BionicTrait.WINGLESS_FLIGHT)
			|| traits.has(BionicTrait.WING_FLIGHT));
		if (Float.isNaN(baseWaterPathMalus))
			baseWaterPathMalus = getPathfindingMalus(PathType.WATER);
		// Random swim targets are chosen before path creation: amphibious navigation alone
		// sets this too late, leaving DefaultRandomPos to reject every water candidate.
		setPathfindingMalus(PathType.WATER, (aquatic ? 0.0f : baseWaterPathMalus)
			+ (traits.has(BionicTrait.WATER_AVERSION) ? 16.0f : 0.0f));
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
			nextNavigation = new AmphibiousPathNavigation(this, level());
		} else if (!enabled && !aquatic && !(navigation instanceof SlimeBionicGroundNavigation)) {
			nextNavigation = new SlimeBionicGroundNavigation(this, level());
			nextNavigation.setCanFloat(true);
		}
		if (nextNavigation != navigation) {
			navigation.stop();
			navigation = nextNavigation;
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
	public boolean canStandOnFluid(FluidState state) {
		return state.is(FluidTags.LAVA)
			&& getBionicTraits().has(BionicTrait.LAVA_WALK)
			|| super.canStandOnFluid(state);
	}

	@Override
	public boolean onClimbable() {
		return horizontalCollision && getBionicTraits().has(BionicTrait.WALL_CLIMB)
			|| super.onClimbable();
	}

	@Override
	public void travel(Vec3 movement) {
		if (isSwimming())
			movement = movement.scale(1.35d);
		if (shellGuardTicks > 0 && getBionicTraits().has(BionicTrait.SHELL_DEFENSE))
			movement = movement.scale(0.25d);
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
		if (shellGuardTicks > 0 && traits.has(BionicTrait.SHELL_DEFENSE)
			&& !source.is(DamageTypeTags.BYPASSES_ARMOR))
			amount *= (float) traits.parameter(
				BionicTrait.SHELL_DEFENSE, "damage_multiplier", 0.5d);
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
		boolean hurt = super.hurt(source, amount);
		if (hurt && traits.has(BionicTrait.SHELL_DEFENSE))
			shellGuardTicks = (int) traits.parameter(
				BionicTrait.SHELL_DEFENSE, "duration_ticks", 80.0d);
		double retaliation = traits.strength(BionicTrait.CONTACT_RETALIATION);
		if (hurt && retaliation > 0.0d && !level().isClientSide
			&& !source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)
			&& !source.is(DamageTypes.THORNS)
			&& source.getDirectEntity() instanceof LivingEntity attacker
			&& attacker != this)
			attacker.hurt(damageSources().thorns(this), (float) (2.0d * retaliation));
		if (hurt && traits.has(BionicTrait.ACTIVE_SPINES))
			activeSpinesTicks = (int) traits.parameter(
				BionicTrait.ACTIVE_SPINES, "duration_ticks", 100.0d);
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
	public void jumpFromGround() {
		super.jumpFromGround();
		double bounce = getBionicTraits().strength(BionicTrait.BODY_BOUNCE);
		if (bounce > 0.0d)
			setDeltaMovement(getDeltaMovement().add(0.0d, 0.1d * bounce, 0.0d));
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
		if (geometry == null)
			return SlimeBionicCombat.withinStartEnvelope(targetBounds, position(), combatBodyYaw(),
				SlimeBionicCombat.fallbackArm(getBbWidth(), getBbHeight()));
		for (SurgicalAssembly.ArmAttackGeometry arm : geometry.arms())
			if (SlimeBionicCombat.withinStartEnvelope(targetBounds, position(), combatBodyYaw(), arm))
				return true;
		return false;
	}

	private float combatBodyYaw() {
		return combatFacingControlled ? combatFacingYaw : getYRot();
	}

	/** Movement and visual body smoothing follow the server's combat facing, never the reverse. */
	public boolean applyCombatFacing() {
		if (!combatFacingControlled || isNoAi() || !isAlive())
			return false;
		setYRot(combatFacingYaw);
		yBodyRot = combatFacingYaw;
		return true;
	}

	@Override
	public void aiStep() {
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
		ArmSelection selection = geometry == null ? fallbackArm(target, rightRecovery[0])
			: selectArm(target, geometry, rightRecovery, leftRecovery, lastArm);
		if (selection == null)
			return null;

		ArmCandidate selected = selection.arm();
		SurgicalCombatCalibration.ArmCombatStats stats = SurgicalCombatCalibration.stats(
			geometry == null ? null : selected.arm());
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
		int fixedArmIndex = getIntelligence() == BionicIntelligence.SIMPLE
			? SurgicalCombatCalibration.highestDpsArmIndex(geometry) : -1;
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
		super.tick();
		if (shellGuardTicks > 0)
			shellGuardTicks--;
		if (activeSpinesTicks > 0)
			activeSpinesTicks--;
		if (!level().isClientSide) {
			boolean guarding = shellGuardTicks > 0
				&& getBionicTraits().has(BionicTrait.SHELL_DEFENSE);
			setShiftKeyDown(guarding);
			if (guarding)
				getNavigation().stop();
			if (tickCount % 10 == 0 && getBionicTraits().has(BionicTrait.ACTIVE_SPINES)
				&& !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.0d),
					entity -> entity != this && entity.isAlive() && !isAlliedTo(entity)).isEmpty())
				activeSpinesTicks = (int) getBionicTraits().parameter(
					BionicTrait.ACTIVE_SPINES, "duration_ticks", 100.0d);
			if (activeSpinesTicks > 0 && tickCount % 20 == 0)
				tickActiveSpines();
		}
		if (!level().isClientSide)
			refreshLocomotion(getBionicTraits());
		vibrationSense.tick();
		updateOwnerTarget();
		if (!level().isClientSide && tickCount % 10 == 0) {
			tickDeterrence();
		}
		tickHeadRespiration();
		tickBodyEnvironment();
		updateHitParts();
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
			ownerAssignedTarget = candidate;
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

	private void tickDeterrence() {
		if (!getBionicTraits().has(BionicTrait.DETERRENCE)) return;
		var targets = getBionicTraits().result(BionicTrait.DETERRENCE).deterrenceTargets();
		for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(8.0d),
			candidate -> candidate != this && candidate.isAlive() && !isAlliedTo(candidate))) {
			if (!targets.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())) || !BionicDeterrence.canFlee(mob)) continue;
			Vec3 away = mob.position().subtract(position());
			if (away.horizontalDistanceSqr() < 1.0e-6d) away = new Vec3(1, 0, 0);
			Vec3 destination = mob.position().add(away.normalize().scale(8));
			if (mob.getTarget() == this) mob.setTarget(null);
			BionicDeterrence.flee(mob, this, destination);
		}
	}

	private void tickActiveSpines() {
		if (!getBionicTraits().has(BionicTrait.ACTIVE_SPINES))
			return;
		for (LivingEntity touched : level().getEntitiesOfClass(LivingEntity.class,
			getBoundingBox().inflate(0.2d), entity -> entity != this && entity.isAlive()
				&& !isAlliedTo(entity))) {
			if (touched.hurt(damageSources().thorns(this), 1.0f))
				touched.addEffect(new MobEffectInstance(MobEffects.POISON,
					(int) getBionicTraits().parameter(BionicTrait.ACTIVE_SPINES,
						"poison_duration_ticks", 60.0d)), this);
		}
	}

	private void tickHeadRespiration() {
		if (level().isClientSide || !isAlive())
			return;
		if (!getBionicTraits().has(BionicTrait.DRY_SUFFOCATION)) {
			dryAir = -1;
			return;
		}
		if (isInWaterOrBubble()) {
			dryAir = 300;
			return;
		}
		if (dryAir < 0)
			dryAir = 300;
		else if (dryAir > 0)
			dryAir--;
		else if (tickCount % 20 == 0)
			hurt(damageSources().dryOut(), 1.0f);
	}

	private void tickBodyEnvironment() {
		if (level().isClientSide || !isAlive())
			return;
		BionicTraitSet traits = getBionicTraits();
		if (tickCount % 20 == 0 && traits.value(BionicTrait.PASSIVE_REGENERATION) > 0.0d
			&& getHealth() < getMaxHealth())
			heal((float) traits.value(BionicTrait.PASSIVE_REGENERATION));
		double sunSensitivity = traits.strength(BionicTrait.SUN_SENSITIVE);
		if (sunSensitivity > 0.0d && random.nextDouble() < sunSensitivity && isSunBurnTick()) {
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
		if (isInWaterRainOrBubble()) {
			moisture = MAX_MOISTURE;
			return;
		}
		if (moisture < 0)
			moisture = MAX_MOISTURE;
		else if (moisture > 0)
			moisture--;
		else if (tickCount % 20 == 0)
			hurt(damageSources().dryOut(), (float) moistureDependence);
	}

	private void updateHitParts() {
		if (hitParts == null || registeredHitParts.length == 0)
			return;
		SurgicalAssembly.HitboxGeometry geometry = authoritativeHitboxGeometry();
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
		SurgicalAssembly.HitboxGeometry geometry = renderHitboxGeometry();
		return geometry == null ? super.getBoundingBoxForCulling()
			: getBoundingBox().minmax(worldBounds(geometry.overall()));
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
		if (slot != 0)
			return false;
		HumanoidArm arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		return arm == getMainArm() ? isAttackWeapon(getMainHandItem())
			: isAttackWeapon(getOffhandItem());
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

	private boolean doSelectedAttack(Entity target) {
		boolean hit = super.doHurtTarget(target);
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
		HumanoidArm attackingArm = attackActionLeft ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		return attackActionWeapon && attackingArm == getMainArm();
	}

	private ItemStack selectedAttackWeapon() {
		if (!attackActionWeapon)
			return ItemStack.EMPTY;
		HumanoidArm attackingArm = attackActionLeft ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		return attackingArm == getMainArm() ? getMainHandItem() : getOffhandItem();
	}

	private static HashMultimap<Holder<Attribute>, AttributeModifier> combatModifiers(ItemStack stack) {
		HashMultimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
		if (stack == null || stack.isEmpty())
			return modifiers;
		stack.getAttributeModifiers().modifiers()
			.forEach(entry -> modifiers.put(entry.attribute(), entry.modifier()));
		EnchantmentHelper.forEachModifier(stack, EquipmentSlot.MAINHAND, modifiers::put);
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
		return isAttackWeapon(getMainHandItem()) || isAttackWeapon(getOffhandItem());
	}

	public static boolean isAttackWeapon(ItemStack stack) {
		return stack != null && stack.is(Tags.Items.MELEE_WEAPON_TOOLS);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (OWNER_DATA.equals(key))
			ownerId = entityData.get(OWNER_DATA).orElse(null);
		if (SITTING_DATA.equals(key))
			orderedToSit = entityData.get(SITTING_DATA);
		if (ASSEMBLY.equals(key)) {
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
		SlimeMimicAccess slimeState = (SlimeMimicAccess) (Object) this;
		tag.putBoolean(SOURCE_FORM_TAG, !slimeState.createBiotech$isSlimeMimic());
		CompoundTag assembly = entityData.get(ASSEMBLY);
		if (!assembly.isEmpty())
			tag.put(ASSEMBLY_TAG, assembly.copy());
		if (moisture >= 0)
			tag.putInt(MOISTURE_TAG, moisture);
		if (dryAir >= 0)
			tag.putInt(DRY_AIR_TAG, dryAir);
		if (ownerId != null)
			tag.putUUID(OWNER_TAG, ownerId);
		if (shellGuardTicks > 0)
			tag.putInt(SHELL_GUARD_TAG, shellGuardTicks);
		if (activeSpinesTicks > 0)
			tag.putInt(ACTIVE_SPINES_TAG, activeSpinesTicks);
		tag.putBoolean(ORDERED_TO_SIT_TAG, orderedToSit);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (!tag.contains(SOURCE_FORM_TAG, Tag.TAG_BYTE)
			|| !tag.contains(ASSEMBLY_TAG, Tag.TAG_COMPOUND))
			return;
		SurgicalAssembly assembly = SurgicalAssembly.load(tag.getCompound(ASSEMBLY_TAG));
		if (assembly == null || !assembly.isReadyForEntity())
			return;
		boolean sourceForm = tag.getBoolean(SOURCE_FORM_TAG);
		((SlimeMimicAccess) (Object) this).createBiotech$setSlimeMimic(!sourceForm);
		setAssembly(assembly);
		if (tag.contains(MOISTURE_TAG, Tag.TAG_ANY_NUMERIC))
			moisture = Mth.clamp(tag.getInt(MOISTURE_TAG), 0, MAX_MOISTURE);
		if (tag.contains(DRY_AIR_TAG, Tag.TAG_ANY_NUMERIC))
			dryAir = Mth.clamp(tag.getInt(DRY_AIR_TAG), 0, 300);
		setBionicOwner(tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG)
			: tag.hasUUID(TRUSTED_TAG) ? tag.getUUID(TRUSTED_TAG) : null);
		shellGuardTicks = Mth.clamp(tag.getInt(SHELL_GUARD_TAG), 0, 12000);
		activeSpinesTicks = Mth.clamp(tag.getInt(ACTIVE_SPINES_TAG), 0, 12000);
		setOrderedToSit(tag.getBoolean(ORDERED_TO_SIT_TAG));
		if (!getBionicTraits().has(BionicTrait.TAMEABLE)) {
			setBionicOwner(null);
			setOrderedToSit(false);
		}
	}

	private static final class BionicFloatGoal extends FloatGoal {
		private final SlimeBionicEntity bionic;

		private BionicFloatGoal(SlimeBionicEntity bionic) {
			super(bionic);
			this.bionic = bionic;
		}

		@Override public boolean canUse() {
			// Swimming already steers vertically; FloatGoal would keep requesting jumps
			// and repeatedly launch the body through the surface. Keep the lava escape.
			return (!bionic.organSwimEnabled || bionic.isInLava()) && super.canUse();
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
			if (bionic.orderedToSit || bionic.ownerId == null)
				return false;
			owner = bionic.getCompanionPlayer();
			return owner != null && !owner.isSpectator() && bionic.distanceToSqr(owner) > 100.0d;
		}
		@Override public boolean canContinueToUse() {
			return owner != null && owner.isAlive() && !bionic.orderedToSit
				&& (bionic.ownerId != null && bionic.getBionicTraits().has(BionicTrait.TAMEABLE))
				&& bionic.distanceToSqr(owner) > 9.0d;
		}
		@Override public void stop() { owner = null; bionic.getNavigation().stop(); }
		@Override public void tick() {
			if (owner == null)
				return;
			bionic.getLookControl().setLookAt(owner, 10.0f, bionic.getMaxHeadXRot());
			if (--recalculate <= 0) {
				recalculate = adjustedTickDelay(10);
				bionic.getNavigation().moveTo(owner, bionic.isSwimming() ? SWIMMING_PURSUIT_SPEED : 1.2d);
			}
		}
	}

	/** Vanilla pursuit with independent, directional melee actions and persistent recovery deadlines. */
	private static class BionicAttackGoal extends MeleeAttackGoal {
		private final SlimeBionicEntity bionic;
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
		}

		@Override
		public boolean canUse() {
			return validTarget(bionic.getTarget()) && (bionic.vibrationSense.lastHeardPosition(bionic.getTarget()) != null
				|| super.canUse());
		}

		@Override
		public void start() {
			super.start();
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
				&& target.level() == bionic.level() && bionic.canAttack(target)
				&& !bionic.isAlliedTo(target) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = bionic.getTarget();
			return validTarget(target) && bionic.isWithinRestriction(target.blockPosition())
				&& (pendingAttackTarget == target || bionic.isWithinMeleeAttackRange(target)
					|| bionic.vibrationSense.lastHeardPosition(target) != null || super.canContinueToUse());
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
			if (pendingAttackTarget != null && !bionic.getIntelligence().pursuesDuringAttack())
				bionic.getNavigation().stop();
			raiseArmTicks++;
			bionic.setAggressive(pendingAttackTarget != null
				|| raiseArmTicks >= 5 && getTicksUntilNextAttack() < getAttackInterval() / 2);
		}
	}
}

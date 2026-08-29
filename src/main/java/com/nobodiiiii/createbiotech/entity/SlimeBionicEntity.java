package com.nobodiiiii.createbiotech.entity;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicAccess;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicBodyRotationControl;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicGroundNavigation;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.common.Tags;

/** A real, walking entity whose visible body is supplied by a surgical assembly. */
public class SlimeBionicEntity extends PathfinderMob {
	private static final String ASSEMBLY_TAG = "SurgicalAssembly";
	private static final String SOURCE_FORM_TAG = "BionicSourceForm";
	private static final double DEFAULT_ATTACK_DISTANCE_SQR = 5.0d * 5.0d;
	private static final int ATTACK_EVENT_STRIDE = 4;
	private static final int ATTACK_EVENT_VARIANTS =
		SlimeBionicCombat.NORMAL_ATTACK_TICKS * ATTACK_EVENT_STRIDE;
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
	private SurgicalAssembly reportedBoundsAssembly;
	@Nullable
	private SurgicalAssembly.BodyBounds reportedBodyBounds;
	private int attackAnimationTick;
	private int attackAnimationDuration = SlimeBionicAttackTiming.PLAYBACK_TICKS;
	private boolean attackAnimationLeft;
	private boolean attackAnimationWeapon;
	private int attackActionTick;
	private int attackActionDuration = SlimeBionicCombat.NORMAL_ATTACK_TICKS;
	private boolean attackActionLeft;
	private boolean attackActionWeapon;
	private boolean nextEmptyHandAttackLeft;

	public SlimeBionicEntity(EntityType<? extends SlimeBionicEntity> type, Level level) {
		super(type, level);
		// The bionic body begins in the same synced slime state used by ordinary mimics.
		// Loading a cured entity can still restore this value to false from its saved data.
		((SlimeMimicAccess) (Object) this).createBiotech$setSlimeMimic(true);
		setPersistenceRequired();
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
		clientBoundsAssembly = null;
		clientBodyBounds = null;
		reportedBoundsAssembly = null;
		reportedBodyBounds = null;
		refreshMovementSpeed(assembly);
		refreshDimensions();
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

	/** Applies the renderer's exact visible envelope on the client, including slime-shell inflation. */
	public void setClientBodyBounds(SurgicalAssembly assembly, SurgicalAssembly.BodyBounds bounds) {
		if (!level().isClientSide || assembly == null || bounds == null || getAssembly() != assembly)
			return;
		if (clientBoundsAssembly == assembly && bounds.equals(clientBodyBounds))
			return;
		clientBoundsAssembly = assembly;
		clientBodyBounds = bounds;
		refreshDimensions();
		if (!bounds.equals(assembly.bodyBounds())
			&& (reportedBoundsAssembly != assembly || !bounds.equals(reportedBodyBounds))) {
			reportedBoundsAssembly = assembly;
			reportedBodyBounds = bounds;
			CBPackets.sendToServer(new SlimeBionicBodyBoundsPacket(getId(), bounds));
		}
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		SurgicalAssembly.BodyBounds bounds = activeBodyBounds();
		if (bounds == null)
			return super.getDefaultDimensions(pose);
		// Vanilla mobs use one centred, yaw-independent square footprint whose side is the body's
		// lateral width. Their fore-aft model depth is deliberately not promoted to collision width.
		float width = bounds.width();
		float height = bounds.minY() + bounds.height();
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
		boolean weapon = hasAttackWeapon();
		SurgicalAssembly.ArmAttackGeometry arm = geometry.arm(preferredAttackLeft(weapon));
		if (arm == null)
			return false;
		return SlimeBionicCombat.withinStartEnvelope(targetBounds, position(), arm);
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
		if (attackActionWeapon) {
			attackActionLeft = preferredAttackLeft(true);
		} else {
			attackActionLeft = nextEmptyHandAttackLeft;
			nextEmptyHandAttackLeft = !nextEmptyHandAttackLeft;
		}
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		SurgicalAssembly.ArmAttackGeometry arm = geometry == null ? null
			: geometry.arm(attackActionLeft);
		SurgicalCombatCalibration.ArmCombatStats stats = SurgicalCombatCalibration.stats(arm);
		int attackInterval = stats.attackInterval();
		attackActionDuration = SlimeBionicCombat.duration(attackInterval);
		attackActionTick = attackActionDuration;

		attackAnimationDuration = SlimeBionicAttackTiming.playbackTicks(attackInterval);
		attackAnimationTick = attackAnimationDuration;
		attackAnimationWeapon = attackActionWeapon;
		attackAnimationLeft = attackActionLeft;
		int encoded = (attackActionDuration - 1) * ATTACK_EVENT_STRIDE
			+ (attackActionWeapon ? 2 : 0) + (attackActionLeft ? 1 : 0);
		level().broadcastEntityEvent(this, (byte) (ATTACK_EVENT_BASE + encoded));
		return new AttackStart(attackActionDuration, attackInterval, stats.damageMultiplier(), arm);
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
			attackActionDuration = encoded / ATTACK_EVENT_STRIDE + 1;
			attackActionTick = attackActionDuration;
			attackActionLeft = (encoded & 1) != 0;
			attackActionWeapon = (encoded & 2) != 0;
			attackAnimationDuration = SlimeBionicAttackTiming.playbackTicks(attackActionDuration);
			attackAnimationTick = attackAnimationDuration;
			attackAnimationLeft = attackActionLeft;
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

	public int getAttackAnimationTick() {
		return attackAnimationTick;
	}

	public int getAttackAnimationDuration() {
		return attackAnimationDuration;
	}

	public boolean isAttackAnimationLeft() {
		return attackAnimationLeft;
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
			reportedBoundsAssembly = null;
			reportedBodyBounds = null;
			refreshDimensions();
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

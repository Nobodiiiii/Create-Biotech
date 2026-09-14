package com.nobodiiiii.createbiotech.entity;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.HashMultimap;
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
import com.nobodiiiii.createbiotech.network.CBPackets;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
			.add(Attributes.MAX_HEALTH, SurgicalHealthCalibration.MAX_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, SurgicalGait.ZOMBIE_WALK_SPEED)
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
		if (assembly == null || !assembly.isReadyForEntity())
			throw new IllegalArgumentException("A bionic entity requires complete body geometry");
		CompoundTag encoded = assembly.save();
		entityData.set(ASSEMBLY, encoded);
		cachedAssemblyData = encoded;
		cachedAssembly = assembly;
		configureHitPartRegistration(assembly);
		clientBoundsAssembly = null;
		clientBodyBounds = null;
		clientHitboxGeometry = null;
		clientBodyVolume = Double.NaN;
		refreshMaximumHealth(assembly);
		refreshMovementSpeed(assembly);
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

	/** Applies the grounded anatomical gait calibration to the authoritative movement attribute. */
	private void refreshMovementSpeed(SurgicalAssembly assembly) {
		if (level().isClientSide)
			return;
		double speed = SurgicalGait.movementSpeed(assembly.bodyBounds());
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

	public BionicIntelligence getIntelligence() {
		return getMind().intelligence();
	}

	/** Only rest-pose hips touching the ground can drive locomotion or leg animation. */
	public int getLocomotionLegCount() {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.BodyBounds bounds = assembly == null ? null : assembly.bodyBounds();
		return bounds == null ? 0 : bounds.groundedLegCount();
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
		long now = level().getGameTime();
		float bodyYaw = combatBodyYaw();
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			List<SurgicalAssembly.ArmAttackGeometry> arms = left ? geometry.left() : geometry.right();
			long[] recovery = left ? leftRecovery : rightRecovery;
			for (int slot = 0; slot < arms.size(); slot++) {
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
		updateHitParts();
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
				return super.doHurtTarget(target);
			attackDamage.removeModifier(ANATOMICAL_ATTACK_MODIFIER_ID);
			attackDamage.addTransientModifier(new AttributeModifier(ANATOMICAL_ATTACK_MODIFIER_ID,
				damageMultiplier - 1.0d, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			try {
				return super.doHurtTarget(target);
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
		if (ASSEMBLY.equals(key)) {
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
					|| super.canContinueToUse());
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
			if (pendingAttackTarget != null)
				bionic.getNavigation().stop();
			raiseArmTicks++;
			bionic.setAggressive(pendingAttackTarget != null
				|| raiseArmTicks >= 5 && getTicksUntilNextAttack() < getAttackInterval() / 2);
		}
	}
}

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
	private int attackActionSequence;
	private float attackAimYaw;
	private float attackAimPitch;

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
			cachedAssembly = SurgicalAssembly.load(encoded);
		}
		return cachedAssembly;
	}

	/** Current head-derived classification; it intentionally does not modify any Goal yet. */
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
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		if (geometry == null)
			return distanceToSqr(target) <= DEFAULT_ATTACK_DISTANCE_SQR;
		AABB targetBounds = target.getBoundingBox();
		for (SurgicalAssembly.ArmAttackGeometry arm : geometry.arms())
			if (SlimeBionicCombat.withinStartEnvelope(targetBounds, position(), yBodyRot, arm))
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

	/** Selects the best ready arm and snapshots gameplay and presentation state for one attack. */
	@Nullable
	private AttackStart beginAttack(LivingEntity target, int[] rightRecovery, int[] leftRecovery,
		@Nullable ArmUse lastArm) {
		SurgicalAssembly assembly = getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		ArmCandidate selected = geometry == null ? fallbackArm(target)
			: selectArm(target, geometry, rightRecovery, leftRecovery, lastArm);
		if (selected == null)
			return null;

		SurgicalCombatCalibration.ArmCombatStats stats = SurgicalCombatCalibration.stats(selected.arm());
		int baseInterval = stats.attackInterval();
		int readyArms = geometry == null ? 1 : readyArmCount(geometry, rightRecovery, leftRecovery);
		float cadenceScale = SurgicalCombatCalibration.cadenceScale(readyArms);
		int globalInterval = Math.max(1, Math.round(baseInterval * cadenceScale));
		int duration = SlimeBionicCombat.duration(globalInterval);
		startAttackPresentation(selected, duration);
		return new AttackStart(duration, globalInterval, baseInterval, stats.damageMultiplier(),
			selected.arm(), selected.aim(), selected.left(), selected.slot());
	}

	@Nullable
	private ArmCandidate selectArm(LivingEntity target, SurgicalAssembly.AttackGeometry geometry,
		int[] rightRecovery, int[] leftRecovery, @Nullable ArmUse lastArm) {
		ArmCandidate best = null;
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			List<SurgicalAssembly.ArmAttackGeometry> arms = left ? geometry.left() : geometry.right();
			int[] recovery = left ? leftRecovery : rightRecovery;
			for (int slot = 0; slot < arms.size(); slot++) {
				if (slot >= recovery.length || recovery[slot] > 0)
					continue;
				SurgicalAssembly.ArmAttackGeometry arm = arms.get(slot);
				if (!SlimeBionicCombat.withinStartEnvelope(target.getBoundingBox(), position(), yBodyRot, arm))
					continue;
				Vec3 origin = SlimeBionicCombat.worldOrigin(position(), yBodyRot, arm);
				Vec3 desired = SlimeBionicCombat.aimAt(target.getBoundingBox(), origin, yBodyRot);
				float relativeYaw = Mth.wrapDegrees(SlimeBionicCombat.yaw(desired) - yBodyRot);
				if (Math.abs(relativeYaw) > SlimeBionicCombat.MAX_AIM_YAW_DEGREES)
					continue;
				Vec3 aim = SlimeBionicCombat.constrainAim(desired, yBodyRot);
				boolean weapon = armHasWeapon(left, slot);
				Vec3 localTarget = target.getBoundingBox().getCenter().subtract(position())
					.yRot(yBodyRot * Mth.DEG_TO_RAD);
				boolean crossesBody = left ? localTarget.x < -0.05d : localTarget.x > 0.05d;
				double score = Math.abs(relativeYaw)
					+ Math.abs(SlimeBionicCombat.pitch(aim)) * 0.35d
					+ (crossesBody ? 30.0d : 0.0d)
					+ (lastArm != null && lastArm.left() == left && lastArm.slot() == slot ? 18.0d : 0.0d)
					- (weapon ? 12.0d : 0.0d) + slot * 1.0e-3d;
				ArmCandidate candidate = new ArmCandidate(left, slot, weapon, arm, aim, score);
				if (best == null || candidate.score() < best.score())
					best = candidate;
			}
		}
		return best;
	}

	private ArmCandidate fallbackArm(LivingEntity target) {
		boolean left = hasAttackWeapon() ? weaponHoldingArm() == HumanoidArm.LEFT : !attackActionLeft;
		boolean weapon = armHasWeapon(left, 0);
		Vec3 desired = SlimeBionicCombat.aimAt(target.getBoundingBox(), position(), yBodyRot);
		Vec3 aim = SlimeBionicCombat.constrainAim(desired, yBodyRot);
		return new ArmCandidate(left, 0, weapon, null, aim, 0.0d);
	}

	private int readyArmCount(SurgicalAssembly.AttackGeometry geometry,
		int[] rightRecovery, int[] leftRecovery) {
		int ready = 0;
		for (int slot = 0; slot < geometry.right().size(); slot++)
			if (slot < rightRecovery.length && rightRecovery[slot] <= 0)
				ready++;
		for (int slot = 0; slot < geometry.left().size(); slot++)
			if (slot < leftRecovery.length && leftRecovery[slot] <= 0)
				ready++;
		return Math.max(1, ready);
	}

	private void startAttackPresentation(ArmCandidate selected, int duration) {
		attackActionDuration = duration;
		attackActionTick = duration;
		attackActionLeft = selected.left();
		attackActionArmSlot = selected.slot();
		attackActionWeapon = selected.weapon();
		attackAnimationDuration = SlimeBionicAttackTiming.playbackTicks(duration);
		attackAnimationTick = attackAnimationDuration;
		attackAnimationLeft = attackActionLeft;
		attackAnimationArmSlot = attackActionArmSlot;
		attackAnimationWeapon = attackActionWeapon;
		attackActionSequence++;
		setAttackAim(selected.aim());
		CBPackets.sendToTrackingEntity(SlimeBionicAttackActionPacket.start(this), this);
	}

	private void setAttackAim(Vec3 aim) {
		attackAimYaw = SlimeBionicCombat.yaw(aim);
		attackAimPitch = SlimeBionicCombat.pitch(aim);
	}

	private void synchronizeAttackAim(Vec3 aim) {
		setAttackAim(aim);
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

	private HumanoidArm weaponHoldingArm() {
		return isAttackWeapon(getMainHandItem()) ? getMainArm() : getMainArm().getOpposite();
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

	private boolean attackConeIntersects(LivingEntity target, Vec3 aim,
		SurgicalAssembly.ArmAttackGeometry arm) {
		return SlimeBionicCombat.intersects(target.getBoundingBox(), position(), yBodyRot, aim, arm);
	}

	private record AttackStart(int actionDuration, int globalAttackInterval, int armRecovery,
		double damageMultiplier, @Nullable SurgicalAssembly.ArmAttackGeometry arm, Vec3 aim,
		boolean left, int slot) {}

	private record ArmCandidate(boolean left, int slot, boolean weapon,
		@Nullable SurgicalAssembly.ArmAttackGeometry arm, Vec3 aim, double score) {}

	private record ArmUse(boolean left, int slot) {}

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

	public boolean isAttackActionWeapon() {
		return attackActionWeapon;
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

	/** Applies a validated clientbound action snapshot or a later aim-lock update. */
	public void applyAttackAction(int sequence, boolean restart, boolean left, int slot,
		boolean weapon, int duration, float aimYaw, float aimPitch) {
		if (!level().isClientSide || !Float.isFinite(aimYaw) || !Float.isFinite(aimPitch))
			return;
		if (!restart) {
			if (sequence != attackActionSequence)
				return;
			attackAimYaw = Mth.wrapDegrees(aimYaw);
			attackAimPitch = Mth.clamp(aimPitch, SlimeBionicCombat.MIN_AIM_PITCH_DEGREES,
				SlimeBionicCombat.MAX_AIM_PITCH_DEGREES);
			return;
		}
		attackActionSequence = sequence;
		attackActionDuration = Mth.clamp(duration, 1, SlimeBionicCombat.NORMAL_ATTACK_TICKS);
		attackActionTick = attackActionDuration;
		attackActionLeft = left;
		attackActionArmSlot = Mth.clamp(slot, 0, SurgicalLimbType.SHOULDER.maxPerBody() - 1);
		attackActionWeapon = weapon;
		attackAnimationDuration = SlimeBionicAttackTiming.playbackTicks(attackActionDuration);
		attackAnimationTick = attackAnimationDuration;
		attackAnimationLeft = attackActionLeft;
		attackAnimationArmSlot = attackActionArmSlot;
		attackAnimationWeapon = attackActionWeapon;
		attackAimYaw = Mth.wrapDegrees(aimYaw);
		attackAimPitch = Mth.clamp(aimPitch, SlimeBionicCombat.MIN_AIM_PITCH_DEGREES,
			SlimeBionicCombat.MAX_AIM_PITCH_DEGREES);
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
		private static final float AIM_TRACKING_DEGREES_PER_TICK = 15.0f;
		private static final double ATTACK_TRACKING_EPSILON_SQR = 1.0e-8d;

		private final SlimeBionicEntity bionic;
		private final int[] rightArmRecovery = new int[SurgicalLimbType.SHOULDER.maxPerBody()];
		private final int[] leftArmRecovery = new int[SurgicalLimbType.SHOULDER.maxPerBody()];
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
		@Nullable
		private Vec3 pendingAim;
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
			anatomicalAttackCooldown = 0;
			currentAttackInterval = SurgicalCombatCalibration.ZOMBIE_ATTACK_INTERVAL;
			Arrays.fill(rightArmRecovery, 0);
			Arrays.fill(leftArmRecovery, 0);
			lastArm = null;
		}

		@Override
		public void stop() {
			super.stop();
			clearPendingAttack();
			anatomicalAttackCooldown = 0;
			Arrays.fill(rightArmRecovery, 0);
			Arrays.fill(leftArmRecovery, 0);
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
			turnBodyToward(target);
			AttackStart attack = bionic.beginAttack(target, rightArmRecovery, leftArmRecovery, lastArm);
			if (attack == null)
				return;
			anatomicalAttackCooldown = attack.globalAttackInterval();
			currentAttackInterval = attack.globalAttackInterval();
			bionic.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			pendingAttackDuration = attack.actionDuration();
			pendingArmGeometry = attack.arm();
			pendingAim = attack.aim();
			pendingDamageMultiplier = attack.damageMultiplier();
			pendingAttackTarget = target;
			pendingAttackElapsed = 0;
			pendingImpactApplied = false;
			pendingAimSynchronized = false;
			int[] recovery = attack.left() ? leftArmRecovery : rightArmRecovery;
			if (attack.slot() >= 0 && attack.slot() < recovery.length)
				recovery[attack.slot()] = attack.armRecovery();
			lastArm = new ArmUse(attack.left(), attack.slot());
		}

		private void advancePendingAttack() {
			pendingAttackElapsed++;
			LivingEntity target = pendingAttackTarget;
			int activeStart = SlimeBionicCombat.activeStartTick(pendingAttackDuration);
			if (target != null && target.isAlive() && pendingAttackElapsed <= activeStart) {
				turnBodyToward(target);
				trackAim(target);
			}
			if (!pendingAimSynchronized && pendingAttackElapsed >= activeStart && pendingAim != null) {
				pendingAimSynchronized = true;
				bionic.synchronizeAttackAim(pendingAim);
			}
			if (!pendingImpactApplied && target != null && target.isAlive()
				&& SlimeBionicCombat.isActiveTick(pendingAttackElapsed, pendingAttackDuration)) {
				boolean intersects = pendingArmGeometry != null && pendingAim != null
					? bionic.attackConeIntersects(target, pendingAim, pendingArmGeometry)
					: bionic.isWithinMeleeAttackRange(target);
				if (intersects && bionic.hasLineOfSight(target)) {
					pendingImpactApplied = true;
					bionic.performAttackDamage(target, pendingDamageMultiplier);
				}
			}
			if (pendingAttackElapsed >= pendingAttackDuration)
				clearPendingAttack();
		}

		private void turnBodyToward(LivingEntity target) {
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

		private void trackAim(LivingEntity target) {
			Vec3 origin = pendingArmGeometry == null ? bionic.position()
				: SlimeBionicCombat.worldOrigin(bionic.position(), bionic.yBodyRot, pendingArmGeometry);
			Vec3 desired = SlimeBionicCombat.constrainAim(
				SlimeBionicCombat.aimAt(target.getBoundingBox(), origin, bionic.yBodyRot), bionic.yBodyRot);
			pendingAim = pendingAim == null ? desired
				: SlimeBionicCombat.approachAim(pendingAim, desired, AIM_TRACKING_DEGREES_PER_TICK);
		}

		private void clearPendingAttack() {
			pendingAttackTarget = null;
			pendingAttackElapsed = 0;
			pendingAttackDuration = 0;
			pendingImpactApplied = false;
			pendingArmGeometry = null;
			pendingAim = null;
			pendingAimSynchronized = false;
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
			decrementRecovery(rightArmRecovery);
			decrementRecovery(leftArmRecovery);
			if (pendingAttackTarget != null)
				advancePendingAttack();
			super.tick();
			raiseArmTicks++;
			bionic.setAggressive(raiseArmTicks >= 5 && getTicksUntilNextAttack() < getAttackInterval() / 2);
		}

		private static void decrementRecovery(int[] recovery) {
			for (int slot = 0; slot < recovery.length; slot++)
				if (recovery[slot] > 0)
					recovery[slot]--;
		}
	}
}

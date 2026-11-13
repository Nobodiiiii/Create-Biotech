package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/** One server-authoritative scheduler shared by head, torso and spell attacks. */
public final class BionicSpecialAttacks {
	public static final int EVOKER_FANGS_ACTION = 5;
	public static final int WITCH_POTION_ACTION = 8;
	private static final String SONIC_BOOM_KEY = "create_biotech:sonic_boom/minecraft:sonic_boom";
	private final SlimeBionicEntity entity;
	private final Map<String, Integer> cooldowns = new HashMap<>();
	@Nullable private Choice action;
	@Nullable private LivingEntity target;
	private int elapsed, warmup, duration, blazeShots, nextBlazeShot;
	@Nullable private net.minecraft.world.entity.ai.goal.RangedAttackGoal rangedGoal;
	private boolean fired;
	public BionicSpecialAttacks(SlimeBionicEntity entity) { this.entity = entity; }
	public boolean busy() { return action != null; }
	public boolean locksMovement() { return action != null && rangedGoal == null && !action.kind().equals("small_fireball"); }
	private record Choice(BionicTrait trait, BionicSubtrait variant,
		Set<SurgicalAssembly.CombinationMember> members) {
		String key() { return trait.id() + "/" + variant.id(); }
		String kind() { return variant.id().getPath(); }
		int ticks(String name, int fallback) { return Math.max(0, (int) variant.parameter(name, fallback)); }
		double range() { return variant.parameter("range", 16); }
	}

	public void tick() {
		cooldowns.replaceAll((key, value) -> Math.max(0, value - 1));

		if (!entity.isAlive() || entity.isNoAi() || entity.isOrderedToSit() || entity.isVehicle()) { cancel(); return; }
		if (action != null) {
			if (!stillAvailable(action)) { cancel(); return; }
			if (BionicTrait.SONIC_BOOM.equals(action.trait)) {
				// SonicBoom.canStillUse keeps the native 60-tick action alive even without
				// an attack target. Resolve the current target at launch; never cut off recovery.
				target = entity.getTarget();
			} else if (target == null || !target.isAlive() || target != entity.getTarget()) { cancel(); return; }
			if (rangedGoal != null) {
				if (!rangedGoal.canContinueToUse()) { cancel(); return; }
				rangedGoal.tick();
				if (fired) {
					fired = false;
					List<Choice> ready = choices().stream().filter(c -> cooldowns.getOrDefault(c.key(), 0) == 0 && valid(c, target)).toList();
					if (ready.isEmpty() || !choose(ready, target).key().equals(action.key())) finish();
				}
				return;
			}
			if (target != null && target.isAlive()) entity.getLookControl().setLookAt(target, 30, 30);
			if (action.kind().equals("small_fireball")) {
				if (entity.distanceToSqr(target) < 4) { cancel(); return; }
				elapsed++;
				if (entity.hasLineOfSight(target) && entity.distanceToSqr(target) < action.range() * action.range()) {
					entity.getNavigation().stop();
					if (blazeShots < 3 && elapsed >= nextBlazeShot) {
						fire(action, target); blazeShots++; nextBlazeShot = elapsed + 6;
						if (blazeShots == 3) duration = elapsed + 6;
					}
					if (blazeShots == 3 && elapsed >= duration) finish();
				} else if (entity.tickCount % 5 == 0) entity.getNavigation().moveTo(target, 1);
				return;
			}
			if (action.kind().equals("fireball") || action.kind().equals("dragon_breath")) {
				if (!valid(action, target)) { if (elapsed > 0) elapsed--; return; }
			} else if (!BionicTrait.SONIC_BOOM.equals(action.trait) && !valid(action, target)) {
				if (BionicTrait.GUARDIAN_BEAM.equals(action.trait)) entity.setTarget(null);
				cancel(); return;
			}
			entity.getNavigation().stop();
			elapsed++;
			if (action.kind().equals("fireball") && elapsed == 10) entity.level().levelEvent(null, 1015, entity.blockPosition(), 0);
			if (BionicTrait.GUARDIAN_BEAM.equals(action.trait) && elapsed == 10) entity.syncSpecialAction(2, target.getId());
			boolean launch = elapsed >= warmup && !fired;
			if (launch && (!action.kind().equals("dragon_breath") || facing(target, Math.cos(Math.toRadians(10))))) {
				if (!BionicTrait.SONIC_BOOM.equals(action.trait) || valid(action, target)) {
					if (!action.kind().equals("wind_charge") || facing(target, 0.5)) fire(action, target);
				}
				fired = true;
			}
			if (elapsed >= duration && (!action.kind().equals("dragon_breath") || fired)) finish();
			return;
		}
		if (entity.getAttackActionTick() > 0) return;
		LivingEntity victim = entity.getTarget();
		if (victim == null || !victim.isAlive() || !entity.canAttack(victim)) return;
		List<Choice> ready = choices().stream().filter(c -> cooldowns.getOrDefault(c.key(), 0) == 0 && valid(c, victim)).toList();
		if (ready.isEmpty()) return;
		start(choose(ready, victim), victim);
	}
	private Choice choose(List<Choice> ready, LivingEntity victim) {
		return switch (entity.getIntelligence()) {
		case SIMPLE -> ready.stream().max(Comparator.comparingDouble(this::volume).thenComparing(Choice::key)).orElseThrow();
		case NORMAL -> ready.get(entity.getRandom().nextInt(ready.size()));
		case ADVANCED -> ready.stream().max(Comparator.comparingDouble(c -> score(c, victim))).orElseThrow();
		};
	}
	private double volume(Choice choice) {
		return choice.members.stream().mapToDouble(m -> entity.getAssembly().sources().get(m.source()).anatomy().volume(m.cube())).sum();
	}
	private double score(Choice choice, LivingEntity victim) {
		return switch (choice.kind()) {
		case "sonic_boom" -> victim.getArmorValue() > 10 || !entity.hasLineOfSight(victim) ? 20 : 8;
		case "guardian", "elder_guardian" -> 10;
		case "snowball" -> victim.getType() == EntityType.BLAZE ? 12 : 1;
		case "small_fireball", "fireball" -> victim.fireImmune() ? 0 : 9;
		case "potion" -> victim.hasEffect(MobEffects.POISON) ? 5 : 11;
		case "spit" -> 2;
		default -> 7;
		};
	}
	private List<Choice> choices() {
		List<Choice> choices = new ArrayList<>();
		var traits = entity.getBionicTraits();
		if (traits.has(BionicTrait.SONIC_BOOM)) {
			Map<String, Double> parameters = new HashMap<>();
			for (var e : Map.of("range", 15d, "vertical_range", 20d, "warmup_ticks", 34d, "action_ticks", 60d, "cooldown_ticks", 40d).entrySet())
				parameters.put(e.getKey(), traits.parameter(BionicTrait.SONIC_BOOM, e.getKey(), e.getValue()));
			choices.add(new Choice(BionicTrait.SONIC_BOOM, new BionicSubtrait(ResourceLocation.withDefaultNamespace("sonic_boom"),
				BionicTrait.SONIC_BOOM.carrier(), parameters), traits.members(BionicTrait.SONIC_BOOM)));
		}
		for (BionicTrait trait : List.of(BionicTrait.HEAD_PROJECTILE, BionicTrait.GUARDIAN_BEAM, BionicTrait.SPELLCASTING)) {
			if (!traits.has(trait)) continue;
			traits.result(trait).subtraits().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(v -> v.id().toString())))
				.forEach(entry -> choices.add(new Choice(trait, entry.getKey(), BionicTraitRuntime.firstGroup(entity, entry.getValue()))));
		}
		return choices;
	}
	private boolean stillAvailable(Choice choice) {
		return entity.getBionicTraits().has(choice.trait) && (BionicTrait.SONIC_BOOM.equals(choice.trait)
			? entity.getBionicTraits().members(choice.trait).containsAll(choice.members)
			: entity.getBionicTraits().result(choice.trait).subtraits().entrySet().stream().anyMatch(e ->
				e.getKey().equals(choice.variant) && e.getValue().containsAll(choice.members)));
	}
	private boolean valid(Choice choice, @Nullable LivingEntity victim) {
		if (victim == null || !victim.isAlive() || !entity.canAttack(victim) || choice.members.isEmpty()) return false;
		Vec3 offset = victim.getBoundingBox().getCenter().subtract(BionicTraitRuntime.origin(entity, choice.members));
		double distance = offset.length();
		if (BionicTrait.SONIC_BOOM.equals(choice.trait))
			return offset.horizontalDistance() <= choice.range() && Math.abs(offset.y) <= choice.variant.parameter("vertical_range", 20);
		if (usesRangedGoal(choice)) return distance <= Math.max(choice.range(), entity.getAttributeValue(Attributes.FOLLOW_RANGE));
		return distance < choice.range() && distance > choice.variant.parameter("min_range",
			choice.kind().equals("wind_charge") || choice.kind().equals("small_fireball") ? 2 : 0)
			&& entity.hasLineOfSight(victim);
	}
	private static boolean usesRangedGoal(Choice choice) {
		return Set.of("spit", "snowball", "wither_skull", "potion").contains(choice.kind());
	}
	private boolean facing(LivingEntity victim, double minimumDot) {
		return entity.getViewVector(1).dot(victim.position().subtract(entity.position()).normalize()) > minimumDot;
	}
	public void performRangedAttack(LivingEntity victim) {
		if (action == null || rangedGoal == null) return;
		fire(action, victim); fired = true;
	}

	private void start(Choice choice, LivingEntity victim) {
		action = choice; target = victim; elapsed = 0; fired = false;
		entity.syncSpecialOrigin(BionicTraitRuntime.origin(entity, choice.members));
		if (usesRangedGoal(choice)) {
			rangedGoal = new net.minecraft.world.entity.ai.goal.RangedAttackGoal(entity, 1.0,
				choice.ticks("cooldown_ticks", 40), (float) choice.range());
			// RangedAttackGoal initializes its internal target in canUse(), not start().
			if (!rangedGoal.canUse()) { cancel(); return; }
			rangedGoal.start();
		}
		warmup = choice.ticks("warmup_ticks", 0);
		if (BionicTrait.GUARDIAN_BEAM.equals(choice.trait)) entity.syncGuardianAttackDuration(warmup - 10);
		blazeShots = 0; nextBlazeShot = warmup;
		duration = Math.max(1, choice.ticks("action_ticks", warmup + (choice.kind().equals("small_fireball") ? 18 : 0)));
		entity.syncSpecialAction(BionicTrait.GUARDIAN_BEAM.equals(choice.trait) ? 7 : BionicTrait.SONIC_BOOM.equals(choice.trait) ? 1
			: BionicTrait.SPELLCASTING.equals(choice.trait)
				? choice.kind().equals("potion") ? WITCH_POTION_ACTION : EVOKER_FANGS_ACTION : 3, victim.getId());
		if (BionicTrait.SONIC_BOOM.equals(choice.trait)) entity.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3, 1);
		if (BionicTrait.GUARDIAN_BEAM.equals(choice.trait)) entity.playSound(SoundEvents.GUARDIAN_ATTACK, 1, 1);
		if (choice.kind().equals("fangs")) entity.playSound(SoundEvents.EVOKER_PREPARE_ATTACK);
		if (rangedGoal != null) rangedGoal.tick();
	}
	private void finish() {
		if (action == null) return;
		Choice completed = action;
		int cooldown = rangedGoal != null ? 0 : action.ticks("cooldown_ticks", 0);
		if (action.kind().equals("shulker_bullet")) cooldown = entity.getRandom().nextInt(10) * 10;
		cancel();
		cooldowns.put(completed.key(), cooldown);
		if (BionicTrait.GUARDIAN_BEAM.equals(completed.trait)) entity.setTarget(null);
	}
	private void cancel() {
		if (action != null && BionicTrait.SONIC_BOOM.equals(action.trait)) cooldowns.put(action.key(), action.ticks("cooldown_ticks", 40));
		if (rangedGoal != null) { rangedGoal.stop(); rangedGoal = null; }
		action = null; target = null; entity.syncSpecialAction(0, 0);
	}
	private void fire(Choice choice, LivingEntity victim) {
		Vec3 origin = BionicTraitRuntime.origin(entity, choice.members);
		Vec3 aim = victim.getBoundingBox().getCenter().subtract(origin);
		ServerLevel level = (ServerLevel) entity.level();
		if (BionicTrait.SONIC_BOOM.equals(choice.trait)) {
			beam(origin, victim.getBoundingBox().getCenter(), true); entity.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3, 1);
			if (victim.hurt(entity.damageSources().sonicBoom(entity), 10)) {
				double resistance = 1 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				Vec3 direction = aim.normalize(); victim.push(direction.x * 2.5 * resistance, direction.y * 0.5 * resistance, direction.z * 2.5 * resistance); victim.hurtMarked = true;
			}
			return;
		}
		if (BionicTrait.GUARDIAN_BEAM.equals(choice.trait)) {
			float magic = (float) choice.variant.parameter("magic_damage", 1) + (level.getDifficulty() == Difficulty.HARD ? 2 : 0);
			victim.hurt(entity.damageSources().indirectMagic(entity, entity), magic);
			victim.hurt(entity.damageSources().mobAttack(entity), (float) choice.variant.parameter("damage", 6)); return;
		}
		if (choice.kind().equals("fangs")) {
			fangs(origin, victim); entity.playSound(SoundEvents.EVOKER_CAST_SPELL); return;
		}
		Projectile projectile;
		switch (choice.kind()) {
		case "spit" -> {
			projectile = new LlamaSpit(EntityType.LLAMA_SPIT, level);
			Vec3 delta = new Vec3(victim.getX() - origin.x, victim.getY(1.0 / 3.0) - origin.y, victim.getZ() - origin.z);
			projectile.shoot(delta.x, delta.y + delta.horizontalDistance() * 0.2, delta.z, 1.5f, 10);
			entity.playSound(SoundEvents.LLAMA_SPIT);
		}
		case "snowball" -> {
			projectile = new Snowball(level, entity);
			Vec3 delta = new Vec3(victim.getX() - origin.x, victim.getEyeY() - 1.1 - origin.y, victim.getZ() - origin.z);
			projectile.shoot(delta.x, delta.y + delta.horizontalDistance() * 0.2, delta.z, 1.6f, 12);
			entity.playSound(SoundEvents.SNOW_GOLEM_SHOOT);
		}
		case "small_fireball" -> {
			double spread = Math.sqrt(Math.sqrt(entity.distanceToSqr(victim))) * 0.5;
			Vec3 delta = new Vec3(entity.getRandom().triangle(victim.getX() - origin.x, 2.297 * spread),
				victim.getY(0.5) - origin.y, entity.getRandom().triangle(victim.getZ() - origin.z, 2.297 * spread));
			projectile = new SmallFireball(level, entity, delta.normalize());
			level.levelEvent(null, 1018, entity.blockPosition(), 0);
		}
		case "fireball" -> { projectile = new LargeFireball(level, entity, aim.normalize(), 1); entity.playSound(SoundEvents.GHAST_SHOOT); }
		case "shulker_bullet" -> { projectile = new ShulkerBullet(level, entity, victim, Direction.Axis.Y); entity.playSound(SoundEvents.SHULKER_SHOOT); }
		case "wither_skull" -> { WitherSkull skull = new WitherSkull(level, entity, aim.normalize()); skull.setDangerous(entity.getRandom().nextFloat() < 0.001f); projectile = skull; entity.playSound(SoundEvents.WITHER_SHOOT); }
		case "dragon_breath" -> projectile = new DragonFireball(level, entity, aim.normalize());
		case "wind_charge" -> {
			projectile = new BreezeWindCharge(EntityType.BREEZE_WIND_CHARGE, level);
			projectile.shoot(victim.getX() - origin.x, victim.getY(victim.isPassenger() ? 0.8 : 0.3) - origin.y,
				victim.getZ() - origin.z, 0.7f, 5 - level.getDifficulty().getId() * 4);
			entity.playSound(SoundEvents.BREEZE_SHOOT, 1.5f, 1);
		}
		case "potion" -> {
			Vec3 velocity = victim.getDeltaMovement();
			Vec3 delta = new Vec3(victim.getX() + velocity.x - origin.x, victim.getEyeY() - 1.1 - origin.y,
				victim.getZ() + velocity.z - origin.z);
			double horizontal = delta.horizontalDistance();
			var potion = horizontal >= 8 && !victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? Potions.SLOWNESS
				: victim.getHealth() >= 8 && !victim.hasEffect(MobEffects.POISON) ? Potions.POISON
				: horizontal <= 3 && !victim.hasEffect(MobEffects.WEAKNESS) && entity.getRandom().nextFloat() < 0.25 ? Potions.WEAKNESS : Potions.HARMING;
			ThrownPotion thrown = new ThrownPotion(level, entity);
			thrown.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, potion)); thrown.setXRot(thrown.getXRot() + 20);
			thrown.shoot(delta.x, delta.y + horizontal * 0.2, delta.z, 0.75f, 8); projectile = thrown;
			entity.playSound(SoundEvents.WITCH_THROW, 1, 0.8f + entity.getRandom().nextFloat() * 0.4f);
		}
		default -> { return; }
		}
		projectile.setOwner(entity); projectile.setPos(origin);
		BionicCombatEvents.mark(projectile, entity); level.addFreshEntity(projectile);
	}
	private void beam(Vec3 from, Vec3 to, boolean sonic) {
		Vec3 direction = to.subtract(from); int steps = Math.min(128, (int) direction.length() + (sonic ? 7 : 1));
		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.add(sonic ? direction.normalize().scale(i) : direction.scale(i / (double) Math.max(1, steps)));
			((ServerLevel) entity.level()).sendParticles(sonic ? ParticleTypes.SONIC_BOOM : ParticleTypes.BUBBLE,
				point.x, point.y, point.z, 1, 0, 0, 0, 0);
		}
	}
	private void fangs(Vec3 origin, LivingEntity victim) {
		double angle = Math.atan2(victim.getZ() - origin.z, victim.getX() - origin.x);
		if (entity.distanceToSqr(victim) < 9) {
			for (int ring = 0; ring < 2; ring++) for (int i = 0; i < (ring == 0 ? 5 : 8); i++) {
				double a = angle + i * Math.PI * 2 / (ring == 0 ? 5 : 8) + ring * 1.2566;
				fang(origin.x + Math.cos(a) * (ring == 0 ? 1.5 : 2.5), origin.z + Math.sin(a) * (ring == 0 ? 1.5 : 2.5), a, ring == 0 ? 0 : 3, origin, victim);
			}
		} else for (int i = 0; i < 16; i++) fang(origin.x + Math.cos(angle) * 1.25 * (i + 1),
			origin.z + Math.sin(angle) * 1.25 * (i + 1), angle, i, origin, victim);
	}
	private void fang(double x, double z, double angle, int delay, Vec3 origin, LivingEntity victim) {
		int min = (int) Math.floor(Math.min(origin.y, victim.getY())) - 1;
		for (int y = (int) Math.floor(Math.max(origin.y, victim.getY())) + 1; y >= min; y--) {
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!entity.level().hasChunkAt(pos)) return;
			if (!entity.level().getBlockState(pos.below()).isFaceSturdy(entity.level(), pos.below(), Direction.UP)) continue;
			var shape = entity.level().getBlockState(pos).getCollisionShape(entity.level(), pos);
			double floor = y + (shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y));
			EvokerFangs fang = new EvokerFangs(entity.level(), x, floor, z, (float) angle, delay, entity);
			BionicCombatEvents.mark(fang, entity); entity.level().addFreshEntity(fang); return;
		}
	}
	/** Matches Warden.setAttackTarget's initial melee window; repeated assignments of one target do not restart it. */
	public void onTargetAcquired() { cooldowns.put(SONIC_BOOM_KEY, 200); }
	public void afterMelee() { cooldowns.put(SONIC_BOOM_KEY, 40); }
	public void save(CompoundTag root) {
		CompoundTag tag = new CompoundTag(); cooldowns.forEach(tag::putInt);
		if (action != null) tag.putInt(action.key(), Math.max(tag.getInt(action.key()), action.ticks("cooldown_ticks", 40)));
		root.put("BionicSpecialAttacks", tag);
	}
	public void load(CompoundTag root) {
		cooldowns.clear(); CompoundTag tag = root.getCompound("BionicSpecialAttacks");
		for (String key : tag.getAllKeys()) {
			int slash = key.indexOf('/');
			if (slash < 1) continue;
			cooldowns.put(key, net.minecraft.util.Mth.clamp(tag.getInt(key), 0, 24000));
		}
		action = null;
	}
}

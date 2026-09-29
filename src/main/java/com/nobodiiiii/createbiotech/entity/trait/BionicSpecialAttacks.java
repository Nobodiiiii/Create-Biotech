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
	private final SlimeBionicEntity entity;
	private final Map<String, Integer> cooldowns = new HashMap<>();
	@Nullable private Choice action;
	@Nullable private LivingEntity target;
	private int elapsed, warmup, duration, recovery, roarTicks;
	private boolean fired;
	public BionicSpecialAttacks(SlimeBionicEntity entity) { this.entity = entity; }
	public boolean busy() { return action != null || roarTicks > 0; }
	private record Choice(BionicTrait trait, BionicSubtrait variant,
		Set<SurgicalAssembly.CombinationMember> members) {
		String key() { return trait.id() + "/" + variant.id(); }
		String kind() { return variant.id().getPath(); }
		int ticks(String name, int fallback) { return Math.max(0, (int) variant.parameter(name, fallback)); }
		double range() { return variant.parameter("range", 16); }
	}

	public void tick() {
		cooldowns.replaceAll((key, value) -> Math.max(0, value - 1));
		if (recovery > 0) recovery--;
		if (!entity.isAlive() || entity.isNoAi() || entity.isOrderedToSit() || entity.isVehicle()) { cancel(); roarTicks = 0; return; }
		if (roarTicks > 0) {
			if (!entity.getBionicTraits().has(BionicTrait.ROAR)) { roarTicks = 0; cancel(); return; }
			entity.getNavigation().stop(); entity.setDeltaMovement(entity.getDeltaMovement().multiply(0, 1, 0));
			entity.syncSpecialAction(4, 0);
			entity.syncSpecialOrigin(BionicTraitRuntime.origin(entity, entity.getBionicTraits().members(BionicTrait.ROAR)));
			if (--roarTicks == 0) { roar(); entity.syncSpecialAction(0, 0); }
			return;
		}
		if (action != null) {
			if (target != entity.getTarget() || !valid(action, target) || !stillAvailable(action)) { cancel(); return; }
			entity.getNavigation().stop();
			entity.getLookControl().setLookAt(target, 30, 30);
			elapsed++;
			if (action.trait == BionicTrait.GUARDIAN_BEAM && elapsed == 10) entity.syncSpecialAction(2, target.getId());
			if (action.trait == BionicTrait.GUARDIAN_BEAM && elapsed > 10 && elapsed % 2 == 0)
				beam(BionicTraitRuntime.origin(entity, action.members), target.getBoundingBox().getCenter(), false);
			if (elapsed >= warmup && !fired || action.kind().equals("small_fireball") && (elapsed == warmup + 6 || elapsed == warmup + 12)) {
				fire(action, target); fired = true;
			}
			if (elapsed >= duration) finish();
			return;
		}
		if (recovery > 0 || entity.getAttackActionTick() > 0) return;
		LivingEntity victim = entity.getTarget();
		if (victim == null || !victim.isAlive() || !entity.canAttack(victim)) return;
		List<Choice> ready = choices().stream().filter(c -> cooldowns.getOrDefault(c.key(), 0) == 0 && valid(c, victim)).toList();
		if (ready.isEmpty()) return;
		Choice chosen = switch (entity.getIntelligence()) {
		case SIMPLE -> ready.stream().max(Comparator.comparingDouble(this::volume).thenComparing(Choice::key)).orElseThrow();
		case NORMAL -> ready.get(entity.getRandom().nextInt(ready.size()));
		case ADVANCED -> ready.stream().max(Comparator.comparingDouble(c -> score(c, victim))).orElseThrow();
		};
		start(chosen, victim);
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
		return entity.getBionicTraits().has(choice.trait) && (choice.trait == BionicTrait.SONIC_BOOM
			? entity.getBionicTraits().members(choice.trait).containsAll(choice.members)
			: entity.getBionicTraits().result(choice.trait).subtraits().entrySet().stream().anyMatch(e ->
				e.getKey().equals(choice.variant) && e.getValue().containsAll(choice.members)));
	}
	private boolean valid(Choice choice, @Nullable LivingEntity victim) {
		if (victim == null || !victim.isAlive() || !entity.canAttack(victim) || choice.members.isEmpty()) return false;
		Vec3 offset = victim.getBoundingBox().getCenter().subtract(BionicTraitRuntime.origin(entity, choice.members));
		double distance = offset.length();
		if (choice.trait == BionicTrait.SONIC_BOOM)
			return offset.horizontalDistance() <= choice.range() && Math.abs(offset.y) <= choice.variant.parameter("vertical_range", 20)
				&& (entity.hasLineOfSight(victim) || entity.lastHeardPosition(victim) != null);
		return distance <= choice.range() && distance >= choice.variant.parameter("min_range", choice.kind().equals("wind_charge") ? 2 : 0)
			&& entity.hasLineOfSight(victim) && clearShot(choice, victim);
	}
	private boolean clearShot(Choice choice, LivingEntity victim) {
		Vec3 start = BionicTraitRuntime.origin(entity, choice.members);
		return entity.level().clip(new net.minecraft.world.level.ClipContext(start, victim.getBoundingBox().getCenter(),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, entity))
			.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}
	private void start(Choice choice, LivingEntity victim) {
		action = choice; target = victim; elapsed = 0; fired = false;
		entity.syncSpecialOrigin(BionicTraitRuntime.origin(entity, choice.members));
		warmup = choice.ticks("warmup_ticks", 0);
		duration = Math.max(1, choice.ticks("action_ticks", warmup + (choice.kind().equals("small_fireball") ? 18 : 0)));
		entity.syncSpecialAction(choice.trait == BionicTrait.GUARDIAN_BEAM ? 7 : choice.trait == BionicTrait.SONIC_BOOM ? 1
			: choice.trait == BionicTrait.SPELLCASTING ? 5 : 3, victim.getId());
		if (choice.trait == BionicTrait.SONIC_BOOM) entity.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3, 1);
		if (choice.trait == BionicTrait.GUARDIAN_BEAM) entity.playSound(SoundEvents.GUARDIAN_ATTACK, 1, 1);
		if (choice.kind().equals("fangs")) entity.playSound(SoundEvents.EVOKER_PREPARE_ATTACK);
	}
	private void finish() {
		if (action == null) return;
		int cooldown = action.ticks("cooldown_ticks", 20);
		if (action.kind().equals("shulker_bullet")) cooldown += entity.getRandom().nextInt(10) * 10;

		String key = action.key();
		cancel();
		cooldowns.put(key, cooldown);
		recovery = 5;
	}
	private void cancel() {
		if (action != null) { cooldowns.merge(action.key(), 10, Math::max); }
		action = null; target = null; entity.syncSpecialAction(0, 0);
	}
	private void fire(Choice choice, LivingEntity victim) {
		Vec3 origin = BionicTraitRuntime.origin(entity, choice.members);
		Vec3 aim = victim.getBoundingBox().getCenter().subtract(origin);
		ServerLevel level = (ServerLevel) entity.level();
		if (choice.trait == BionicTrait.SONIC_BOOM) {
			beam(origin, victim.getBoundingBox().getCenter(), true); entity.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3, 1);
			victim.hurt(entity.damageSources().sonicBoom(entity), 10);
			double resistance = 1 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 direction = aim.normalize(); victim.push(direction.x * 2.5 * resistance, 0.5 * resistance, direction.z * 2.5 * resistance); victim.hurtMarked = true;
			return;
		}
		if (choice.trait == BionicTrait.GUARDIAN_BEAM) {
			float magic = (float) choice.variant.parameter("magic_damage", 1) + (level.getDifficulty() == Difficulty.HARD ? 2 : 0);
			victim.hurt(entity.damageSources().indirectMagic(entity, entity), magic);
			victim.hurt(entity.damageSources().mobAttack(entity), (float) choice.variant.parameter("damage", 6)); return;
		}
		if (choice.kind().equals("fangs")) { fangs(origin, victim); return; }
		Projectile projectile;
		switch (choice.kind()) {
		case "spit" -> { projectile = new LlamaSpit(EntityType.LLAMA_SPIT, level); entity.playSound(SoundEvents.LLAMA_SPIT); }
		case "snowball" -> { projectile = new Snowball(level, entity); entity.playSound(SoundEvents.SNOW_GOLEM_SHOOT); }
		case "small_fireball" -> projectile = new SmallFireball(level, entity, aim.normalize());
		case "fireball" -> { projectile = new LargeFireball(level, entity, aim.normalize(), 1); entity.playSound(SoundEvents.GHAST_SHOOT); }
		case "shulker_bullet" -> { projectile = new ShulkerBullet(level, entity, victim, Direction.Axis.Y); entity.playSound(SoundEvents.SHULKER_SHOOT); }
		case "wither_skull" -> { WitherSkull skull = new WitherSkull(level, entity, aim.normalize()); skull.setDangerous(entity.getRandom().nextFloat() < 0.001f); projectile = skull; entity.playSound(SoundEvents.WITHER_SHOOT); }
		case "dragon_breath" -> projectile = new DragonFireball(level, entity, aim.normalize());
		case "wind_charge" -> projectile = new BreezeWindCharge(EntityType.BREEZE_WIND_CHARGE, level);
		case "potion" -> {
			var potion = aim.length() >= 8 && !victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? Potions.SLOWNESS
				: victim.getHealth() >= 8 && !victim.hasEffect(MobEffects.POISON) ? Potions.POISON
				: aim.length() <= 3 && entity.getRandom().nextFloat() < 0.25 && !victim.hasEffect(MobEffects.WEAKNESS) ? Potions.WEAKNESS : Potions.HARMING;
			ThrownPotion thrown = new ThrownPotion(level, entity); thrown.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, potion)); projectile = thrown;
			entity.playSound(SoundEvents.WITCH_THROW);
		}

		default -> { return; }
		}
		projectile.setOwner(entity); projectile.setPos(origin);
		if (!(projectile instanceof ShulkerBullet)) {
			float speed = choice.kind().equals("potion") ? 0.75f : projectile instanceof AbstractHurtingProjectile ? 0.5f : 1.6f;
			double arc = projectile instanceof AbstractHurtingProjectile ? 0 : aim.horizontalDistance() * 0.2;
			projectile.shoot(aim.x, aim.y + arc, aim.z, speed, 14 - level.getDifficulty().getId() * 4);
		}
		BionicCombatEvents.mark(projectile, entity); level.addFreshEntity(projectile);
	}
	private void beam(Vec3 from, Vec3 to, boolean sonic) {
		Vec3 direction = to.subtract(from); int steps = Math.min(128, (int) direction.length() + 1);
		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.add(direction.scale(i / (double) Math.max(1, steps)));
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
	public void afterMelee() { cooldowns.put("sonic_boom/minecraft:sonic_boom", 40); }
	public void blockedByShield(LivingEntity living) {
		if (entity.getRandom().nextBoolean()) {
			cancel(); roarTicks = (int) entity.getBionicTraits().parameter(BionicTrait.ROAR, "stun_ticks", 40)
				+ (int) entity.getBionicTraits().parameter(BionicTrait.ROAR, "warmup_ticks", 10);
		} else push(living);
	}
	private void roar() {
		entity.playSound(SoundEvents.RAVAGER_ROAR);
		for (LivingEntity victim : entity.level().getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(
			entity.getBionicTraits().parameter(BionicTrait.ROAR, "range", 4)), e -> e != entity && e.isAlive() && !entity.isAlliedTo(e))) {
			victim.hurt(entity.damageSources().mobAttack(entity), (float) entity.getBionicTraits().parameter(BionicTrait.ROAR, "damage", 6)); push(victim);
		}
		((ServerLevel) entity.level()).sendParticles(ParticleTypes.POOF, entity.getX(), entity.getEyeY(), entity.getZ(), 40, 1, 1, 1, 0.1);
	}
	private void push(LivingEntity victim) {
		Vec3 direction = victim.position().subtract(entity.position()); double squared = Math.max(0.001, direction.horizontalDistanceSqr());
		victim.push(direction.x / squared * 4, 0.2, direction.z / squared * 4); victim.hurtMarked = true;
	}
	public void save(CompoundTag root) {
		CompoundTag tag = new CompoundTag(); cooldowns.forEach(tag::putInt);
		if (action != null) tag.putInt(action.key(), Math.max(tag.getInt(action.key()), action.ticks("cooldown_ticks", 40)));
		tag.putInt("RoarTicks", roarTicks); root.put("BionicSpecialAttacks", tag);
	}
	public void load(CompoundTag root) {
		cooldowns.clear(); CompoundTag tag = root.getCompound("BionicSpecialAttacks");
		for (String key : tag.getAllKeys()) if (!key.equals("RoarTicks")) cooldowns.put(key, net.minecraft.util.Mth.clamp(tag.getInt(key), 0, 24000));
		roarTicks = net.minecraft.util.Mth.clamp(tag.getInt("RoarTicks"), 0, 200); action = null;
	}
}

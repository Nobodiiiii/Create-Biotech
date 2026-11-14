package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

/** Persistent production cycles and environmental tissue effects. */
public final class BionicProduction {
	private final SlimeBionicEntity entity;
	private int eggTicks = -1, scuteTicks = -1, woolTicks;
	private int pollenTicks, crops, fuse;
	private int flowerCooldown, pollinatingTicks;
	@Nullable private net.minecraft.world.phys.Vec3 hoverPos;
	@Nullable private BlockPos flower;
	private boolean pollen, ignited, exploding;
	@Nullable private SurgicalAssembly carrierAssembly;
	@Nullable private BionicTraitSet carrierTraits;
	private ProductionCarriers productionCarriers = ProductionCarriers.EMPTY;

	private record ProductionCarriers(Map<DyeColor, Integer> wool, int eggs, int scutes, int pollinators) {
		private static final ProductionCarriers EMPTY = new ProductionCarriers(Map.of(), 0, 0, 0);
	}

	public BionicProduction(SlimeBionicEntity entity) { this.entity = entity; }
	private boolean has(BionicTrait trait) { return entity.getBionicTraits().has(trait); }
	/** Count physical output organs once per assembly/data revision, independently of donor count. */
	private ProductionCarriers carriers() {
		var assembly = entity.getAssembly();
		var traits = entity.getBionicTraits();
		if (assembly == carrierAssembly && traits == carrierTraits) return productionCarriers;
		if (assembly == null) productionCarriers = ProductionCarriers.EMPTY;
		else {
			Map<DyeColor, Integer> wool = new EnumMap<>(DyeColor.class);
			for (var member : BionicTraitRuntime.membersWithRole(entity, BionicTrait.WOOL_SHEARING, BionicAnatomyRole.WOOL)) {
				var profile = assembly.sources().get(member.source()).profile();
				wool.merge(DyeColor.byId(profile.stableInt("Color")), 1, Integer::sum);
			}
			productionCarriers = new ProductionCarriers(Map.copyOf(wool),
				BionicTraitRuntime.membersWithRole(entity, BionicTrait.EGG_LAYING, BionicAnatomyRole.TORSO).size(),
				BionicTraitRuntime.membersWithRole(entity, BionicTrait.SCUTE_SHEDDING, BionicAnatomyRole.SHELL).size(),
				BionicTraitRuntime.membersWithRole(entity, BionicTrait.POLLINATION, BionicAnatomyRole.TORSO).size());
		}
		carrierAssembly = assembly;
		carrierTraits = traits;
		return productionCarriers;
	}

	/** Normal stack limits also apply when hundreds of organs produce in one cycle. */
	private static void addDrops(List<ItemStack> drops, Item item, int count) {
		while (count > 0) {
			ItemStack stack = new ItemStack(item);
			int size = Math.min(count, stack.getMaxStackSize());
			stack.setCount(size);
			drops.add(stack);
			count -= size;
		}
	}

	private void drop(Item item, int count) {
		List<ItemStack> drops = new ArrayList<>();
		addDrops(drops, item, count);
		drops.forEach(entity::spawnAtLocation);
	}

	private int parameter(BionicTrait trait, String name, int fallback) {
		return Math.max(1, (int) entity.getBionicTraits().parameter(trait, name, fallback));
	}
	private void sync() {
		entity.setProductionFlags((woolTicks > 0 ? 1 : 0) | fuse << 2);
		entity.setBionicPollen(pollen && has(BionicTrait.POLLINATION));
	}
	public boolean exploding() { return exploding; }
	public boolean shearable() {
		return entity.isAlive() && has(BionicTrait.WOOL_SHEARING) && !entity.isSheared();
	}
	public List<ItemStack> shear() {
		if (entity.level().isClientSide || !shearable()) return List.of();
		List<ItemStack> drops = new ArrayList<>();
		carriers().wool().forEach((color, parts) -> {
			int count = 0;
			for (int part = 0; part < parts; part++) count += 1 + entity.getRandom().nextInt(3);
			addDrops(drops, BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_wool")), count);
		});
		if (drops.isEmpty()) return List.of();
		var generated = BionicGameplayRuntime.output(entity, BionicTrait.WOOL_SHEARING, "shear", drops, carriers().wool().values().stream().mapToInt(Integer::intValue).sum());
		drops.clear(); drops.addAll(generated);
		if (drops.isEmpty()) return List.of();
		woolTicks = 1;
		sync();
		entity.playSound(SoundEvents.SHEEP_SHEAR);
		entity.gameEvent(GameEvent.SHEAR);
		BionicGameplayRuntime.produced(entity, BionicTrait.WOOL_SHEARING, "shear", drops);
		return List.copyOf(drops);
	}
	public InteractionResult interactShears(Player player, InteractionHand hand) {
		if (!entity.isAlive()) return InteractionResult.PASS;
		ItemStack held = player.getItemInHand(hand);
		boolean client = entity.level().isClientSide;
		if (held.getItem() instanceof ShearsItem && shearable()) {
			if (!client) {
				var drops = shear();
				if (drops.isEmpty()) return InteractionResult.FAIL;
				drops.forEach(drop -> entity.spawnShearedDrop(entity.level(), entity.blockPosition(), drop));
				held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
			}
			return InteractionResult.sidedSuccess(client);
		}
		return InteractionResult.PASS;
	}
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!entity.isAlive()) return InteractionResult.PASS;
		ItemStack held = player.getItemInHand(hand);
		boolean client = entity.level().isClientSide;
		if (has(BionicTrait.SELF_DESTRUCT) && held.is(ItemTags.CREEPER_IGNITERS)
			&& (entity.getBionicOwnerId() == null || entity.getBionicOwnerId().equals(player.getUUID()))) {
			if (!client) {
				ignited = true;
				entity.playSound(held.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE);
				if (held.isDamageableItem()) held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
				else held.consume(1, player);
			}
			return InteractionResult.sidedSuccess(client);
		}
		if (has(BionicTrait.MILKABLE) && BionicGameplayRuntime.nativeInteractions(entity, BionicTrait.MILKABLE)) {
			var traits = entity.getBionicTraits();
			boolean milk = held.is(Items.BUCKET) && traits.hasVariant(BionicTrait.MILKABLE, CreateBiotech.asResource("milk_bucket"));
			boolean stew = held.is(Items.BOWL) && traits.hasVariant(BionicTrait.MILKABLE, CreateBiotech.asResource("mushroom_stew"));
			if (milk || stew) {
				if (!client) {
					var outputs = BionicGameplayRuntime.output(entity, BionicTrait.MILKABLE, stew ? "stew" : "milk",
						List.of(new ItemStack(stew ? Items.MUSHROOM_STEW : Items.MILK_BUCKET)), 1);
					if (outputs.isEmpty()) return InteractionResult.FAIL;
					player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, outputs.getFirst().copy()));
					for (var extra : outputs.subList(1, outputs.size())) {
						var remainder = extra.copy(); if (!player.getInventory().add(remainder)) player.drop(remainder, false);
					}
					BionicGameplayRuntime.produced(entity, BionicTrait.MILKABLE, stew ? "stew" : "milk", outputs);
					entity.playSound(stew ? SoundEvents.MOOSHROOM_MILK : SoundEvents.COW_MILK);
					entity.gameEvent(GameEvent.ENTITY_INTERACT);
				}
				return InteractionResult.sidedSuccess(client);
			}
		}
		if (has(BionicTrait.SCUTE_SHEDDING) && held.is(Items.BRUSH) && BionicGameplayRuntime.nativeInteractions(entity, BionicTrait.SCUTE_SHEDDING)) {
			if (!client) {
				if (!BionicGameplayRuntime.drop(entity, BionicTrait.SCUTE_SHEDDING, "brush", Items.ARMADILLO_SCUTE, carriers().scutes())) return InteractionResult.FAIL;
				held.hurtAndBreak(16, player, LivingEntity.getSlotForHand(hand));
				entity.playSound(SoundEvents.ARMADILLO_BRUSH); entity.gameEvent(GameEvent.ENTITY_INTERACT);
			}
			return InteractionResult.sidedSuccess(client);
		}
		return InteractionResult.PASS;
	}

	public void tick() {
		if (!entity.isAlive()) return;
		BionicGameplayRuntime.tick(entity);
		if (has(BionicTrait.EGG_LAYING)) {
			if (eggTicks < 0) eggTicks = interval(BionicTrait.EGG_LAYING);
			if (--eggTicks <= 0) { BionicGameplayRuntime.drop(entity, BionicTrait.EGG_LAYING, "egg", Items.EGG, carriers().eggs()); entity.playSound(SoundEvents.CHICKEN_EGG); eggTicks = interval(BionicTrait.EGG_LAYING); }
		}
		if (has(BionicTrait.SCUTE_SHEDDING)) {
			if (scuteTicks < 0) scuteTicks = interval(BionicTrait.SCUTE_SHEDDING);
			if (--scuteTicks <= 0) { BionicGameplayRuntime.drop(entity, BionicTrait.SCUTE_SHEDDING, "scute", Items.ARMADILLO_SCUTE, carriers().scutes()); entity.playSound(SoundEvents.ARMADILLO_SCUTE_DROP); scuteTicks = interval(BionicTrait.SCUTE_SHEDDING); }
		}
		if (flowerCooldown > 0) flowerCooldown--;
		if (has(BionicTrait.SNOW_TRAIL) && EventHooks.canEntityGrief(entity.level(), entity)) snow();
		if (has(BionicTrait.POLLINATION) && !entity.isNoAi()) { if (pollen) growCrops(); }
		else { flower = null; pollenTicks = 0; }
		tickFuse();
	}
	private int interval(BionicTrait trait) {
		int min = parameter(trait, "min_interval_ticks", 6000), max = Math.max(min, parameter(trait, "max_interval_ticks", 11999));
		return min + entity.getRandom().nextInt(max - min + 1);
	}
	public net.minecraft.world.entity.ai.goal.Goal eatGrassGoal() {
		return new net.minecraft.world.entity.ai.goal.EatBlockGoal(entity) {
			@Override public boolean canUse() {
				return has(BionicTrait.WOOL_SHEARING) && !entity.isOrderedToSit() && !entity.isVehicle() && super.canUse();
			}
		};
	}
	public void ate() {
		if (has(BionicTrait.WOOL_SHEARING)) { woolTicks = 0; sync(); }
	}
	private void snow() {
		for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
			BlockPos pos = BlockPos.containing(entity.getX() + x * 0.25, entity.getY(), entity.getZ() + z * 0.25);
			BlockState snow = Blocks.SNOW.defaultBlockState();
			if (entity.level().isEmptyBlock(pos) && snow.canSurvive(entity.level(), pos)
				&& CommonHooks.canEntityDestroy(entity.level(), pos, entity)) entity.level().setBlockAndUpdate(pos, snow);
		}
	}
	/** BeePollinateGoal timing and hover behavior, using retained bee tissue. */
	public net.minecraft.world.entity.ai.goal.Goal pollinationGoal() {
		return new net.minecraft.world.entity.ai.goal.Goal() {
			{ setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }
			private boolean available() {
				return has(BionicTrait.POLLINATION) && !pollen && !entity.level().isRaining()
					&& entity.getTarget() == null && !entity.isVehicle() && !entity.isOrderedToSit();
			}
			@Override public boolean canUse() {
				if (!available() || flowerCooldown > 0) return false;
				flower = findFlower();
				if (flower == null) flowerCooldown = 20 + entity.getRandom().nextInt(41);
				return flower != null;
			}
			@Override public boolean canContinueToUse() {
				if (!available() || flower == null || pollinatingTicks > 600) return false;
				if (pollenTicks > parameter(BionicTrait.POLLINATION, "collect_ticks", 400))
					return entity.getRandom().nextFloat() < 0.2f;
				return entity.tickCount % 20 != 0 || validFlower(flower);
			}
			@Override public void start() {
				pollenTicks = 0; pollinatingTicks = 0; hoverPos = null;
				entity.getNavigation().moveTo(flower.getX() + 0.5, flower.getY() + 0.5, flower.getZ() + 0.5, 1.2);
			}
			@Override public boolean requiresUpdateEveryTick() { return true; }
			@Override public void tick() {
				pollinatingTicks++;
				var center = net.minecraft.world.phys.Vec3.atBottomCenterOf(flower).add(0, 0.6, 0);
				if (center.distanceTo(entity.position()) > 1) hoverPos = center;
				else {
					if (hoverPos == null) hoverPos = center;
					if (entity.position().distanceTo(hoverPos) <= 0.1 && entity.getRandom().nextInt(25) == 0) {
						hoverPos = center.add((entity.getRandom().nextFloat() * 2 - 1) / 3, 0,
							(entity.getRandom().nextFloat() * 2 - 1) / 3);
						entity.getNavigation().stop();
					}
					entity.getLookControl().setLookAt(center.x, center.y, center.z);
					pollenTicks++;
					if (entity.getRandom().nextFloat() < 0.05f && pollenTicks % 60 == 0) entity.playSound(SoundEvents.BEE_POLLINATE);
				}
				entity.getMoveControl().setWantedPosition(hoverPos.x, hoverPos.y, hoverPos.z, 0.35);
			}
			@Override public void stop() {
				if (pollenTicks > parameter(BionicTrait.POLLINATION, "collect_ticks", 400)) { pollen = true; crops = 0; }
				entity.getNavigation().stop(); flower = null; hoverPos = null; pollenTicks = 0; flowerCooldown = 200;
			}
		};
	}
	private boolean validFlower(BlockPos pos) {
		BlockState state = entity.level().getBlockState(pos);
		if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
			&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) return false;
		return state.is(BlockTags.FLOWERS) && (!state.is(Blocks.SUNFLOWER)
			|| state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
				== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
	}
	@Nullable private BlockPos findFlower() {
		BlockPos origin = entity.blockPosition();
		for (int y = 0; y <= 5; y = y > 0 ? -y : 1 - y)
			for (int radius = 0; radius < 5; radius++)
				for (int x = 0; x <= radius; x = x > 0 ? -x : 1 - x)
					for (int z = x < radius && x > -radius ? radius : 0; z <= radius; z = z > 0 ? -z : 1 - z) {
						BlockPos pos = origin.offset(x, y - 1, z);
						if (origin.closerThan(pos, 5) && validFlower(pos)) return pos;
					}
		return null;
	}
	/** Each retained bee body supplies its own growth attempt and pollen-use budget. */
	private void growCrops() {
		ServerLevel level = (ServerLevel) entity.level();
		int organs = carriers().pollinators();
		int max = parameter(BionicTrait.POLLINATION, "max_crops", 10) * organs;
		int interval = parameter(BionicTrait.POLLINATION, "growth_interval_ticks", 30);
		boolean griefingChecked = false;
		for (int organ = 0; organ < organs && crops < max; organ++) {
			if (entity.getRandom().nextFloat() < 0.3f || entity.getRandom().nextInt(interval) != 0) continue;
			if (!griefingChecked && !EventHooks.canEntityGrief(level, entity)) return;
			griefingChecked = true;
			for (int depth = 1; depth <= 2 && crops < max; depth++) {
				BlockPos pos = entity.blockPosition().below(depth);
				BlockState state = level.getBlockState(pos), grown = null;
				if (!state.is(BlockTags.BEE_GROWABLES) || !CommonHooks.canEntityDestroy(level, pos, entity)) continue;
				if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) grown = crop.getStateForAge(crop.getAge(state) + 1);
				else if (state.getBlock() instanceof StemBlock && state.getValue(StemBlock.AGE) < 7) grown = state.cycle(StemBlock.AGE);
				else if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) < 3) grown = state.cycle(SweetBerryBushBlock.AGE);
				else if ((state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) && state.getBlock() instanceof BonemealableBlock plant) {
					plant.performBonemeal(level, entity.getRandom(), pos, state);
					if (!level.getBlockState(pos).equals(state)) crops++;
				}
				if (grown != null && level.setBlockAndUpdate(pos, grown)) { crops++; level.levelEvent(2011, pos, 15); }
			}
		}
		if (crops >= max) pollen = false;
	}

	private void tickFuse() {
		if (!has(BionicTrait.SELF_DESTRUCT)) { fuse = 0; ignited = false; sync(); return; }
		LivingEntity target = entity.getTarget();
		boolean swell = ignited || entity.getBionicOwnerId() == null && !entity.isNoAi() && !entity.isOrderedToSit()
			&& target != null && entity.canAttack(target) && entity.hasLineOfSight(target) && entity.distanceToSqr(target) < (fuse > 0 ? 49 : 9);
		if (swell) entity.getNavigation().stop();
		if (swell && fuse == 0) entity.playSound(SoundEvents.CREEPER_PRIMED);
		fuse = Math.max(0, fuse + (swell ? 1 : -1)); sync();
		if (fuse < parameter(BionicTrait.SELF_DESTRUCT, "fuse_ticks", 30)) return;
		exploding = true;
		entity.markSelfDestructing();
		entity.level().explode(entity, entity.getX(), entity.getY(), entity.getZ(),
			parameter(BionicTrait.SELF_DESTRUCT, "power", 3),
			EventHooks.canEntityGrief(entity.level(), entity) ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
		if (!entity.getActiveEffects().isEmpty()) {
			AreaEffectCloud cloud = new AreaEffectCloud(entity.level(), entity.getX(), entity.getY(), entity.getZ());
			cloud.setOwner(entity); cloud.setRadius(2.5f); cloud.setRadiusOnUse(-0.5f); cloud.setWaitTime(10);
			cloud.setDuration(300); cloud.setRadiusPerTick(-2.5f / 300);
			for (var effect : entity.getActiveEffects()) cloud.addEffect(new MobEffectInstance(effect));
			BionicCombatEvents.mark(cloud, entity); entity.level().addFreshEntity(cloud);
		}
		entity.finishSelfDestruct();
	}
	public void save(CompoundTag root) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Egg", eggTicks); tag.putInt("Scute", scuteTicks); tag.putInt("Wool", woolTicks);
		tag.putInt("FlowerCooldown", flowerCooldown); tag.putInt("PollenTicks", pollenTicks); tag.putInt("Crops", crops); tag.putBoolean("Pollen", pollen);
		tag.putBoolean("Ignited", ignited); tag.putInt("Fuse", fuse);
		if (flower != null) tag.putLong("Flower", flower.asLong());
		root.put("BionicProduction", tag);
	}
	public void load(CompoundTag root) {
		CompoundTag tag = root.getCompound("BionicProduction");
		eggTicks = tag.contains("Egg") && tag.getInt("Egg") >= 0 ? net.minecraft.util.Mth.clamp(tag.getInt("Egg"), 1, 24000) : -1;
		scuteTicks = tag.contains("Scute") && tag.getInt("Scute") >= 0 ? net.minecraft.util.Mth.clamp(tag.getInt("Scute"), 1, 24000) : -1;
		woolTicks = net.minecraft.util.Mth.clamp(tag.getInt("Wool"), 0, 24000);
		flowerCooldown = net.minecraft.util.Mth.clamp(tag.getInt("FlowerCooldown"), 0, 200);
		pollenTicks = net.minecraft.util.Mth.clamp(tag.getInt("PollenTicks"), 0, 24000);
		crops = net.minecraft.util.Mth.clamp(tag.getInt("Crops"), 0, SurgicalAssembly.MAX_CUBES * 100); pollen = tag.getBoolean("Pollen");
		ignited = tag.getBoolean("Ignited");
		fuse = net.minecraft.util.Mth.clamp(tag.getInt("Fuse"), 0, 24000);
		flower = tag.contains("Flower") ? BlockPos.of(tag.getLong("Flower")) : null; sync();
	}
}

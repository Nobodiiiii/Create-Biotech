package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import org.jetbrains.annotations.Nullable;
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
	private boolean pollen, ignited, charged, exploding;
	public BionicProduction(SlimeBionicEntity entity) { this.entity = entity; }
	private boolean has(BionicTrait trait) { return entity.getBionicTraits().has(trait); }
	private int parameter(BionicTrait trait, String name, int fallback) {
		return Math.max(1, (int) entity.getBionicTraits().parameter(trait, name, fallback));
	}
	private void sync() { entity.setProductionFlags((woolTicks > 0 ? 1 : 0) | (charged ? 2 : 0) | fuse << 2); }
	public boolean exploding() { return exploding; }
	public void charge() { if (has(BionicTrait.SELF_DESTRUCT)) { charged = true; sync(); } }
	public boolean shearable() {
		return entity.isAlive() && has(BionicTrait.WOOL_SHEARING) && !entity.isSheared();
	}
	public List<ItemStack> shear() {
		if (entity.level().isClientSide || !shearable()) return List.of();
		var source = BionicTraitRuntime.dominant(entity, BionicTrait.WOOL_SHEARING);
		if (source == null) return List.of();
		DyeColor color = DyeColor.byId(source.profile().stableInt("Color"));
		woolTicks = 1;
		sync();
		entity.playSound(SoundEvents.SHEEP_SHEAR);
		entity.gameEvent(GameEvent.SHEAR);
		return List.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_wool")),
			1 + entity.getRandom().nextInt(3)));
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
		if (has(BionicTrait.MILKABLE)) {
			boolean stew = held.is(Items.BOWL) && entity.getBionicTraits().members(BionicTrait.MILKABLE).stream()
				.anyMatch(member -> entity.getAssembly().sources().get(member.source()).profile().entityTypeId()
					.equals(ResourceLocation.withDefaultNamespace("mooshroom")));
			if (held.is(Items.BUCKET) || stew) {
				if (!client) {
					player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(stew ? Items.MUSHROOM_STEW : Items.MILK_BUCKET)));
					entity.playSound(SoundEvents.COW_MILK); entity.gameEvent(GameEvent.ENTITY_INTERACT);
				}
				return InteractionResult.sidedSuccess(client);
			}
		}
		if (has(BionicTrait.SCUTE_SHEDDING) && held.is(Items.BRUSH)) {
			if (!client) {
				entity.spawnAtLocation(Items.ARMADILLO_SCUTE);
				held.hurtAndBreak(16, player, LivingEntity.getSlotForHand(hand));
				entity.playSound(SoundEvents.ARMADILLO_BRUSH); entity.gameEvent(GameEvent.ENTITY_INTERACT);
			}
			return InteractionResult.sidedSuccess(client);
		}
		return InteractionResult.PASS;
	}

	public void tick() {
		if (!entity.isAlive()) return;
		if (has(BionicTrait.EGG_LAYING)) {
			if (eggTicks < 0) eggTicks = interval(BionicTrait.EGG_LAYING);
			if (--eggTicks <= 0) { entity.spawnAtLocation(Items.EGG); entity.playSound(SoundEvents.CHICKEN_EGG); eggTicks = interval(BionicTrait.EGG_LAYING); }
		}
		if (has(BionicTrait.SCUTE_SHEDDING)) {
			if (scuteTicks < 0) scuteTicks = interval(BionicTrait.SCUTE_SHEDDING);
			if (--scuteTicks <= 0) { entity.spawnAtLocation(Items.ARMADILLO_SCUTE); entity.playSound(SoundEvents.ARMADILLO_SCUTE_DROP); scuteTicks = interval(BionicTrait.SCUTE_SHEDDING); }
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
	/** BeeGrowCropGoal's random triggers and per-block growth dispatch. */
	private void growCrops() {
		ServerLevel level = (ServerLevel) entity.level();
		if (entity.getRandom().nextFloat() < 0.3f || entity.getRandom().nextInt(parameter(BionicTrait.POLLINATION, "growth_interval_ticks", 30)) != 0
			|| !EventHooks.canEntityGrief(level, entity)) return;
		int max = parameter(BionicTrait.POLLINATION, "max_crops", 10);
		for (int depth = 1; depth <= 2 && crops < max; depth++) {
			BlockPos pos = entity.blockPosition().below(depth);
			BlockState state = level.getBlockState(pos), grown = null;
			if (!state.is(BlockTags.BEE_GROWABLES) || !CommonHooks.canEntityDestroy(level, pos, entity)) continue;
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) grown = crop.getStateForAge(crop.getAge(state) + 1);
			else if (state.getBlock() instanceof StemBlock && state.getValue(StemBlock.AGE) < 7) grown = state.cycle(StemBlock.AGE);
			else if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) < 3) grown = state.cycle(SweetBerryBushBlock.AGE);
			else if ((state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) && state.getBlock() instanceof BonemealableBlock plant)
				plant.performBonemeal(level, entity.getRandom(), pos, state);
			if (grown != null && level.setBlockAndUpdate(pos, grown)) { crops++; level.levelEvent(2011, pos, 15); }
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
			parameter(BionicTrait.SELF_DESTRUCT, charged ? "charged_power" : "power", charged ? 6 : 3),
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
		tag.putBoolean("Ignited", ignited); tag.putBoolean("Charged", charged); tag.putInt("Fuse", fuse);
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
		crops = net.minecraft.util.Mth.clamp(tag.getInt("Crops"), 0, 100); pollen = tag.getBoolean("Pollen");
		ignited = tag.getBoolean("Ignited"); charged = tag.getBoolean("Charged");
		fuse = net.minecraft.util.Mth.clamp(tag.getInt("Fuse"), 0, 24000);
		flower = tag.contains("Flower") ? BlockPos.of(tag.getLong("Flower")) : null; sync();
	}
}

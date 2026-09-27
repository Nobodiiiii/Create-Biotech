package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.EnumSet;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Ownership survives the fragment stage, world saves, and subsequent vanilla slime splits. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class SlimeCompanionHandler {
	private static final String OWNER = "CreateBiotechSlimeOwner";
	private static final String SITTING = "CreateBiotechSlimeSitting";
	private SlimeCompanionHandler() {}

	@Nullable public static UUID ownerOf(Entity entity) {
		if (entity instanceof SlimeBionicEntity bionic) return bionic.getBionicOwnerId();
		if (entity instanceof TamableAnimal animal && animal.isTame()) return animal.getOwnerUUID();
		if (entity instanceof AbstractHorse horse && horse.isTamed()) return horse.getOwnerUUID();
		if (entity instanceof Slime && entity.getPersistentData().hasUUID(OWNER))
			return entity.getPersistentData().getUUID(OWNER);
		return null;
	}
	public static void tame(Slime slime, @Nullable UUID owner) {
		if (owner == null) return;
		slime.getPersistentData().putUUID(OWNER, owner);
		slime.setPersistenceRequired();
		slime.setTarget(null);
	}
	@SubscribeEvent public static void onSplit(MobSplitEvent event) {
		UUID owner = ownerOf(event.getParent());
		for (Mob child : event.getChildren()) if (child instanceof Slime slime) tame(slime, owner);
	}
	@SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide || !(event.getEntity() instanceof Slime slime) || ownerOf(slime) == null) return;
		slime.setPersistenceRequired();
		slime.targetSelector.removeAllGoals(goal -> true);
		slime.goalSelector.removeAllGoals(goal -> goal instanceof CompanionGoal);
		slime.goalSelector.addGoal(0, new CompanionGoal(slime));
	}
	@SubscribeEvent public static void onTarget(LivingChangeTargetEvent event) {
		if (!(event.getEntity() instanceof Slime slime) || ownerOf(slime) == null) return;
		LivingEntity target = event.getNewAboutToBeSetTarget();
		if (target != null) event.setNewAboutToBeSetTarget(null);
	}
	@SubscribeEvent public static void onDamage(LivingIncomingDamageEvent event) {
		if (event.getSource().getEntity() instanceof Slime slime && ownerOf(slime) != null)
			event.setCanceled(true); // Vanilla slimes also hurt players by touching them without a target.
	}
	@SubscribeEvent public static void onInteract(PlayerInteractEvent.EntityInteract event) {
		if (!(event.getTarget() instanceof LivingEntity living) || ownerOf(living) == null) return;
		Player player = event.getEntity();
		ItemStack held = event.getItemStack();
		boolean bottle = held.is(CBFluids.LIQUID_LIVING_SLIME_BOTTLE.get());
		boolean bucket = held.is(CBFluids.LIQUID_LIVING_SLIME_BUCKET.get());
		if ((bottle || bucket) && living.getHealth() < living.getMaxHealth()) {
			if (!living.level().isClientSide) {
				living.heal(bottle ? 4 : 16);
				if (!player.getAbilities().instabuild) {
					held.shrink(1);
					ItemStack container = new ItemStack(bottle ? Items.GLASS_BOTTLE : Items.BUCKET);
					if (held.isEmpty()) player.setItemInHand(event.getHand(), container);
					else if (!player.getInventory().add(container)) player.drop(container, false);
				}
			}
		} else if (living instanceof Slime && held.isEmpty() && player.getUUID().equals(ownerOf(living))) {
			if (!living.level().isClientSide)
				living.getPersistentData().putBoolean(SITTING, !living.getPersistentData().getBoolean(SITTING));
		} else return;
		event.setCancellationResult(InteractionResult.sidedSuccess(living.level().isClientSide));
		event.setCanceled(true);
	}
	private static final class CompanionGoal extends Goal {
		private final Slime slime;
		CompanionGoal(Slime slime) {
			this.slime = slime;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}
		@Override public boolean canUse() { return ownerOf(slime) != null; }
		@Override public boolean requiresUpdateEveryTick() { return true; }
		@Override public void tick() {
			Player owner = slime.level().getPlayerByUUID(ownerOf(slime));
			if (slime.getPersistentData().getBoolean(SITTING) || owner == null || owner.isSpectator()
				|| slime.distanceToSqr(owner) <= 9) { slime.getNavigation().stop(); return; }
			if (slime.tickCount % 10 == 0) slime.getNavigation().moveTo(owner, 1.0);
			var path = slime.getNavigation().getPath();
			if (path == null || path.isDone()) return;
			Vec3 direction = path.getNextEntityPos(slime).subtract(slime.position());
			slime.lookAt(owner, 30, 30);
			if (slime.onGround() || slime.isInWater()) {
				Vec3 step = new Vec3(direction.x, 0, direction.z).normalize().scale(0.25);
				slime.setDeltaMovement(step.x, slime.isInWater() ? 0.15 : 0.42, step.z);
			}
		}
	}
}

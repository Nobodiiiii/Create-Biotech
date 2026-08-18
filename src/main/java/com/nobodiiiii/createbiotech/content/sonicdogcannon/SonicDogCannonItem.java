package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.client.SonicDogCannonArmPose;
import com.nobodiiiii.createbiotech.client.SonicDogCannonItemRenderer;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonChargeSoundPacket.Action;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBEnchantments;
import com.nobodiiiii.createbiotech.registry.CBSoundEvents;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.render.CustomRenderedItems;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class SonicDogCannonItem extends Item {

	public static final int MAX_DURABILITY = 100;
	public static final int MIN_CHARGE_TICKS = 10;
	public static final int FULL_CHARGE_TICKS = 40;
	private static final int COOLDOWN_TICKS = 20;
	private static final int VOICE_PACK_CHARGE_START_TICKS = 36;
	public static final double MIN_NORMAL_RANGE = 4.0d;
	public static final double MAX_NORMAL_RANGE = 16.0d;
	private static final double MIN_SHRIEK_RANGE = 8.0d;
	private static final double MAX_SHRIEK_RANGE = 24.0d;
	public static final float MIN_SHRIEK_DAMAGE = 6.0f;
	public static final float MAX_SHRIEK_DAMAGE = 12.0f;
	public static final float SONIC_BOOM_DAMAGE_PER_LEVEL = 2.0f;
	private static final double BEAM_HIT_RADIUS = 0.6d;

	public SonicDogCannonItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return slotChanged || newStack.getItem() != oldStack.getItem();
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
			&& AllItems.WRENCH.isIn(player.getOffhandItem())) {
			if (!SonicDogCannonUpgrade.hasInstalledUpgrade(stack))
				return InteractionResultHolder.fail(stack);

			if (!level.isClientSide) {
				SonicDogCannonUpgrade removed = SonicDogCannonUpgrade.removeLastInstalled(stack);
				if (removed != null)
					player.getInventory().placeItemBackInInventory(removed.getRemovalRefund());
				AllSoundEvents.WRENCH_REMOVE.playOnServer(level, player.blockPosition(), 1.0f,
					level.getRandom().nextFloat() * 0.5f + 0.5f);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		if (player.isShiftKeyDown() && SonicDogCannonUpgrade.SCOPE.isInstalled(stack)) {
			if (!level.isClientSide)
				setScopeFolded(stack, !isScopeFolded(stack));
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		player.startUsingItem(hand);
		if (!level.isClientSide) {
			Action action = SonicDogCannonUpgrade.VOICE_PACK.isInstalled(stack)
				? Action.VOICE_PACK_START
				: Action.DEFAULT_START;
			sendChargeSound(player, action);
		}
		return InteractionResultHolder.consume(stack);
	}

	public static boolean isScopeFolded(ItemStack stack) {
		CompoundTag tag = CBItemData.getReadOnly(stack);
		return tag != null && tag.getBoolean(SonicDogCannonUpgrade.SCOPE_FOLDED_TAG);
	}

	public static void setScopeFolded(ItemStack stack, boolean folded) {
		CBItemData.edit(stack, tag -> {
			if (folded)
				tag.putBoolean(SonicDogCannonUpgrade.SCOPE_FOLDED_TAG, true);
			else
				tag.remove(SonicDogCannonUpgrade.SCOPE_FOLDED_TAG);
		});
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return 72000;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		// The cannon supplies its own two-handed pose; vanilla's bow pose would override it.
		return UseAnim.NONE;
	}

	@Override
	public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
		// Match the Potato Cannon: successful block/item interactions should not play the
		// vanilla full arm swing. The interaction still completes normally.
		return true;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
		if (level.isClientSide || !(entity instanceof Player player))
			return;

		int elapsed = getUseDuration(stack) - remainingUseDuration;
		if (elapsed == VOICE_PACK_CHARGE_START_TICKS
			&& SonicDogCannonUpgrade.VOICE_PACK.isInstalled(stack))
			sendChargeSound(player, Action.VOICE_PACK_LOOP);

		if (elapsed <= 0 || elapsed % 20 != 0)
			return;

		if (!BacktankUtil.canAbsorbDamage(player, MAX_DURABILITY)) {
			EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND
				? EquipmentSlot.MAINHAND
				: EquipmentSlot.OFFHAND;
			stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(slot));
		}
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (!(entity instanceof Player player) || !(level instanceof ServerLevel serverLevel))
			return;
		boolean hasVoicePack = SonicDogCannonUpgrade.VOICE_PACK.isInstalled(stack);
		sendChargeSound(player, Action.STOP);

		int chargeTicks = Math.max(0, getUseDuration(stack) - timeLeft);
		if (chargeTicks < MIN_CHARGE_TICKS)
			return;

		double charge = Math.min(chargeTicks - MIN_CHARGE_TICKS, FULL_CHARGE_TICKS - MIN_CHARGE_TICKS)
			/ (double) (FULL_CHARGE_TICKS - MIN_CHARGE_TICKS);
		Vec3 direction = player.getLookAngle().normalize();
		Vec3 origin = player.getEyePosition().add(direction.scale(0.5d));
		int punchLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);

		if (SonicDogCannonUpgrade.SHRIEK_SONIC_BOOM.isInstalled(stack)) {
			double range = MIN_SHRIEK_RANGE + (MAX_SHRIEK_RANGE - MIN_SHRIEK_RANGE) * charge;
			int enchantmentLevel = EnchantmentHelper.getItemEnchantmentLevel(CBEnchantments.SONIC_BOOM.get(), stack);
			float damage = (float) (MIN_SHRIEK_DAMAGE
				+ (MAX_SHRIEK_DAMAGE - MIN_SHRIEK_DAMAGE) * charge)
				+ SONIC_BOOM_DAMAGE_PER_LEVEL * enchantmentLevel;
			fireSonicBoom(serverLevel, player, origin, direction, range, damage, punchLevel);
		} else {
			double range = MIN_NORMAL_RANGE + (MAX_NORMAL_RANGE - MIN_NORMAL_RANGE) * charge;
			SonicDogConeWave.fire(serverLevel, player, origin, direction, range, punchLevel);
		}
		sendGearAnimation(player, chargeTicks);

		SoundEvent fireSound = hasVoicePack
			? chargeTicks >= FULL_CHARGE_TICKS
				? CBSoundEvents.SONIC_DOG_CANNON_VOICE_PACK_FIRE_FULL.get()
				: CBSoundEvents.SONIC_DOG_CANNON_VOICE_PACK_FIRE_PARTIAL.get()
			: SoundEvents.WOLF_AMBIENT;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), fireSound,
			SoundSource.PLAYERS, 1.0f, 1.0f);
		player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		player.awardStat(Stats.ITEM_USED.get(this));
	}

	private static void sendChargeSound(Player player, Action action) {
		SonicDogCannonChargeSoundPacket packet =
			new SonicDogCannonChargeSoundPacket(player.getId(), action);
		CBPackets.sendToTrackingEntity(packet, player);
		if (player instanceof ServerPlayer serverPlayer)
			CBPackets.sendToPlayer(packet, serverPlayer);
	}

	private static void sendGearAnimation(Player player, int chargeTicks) {
		SonicDogCannonGearAnimationPacket packet = new SonicDogCannonGearAnimationPacket(
			player.getId(), player.getUsedItemHand(), chargeTicks);
		CBPackets.sendToTrackingEntity(packet, player);
		if (player instanceof ServerPlayer serverPlayer)
			CBPackets.sendToPlayer(packet, serverPlayer);
	}

	private static void fireSonicBoom(ServerLevel level, Player player, Vec3 origin, Vec3 direction,
		double range, float damage, int punchLevel) {
		Vec3 end = origin.add(direction.scale(range));
		int particleCount = Math.max(1, (int) Math.ceil(range));
		for (int i = 1; i <= particleCount; i++) {
			double distance = Math.min(i, range);
			Vec3 particlePos = origin.add(direction.scale(distance));
			level.sendParticles(ParticleTypes.SONIC_BOOM,
				particlePos.x, particlePos.y, particlePos.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
		}

		AABB searchBox = new AABB(origin, end).inflate(BEAM_HIT_RADIUS);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox,
			candidate -> candidate != player && candidate.isAlive() && !candidate.isSpectator())) {
			if (target.getBoundingBox().inflate(BEAM_HIT_RADIUS).clip(origin, end).isPresent()
				&& target.hurt(level.damageSources().sonicBoom(player), damage)) {
				SonicDogCannonKnockback.applyWarden(target, direction);
				SonicDogCannonKnockback.applyPunch(target, direction, punchLevel);
			}
		}
	}

	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
		if (enchantment == Enchantments.UNBREAKING
			|| enchantment == Enchantments.MENDING
			|| enchantment == Enchantments.VANISHING_CURSE
			|| enchantment == Enchantments.MOB_LOOTING
			|| enchantment == Enchantments.PUNCH_ARROWS
			|| enchantment == CBEnchantments.SONIC_BOOM.get())
			return true;
		return super.canApplyAtEnchantingTable(stack, enchantment);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return BacktankUtil.isBarVisible(stack, MAX_DURABILITY);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return BacktankUtil.getBarWidth(stack, MAX_DURABILITY);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BacktankUtil.getBarColor(stack, MAX_DURABILITY);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		// Forge extends ArmPose at runtime. Create it during item initialization, before
		// HumanoidModel's enum switch table can be initialized with only vanilla poses.
		HumanoidModel.ArmPose armPose = SonicDogCannonArmPose.ARM_POSE;
		CustomRenderedItems.register(this);
		consumer.accept(new IClientItemExtensions() {
			private final SonicDogCannonItemRenderer renderer = new SonicDogCannonItemRenderer();

			@Override
			public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
				ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
				if (!player.isUsingItem() || !player.getUseItem().is(SonicDogCannonItem.this))
					return false;

				HumanoidArm usedArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
					? player.getMainArm()
					: player.getMainArm().getOpposite();
				if (arm != usedArm)
					return false;

				// Vanilla resets the hand's equip height after every successful use(), which normally
				// lowers the item by 0.6 blocks before raising it again. Keep the neutral held transform
				// throughout charging while the internal equip height catches up in the background.
				float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
				poseStack.translate(side * 0.56f, -0.52f, -0.72f);
				return true;
			}

			@Override
			public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
				return armPose;
			}

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		});
	}
}

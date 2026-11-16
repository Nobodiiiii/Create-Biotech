package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public final class CaptureNetItem extends TieredItem {
	public static final int ANIMATION_TICKS = 12;
	public static final TagKey<EntityType<?>> CAPTURE_BLACKLIST =
		TagKey.create(Registries.ENTITY_TYPE, CreateBiotech.asResource("capture_net_blacklist"));
	private static final String ESCAPE_AT = "CaptureNetEscapeAt";
	private static final String ESCAPE_DURATION = "CaptureNetEscapeDuration";
	private static final String ESCAPE_COOLDOWN_UNTIL = "CaptureNetEscapeCooldownUntil";
	private static final String ACTION_AT = "CaptureNetActionAt";
	private static final String RELEASING = "CaptureNetReleasing";
	private static final int[] RELEASE_HEIGHTS = {0, 1, -1, 2};
	private final CaptureNetTier tier;

	public CaptureNetItem(CaptureNetTier tier, Properties properties) {
		super(tier.toolTier(), properties.stacksTo(1).attributes(ShovelItem.createAttributes(tier.toolTier(), 1.5f, -3.0f)
			.withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
				new AttributeModifier(CreateBiotech.asResource("capture_net_entity_interaction_range"), 1,
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)));
		this.tier = tier;
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
		return itemAbility == ItemAbilities.SWORD_SWEEP;
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker.level() instanceof ServerLevel level)
			damageNet(stack, 2, level, attacker, attacker.position());
	}

	public CaptureNetTier tier() {
		return tier;
	}

	@Override
	public void verifyComponentsAfterLoad(ItemStack stack) {
		super.verifyComponentsAfterLoad(stack);
		CapturedEntityBoxHelper.migrateReleasedAssembly(stack);
	}

	public static boolean isOccupied(ItemStack stack) {
		return CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	/** Called before the mob's own interaction, so villagers, horses and pets can be caught. */
	public void interact(ItemStack stack, Player player, InteractionHand hand, Entity target) {
		if (player.level().isClientSide() || player.isSpectator() || !player.mayBuild()
			|| player.getCooldowns().isOnCooldown(this) || isEscapeCoolingDown(stack, player.level()))
			return;
		if (player.isShiftKeyDown()) {
			if (isOccupied(stack))
				releaseByPlayer(stack, player, player.position().add(player.getLookAngle().scale(1.5)));
			return;
		}
		if (isOccupied(stack)) {
			message(player, "occupied");
			return;
		}
		if (!(CapturedEntityBoxHelper.resolveLivingTarget(target) instanceof Mob mob)
			|| !mob.isAlive() || !mob.getType().canSerialize() || mob.getType().is(CAPTURE_BLACKLIST)) {
			message(player, "invalid_target");
			return;
		}
		if (mob.isPassenger() || mob.isVehicle() || mob.isLeashed()) {
			message(player, "attached");
			return;
		}
		if (!Float.isFinite(mob.getHealth()) || mob.getHealth() > tier.healthLimit()) {
			player.displayClientMessage(Component.translatable("item.create_biotech.capture_net.too_healthy",
				formatHealth(tier.healthLimit())), true);
			return;
		}
		if (!CapturedEntityBoxHelper.captureEntity(stack, mob))
			return;
		var config = CBConfigs.SERVER.captureNet;
		int fastest = Math.min(config.fastestEscapeSeconds.get(), config.slowestEscapeSeconds.get());
		int slowest = Math.max(config.fastestEscapeSeconds.get(), config.slowestEscapeSeconds.get());
		double healthRatio = Mth.clamp(mob.getHealth() / tier.healthLimit(), 0, 1);
		int duration = (int) Math.round(20 * Mth.lerp(healthRatio, slowest, fastest));
		long now = clock(player.level());
		CBItemData.edit(stack, data -> {
			data.putLong(ESCAPE_AT, now + duration);
			data.putInt(ESCAPE_DURATION, duration);
		});
		mob.discard();
		damageNet(stack, 1, (ServerLevel) player.level(), player, player.position());
		if (stack.isEmpty()) return;
		animate(stack, player, hand, false);
		player.level().playSound(null, player.blockPosition(), SoundEvents.LEASH_KNOT_PLACE,
			SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !isOccupied(context.getItemInHand()))
			return InteractionResult.PASS;
		if (player.getCooldowns().isOnCooldown(this)
			|| isEscapeCoolingDown(context.getItemInHand(), context.getLevel()))
			return InteractionResult.FAIL;
		if (!player.isShiftKeyDown()) {
			if (!context.getLevel().isClientSide()) message(player, "occupied");
			return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
		}
		if (!context.getLevel().isClientSide()) {
			Vec3 origin = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
			releaseByPlayer(context.getItemInHand(), player, origin);
		}
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && !player.isSpectator() && player.mayBuild()
			&& !player.getCooldowns().isOnCooldown(this) && !isEscapeCoolingDown(stack, level)) {
			if (isOccupied(stack)) {
				if (player.isShiftKeyDown())
					releaseByPlayer(stack, player, player.position().add(player.getLookAngle().scale(1.5)));
				else message(player, "occupied");
			} else {
				animate(stack, player, hand, false);
				level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
					SoundSource.PLAYERS, 0.5f, 1.3f);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	private void releaseByPlayer(ItemStack stack, Player player, Vec3 origin) {
		if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || !player.mayBuild()
			|| player.getCooldowns().isOnCooldown(this) || isEscapeCoolingDown(stack, level))
			return;
		if (release(stack, level, origin, player))
			animate(stack, player, player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND
				: InteractionHand.OFF_HAND, true);
		else message(player, "no_space");
	}

	/** Deadline survives slot changes, dropping, storage, logout and server restarts. No per-tick NBT writes. */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
		if (!(level instanceof ServerLevel server)) return;
		if (holder instanceof Player player) {
			int remaining = escapeCooldownTicks(stack, server);
			if (remaining > 0 && !player.getCooldowns().isOnCooldown(this))
				player.getCooldowns().addCooldown(this, remaining);
		}
		if (Math.floorMod(level.getGameTime() + slot, 10) == 0)
			tryEscape(stack, server, holder);
	}

	@Override
	public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
		if (entity.level() instanceof ServerLevel server && entity.tickCount % 10 == 0
			&& isEscapeDue(stack, server)) {
			// Mutating the tracked stack in place does not mark ItemEntity's data dirty.
			ItemStack updated = stack.copy();
			if (tryEscape(updated, server, entity)) {
				if (updated.isEmpty()) {
					entity.discard();
					return true;
				}
				entity.setItem(updated);
			}
		}
		return false;
	}

	private static boolean isEscapeDue(ItemStack stack, ServerLevel level) {
		CompoundTag data = CBItemData.getReadOnly(stack);
		return data != null && isOccupied(stack) && clock(level) >= data.getLong(ESCAPE_AT);
	}

	private boolean tryEscape(ItemStack stack, ServerLevel level, Entity holder) {
		if (!isEscapeDue(stack, level) || !release(stack, level, holder.position(), null))
			return false;
		var config = CBConfigs.SERVER.captureNet;
		int cooldown = config.escapeCooldownSeconds.get() * 20;
		CBItemData.edit(stack, tag -> tag.putLong(ESCAPE_COOLDOWN_UNTIL, clock(level) + cooldown));
		if (holder instanceof Player player && cooldown > 0)
			player.getCooldowns().addCooldown(this, cooldown);
		damageNet(stack, Mth.ceil(stack.getMaxDamage() * config.escapeDurabilityFraction.get()),
			level, holder instanceof LivingEntity living ? living : null, holder.position());
		return true;
	}

	public static boolean isEscapeCoolingDown(ItemStack stack, Level level) {
		return escapeCooldownTicks(stack, level) > 0;
	}

	private static int escapeCooldownTicks(ItemStack stack, Level level) {
		CompoundTag data = CBItemData.getReadOnly(stack);
		return data == null ? 0 : (int) Mth.clamp(data.getLong(ESCAPE_COOLDOWN_UNTIL) - clock(level), 0, 72000);
	}

	/** Negative means no captured creature; older nets use the configured maximum duration. */
	public static float remainingEscapeFraction(ItemStack stack, Level level) {
		CompoundTag data = CBItemData.getReadOnly(stack);
		if (data == null || !isOccupied(stack) || !data.contains(ESCAPE_AT)) return -1;
		int duration = data.getInt(ESCAPE_DURATION);
		if (duration <= 0) {
			var config = CBConfigs.SERVER.captureNet;
			int fastest = CBConfigs.SERVER_SPEC.isLoaded() ? config.fastestEscapeSeconds.get()
				: config.fastestEscapeSeconds.getDefault();
			int slowest = CBConfigs.SERVER_SPEC.isLoaded() ? config.slowestEscapeSeconds.get()
				: config.slowestEscapeSeconds.getDefault();
			duration = 20 * Math.max(fastest, slowest);
		}
		return Mth.clamp((float) (data.getLong(ESCAPE_AT) - clock(level)) / Math.max(1, duration), 0, 1);
	}

	/** Always use vanilla durability processing, including Unbreaking and creative immunity. */
	private static void damageNet(ItemStack stack, int amount, ServerLevel level, @Nullable LivingEntity holder, Vec3 origin) {
		if (amount <= 0) return;
		ItemStack captured = isOccupied(stack) ? stack.copy() : ItemStack.EMPTY;
		EquipmentSlot slot = holder == null ? null : holder.getMainHandItem() == stack ? EquipmentSlot.MAINHAND
			: holder.getOffhandItem() == stack ? EquipmentSlot.OFFHAND : null;
		stack.hurtAndBreak(amount, level, holder, item -> {
			if (slot != null)
				holder.onEquippedItemBroken(item, slot);
			else
				level.playSound(null, BlockPos.containing(origin), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1);
			if (captured.isEmpty()) return;
			// A broken net releases its occupant even if every nearby safe position is blocked.
			boolean released = CapturedEntityBoxHelper.releaseCapturedEntity(captured, level, origin, Vec3.ZERO, entity -> {
				if (!findReleasePosition(entity, level, origin, null)) entity.setPos(origin);
				return true;
			});
			if (!released) {
				// Preserve the payload at zero durability if a release hook vetoes spawning.
				captured.setDamageValue(captured.getMaxDamage());
				level.addFreshEntity(new ItemEntity(level, origin.x, origin.y, origin.z, captured));
			}
		});
	}

	private static boolean release(ItemStack stack, ServerLevel level, Vec3 origin, Player player) {
		// Only clear the stored creature after a valid, unobstructed spawn was actually accepted.
		boolean released = CapturedEntityBoxHelper.releaseCapturedEntity(stack, level, origin, Vec3.ZERO,
			entity -> findReleasePosition(entity, level, origin, player));
		if (!released) return false;
		CBItemData.edit(stack, data -> {
			data.remove(ESCAPE_AT);
			data.remove(ESCAPE_DURATION);
		});
		level.playSound(null, BlockPos.containing(origin), SoundEvents.LEASH_KNOT_BREAK,
			SoundSource.PLAYERS, 0.8f, 1.1f);
		return true;
	}

	private static boolean findReleasePosition(Entity entity, ServerLevel level, Vec3 origin, Player player) {
		for (int radius = 0; radius <= 3; radius++) {
			for (int height : RELEASE_HEIGHTS) {
				for (int direction = 0; direction < (radius == 0 ? 1 : 8); direction++) {
					double angle = direction * Math.PI / 4;
					Vec3 candidate = origin.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
					BlockPos pos = BlockPos.containing(candidate);
					entity.setPos(candidate);
					if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
						&& level.getWorldBorder().isWithinBounds(entity.getBoundingBox())
						&& entity.getBoundingBox().minY >= level.getMinBuildHeight()
						&& entity.getBoundingBox().maxY < level.getMaxBuildHeight()
						&& (player == null || level.mayInteract(player, pos))
						&& level.noCollision(entity, entity.getBoundingBox()))
						return true;
				}
			}
		}
		return false;
	}

	private static void animate(ItemStack stack, Player player, InteractionHand hand, boolean releasing) {
		CBItemData.edit(stack, data -> {
			data.putLong(ACTION_AT, clock(player.level()));
			data.putBoolean(RELEASING, releasing);
		});
		player.getCooldowns().addCooldown(stack.getItem(), ANIMATION_TICKS);
		player.swing(hand, true);
	}

	public static float animationProgress(ItemStack stack, double renderTime) {
		CompoundTag data = CBItemData.getReadOnly(stack);
		if (data == null || !data.contains(ACTION_AT)) return -1;
		double elapsed = renderTime - data.getLong(ACTION_AT);
		return elapsed >= 0 && elapsed < ANIMATION_TICKS ? (float) (elapsed / ANIMATION_TICKS) : -1;
	}

	public static boolean isReleasing(ItemStack stack) {
		CompoundTag data = CBItemData.getReadOnly(stack);
		return data != null && data.getBoolean(RELEASING);
	}

	private static long clock(Level level) {
		return level instanceof ServerLevel server ? server.getServer().overworld().getGameTime()
			: level.getGameTime();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.create_biotech.capture_net.limit", formatHealth(tier.healthLimit()))
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.create_biotech.capture_net.instructions")
			.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.create_biotech.capture_net.lethal_capture")
			.withStyle(ChatFormatting.DARK_GRAY));
		if (!isOccupied(stack)) return;
		EntityType<?> type = CapturedEntityBoxHelper.getCapturedEntityType(stack);
		if (type != null)
			tooltip.add(Component.translatable("item.create_biotech.capture_net.contains", type.getDescription())
				.withStyle(ChatFormatting.GOLD));
		CompoundTag data = CBItemData.getReadOnly(stack);
		if (context.level() != null && data != null) {
			long seconds = Math.max(0, (data.getLong(ESCAPE_AT) - clock(context.level()) + 19) / 20);
			tooltip.add(Component.translatable("item.create_biotech.capture_net.escape", seconds)
				.withStyle(seconds <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW));
		}
	}

	private static String formatHealth(double health) {
		return java.math.BigDecimal.valueOf(health).stripTrailingZeros().toPlainString();
	}

	private static void message(Player player, String key) {
		player.displayClientMessage(Component.translatable("item.create_biotech.capture_net." + key), true);
	}
}

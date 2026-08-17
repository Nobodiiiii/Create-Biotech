package com.yision.allay.item.allaycourier;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.nobodiiiii.createbiotech.foundation.item.BlockCenteredRenderedLivingEntityItem;
import com.yision.allay.entity.courier.AllayCourierEntity;
import com.yision.allay.logistics.courier.AllayCourierDispatchService;
import com.yision.allay.registry.AllEntityTypes;
import com.yision.allay.registry.AllItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class AllayCourierItem extends BlockCenteredRenderedLivingEntityItem<AllayCourierEntity> {
	private static final float ITEM_RENDER_SCALE = 1.5f;
	private static final int EMPTY_CARRIER_MAX_STACK_SIZE = 64;
	private static final String CARGO_KEY = "Cargo";
	private static final String HEADING_KEY = "Heading";
	private static final double PLAYER_LAUNCH_FORWARD_OFFSET = 0.75;
	private static final double PLAYER_LAUNCH_EYE_OFFSET = -0.35;

	public AllayCourierItem(Properties properties) {
		super(properties, AllEntityTypes.ALLAY_COURIER, ITEM_RENDER_SCALE);
	}

	@Override
	public void configureRenderedEntity(AllayCourierEntity courier, ItemStack stack,
		ItemDisplayContext displayContext) {
		configureRenderedCourier(courier, copyCargoPackage(stack), true);
	}

	/**
	 * Measured without cargo so a carried package cannot drag the courier off the
	 * center the other display contexts are aligned to.
	 */
	@Override
	public void configureRenderedEntityForGeometryMeasurement(AllayCourierEntity courier, ItemStack stack,
		ItemDisplayContext displayContext) {
		configureRenderedCourier(courier, ItemStack.EMPTY, true);
	}

	/**
	 * Only the fixed and dropped presentations keep the delivery heading; in hand
	 * and in the GUI the courier faces the viewer.
	 */
	@Override
	public float getRenderedEntityYRotation(ItemStack stack, ItemDisplayContext displayContext) {
		if (displayContext != ItemDisplayContext.FIXED && displayContext != ItemDisplayContext.GROUND)
			return 0.0f;
		return hasHeadingAngle(stack) ? getHeadingAngle(stack) : 0.0f;
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return isPlainCarrier(stack) ? EMPTY_CARRIER_MAX_STACK_SIZE : 1;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
				tryLaunch(serverPlayer, stack);
			}
		} else if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			openMenu(serverPlayer, stack, usedHand);
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}

		ItemStack stack = context.getItemInHand();
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
				tryLaunch(serverPlayer, stack);
			}
		} else if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			openMenu(serverPlayer, stack, context.getHand());
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static boolean tryLaunch(ServerPlayer player, ItemStack stack) {
		Vec3 launchDirection = horizontalLaunchDirection(player);
		Vec3 spawnPosition = player.getEyePosition()
			.add(0, PLAYER_LAUNCH_EYE_OFFSET, 0)
			.add(launchDirection.scale(PLAYER_LAUNCH_FORWARD_OFFSET));
		ItemStack box = copyCargoPackage(stack);
		if (!AllayCourierDispatchService.dispatchFromPlayer(player, box, spawnPosition, launchDirection)) {
			player.displayClientMessage(
				Component.translatable("gui.create_biotech.allay_courier.invalid_target")
					.withStyle(ChatFormatting.RED),
				true);
			return false;
		}

		player.level().playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
			SoundSource.PLAYERS, 0.8f, 1.0f);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return true;
	}

	private static Vec3 horizontalLaunchDirection(Player player) {
		Vec3 direction = player.getLookAngle().multiply(1, 0, 1);
		if (direction.lengthSqr() < 1.0E-6) {
			direction = Vec3.directionFromRotation(0, player.getYRot()).multiply(-1, 0, -1);
		}
		return direction.normalize();
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
		TooltipFlag tooltipFlag) {
		ItemStack cargoPackage = copyCargoPackage(stack);
		if (!cargoPackage.isEmpty()) {
			cargoPackage.getItem().appendHoverText(cargoPackage, level, tooltipComponents, tooltipFlag);
		}
	}

	protected static void openMenu(ServerPlayer serverPlayer, ItemStack stack, InteractionHand usedHand) {
		LegacyAllayCourierClipboardData.recoverClipboard(serverPlayer);
		stack = isolateCarrierForEditing(serverPlayer, stack, usedHand);
		ItemStack openedStack = stack;
		NetworkHooks.openScreen(serverPlayer,
			new SimpleMenuProvider((id, inv, p) -> AllayCourierMenu.create(id, inv, openedStack, usedHand),
				Component.translatable("item.create_biotech.allay_courier")),
			buffer -> {
				buffer.writeItem(openedStack);
				buffer.writeEnum(usedHand);
			});
	}

	private static ItemStack isolateCarrierForEditing(ServerPlayer player, ItemStack stack, InteractionHand hand) {
		if (stack.getCount() <= 1) {
			return stack;
		}

		ItemStack remainder = stack.copyWithCount(stack.getCount() - 1);
		stack.setCount(1);
		storeOutsideEditedHand(player, remainder, hand);
		return stack;
	}

	private static void storeOutsideEditedHand(ServerPlayer player, ItemStack remainder, InteractionHand hand) {
		Inventory inventory = player.getInventory();
		int excludedSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;

		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && !remainder.isEmpty(); slot++) {
			if (slot == excludedSlot) {
				continue;
			}
			ItemStack existing = inventory.getItem(slot);
			if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remainder)) {
				continue;
			}
			int limit = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize());
			int moved = Math.min(remainder.getCount(), limit - existing.getCount());
			if (moved > 0) {
				existing.grow(moved);
				remainder.shrink(moved);
			}
		}

		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && !remainder.isEmpty(); slot++) {
			if (slot == excludedSlot || !inventory.getItem(slot).isEmpty()) {
				continue;
			}
			int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
			inventory.setItem(slot, remainder.copyWithCount(moved));
			remainder.shrink(moved);
		}

		if (!remainder.isEmpty()) {
			player.drop(remainder.copy(), false);
		}
		inventory.setChanged();
	}

	public static ItemStack createLoaded(ItemStack packageStack) {
		ItemStack allay = AllItems.ALLAY_COURIER.asStack();
		loadCargo(allay, packageStack);
		return allay;
	}

	public static boolean loadCargo(ItemStack allay, ItemStack packageStack) {
		AllayCourierCargo cargo = new AllayCourierCargo(packageStack);
		if (!cargo.isValid()) {
			remove(allay, CARGO_KEY);
			return false;
		}

		allay.getOrCreateTag().put(CARGO_KEY, cargo.packageCopy().save(new CompoundTag()));
		return true;
	}

	public static boolean updateCargoAddress(ItemStack allay, String address) {
		if (!allay.is(AllItems.ALLAY_COURIER.get())) {
			return false;
		}
		ItemStack packageStack = copyCargoPackage(allay);
		if (packageStack.isEmpty()) {
			return false;
		}

		PackageItem.clearAddress(packageStack);
		String normalizedAddress = address == null ? "" : address.trim();
		if (!normalizedAddress.isEmpty()) {
			PackageItem.addAddress(packageStack, normalizedAddress);
		}
		return loadCargo(allay, packageStack);
	}

	public static ItemStack copyCargoPackage(ItemStack allay) {
		CompoundTag tag = allay.getTag();
		if (tag == null || !tag.contains(CARGO_KEY, Tag.TAG_COMPOUND)) {
			return ItemStack.EMPTY;
		}
		ItemStack packageStack = ItemStack.of(tag.getCompound(CARGO_KEY));
		return PackageItem.isPackage(packageStack) ? packageStack.copy() : ItemStack.EMPTY;
	}

	public static boolean hasCargo(ItemStack allay) {
		return !copyCargoPackage(allay).isEmpty();
	}

	public static boolean isPlainCarrier(ItemStack stack) {
		return stack.is(AllItems.ALLAY_COURIER.get())
			&& !hasCargo(stack);
	}

	public static void clearCargo(ItemStack allay) {
		remove(allay, CARGO_KEY);
	}

	public static void setHeadingAngle(ItemStack stack, int headingAngle) {
		stack.getOrCreateTag().putInt(HEADING_KEY, Math.floorMod(headingAngle, 360));
	}

	public static boolean hasHeadingAngle(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.contains(HEADING_KEY, Tag.TAG_INT);
	}

	public static int getHeadingAngle(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag == null ? 0 : Math.floorMod(tag.getInt(HEADING_KEY), 360);
	}

	private static void remove(ItemStack stack, String key) {
		CompoundTag tag = stack.getTag();
		if (tag != null) {
			tag.remove(key);
		}
	}

	static void configureRenderedCourier(AllayCourierEntity courier, ItemStack cargoPackage,
		boolean renderLogisticsHat) {
		courier.setPackage(cargoPackage);
		courier.setPhase(AllayCourierEntity.Phase.WAITING);
		courier.setRenderLogisticsHat(renderLogisticsHat);
		courier.setNoGravity(true);
		courier.setDeltaMovement(Vec3.ZERO);
		courier.setPos(0, 0, 0);
	}
}

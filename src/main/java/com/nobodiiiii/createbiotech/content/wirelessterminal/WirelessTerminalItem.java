package com.nobodiiiii.createbiotech.content.wirelessterminal;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class WirelessTerminalItem extends Item {

	private static final String BOUND_POS_KEY = "BoundPos";
	private static final String BOUND_DIMENSION_KEY = "BoundDimension";

	public WirelessTerminalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget,
		InteractionHand usedHand) {
		BlockPos stockTickerPos = StockTickerInteractionHandler.getStockTickerPosition(interactionTarget);
		if (stockTickerPos == null)
			return InteractionResult.PASS;
		return bindOrOpenAt(player, player.level(), stack, stockTickerPos);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		if (!(level.getBlockEntity(clickedPos) instanceof BlazeBurnerBlockEntity))
			return InteractionResult.PASS;

		StockTickerBlockEntity stockTicker = BlazeBurnerBlockEntity.getStockTicker(level, clickedPos);
		if (stockTicker == null)
			return InteractionResult.PASS;

		return bindOrOpenAt(context.getPlayer(), level, context.getItemInHand(), stockTicker.getBlockPos());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		BoundTarget boundTarget = getBoundTarget(stack);
		if (boundTarget == null) {
			if (!level.isClientSide)
				sendStatus(player, "item.create_biotech.wireless_terminal.not_bound", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		boolean sameDimension = boundTarget.dimension().equals(level.dimension().location());
		if (!sameDimension && !allowsCrossDimensionTerminal()) {
			if (!level.isClientSide)
				sendStatus(player, "item.create_biotech.wireless_terminal.wrong_dimension", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		if (sameDimension && !isWithinRange(player, boundTarget.pos())) {
			if (!level.isClientSide)
				sendStatus(player, "item.create_biotech.wireless_terminal.out_of_range", ChatFormatting.RED,
					getAccessRange());
			return InteractionResultHolder.fail(stack);
		}

		Level targetLevel = sameDimension ? level : getBoundServerLevel(player, boundTarget);
		if (targetLevel == null) {
			if (!level.isClientSide)
				sendStatus(player, "item.create_biotech.wireless_terminal.wrong_dimension", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		if (!(targetLevel.getBlockEntity(boundTarget.pos()) instanceof StockTickerBlockEntity stockTicker)) {
			if (!level.isClientSide)
				sendStatus(player, "item.create_biotech.wireless_terminal.target_missing", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		if (!stockTicker.isKeeperPresent()) {
			if (!level.isClientSide)
				player.displayClientMessage(CreateLang.translate("stock_ticker.keeper_missing").component(), true);
			return InteractionResultHolder.fail(stack);
		}

		if (!level.isClientSide)
			openRequestMenu(player, stockTicker);

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);

		BoundTarget boundTarget = getBoundTarget(stack);
		if (boundTarget == null) {
			tooltip.add(Component.translatable("item.create_biotech.wireless_terminal.unbound")
				.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}

		tooltip.add(Component.translatable("item.create_biotech.wireless_terminal.bound", boundTarget.pos().getX(),
			boundTarget.pos().getY(), boundTarget.pos().getZ()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.create_biotech.wireless_terminal.bound_dimension",
			boundTarget.dimension().toString()).withStyle(ChatFormatting.DARK_GRAY));
	}

	private InteractionResult bindOrOpenAt(@Nullable Player player, Level level, ItemStack stack, BlockPos stockTickerPos) {
		if (player == null)
			return InteractionResult.PASS;

		BoundTarget boundTarget = getBoundTarget(stack);
		if (boundTarget != null && boundTarget.matches(level, stockTickerPos)) {
			if (!level.isClientSide && level.getBlockEntity(stockTickerPos) instanceof StockTickerBlockEntity stockTicker) {
				if (!stockTicker.isKeeperPresent()) {
					player.displayClientMessage(CreateLang.translate("stock_ticker.keeper_missing").component(), true);
					return InteractionResult.SUCCESS;
				}
				openRequestMenu(player, stockTicker);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		if (!level.isClientSide) {
			bind(stack, level, stockTickerPos);
			sendStatus(player, "item.create_biotech.wireless_terminal.bind_success", ChatFormatting.GREEN,
				stockTickerPos.getX(), stockTickerPos.getY(), stockTickerPos.getZ());
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void openRequestMenu(Player player, StockTickerBlockEntity stockTicker) {
		if (!(player instanceof ServerPlayer serverPlayer))
			return;

		if (!stockTicker.behaviour.mayInteract(player)) {
			player.displayClientMessage(CreateLang.translate("stock_keeper.locked")
				.style(ChatFormatting.RED)
				.component(), true);
			return;
		}

		boolean showLockOption =
			stockTicker.behaviour.mayAdministrate(player) && Create.LOGISTICS.isLockable(stockTicker.behaviour.freqId);
		boolean isCurrentlyLocked = Create.LOGISTICS.isLocked(stockTicker.behaviour.freqId);

		NetworkHooks.openScreen(serverPlayer, createRequestMenuProvider(stockTicker), buf -> {
			buf.writeBoolean(showLockOption);
			buf.writeBoolean(isCurrentlyLocked);
			buf.writeBlockPos(stockTicker.getBlockPos());
		});
		stockTicker.getRecentSummary()
			.divideAndSendTo(serverPlayer, stockTicker.getBlockPos());
	}

	private static MenuProvider createRequestMenuProvider(StockTickerBlockEntity stockTicker) {
		return new MenuProvider() {
			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
				return WirelessStockKeeperRequestMenu.create(containerId, playerInventory, stockTicker);
			}

			@Override
			public Component getDisplayName() {
				return Component.empty();
			}
		};
	}

	private static boolean isWithinRange(Player player, BlockPos stockTickerPos) {
		int accessRange = getAccessRange();
		return player.distanceToSqr(Vec3.atCenterOf(stockTickerPos)) <= accessRange * accessRange;
	}

	private static int getAccessRange() {
		return WirelessStockKeeperRequestMenu.getAccessRange();
	}

	private static boolean allowsCrossDimensionTerminal() {
		return CBConfigs.COMMON.wireless.allowCrossDimensionTerminal.get();
	}

	@Nullable
	private static Level getBoundServerLevel(Player player, BoundTarget boundTarget) {
		if (!(player instanceof ServerPlayer serverPlayer))
			return null;
		ResourceKey<Level> targetDimension = ResourceKey.create(Registries.DIMENSION, boundTarget.dimension());
		ServerLevel targetLevel = serverPlayer.server.getLevel(targetDimension);
		return targetLevel;
	}

	private static void bind(ItemStack stack, Level level, BlockPos stockTickerPos) {
		CompoundTag tag = stack.getOrCreateTag();
		tag.put(BOUND_POS_KEY, NbtUtils.writeBlockPos(stockTickerPos));
		tag.putString(BOUND_DIMENSION_KEY, level.dimension().location().toString());
	}

	@Nullable
	private static BoundTarget getBoundTarget(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(BOUND_POS_KEY, Tag.TAG_COMPOUND)
			|| !tag.contains(BOUND_DIMENSION_KEY, Tag.TAG_STRING))
			return null;

		ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(BOUND_DIMENSION_KEY));
		if (dimension == null)
			return null;

		return new BoundTarget(dimension, NbtUtils.readBlockPos(tag.getCompound(BOUND_POS_KEY)));
	}

	private static void sendStatus(Player player, String key, ChatFormatting style, Object... args) {
		player.displayClientMessage(Component.translatable(key, args)
			.withStyle(style), true);
	}

	private record BoundTarget(ResourceLocation dimension, BlockPos pos) {

		private boolean matches(Level level, BlockPos stockTickerPos) {
			return dimension.equals(level.dimension().location()) && pos.equals(stockTickerPos);
		}
	}
}

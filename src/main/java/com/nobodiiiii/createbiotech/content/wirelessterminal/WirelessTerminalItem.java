package com.nobodiiiii.createbiotech.content.wirelessterminal;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperBlock;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.FactoryGaugeCatalog;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
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
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class WirelessTerminalItem extends Item {

	private static final String BOUND_POS_KEY = "BoundPos";
	private static final String BOUND_DIMENSION_KEY = "BoundDimension";
	private static final String BOUND_KEEPER_POS_KEY = "BoundKeeperPos";

	public WirelessTerminalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget,
		InteractionHand usedHand) {
		if (player.isShiftKeyDown()) {
			if (!(interactionTarget instanceof EnderMan))
				return clearBindingOrShowHint(player, player.level(), stack);

			BlockPos stockTickerPos = StockTickerInteractionHandler.getStockTickerPosition(interactionTarget);
			if (stockTickerPos == null)
				return showBindingHint(player, player.level());
			return bindToEndermanStockKeeper(player, player.level(), stack, stockTickerPos);
		}

		BlockPos stockTickerPos = StockTickerInteractionHandler.getStockTickerPosition(interactionTarget);
		if (stockTickerPos == null)
			return InteractionResult.PASS;
		return bindOrOpenAt(player, player.level(), stack, stockTickerPos, interactionTarget instanceof EnderMan, null);
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof EndermanStockKeeperBlock)
			return useOn(context);
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;

		BlockPos clickedPos = context.getClickedPos();
		if (level.getBlockState(clickedPos).getBlock() instanceof EndermanStockKeeperBlock) {
			StockTickerBlockEntity stockTicker = EndermanStockKeeperBlock.findStockTicker(level, clickedPos);
			if (stockTicker == null) {
				if (!level.isClientSide)
					sendStatus(player, "block.create_biotech.enderman_stock_keeper.missing_stock_ticker", ChatFormatting.RED);
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
			if (player.isShiftKeyDown())
				return bindToEndermanStockKeeper(player, level, context.getItemInHand(), stockTicker.getBlockPos(), clickedPos);
			return bindOrOpenAt(player, level, context.getItemInHand(), stockTicker.getBlockPos(), true, clickedPos);
		}

		if (player.isShiftKeyDown())
			return clearBindingOrShowHint(player, level, context.getItemInHand());

		if (!(level.getBlockEntity(clickedPos) instanceof BlazeBurnerBlockEntity))
			return InteractionResult.PASS;

		StockTickerBlockEntity stockTicker = BlazeBurnerBlockEntity.getStockTicker(level, clickedPos);
		if (stockTicker == null)
			return InteractionResult.PASS;

		return bindOrOpenAt(player, level, context.getItemInHand(), stockTicker.getBlockPos(), false, null);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (player.isShiftKeyDown()) {
			clearBindingOrShowHint(player, level, stack);
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		BoundTarget boundTarget = getBoundTarget(stack);
		if (boundTarget == null) {
			if (!level.isClientSide)
				sendStatus(player, "item.create_biotech.wireless_terminal.not_bound", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		if (level.isClientSide)
			return InteractionResultHolder.success(stack);

		Level targetLevel = getBoundServerLevel(player, boundTarget);
		if (targetLevel == null) {
			sendStatus(player, "item.create_biotech.wireless_terminal.wrong_dimension", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		if (!(targetLevel.getBlockEntity(boundTarget.pos()) instanceof StockTickerBlockEntity stockTicker)) {
			sendStatus(player, "item.create_biotech.wireless_terminal.target_missing", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		if (boundTarget.keeperPos() != null
			&& !EndermanStockKeeperBlock.isKeeperFor(targetLevel, boundTarget.keeperPos(), boundTarget.pos())) {
			sendStatus(player, "item.create_biotech.wireless_terminal.target_missing", ChatFormatting.RED);
			return InteractionResultHolder.fail(stack);
		}

		if (!stockTicker.isKeeperPresent()) {
			player.displayClientMessage(CreateLang.translate("stock_ticker.keeper_missing").component(), true);
			return InteractionResultHolder.fail(stack);
		}

		openRequestMenu(player, stockTicker, boundTarget.keeperPos());

		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);

		BoundTarget boundTarget = getBoundTarget(stack);
		if (boundTarget == null) {
			tooltip.add(Component.translatable("item.create_biotech.wireless_terminal.unbound")
				.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}

		BlockPos displayPos = boundTarget.keeperPos() == null ? boundTarget.pos() : boundTarget.keeperPos();
		tooltip.add(Component.translatable("item.create_biotech.wireless_terminal.bound", displayPos.getX(),
			displayPos.getY(), displayPos.getZ()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.create_biotech.wireless_terminal.bound_dimension",
			boundTarget.dimension().toString()).withStyle(ChatFormatting.DARK_GRAY));
	}

	private InteractionResult bindOrOpenAt(@Nullable Player player, Level level, ItemStack stack, BlockPos stockTickerPos,
		boolean canBind, @Nullable BlockPos keeperPos) {
		if (player == null)
			return InteractionResult.PASS;

		BoundTarget boundTarget = getBoundTarget(stack);
		if (boundTarget != null && boundTarget.matches(level, stockTickerPos, keeperPos)) {
			if (!level.isClientSide && level.getBlockEntity(stockTickerPos) instanceof StockTickerBlockEntity stockTicker) {
				if (!stockTicker.isKeeperPresent()) {
					player.displayClientMessage(CreateLang.translate("stock_ticker.keeper_missing").component(), true);
					return InteractionResult.SUCCESS;
				}
				openRequestMenu(player, stockTicker, keeperPos);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		if (!canBind) {
			if (!level.isClientSide)
				sendStatus(player, "item.create_biotech.wireless_terminal.bind_enderman_only", ChatFormatting.RED);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		return bindToEndermanStockKeeper(player, level, stack, stockTickerPos, keeperPos);
	}

	InteractionResult bindToEndermanStockKeeper(Player player, Level level, ItemStack stack, BlockPos stockTickerPos) {
		return bindToEndermanStockKeeper(player, level, stack, stockTickerPos, null);
	}

	private InteractionResult bindToEndermanStockKeeper(Player player, Level level, ItemStack stack, BlockPos stockTickerPos,
		@Nullable BlockPos keeperPos) {
		if (!level.isClientSide) {
			bind(stack, level, stockTickerPos, keeperPos);
			BlockPos displayPos = keeperPos == null ? stockTickerPos : keeperPos;
			sendStatus(player, "item.create_biotech.wireless_terminal.bind_success", ChatFormatting.GREEN,
				displayPos.getX(), displayPos.getY(), displayPos.getZ());
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	InteractionResult clearBindingOrShowHint(Player player, Level level, ItemStack stack) {
		if (!level.isClientSide) {
			if (clearBinding(stack))
				sendStatus(player, "item.create_biotech.wireless_terminal.unbind_success", ChatFormatting.YELLOW);
			else
				sendStatus(player, "item.create_biotech.wireless_terminal.bind_hint", ChatFormatting.YELLOW);
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	InteractionResult showBindingHint(Player player, Level level) {
		if (!level.isClientSide)
			sendStatus(player, "item.create_biotech.wireless_terminal.bind_hint", ChatFormatting.YELLOW);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void openRequestMenu(Player player, StockTickerBlockEntity stockTicker, @Nullable BlockPos keeperPos) {
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
		List<ItemStack> gaugeOutputs = keeperPos == null ? List.of()
			: FactoryGaugeCatalog.getCraftableOutputs(stockTicker.behaviour.freqId);

		serverPlayer.openMenu(createRequestMenuProvider(stockTicker, keeperPos, gaugeOutputs), buf -> {
			buf.writeBoolean(showLockOption);
			buf.writeBoolean(isCurrentlyLocked);
			buf.writeBlockPos(stockTicker.getBlockPos());
			buf.writeNullable(keeperPos, (buffer, pos) -> buffer.writeBlockPos(pos));
			ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, gaugeOutputs);
			buf.writeNbt(stockTicker.getUpdateTag(stockTicker.getLevel().registryAccess()));
		});
		stockTicker.getRecentSummary()
			.divideAndSendTo(serverPlayer, stockTicker.getBlockPos());
	}

	private static MenuProvider createRequestMenuProvider(StockTickerBlockEntity stockTicker,
		@Nullable BlockPos keeperPos, List<ItemStack> gaugeOutputs) {
		return new MenuProvider() {
			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
				return WirelessStockKeeperRequestMenu.create(containerId, playerInventory, stockTicker, keeperPos, gaugeOutputs);
			}

			@Override
			public Component getDisplayName() {
				return Component.empty();
			}
		};
	}

	@Nullable
	private static Level getBoundServerLevel(Player player, BoundTarget boundTarget) {
		if (!(player instanceof ServerPlayer serverPlayer))
			return null;
		ResourceKey<Level> targetDimension = ResourceKey.create(Registries.DIMENSION, boundTarget.dimension());
		ServerLevel targetLevel = serverPlayer.server.getLevel(targetDimension);
		return targetLevel;
	}

	private static void bind(ItemStack stack, Level level, BlockPos stockTickerPos, @Nullable BlockPos keeperPos) {
		CompoundTag tag = CBItemData.getOrEmpty(stack);
		tag.put(BOUND_POS_KEY, NbtUtils.writeBlockPos(stockTickerPos));
		tag.putString(BOUND_DIMENSION_KEY, level.dimension().location().toString());
		if (keeperPos == null)
			tag.remove(BOUND_KEEPER_POS_KEY);
		else
			tag.put(BOUND_KEEPER_POS_KEY, NbtUtils.writeBlockPos(keeperPos));
		CBItemData.set(stack, tag);
	}

	private static boolean clearBinding(ItemStack stack) {
		CompoundTag tag = CBItemData.get(stack);
		if (tag == null || (!tag.contains(BOUND_POS_KEY) && !tag.contains(BOUND_DIMENSION_KEY)
			&& !tag.contains(BOUND_KEEPER_POS_KEY)))
			return false;

		tag.remove(BOUND_POS_KEY);
		tag.remove(BOUND_DIMENSION_KEY);
		tag.remove(BOUND_KEEPER_POS_KEY);
		CBItemData.set(stack, tag);
		return true;
	}

	@Nullable
	private static BoundTarget getBoundTarget(ItemStack stack) {
		CompoundTag tag = CBItemData.get(stack);
		if (tag == null || !tag.contains(BOUND_POS_KEY, Tag.TAG_INT_ARRAY)
			|| !tag.contains(BOUND_DIMENSION_KEY, Tag.TAG_STRING))
			return null;

		ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(BOUND_DIMENSION_KEY));
		if (dimension == null)
			return null;

		BlockPos pos = NbtUtils.readBlockPos(tag, BOUND_POS_KEY)
			.orElse(null);
		BlockPos keeperPos = NbtUtils.readBlockPos(tag, BOUND_KEEPER_POS_KEY).orElse(null);
		return pos == null ? null : new BoundTarget(dimension, pos, keeperPos);
	}

	private static void sendStatus(Player player, String key, ChatFormatting style, Object... args) {
		player.displayClientMessage(Component.translatable(key, args)
			.withStyle(style), true);
	}

	private record BoundTarget(ResourceLocation dimension, BlockPos pos, @Nullable BlockPos keeperPos) {

		private boolean matches(Level level, BlockPos stockTickerPos, @Nullable BlockPos keeperPos) {
			return dimension.equals(level.dimension().location()) && pos.equals(stockTickerPos)
				&& Objects.equals(this.keeperPos, keeperPos);
		}
	}
}

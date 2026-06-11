package com.nobodiiiii.createbiotech.content.slimemimic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import com.nobodiiiii.createbiotech.mixin.AbstractVillagerAccessor;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

public final class SlimeMimicVillagerTrades {
	private static final String ORIGINAL_RESULTS_TAG = "CreateBiotechOriginalOfferResults";
	private static final String SELL_TAG = "sell";

	private SlimeMimicVillagerTrades() {
	}

	public static void rewriteSellItems(AbstractVillager villager) {
		if (!SlimeMimicHandler.isSlimeMimic(villager))
			return;

		MerchantOffers offers = getOffersField(villager);
		if (offers == null || offers.isEmpty())
			return;

		CompoundTag data = villager.getPersistentData();
		ListTag originalResults = data.contains(ORIGINAL_RESULTS_TAG, Tag.TAG_LIST)
			? data.getList(ORIGINAL_RESULTS_TAG, Tag.TAG_COMPOUND)
			: new ListTag();
		syncOriginalResults(originalResults, offers);
		data.put(ORIGINAL_RESULTS_TAG, originalResults);

		if (hasOnlySlimeBallResults(offers, originalResults))
			return;

		setOffersField(villager, rewriteOffers(villager, offers, originalResults));
	}

	public static void restoreOriginalOffers(AbstractVillager villager) {
		CompoundTag data = villager.getPersistentData();
		if (!data.contains(ORIGINAL_RESULTS_TAG, Tag.TAG_LIST))
			return;

		ListTag originalResults = data.getList(ORIGINAL_RESULTS_TAG, Tag.TAG_COMPOUND);
		MerchantOffers currentOffers = getOffersField(villager);
		if (currentOffers != null && !currentOffers.isEmpty())
			setOffersField(villager, restoreOffers(currentOffers, originalResults));
		data.remove(ORIGINAL_RESULTS_TAG);
	}

	public static void clearStoredOriginalResults(AbstractVillager villager) {
		villager.getPersistentData().remove(ORIGINAL_RESULTS_TAG);
	}

	private static void syncOriginalResults(ListTag originalResults, MerchantOffers offers) {
		for (int i = originalResults.size(); i < offers.size(); i++)
			originalResults.add(offers.get(i).getResult().save(new CompoundTag()));
	}

	private static boolean hasOnlySlimeBallResults(MerchantOffers offers, ListTag originalResults) {
		for (int i = 0; i < offers.size(); i++) {
			MerchantOffer offer = offers.get(i);
			if (isOriginalResult(offer.getResult(), originalResults, i) || !isSlimeBallResult(offer.getResult()))
				return false;
		}
		return true;
	}

	private static MerchantOffers rewriteOffers(AbstractVillager villager, MerchantOffers currentOffers, ListTag originalResults) {
		MerchantOffers rewrittenOffers = new MerchantOffers();
		for (int i = 0; i < currentOffers.size(); i++) {
			MerchantOffer offer = currentOffers.get(i);
			ItemStack result = isOriginalResult(offer.getResult(), originalResults, i)
				? new ItemStack(Items.SLIME_BALL, getRandomSlimeBallTradeCount(villager))
				: offer.getResult().copy();
			rewrittenOffers.add(copyOfferWithResult(offer, result));
		}
		return rewrittenOffers;
	}

	private static MerchantOffers restoreOffers(MerchantOffers currentOffers, ListTag originalResults) {
		MerchantOffers restoredOffers = new MerchantOffers();
		for (int i = 0; i < currentOffers.size(); i++) {
			ItemStack result =
				i < originalResults.size() ? ItemStack.of(originalResults.getCompound(i)) : currentOffers.get(i).getResult().copy();
			restoredOffers.add(copyOfferWithResult(currentOffers.get(i), result));
		}
		return restoredOffers;
	}

	private static MerchantOffer copyOfferWithResult(MerchantOffer sourceOffer, ItemStack result) {
		CompoundTag offerTag = sourceOffer.createTag();
		offerTag.put(SELL_TAG, result.save(new CompoundTag()));
		return new MerchantOffer(offerTag);
	}

	private static boolean isOriginalResult(ItemStack result, ListTag originalResults, int index) {
		if (index >= originalResults.size())
			return false;
		return result.save(new CompoundTag()).equals(originalResults.getCompound(index));
	}

	private static boolean isSlimeBallResult(ItemStack result) {
		return result.is(Items.SLIME_BALL) && result.getCount() >= getMinSlimeBallTradeCount()
			&& result.getCount() <= getMaxSlimeBallTradeCount();
	}

	private static int getRandomSlimeBallTradeCount(AbstractVillager villager) {
		int min = getMinSlimeBallTradeCount();
		int max = getMaxSlimeBallTradeCount();
		return min + villager.getRandom().nextInt(max - min + 1);
	}

	private static int getMinSlimeBallTradeCount() {
		return Math.min(CBConfigs.COMMON.slimeMimic.villagerTradeMinSlimeBalls.get(),
			CBConfigs.COMMON.slimeMimic.villagerTradeMaxSlimeBalls.get());
	}

	private static int getMaxSlimeBallTradeCount() {
		return Math.max(CBConfigs.COMMON.slimeMimic.villagerTradeMinSlimeBalls.get(),
			CBConfigs.COMMON.slimeMimic.villagerTradeMaxSlimeBalls.get());
	}

	private static MerchantOffers getOffersField(AbstractVillager villager) {
		return ((AbstractVillagerAccessor) villager).createBiotech$getOffersField();
	}

	private static void setOffersField(AbstractVillager villager, MerchantOffers offers) {
		((AbstractVillagerAccessor) villager).createBiotech$setOffersField(offers);
	}
}

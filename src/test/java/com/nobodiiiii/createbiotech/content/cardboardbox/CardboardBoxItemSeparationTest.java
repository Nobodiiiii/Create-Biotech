package com.nobodiiiii.createbiotech.content.cardboardbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import com.simibubi.create.content.logistics.packagePort.PackagePortAutomationInventoryWrapper;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.GameData;

class CardboardBoxItemSeparationTest {
	private static final EmptyCardboardBoxItem[] emptyBoxes = new EmptyCardboardBoxItem[2];
	private static final CapturedEntityBoxItem[] filledBoxes = new CapturedEntityBoxItem[2];
	private static PackageItem parcelItem;

	@BeforeAll
	@SuppressWarnings("deprecation")
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		Bootstrap.bootStrap();
		GameData.unfreezeData();
		try {
			((MappedRegistry<?>) BuiltInRegistries.REGISTRY).unfreeze();
			Objects.requireNonNull(AllDataComponents.PACKAGE_ADDRESS);
			filledBoxes[0] = register("small_filled", new CardboardBoxItem(new Item.Properties(), () -> emptyBoxes[0]));
			filledBoxes[1] = register("large_filled", new LargeCardboardBoxItem(new Item.Properties(), () -> emptyBoxes[1]));
			emptyBoxes[0] = register("small_empty", new EmptyCardboardBoxItem(new Item.Properties(), false, () -> filledBoxes[0]));
			emptyBoxes[1] = register("large_empty", new EmptyCardboardBoxItem(new Item.Properties(), true, () -> filledBoxes[1]));
			parcelItem = register("parcel", new PackageItem(new Item.Properties().stacksTo(1),
				new PackageStyle("cardboard", 12, 10, 21f, false)));
		} finally {
			BuiltInRegistries.REGISTRY.forEach(Registry::freeze);
			BuiltInRegistries.REGISTRY.freeze();
		}
	}

	private static <T extends Item> T register(String name, T item) {
		return Registry.register(BuiltInRegistries.ITEM,
			ResourceLocation.fromNamespaceAndPath("create_biotech_test", "box_separation_" + name), item);
	}

	static Stream<Arguments> sizesAndCounts() {
		return Stream.of(0, 1).flatMap(size -> Stream.of(1, 2, 16)
			.map(count -> Arguments.of(size, count)));
	}

	static Stream<Integer> sizes() {
		return Stream.of(0, 1);
	}

	@ParameterizedTest
	@MethodSource("sizesAndCounts")
	void emptyBoxesAreOrdinaryStackableCargo(int size, int count) {
		ItemStack boxes = new ItemStack(emptyBoxes[size], count);
		assertFalse(PackageItem.isPackage(boxes));
		assertTrue(boxes.canFitInsideContainerItems());
		assertEquals(16, boxes.getMaxStackSize());
		assertEquals(size == 0, CapturedEntityBoxHelper.isEmptySmallBox(boxes));
		assertEquals(size == 1, CapturedEntityBoxHelper.isEmptyLargeBox(boxes));
		assertTrue(CapturedEntityBoxHelper.getVisiblePackageContents(boxes).getStackInSlot(0).isEmpty());
	}

	@ParameterizedTest
	@MethodSource("sizesAndCounts")
	void oneParcelDeliversAllOrderedEmptyBoxesExactlyOnceThroughUnmodifiedCreate(int size, int count) {
		ItemStack parcel = new ItemStack(parcelItem);
		parcel.set(AllDataComponents.PACKAGE_CONTENTS,
			ItemContainerContents.fromItems(List.of(new ItemStack(emptyBoxes[size], count))));
		PackageItem.addAddress(parcel, "destination");
		MemoryPackager destination = deliverRepeatedly(parcel);
		assertEquals(1, destination.unpacks);
		assertEquals(count, destination.received.getStackInSlot(0).getCount());
		assertTrue(destination.received.getStackInSlot(0).is(emptyBoxes[size]));
	}

	@ParameterizedTest
	@MethodSource("sizesAndCounts")
	void legacyEmptyStacksDrainWithoutDuplicationOrDiscardingBoxes(int size, int count) {
		ItemStack legacy = new ItemStack(filledBoxes[size], count);
		PackageItem.addAddress(legacy, "destination");
		MemoryPackager destination = deliverRepeatedly(legacy);
		assertEquals(count, destination.unpacks);
		assertEquals(count, destination.received.getStackInSlot(0).getCount());
		assertTrue(destination.received.getStackInSlot(0).is(emptyBoxes[size]));
		assertEquals(count, CapturedEntityBoxHelper.createEmptyBox(legacy).getCount());
	}

	@ParameterizedTest
	@MethodSource("sizesAndCounts")
	void capturePreparationIsOnePackageAndDoesNotConsumeOrMutateInput(int size, int count) {
		ItemStack boxes = new ItemStack(emptyBoxes[size], count);
		boxes.set(DataComponents.CUSTOM_NAME, Component.literal("Named box"));
		ItemStack capture = CapturedEntityBoxHelper.createCaptureBox(boxes);
		assertTrue(capture.is(filledBoxes[size]));
		assertTrue(PackageItem.isPackage(capture));
		assertFalse(capture.canFitInsideContainerItems());
		assertEquals(1, capture.getCount());
		assertEquals(1, capture.getMaxStackSize());
		assertEquals(count, boxes.getCount());
		assertFalse(CapturedEntityBoxHelper.hasCapturedEntity(boxes));
		assertEquals(boxes.get(DataComponents.CUSTOM_NAME), capture.get(DataComponents.CUSTOM_NAME));
	}

	@ParameterizedTest
	@MethodSource("sizes")
	void emptyReturnsKeepNamesAndCustomDataButRemoveCreatureAndTransportData(int size) {
		ItemStack filled = CapturedEntityBoxHelper.createFilledBox(filledBoxes[size], EntityType.PIG);
		filled.set(DataComponents.CUSTOM_NAME, Component.literal("Named box"));
		CBItemData.edit(filled, tag -> tag.putString("OwnerNote", "keep"));
		PackageItem.addAddress(filled, "destination");
		PackageItem.setOrder(filled, 123, 0, true, 0, true, null);
		ItemStack empty = CapturedEntityBoxHelper.createEmptyBox(filled);
		assertTrue(empty.is(emptyBoxes[size]));
		assertFalse(CapturedEntityBoxHelper.hasCapturedEntity(empty));
		assertFalse(PackageItem.isPackage(empty));
		assertEquals(16, empty.getMaxStackSize());
		assertEquals(filled.get(DataComponents.CUSTOM_NAME), empty.get(DataComponents.CUSTOM_NAME));
		assertEquals("keep", CBItemData.getOrEmpty(empty).getString("OwnerNote"));
		assertFalse(empty.has(AllDataComponents.PACKAGE_ADDRESS));
		assertFalse(empty.has(AllDataComponents.PACKAGE_ORDER_DATA));
		assertFalse(empty.has(AllDataComponents.PACKAGE_CONTENTS));
		assertTrue(CapturedEntityBoxHelper.containsEntityType(filled, EntityType.PIG));
		assertTrue(filled.hasCraftingRemainingItem());
		assertTrue(ItemStack.matches(empty, filled.getCraftingRemainingItem()));
		assertTrue(CapturedEntityBoxHelper.createCaptureBox(filled).isEmpty());
	}

	@ParameterizedTest
	@MethodSource("sizes")
	void filledBoxesStillTravelAsSingleParcelsWithTheirCreatureIntact(int size) {
		ItemStack filled = CapturedEntityBoxHelper.createFilledBox(emptyBoxes[size], EntityType.PIG);
		PackageItem.addAddress(filled, "destination");
		assertEquals(1, filled.getMaxStackSize());
		assertFalse(CapturedEntityBoxHelper.isEmptyBox(filled));
		MemoryPackager destination = deliverRepeatedly(filled);
		assertEquals(1, destination.unpacks);
		assertTrue(ItemStack.matches(filled, destination.received.getStackInSlot(0)));
		assertTrue(CapturedEntityBoxHelper.containsEntityType(destination.received.getStackInSlot(0), EntityType.PIG));
	}

	@Test
	void legacyBoxesWithCargoAreNotMigratedOrRecaptured() {
		ItemStack legacy = new ItemStack(filledBoxes[0]);
		legacy.set(AllDataComponents.PACKAGE_CONTENTS,
			ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3))));
		assertFalse(CapturedEntityBoxHelper.isEmptyBox(legacy));
		assertTrue(CapturedEntityBoxHelper.createCaptureBox(legacy).isEmpty());
		ItemStackHandler contents = CapturedEntityBoxHelper.applyVirtualSelfFallbackContents(
			legacy, PackageItem.getContents(legacy));
		assertTrue(contents.getStackInSlot(0).is(Items.DIAMOND));
		assertEquals(3, contents.getStackInSlot(0).getCount());
	}

	@ParameterizedTest
	@MethodSource("sizes")
	void legacyEmptyConversionIsNeverExposedAsRemovableCourierCargo(int size) {
		ItemStack legacy = new ItemStack(filledBoxes[size]);
		PackageItem.addAddress(legacy, "destination");
		for (int reopen = 0; reopen < 3; reopen++) {
			ItemStackHandler unpacked = CapturedEntityBoxHelper.applyVirtualSelfFallbackContents(
				legacy, PackageItem.getContents(legacy));
			assertTrue(unpacked.getStackInSlot(0).is(emptyBoxes[size]));
			assertTrue(CapturedEntityBoxHelper.getVisiblePackageContents(legacy).getStackInSlot(0).isEmpty());
			// The courier menu saves an empty contents component when closed with no cargo.
			legacy.set(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY);
		}
		assertEquals("destination", PackageItem.getAddress(legacy));
		assertEquals(1, legacy.getCount());
	}

	@ParameterizedTest
	@MethodSource("sizes")
	void courierShowsOnlyStoredCargoWithoutExposingTheCapturedCreature(int size) {
		ItemStack filled = CapturedEntityBoxHelper.createFilledBox(filledBoxes[size], EntityType.PIG);
		assertTrue(CapturedEntityBoxHelper.getVisiblePackageContents(filled).getStackInSlot(0).isEmpty());
		filled.set(AllDataComponents.PACKAGE_CONTENTS,
			ItemContainerContents.fromItems(List.of(new ItemStack(emptyBoxes[size], 2))));
		ItemStackHandler visible = CapturedEntityBoxHelper.getVisiblePackageContents(filled);
		assertTrue(visible.getStackInSlot(0).is(emptyBoxes[size]));
		assertEquals(2, visible.getStackInSlot(0).getCount());
		visible.extractItem(0, 1, false);
		assertEquals(2, CapturedEntityBoxHelper.getVisiblePackageContents(filled).getStackInSlot(0).getCount());
		assertTrue(CapturedEntityBoxHelper.containsEntityType(filled, EntityType.PIG));
	}

	private static MemoryPackager deliverRepeatedly(ItemStack parcel) {
		ItemStackHandler inventory = new ItemStackHandler(1);
		inventory.setStackInSlot(0, parcel.copy());
		PackagePortBlockEntity port = new PackagePortBlockEntity(BlockEntityType.CHEST,
			BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {
			@Override
			protected void onOpenChange(boolean open) {}
		};
		port.addressFilter = "destination";
		PackagePortAutomationInventoryWrapper source = new PackagePortAutomationInventoryWrapper(inventory, port);
		MemoryPackager destination = new MemoryPackager();
		// The frogport's actual transfer contract: commit extraction only after full acceptance.
		for (int attempt = 0; attempt < 40; attempt++) {
			ItemStack preview = source.extractItem(0, 1, true);
			if (preview.isEmpty())
				continue;
			assertEquals(1, preview.getCount());
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination.inventory, preview, false);
			assertTrue(remainder.isEmpty());
			assertEquals(1, source.extractItem(0, 1, false).getCount());
		}
		assertTrue(inventory.getStackInSlot(0).isEmpty());
		return destination;
	}

	/** Uses Create's real PackagerItemHandler, replacing only world-dependent unpacking effects. */
	private static class MemoryPackager extends PackagerBlockEntity {
		private final ItemStackHandler received = new ItemStackHandler(9);
		private int unpacks;

		MemoryPackager() {
			super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
		}

		@Override
		public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

		@Override
		public void triggerStockCheck() {}

		@Override
		public boolean unwrapBox(ItemStack box, boolean simulate) {
			if (simulate)
				return true;
			unpacks++;
			ItemStackHandler contents = CapturedEntityBoxHelper.applyVirtualSelfFallbackContents(
				box, PackageItem.getContents(box));
			for (int slot = 0; slot < contents.getSlots(); slot++)
				assertTrue(ItemHandlerHelper.insertItemStacked(received, contents.getStackInSlot(slot), false).isEmpty());
			return true;
		}
	}
}

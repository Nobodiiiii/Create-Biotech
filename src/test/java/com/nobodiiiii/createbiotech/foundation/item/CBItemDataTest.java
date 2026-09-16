package com.nobodiiiii.createbiotech.foundation.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

class CBItemDataTest {

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		Bootstrap.bootStrap();
	}

	@Test
	void unchangedAndCopiedStacksReuseReadSnapshot() {
		ItemStack stack = stackWithNestedData();
		CompoundTag snapshot = CBItemData.getReadOnly(stack);
		assertNotNull(snapshot);
		assertSame(snapshot, CBItemData.getReadOnly(stack));
		assertSame(snapshot, CBItemData.getReadOnly(stack.copy()));
		assertEquals(1, snapshot.getCompound("Entity").getInt("Value"));
	}

	@Test
	void editingCopyReplacesItsSnapshotWithoutChangingOriginal() {
		ItemStack original = stackWithNestedData();
		ItemStack edited = original.copy();
		CompoundTag originalSnapshot = CBItemData.getReadOnly(original);
		assertNotNull(originalSnapshot);

		CBItemData.edit(edited, tag -> tag.getCompound("Entity").putInt("Value", 2));

		CompoundTag editedSnapshot = CBItemData.getReadOnly(edited);
		assertNotNull(editedSnapshot);
		assertNotSame(originalSnapshot, editedSnapshot);
		assertSame(originalSnapshot, CBItemData.getReadOnly(original));
		assertEquals(1, originalSnapshot.getCompound("Entity").getInt("Value"));
		assertEquals(1, CBItemData.getOrEmpty(original).getCompound("Entity").getInt("Value"));
		assertEquals(2, editedSnapshot.getCompound("Entity").getInt("Value"));
		assertEquals(2, CBItemData.getOrEmpty(edited).getCompound("Entity").getInt("Value"));
	}

	@Test
	void writableCopiesCannotMutateComponentOrCachedSnapshot() {
		ItemStack stack = stackWithNestedData();
		CompoundTag snapshot = CBItemData.getReadOnly(stack);
		CompoundTag copy = CBItemData.get(stack);
		assertNotNull(snapshot);
		assertNotNull(copy);
		assertNotSame(snapshot, copy);

		copy.getCompound("Entity").putInt("Value", 2);
		CBItemData.getOrEmpty(stack).getCompound("Entity").putInt("Value", 3);

		assertSame(snapshot, CBItemData.getReadOnly(stack));
		assertEquals(1, snapshot.getCompound("Entity").getInt("Value"));
		assertEquals(1, CBItemData.getOrEmpty(stack).getCompound("Entity").getInt("Value"));
	}

	@Test
	void removingAndRestoringDataNeverReturnsStaleSnapshot() {
		ItemStack stack = stackWithNestedData();
		CompoundTag originalSnapshot = CBItemData.getReadOnly(stack);
		assertNotNull(originalSnapshot);

		CBItemData.set(stack, null);
		assertFalse(CBItemData.has(stack));
		assertNull(CBItemData.getReadOnly(stack));
		assertNull(CBItemData.getReadOnlyComponent(stack));

		CBItemData.edit(stack, tag -> tag.putInt("Value", 4));
		CompoundTag restoredSnapshot = CBItemData.getReadOnly(stack);
		assertNotNull(restoredSnapshot);
		assertNotSame(originalSnapshot, restoredSnapshot);
		assertFalse(restoredSnapshot.contains("Entity"));
		assertEquals(4, restoredSnapshot.getInt("Value"));
		assertEquals(1, originalSnapshot.getCompound("Entity").getInt("Value"));

		CBItemData.set(stack, new CompoundTag());
		assertFalse(CBItemData.has(stack));
		assertNull(CBItemData.getReadOnly(stack));
	}

	private static ItemStack stackWithNestedData() {
		ItemStack stack = new ItemStack(Items.PAPER);
		CompoundTag entity = new CompoundTag();
		entity.putInt("Value", 1);
		CompoundTag data = new CompoundTag();
		data.put("Entity", entity);
		CBItemData.set(stack, data);
		return stack;
	}
}

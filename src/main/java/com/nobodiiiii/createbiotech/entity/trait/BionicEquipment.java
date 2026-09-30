package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.Set;
import java.util.Comparator;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.inventory.BionicCargoMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Equipment is owned by the assembled creature and survives packing, reloads and death. */
public final class BionicEquipment {
	private final SlimeBionicEntity entity;
	private ItemStack saddle = ItemStack.EMPTY;
	private boolean chest;
	public final SimpleContainer cargo = new SimpleContainer(15) {
		@Override public boolean stillValid(Player player) {
			return entity.isAlive() && entity.distanceToSqr(player) < 64 && mayUse(player)
				&& chest && capacity() > 0;
		}
	};

	public BionicEquipment(SlimeBionicEntity entity) { this.entity = entity; }
	public boolean mayUse(Player player) {
		return entity.getBionicOwnerId() == null ? !entity.getBionicTraits().has(BionicTrait.TAMEABLE)
			: entity.getBionicOwnerId().equals(player.getUUID());
	}
	public boolean saddled() { return entity.getEquipmentFlags() % 2 == 1; }
	public boolean hasChest() { return (entity.getEquipmentFlags() & 2) != 0; }
	public void equipSaddle(ItemStack stack) {
		saddle = stack.copyWithCount(1);
		sync();
	}
	private void sync() { entity.setEquipmentFlags((saddle.isEmpty() ? 0 : 1) | (chest ? 2 : 0)); }

	public int capacity() {
		var source = BionicTraitRuntime.dominant(entity, BionicTrait.CARGO_CHEST);
		if (source == null) return 0;
		String type = source.profile().entityTypeId().getPath();
		return type.equals("llama") || type.equals("trader_llama")
			? 3 * net.minecraft.util.Mth.clamp(source.profile().stableInt("Strength"), 1, 5) : 15;
	}
	public int seats() {
		var source = ridingSource();
		return source != null && source.profile().entityTypeId().getPath().equals("camel") ? 2 : 1;
	}
	public boolean camel() { return seats() == 2; }

	public boolean jumpingDonor() {
		var source = ridingSource();
		return source != null && switch (source.profile().entityTypeId().getPath()) {
			case "horse", "donkey", "mule", "skeleton_horse", "zombie_horse", "camel" -> true;
			default -> false;
		};
	}

	/** One retained saddle-bearing cube, with stable tie-breaking for bodies made of several mounts. */
	@Nullable
	private SurgicalAssembly.CombinationMember saddleCarrier() {
		var assembly = entity.getAssembly();
		if (assembly == null) return null;
		var dominant = BionicTraitRuntime.dominant(entity, BionicTrait.RIDEABLE);
		var geometry = assembly.attackGeometry();
		return entity.getBionicTraits().members(BionicTrait.RIDEABLE).stream().min(Comparator
			.comparing((SurgicalAssembly.CombinationMember member) -> geometry == null
				|| !geometry.ridingSeats().containsKey(member))
			.thenComparing(member -> assembly.sources().get(member.source()) != dominant)
			.thenComparing(Comparator.comparingDouble((SurgicalAssembly.CombinationMember member) ->
				assembly.sources().get(member.source()).anatomy().volume(member.cube())).reversed())
			.thenComparingInt(SurgicalAssembly.CombinationMember::source)
			.thenComparingInt(SurgicalAssembly.CombinationMember::cube)).orElse(null);
	}

	@Nullable
	private SurgicalAssembly.Source ridingSource() {
		var carrier = saddleCarrier();
		return carrier == null ? null : entity.getAssembly().sources().get(carrier.source());
	}

	/** Shared by client and server: seats were transformed with this cube when the body was packed. */
	@Nullable
	public Vec3 passengerAttachment(Entity passenger) {
		var assembly = entity.getAssembly();
		var carrier = saddleCarrier();
		if (assembly == null || assembly.attackGeometry() == null || carrier == null) return null;
		var seat = assembly.attackGeometry().ridingSeats().get(carrier);
		boolean rear = seats() == 2 && entity.getPassengers().indexOf(passenger) > 0;
		if (seat != null) return rear ? seat.second() : seat.first();
		// Older saved bodies have cube centers but no native passenger anchors. Keep their seat
		// on the selected donor cube until repacking bakes its exact dimensions and orientation.
		Vec3 center = assembly.attackGeometry().carrierOrigins().get(carrier);
		if (center == null) return null;
		double volume = assembly.sources().get(carrier.source()).anatomy().volume(carrier.cube());
		double size = Double.isFinite(volume) && volume > 0 ? Math.cbrt(volume) : 0.5d;
		return center.add(0, size * 0.5d, seats() == 2 ? size * (rear ? -0.25d : 0.25d) : 0);
	}

	public Set<SurgicalAssembly.CombinationMember> armMembers(InteractionHand hand) {
		var assembly = entity.getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return Set.of();
		boolean left = (hand == InteractionHand.MAIN_HAND) == (entity.getMainArm() == HumanoidArm.LEFT);
		List<SurgicalAssembly.ArmAttackGeometry> arms = left ? assembly.attackGeometry().left() : assembly.attackGeometry().right();
		Set<SurgicalAssembly.Limb> limbs = arms.stream().filter(arm -> arm.sourceId() >= 0)
			.map(arm -> assembly.primaryLimbOf(arm.sourceId(), arm.cubeId()))
			.filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
		Set<SurgicalAssembly.CombinationMember> members = new java.util.HashSet<>();
		for (int source = 0; source < assembly.sources().size(); source++) {
			var present = assembly.sources().get(source).presentCubes();
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1))
				if (limbs.contains(assembly.primaryLimbOf(source, cube)))
					members.add(new SurgicalAssembly.CombinationMember(source, cube));
		}
		return Set.copyOf(members);
	}
	public boolean accepts(InteractionHand hand, ItemStack stack) {
		Set<SurgicalAssembly.CombinationMember> members = armMembers(hand);
		if (members.isEmpty()) return false;

		return stack.getAttributeModifiers().modifiers().stream()
			.anyMatch(entry -> entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
				&& entry.modifier().amount() > 0);
	}

	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!mayUse(player)) return InteractionResult.PASS;
		ItemStack held = player.getItemInHand(hand);
		boolean client = entity.level().isClientSide;
		if (player.isShiftKeyDown()) {
			if (held.is(Items.SHEARS) && saddled()) {
				if (!client) { give(player, saddle); saddle = ItemStack.EMPTY; entity.ejectPassengers(); sync(); }
			} else if (held.getItem() instanceof AxeItem && hasChest() && cargo.isEmpty()) {
				if (!client) { give(player, new ItemStack(Items.CHEST)); chest = false; sync(); }
			} else if (accepts(hand, held)) {
				if (!client) {
					ItemStack previous = entity.getItemInHand(hand);
					entity.setItemInHand(hand, held.copyWithCount(1));
					held.consume(1, player);
					give(player, previous);
					entity.setGuaranteedDrop(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				}
			} else if (held.isEmpty() && hasChest() && capacity() > 0) {
				if (!client) player.openMenu(new SimpleMenuProvider((id, inventory, opener) ->
					new BionicCargoMenu(id, inventory, entity, capacity()), Component.translatable("create_biotech.bionic.cargo")),
					buffer -> { buffer.writeInt(entity.getId()); buffer.writeVarInt(capacity()); });
			} else if (held.isEmpty() && !entity.getItemInHand(hand).isEmpty()) {
				if (!client) { give(player, entity.getItemInHand(hand)); entity.setItemInHand(hand, ItemStack.EMPTY); }
			} else return InteractionResult.PASS;
			return InteractionResult.sidedSuccess(client);
		}
		if (held.is(Items.CHEST) && entity.getBionicTraits().has(BionicTrait.CARGO_CHEST) && !hasChest()) {
			if (!client) { chest = true; held.consume(1, player); sync(); }
			return InteractionResult.sidedSuccess(client);
		}
		if (held.is(Items.SADDLE) && entity.isSaddleable() && !saddled()) {
			if (!client) { entity.equipSaddle(held, entity.getSoundSource()); held.consume(1, player); }
			return InteractionResult.sidedSuccess(client);
		}
		if (held.isEmpty() && saddled() && entity.isSaddleable()) {
			if (!client) { entity.setBionicSitting(false); player.startRiding(entity); }
			return InteractionResult.sidedSuccess(client);
		}
		return InteractionResult.PASS;
	}

	private static void give(Player player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
	}
	public void tick() {
		if (entity.level().isClientSide) return;
		if (!entity.getBionicTraits().has(BionicTrait.RIDEABLE) && !saddle.isEmpty()) {
			entity.ejectPassengers(); entity.spawnAtLocation(saddle); saddle = ItemStack.EMPTY; sync();
		}
		if (chest) {
			int capacity = capacity();
			for (int slot = capacity; slot < 15; slot++) entity.spawnAtLocation(cargo.removeItemNoUpdate(slot));
			if (capacity == 0) { entity.spawnAtLocation(Items.CHEST); chest = false; sync(); }
		}
		if (!entity.isAlive() || entity.isNoAi() || entity.isOrderedToSit() || entity.isVehicle()
			|| entity.tickCount % 10 != 0 || !entity.getBionicTraits().has(BionicTrait.LOOT_PICKUP)
			|| !EventHooks.canEntityGrief(entity.level(), entity)) return;
		for (ItemEntity item : entity.level().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(1))) {
			if (!item.isAlive() || item.hasPickUpDelay()) continue;
			for (InteractionHand hand : InteractionHand.values()) {
				if (armMembers(hand).stream().noneMatch(entity.getBionicTraits().members(BionicTrait.LOOT_PICKUP)::contains)
					|| !accepts(hand, item.getItem()) || !entity.canReplaceBionicWeapon(item.getItem(), entity.getItemInHand(hand))) continue;
				entity.spawnAtLocation(entity.getItemInHand(hand));
				entity.setItemInHand(hand, item.getItem().split(1));
				entity.setGuaranteedDrop(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				entity.take(item, 1);
				if (item.getItem().isEmpty()) item.discard();
				break;
			}
		}
	}
	public void drop() {
		entity.spawnAtLocation(saddle); saddle = ItemStack.EMPTY;
		if (chest) entity.spawnAtLocation(Items.CHEST);
		chest = false;
		for (int i = 0; i < 15; i++) entity.spawnAtLocation(cargo.removeItemNoUpdate(i));
		sync();
	}
	public void save(CompoundTag root) {
		CompoundTag tag = new CompoundTag();
		if (!saddle.isEmpty()) tag.put("Saddle", saddle.save(entity.registryAccess()));
		tag.putBoolean("Chest", chest);
		ListTag items = new ListTag();
		for (int i = 0; i < 15; i++) if (!cargo.getItem(i).isEmpty()) {
			CompoundTag item = new CompoundTag(); item.putInt("Slot", i);
			item.put("Item", cargo.getItem(i).save(entity.registryAccess())); items.add(item);
		}
		tag.put("Items", items); root.put("BionicEquipment", tag);
	}
	public void load(CompoundTag root) {
		CompoundTag tag = root.getCompound("BionicEquipment");
		saddle = ItemStack.parseOptional(entity.registryAccess(), tag.getCompound("Saddle"));
		chest = tag.getBoolean("Chest"); cargo.clearContent();
		ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
		for (int i = 0; i < items.size(); i++) {
			CompoundTag item = items.getCompound(i); int slot = item.getInt("Slot");
			if (slot >= 0 && slot < 15) cargo.setItem(slot, ItemStack.parseOptional(entity.registryAccess(), item.getCompound("Item")));
		}
		sync();
	}
}

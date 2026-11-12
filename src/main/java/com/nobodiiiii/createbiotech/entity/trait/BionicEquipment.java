package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.BionicRiding;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Equipment and native passenger slots belong to individual retained cubes. */
public final class BionicEquipment {
	private static final Comparator<CombinationMember> ORDER = Comparator.comparingInt(CombinationMember::source)
		.thenComparingInt(CombinationMember::cube);
	private final SlimeBionicEntity entity;
	private final Map<CombinationMember, ItemStack> saddles = new HashMap<>();
	private final Map<CombinationMember, SimpleContainer> chests = new HashMap<>();
	private final Map<UUID, PassengerSeat> passengers = new HashMap<>();
	@Nullable private CompoundTag lastSnapshot;
	@Nullable private SurgicalAssembly saddleAssembly;
	private Set<CombinationMember> saddleCarriers = Set.of();

	public record PassengerSeat(CombinationMember carrier, int slot) {}
	public BionicEquipment(SlimeBionicEntity entity) { this.entity = entity; }
	public boolean mayUse(Player player) {
		return entity.getBionicOwnerId() == null ? !entity.getBionicTraits().isTameable()
			: entity.getBionicOwnerId().equals(player.getUUID());
	}
	public boolean saddled() { receive(); return !saddles.isEmpty(); }
	public boolean saddled(@Nullable CombinationMember member) { receive(); return saddles.containsKey(member); }
	public boolean hasChest() { receive(); return !chests.isEmpty(); }
	public boolean hasChest(@Nullable CombinationMember member) { receive(); return chests.containsKey(member); }
	public Set<Integer> saddleCubes(int source) {
		receive(); return cubes(saddles.keySet(), source);
	}
	public Set<Integer> chestCubes(int source) {
		receive(); return cubes(chests.keySet(), source);
	}
	private static Set<Integer> cubes(Set<CombinationMember> members, int source) {
		if (members.isEmpty()) return Set.of();
		return members.stream().filter(member -> member.source() == source).map(CombinationMember::cube)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}
	public void equipSaddle(ItemStack stack) {
		var member = defaultCarrier(BionicTrait.RIDEABLE, saddles.keySet());
		if (member != null) equipSaddle(member, stack);
	}
	private void equipSaddle(CombinationMember member, ItemStack stack) {
		saddles.put(member, stack.copyWithCount(1)); sync();
	}
	public int capacity() { receive(); return chests.keySet().stream().mapToInt(this::capacity).sum(); }
	public int capacity(@Nullable CombinationMember member) {
		var source = source(member);
		if (source == null) return 0;
		String type = source.profile().entityTypeId().getPath();
		return type.equals("llama") || type.equals("trader_llama")
			? 3 * net.minecraft.util.Mth.clamp(source.profile().stableInt("Strength"), 1, 5) : 15;
	}
	@Nullable public SimpleContainer cargo(CombinationMember member) { receive(); return chests.get(member); }
	private SimpleContainer newCargo(CombinationMember member) {
		return new SimpleContainer(capacity(member)) {
			@Override public boolean stillValid(Player player) {
				return entity.isAlive() && entity.distanceToSqr(player) < 64 && mayUse(player)
					&& chests.get(member) == this && entity.getBionicTraits().members(BionicTrait.CARGO_CHEST).contains(member);
			}
		};
	}
	public int seats() { receive(); return saddles.keySet().stream().mapToInt(this::seats).sum(); }
	private int seats(CombinationMember member) { return isCamel(member) ? 2 : 1; }
	private boolean isCamel(@Nullable CombinationMember member) {
		var source = source(member);
		return source != null && source.profile().entityTypeId().getPath().equals("camel");
	}
	public boolean camel() { return isCamel(saddleCarrier()); }
	public boolean jumpingDonor() {
		var source = source(saddleCarrier());
		return source != null && switch (source.profile().entityTypeId().getPath()) {
			case "horse", "donkey", "mule", "skeleton_horse", "zombie_horse", "camel" -> true;
			default -> false;
		};
	}
	@Nullable private SurgicalAssembly.Source source(@Nullable CombinationMember member) {
		var assembly = entity.getAssembly();
		return assembly == null || member == null || member.source() < 0 || member.source() >= assembly.sources().size()
			? null : assembly.sources().get(member.source());
	}
	@Nullable private CombinationMember defaultCarrier(BionicTrait trait, Set<CombinationMember> occupied) {
		var assembly = entity.getAssembly();
		if (assembly == null) return null;
		var geometry = assembly.attackGeometry();
		var dominant = BionicTraitRuntime.dominant(entity, trait);
		return entity.getBionicTraits().members(trait).stream().filter(member -> !occupied.contains(member)
			&& (trait != BionicTrait.RIDEABLE || allowedSaddleCarriers().contains(member))).min(Comparator
			.comparing((CombinationMember member) -> trait == BionicTrait.RIDEABLE
				&& (geometry == null || !geometry.ridingSeats().containsKey(member)))
			.thenComparing(member -> assembly.sources().get(member.source()) != dominant)
			.thenComparing(Comparator.comparingDouble((CombinationMember member) ->
				assembly.sources().get(member.source()).anatomy().volume(member.cube())).reversed())
			.thenComparing(ORDER)).orElse(null);
	}
	private Set<CombinationMember> allowedSaddleCarriers() {
		var assembly = entity.getAssembly();
		if (assembly == null) return Set.of();
		if (saddleAssembly != assembly) {
			Set<CombinationMember> carriers = new HashSet<>();
			for (int source = 0; source < assembly.sources().size(); source++) {
				var cubes = BionicSaddleCarriers.select(assembly.sources().get(source));
				for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
					carriers.add(new CombinationMember(source, cube));
			}
			saddleCarriers = Set.copyOf(carriers);
			saddleAssembly = assembly;
		}
		return saddleCarriers;
	}
	private boolean canSaddle(@Nullable CombinationMember member) {
		return member != null && allowedSaddleCarriers().contains(member)
			&& entity.getBionicTraits().members(BionicTrait.RIDEABLE).contains(member);
	}
	/** The first passenger selects the active saddle and its riding behavior. */
	@Nullable public CombinationMember saddleCarrier() {
		receive();
		var first = entity.getFirstPassenger();
		var seat = first == null ? null : passengers.get(first.getUUID());
		return seat != null && saddles.containsKey(seat.carrier()) ? seat.carrier()
			: saddles.keySet().stream().min(ORDER).orElse(null);
	}
	@Nullable public PassengerSeat passengerSeat(Entity passenger) {
		receive();
		var assigned = passengers.get(passenger.getUUID());
		if (assigned != null && saddled(assigned.carrier()) && canSaddle(assigned.carrier())
			&& assigned.slot() >= 0 && assigned.slot() < seats(assigned.carrier()))
			return assigned;
		return freeSeat(passenger, null);
	}
	@Nullable private PassengerSeat freeSeat(Entity passenger, @Nullable CombinationMember selected) {
		if (selected != null) {
			int slot = saddles.containsKey(selected) && canSaddle(selected) ? firstFreeSlot(passenger, selected) : -1;
			return slot < 0 ? null : new PassengerSeat(selected, slot);
		}
		PassengerSeat result = null;
		for (var member : saddles.keySet()) {
			if (!canSaddle(member) || result != null && ORDER.compare(member, result.carrier()) >= 0) continue;
			int slot = firstFreeSlot(passenger, member);
			if (slot >= 0) result = new PassengerSeat(member, slot);
		}
		return result;
	}
	private int firstFreeSlot(Entity passenger, CombinationMember member) {
		for (int slot = 0; slot < seats(member); slot++) {
			boolean occupied = false;
			for (var entry : passengers.entrySet()) {
				var seat = entry.getValue();
				if (!entry.getKey().equals(passenger.getUUID()) && seat.carrier().equals(member) && seat.slot() == slot) {
					occupied = true;
					break;
				}
			}
			if (!occupied) return slot;
		}
		return -1;
	}
	public boolean canSeat(Entity passenger) { return passengerSeat(passenger) != null; }
	public void passengerAdded(Entity passenger) {
		if (entity.level().isClientSide) return;
		var seat = passengerSeat(passenger);
		if (seat != null) { passengers.put(passenger.getUUID(), seat); sync(); }
	}
	public void passengerRemoved(Entity passenger) {
		if (!entity.level().isClientSide && passengers.remove(passenger.getUUID()) != null) sync();
	}
	private void mount(Player player, CombinationMember member) {
		if (player.getVehicle() == entity) return;
		var seat = freeSeat(player, member);
		if (seat == null) return;
		passengers.put(player.getUUID(), seat);
		entity.setBionicSitting(false);
		if (!player.startRiding(entity)) passengers.remove(player.getUUID());
		sync();
	}
	@Nullable public SurgicalAssembly.RidingSeat ridingSeat(CombinationMember carrier) {
		var assembly = entity.getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return null;
		var seat = assembly.attackGeometry().ridingSeats().get(carrier);
		return seat == null ? null : seat.rotation() != null ? seat
			: new SurgicalAssembly.RidingSeat(seat.first(), seat.second(), BionicRiding.restRotation(assembly, carrier));
	}
	public Vec3 carrierOrigin() {
		var assembly = entity.getAssembly();
		var carrier = saddleCarrier();
		return assembly == null || assembly.attackGeometry() == null || carrier == null ? Vec3.ZERO
			: assembly.attackGeometry().carrierOrigins().getOrDefault(carrier, Vec3.ZERO);
	}

	/** Only compare eligible saddle origins; a sole candidate needs no click transform or distance check. */
	@Nullable private CombinationMember nearestSaddle(Player player, @Nullable Vec3 hit, boolean mounting) {
		var candidates = mounting ? saddles.keySet() : entity.getBionicTraits().members(BionicTrait.RIDEABLE);
		CombinationMember selected = null;
		Vec3 point = null;
		double closest = Double.POSITIVE_INFINITY;
		var assembly = entity.getAssembly();
		var origins = assembly == null || assembly.attackGeometry() == null ? Map.<CombinationMember, Vec3>of()
			: assembly.attackGeometry().carrierOrigins();
		for (var member : candidates) {
			if (!canSaddle(member) || (mounting ? firstFreeSlot(player, member) < 0 : saddles.containsKey(member))) continue;
			if (selected == null) {
				selected = member;
				continue;
			}
			if (point == null) {
				point = saddleClickPoint(player, hit);
				closest = origins.getOrDefault(selected, Vec3.ZERO).distanceToSqr(point);
			}
			double distance = origins.getOrDefault(member, Vec3.ZERO).distanceToSqr(point);
			if (distance < closest || distance == closest && ORDER.compare(member, selected) < 0) {
				closest = distance;
				selected = member;
			}
		}
		return selected;
	}
	private Vec3 saddleClickPoint(Player player, @Nullable Vec3 hit) {
		Vec3 worldPoint;
		if (hit != null) {
			worldPoint = entity.position().add(hit);
		} else {
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getLookAngle().scale(player.entityInteractionRange() + 1));
			worldPoint = entity.getBoundingBox().clip(eye, end).orElse(entity.position());
		}
		Vec3 point = entity.worldToBody(worldPoint);
		if (entity.isBlockAttached()) return point;
		Vec3 pivot = carrierOrigin();
		return pivot.add(BionicRiding.inverse(BionicRiding.bodyRotation(entity, 1)).rotate(point.subtract(pivot)));
	}

	/** Pick the first retained cuboid along the same player ray used by entity interaction. */
	@Nullable private CombinationMember pick(Player player, @Nullable Vec3 hit) {
		var assembly = entity.getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return null;
		var geometry = assembly.attackGeometry();
		Vec3 pivot = carrierOrigin();
		var rotation = entity.isBlockAttached() ? com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation.IDENTITY
			: BionicRiding.bodyRotation(entity, 1);
		Vec3 start = entity.worldToBody(player.getEyePosition());
		Vec3 end = entity.worldToBody(player.getEyePosition().add(player.getLookAngle().scale(player.entityInteractionRange() + 1)));
		start = pivot.add(BionicRiding.inverse(rotation).rotate(start.subtract(pivot)));
		end = pivot.add(BionicRiding.inverse(rotation).rotate(end.subtract(pivot)));
		CombinationMember selected = null;
		double closest = Double.POSITIVE_INFINITY;
		for (var entry : geometry.carrierCubes().entrySet()) {
			List<Vec3> corners = entry.getValue();
			Vec3 base = corners.getFirst();
			Vec3 a = corners.get(1).subtract(base), b = corners.get(2).subtract(base), c = corners.get(4).subtract(base);
			Vec3 localStart = project(start.subtract(base), a, b, c), localEnd = project(end.subtract(base), a, b, c);
			var intersection = new AABB(0, 0, 0, 1, 1, 1).inflate(0.04).clip(localStart, localEnd);
			if (intersection.isEmpty()) continue;
			double distance = localStart.distanceToSqr(intersection.get()) / Math.max(1.0e-9, localStart.distanceToSqr(localEnd));
			if (distance < closest || distance == closest && (selected == null || ORDER.compare(entry.getKey(), selected) < 0)) {
				closest = distance; selected = entry.getKey();
			}
		}
		for (var entry : geometry.equipmentBounds().entrySet()) {
			var member = entry.getKey(); var bounds = entry.getValue();
			for (var visual : activeBounds(member, bounds)) {
				var intersection = box(visual).clip(start, end);
				if (intersection.isEmpty()) continue;
				double distance = start.distanceToSqr(intersection.get()) / Math.max(1.0e-9, start.distanceToSqr(end));
				if (distance < closest || distance == closest && (selected == null || ORDER.compare(member, selected) < 0)) {
					closest = distance; selected = member;
				}
			}
		}
		if (!geometry.carrierCubes().isEmpty()) return selected;
		// Assemblies saved before cuboid picking retain their per-cube attachment origins.
		Vec3 point = hit == null ? end : pivot.add(BionicRiding.inverse(rotation).rotate(
			entity.worldToBody(entity.position().add(hit)).subtract(pivot)));
		return geometry.carrierOrigins().entrySet().stream().min(Comparator
			.comparingDouble((Map.Entry<CombinationMember, Vec3> entry) -> entry.getValue().distanceToSqr(point))
			.thenComparing(Map.Entry::getKey, ORDER)).map(Map.Entry::getKey).orElse(null);
	}
	private List<SurgicalAssembly.VisualBounds> activeBounds(CombinationMember member,
		SurgicalAssembly.CarrierEquipmentBounds bounds) {
		List<SurgicalAssembly.VisualBounds> active = new ArrayList<>(2);
		if (saddled(member) && bounds.saddle() != null) active.add(bounds.saddle());
		if (hasChest(member) && bounds.chest() != null) active.add(bounds.chest());
		return active;
	}
	private static AABB box(SurgicalAssembly.VisualBounds bounds) {
		return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
	}
	/** One pick-only multipart envelope admits clicks on native tack outside the body's collision. */
	@Nullable public AABB interactionBounds() {
		var assembly = entity.getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return null;
		AABB result = null;
		for (var entry : assembly.attackGeometry().equipmentBounds().entrySet()) {
			for (var bounds : activeBounds(entry.getKey(), entry.getValue())) {
				AABB transformed = entity.isBlockAttached()
					? BionicRiding.rotateBounds(box(bounds), Vec3.ZERO, entity.blockAttachmentRotation())
					: BionicRiding.rotateBounds(box(bounds), carrierOrigin(), BionicRiding.bodyRotation(entity, 1));
				transformed = BionicRiding.rotateBounds(transformed, Vec3.ZERO,
					com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation.around(new Vec3(0, 1, 0), entity.isBlockAttached() ? 0 : -entity.yBodyRot))
					.move(entity.position());
				result = result == null ? transformed : result.minmax(transformed);
			}
		}
		return result;
	}

	private static Vec3 project(Vec3 point, Vec3 a, Vec3 b, Vec3 c) {
		return new Vec3(point.dot(a) / Math.max(a.lengthSqr(), 1.0e-9),
			point.dot(b) / Math.max(b.lengthSqr(), 1.0e-9), point.dot(c) / Math.max(c.lengthSqr(), 1.0e-9));
	}

	public Set<SurgicalAssembly.CombinationMember> armMembers(InteractionHand hand) {
		var assembly = entity.getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return Set.of();
		boolean left = (hand == InteractionHand.MAIN_HAND) == (entity.getMainArm() == HumanoidArm.LEFT);
		List<SurgicalAssembly.ArmAttackGeometry> arms = left ? assembly.attackGeometry().left() : assembly.attackGeometry().right();
		Set<SurgicalAssembly.Limb> limbs = arms.stream().filter(arm -> arm.sourceId() >= 0 && !entity.isBionicWing(arm))
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

	public InteractionResult interact(Player player, InteractionHand hand, @Nullable Vec3 hit) {
		if (entity.getBionicOwnerId() != null && !mayUse(player)) return InteractionResult.PASS;
		receive();
		ItemStack held = player.getItemInHand(hand);
		var member = player.isShiftKeyDown() || held.is(Items.CHEST) ? pick(player, hit) : null;
		boolean client = entity.level().isClientSide;
		if (player.isShiftKeyDown()) {
			if (held.is(Items.SHEARS) && saddled(member)) {
				if (!client) {
					for (Entity rider : List.copyOf(entity.getPassengers())) {
						var seat = passengers.get(rider.getUUID());
						if (seat != null && seat.carrier().equals(member)) rider.stopRiding();
					}
					give(player, saddles.remove(member)); sync();
				}
			} else if (!mayUse(player)) {
				return InteractionResult.PASS;
			} else if (held.getItem() instanceof AxeItem && hasChest(member) && chests.get(member).isEmpty()) {
				if (!client) { give(player, new ItemStack(Items.CHEST)); chests.remove(member); sync(); }
			} else if (accepts(hand, held)) {
				if (!client) {
					ItemStack previous = entity.getItemInHand(hand);
					entity.setItemInHand(hand, held.copyWithCount(1)); held.consume(1, player); give(player, previous);
					entity.setGuaranteedDrop(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				}
			} else if (held.isEmpty() && hasChest(member)) {
				if (!client) player.openMenu(new SimpleMenuProvider((id, inventory, opener) ->
					new BionicCargoMenu(id, inventory, entity, member, capacity(member)), Component.translatable("create_biotech.bionic.cargo")),
					buffer -> { buffer.writeInt(entity.getId()); buffer.writeVarInt(member.source());
						buffer.writeVarInt(member.cube()); buffer.writeVarInt(capacity(member)); });
			} else if (held.isEmpty() && !entity.getItemInHand(hand).isEmpty()) {
				if (!client) { give(player, entity.getItemInHand(hand)); entity.setItemInHand(hand, ItemStack.EMPTY); }
			} else return InteractionResult.PASS;
			return InteractionResult.sidedSuccess(client);
		}
		if (held.is(Items.CHEST) && member != null && entity.getBionicTraits().members(BionicTrait.CARGO_CHEST).contains(member)
			&& !hasChest(member) && mayUse(player)) {
			if (!client) {
				chests.put(member, newCargo(member)); held.consume(1, player);
				boolean llama = source(member).profile().entityTypeId().getPath().endsWith("llama");
				entity.playSound(llama ? net.minecraft.sounds.SoundEvents.LLAMA_CHEST : net.minecraft.sounds.SoundEvents.DONKEY_CHEST,
					1, (entity.getRandom().nextFloat() - entity.getRandom().nextFloat()) * 0.2f + 1); sync();
			}
			return InteractionResult.sidedSuccess(client);
		}
		if (held.is(Items.SADDLE) && entity.isSaddleable()) {
			var saddle = nearestSaddle(player, hit, false);
			if (saddle != null) {
				if (!client) {
					equipSaddle(saddle, held); held.consume(1, player);
					entity.playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE, 1, 1);
				}
				return InteractionResult.sidedSuccess(client);
			}
		}
		return InteractionResult.PASS;
	}
	/** AbstractHorse/Camel: let the held item interact before falling through to mounting. */
	public InteractionResult ride(Player player, InteractionHand hand, @Nullable Vec3 hit) {
		if (player.isSecondaryUseActive()) return InteractionResult.PASS;
		receive();
		if (!entity.isSaddleable()) return InteractionResult.PASS;
		var member = nearestSaddle(player, hit, true);
		if (member == null) return InteractionResult.PASS;
		ItemStack held = player.getItemInHand(hand);
		if (!held.isEmpty()) {
			InteractionResult result = held.interactLivingEntity(player, entity, hand);
			if (result.consumesAction()) return result;
		}
		if (!entity.level().isClientSide) mount(player, member);
		return InteractionResult.sidedSuccess(entity.level().isClientSide);
	}

	private static void give(Player player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
	}
	public void tick() {
		if (entity.level().isClientSide) return;
		// Nothing equipped and nobody mounted: every loop below would be a no-op.
		if (saddles.isEmpty() && chests.isEmpty() && passengers.isEmpty() && entity.getPassengers().isEmpty())
			return;
		boolean changed = false;
		for (var member : Set.copyOf(saddles.keySet())) {
			if (canSaddle(member)) continue;
			for (Entity rider : List.copyOf(entity.getPassengers())) {
				var seat = passengers.get(rider.getUUID());
				if (seat != null && seat.carrier().equals(member)) rider.stopRiding();
			}
			entity.spawnAtLocation(saddles.remove(member)); changed = true;
		}
		for (var member : Set.copyOf(chests.keySet())) {
			if (entity.getBionicTraits().members(BionicTrait.CARGO_CHEST).contains(member)) continue;
			dropChest(chests.remove(member)); changed = true;
		}
		Set<UUID> riders = entity.getPassengers().stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
		changed |= passengers.keySet().removeIf(id -> !riders.contains(id));
		for (Entity rider : entity.getPassengers()) if (!passengers.containsKey(rider.getUUID())) passengerAdded(rider);
		if (changed) sync();
	}
	/** Mob.aiStep and pickUpItem own discovery, pickup events and removal. */
	public ItemStack equipLoot(ItemStack incoming) {
		if (!entity.getBionicTraits().has(BionicTrait.LOOT_PICKUP)) return ItemStack.EMPTY;
		for (InteractionHand hand : InteractionHand.values()) {
			if (armMembers(hand).stream().noneMatch(entity.getBionicTraits().members(BionicTrait.LOOT_PICKUP)::contains)
				|| !accepts(hand, incoming) || !entity.canReplaceBionicWeapon(incoming, entity.getItemInHand(hand))) continue;
			entity.spawnAtLocation(entity.getItemInHand(hand));
			ItemStack equipped = incoming.copyWithCount(1);
			entity.equipBionicLoot(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, equipped);
			return equipped;
		}
		return ItemStack.EMPTY;
	}
	private void dropChest(SimpleContainer cargo) {
		entity.spawnAtLocation(Items.CHEST);
		for (int slot = 0; slot < cargo.getContainerSize(); slot++) entity.spawnAtLocation(cargo.removeItemNoUpdate(slot));
	}
	/** Transfer installed saddles when the captured body is unpacked onto a surgical table. */
	public List<ItemStack> removeSaddles() {
		if (saddles.isEmpty()) return List.of();
		entity.ejectPassengers();
		List<ItemStack> removed = new ArrayList<>(saddles.values());
		saddles.clear();
		passengers.clear();
		sync();
		return removed;
	}
	public void drop() {
		saddles.values().forEach(entity::spawnAtLocation); saddles.clear();
		chests.values().forEach(this::dropChest); chests.clear(); passengers.clear(); sync();
	}
	private static CompoundTag memberTag(CombinationMember member) {
		CompoundTag tag = new CompoundTag(); tag.putInt("Source", member.source()); tag.putInt("Cube", member.cube()); return tag;
	}
	private static CombinationMember member(CompoundTag tag) { return new CombinationMember(tag.getInt("Source"), tag.getInt("Cube")); }
	private ListTag savePassengers() {
		ListTag list = new ListTag();
		passengers.forEach((id, seat) -> {
			CompoundTag tag = memberTag(seat.carrier()); tag.putUUID("Passenger", id); tag.putInt("Seat", seat.slot()); list.add(tag);
		});
		return list;
	}
	private void loadPassengers(CompoundTag tag) {
		passengers.clear(); ListTag list = tag.getList("Passengers", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			if (entry.hasUUID("Passenger")) passengers.put(entry.getUUID("Passenger"), new PassengerSeat(member(entry), entry.getInt("Seat")));
		}
	}
	private void sync() {
		CompoundTag snapshot = new CompoundTag(); ListTag list = new ListTag();
		Set<CombinationMember> members = new TreeSet<>(ORDER); members.addAll(saddles.keySet()); members.addAll(chests.keySet());
		for (var member : members) {
			CompoundTag entry = memberTag(member); entry.putBoolean("Saddle", saddles.containsKey(member));
			entry.putBoolean("Chest", chests.containsKey(member)); list.add(entry);
		}
		snapshot.put("Cubes", list); snapshot.put("Passengers", savePassengers()); entity.setBionicEquipmentData(snapshot);
	}
	private void receive() {
		if (!entity.level().isClientSide) return;
		CompoundTag snapshot = entity.getBionicEquipmentData();
		if (snapshot == lastSnapshot) return;
		lastSnapshot = snapshot; saddles.clear(); chests.clear();
		ListTag list = snapshot.getList("Cubes", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i); var member = member(entry);
			if (entry.getBoolean("Saddle")) saddles.put(member, new ItemStack(Items.SADDLE));
			if (entry.getBoolean("Chest")) chests.put(member, newCargo(member));
		}
		loadPassengers(snapshot);
	}
	public void save(CompoundTag root) {
		CompoundTag tag = new CompoundTag(); ListTag list = new ListTag();
		Set<CombinationMember> members = new TreeSet<>(ORDER); members.addAll(saddles.keySet()); members.addAll(chests.keySet());
		for (var member : members) {
			CompoundTag entry = memberTag(member);
			if (saddles.containsKey(member)) entry.put("Saddle", saddles.get(member).save(entity.registryAccess()));
			SimpleContainer cargo = chests.get(member);
			if (cargo != null) {
				entry.putBoolean("Chest", true); ListTag items = new ListTag();
				for (int slot = 0; slot < cargo.getContainerSize(); slot++) if (!cargo.getItem(slot).isEmpty()) {
					CompoundTag item = new CompoundTag(); item.putInt("Slot", slot);
					item.put("Item", cargo.getItem(slot).save(entity.registryAccess())); items.add(item);
				}
				entry.put("Items", items);
			}
			list.add(entry);
		}
		tag.put("Cubes", list); tag.put("Passengers", savePassengers()); root.put("BionicEquipment", tag);
	}
	private void loadItems(SimpleContainer cargo, CompoundTag tag) {
		ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
		for (int i = 0; i < items.size(); i++) {
			CompoundTag item = items.getCompound(i); int slot = item.getInt("Slot");
			if (slot >= 0 && slot < cargo.getContainerSize())
				cargo.setItem(slot, ItemStack.parseOptional(entity.registryAccess(), item.getCompound("Item")));
		}
	}
	public void load(CompoundTag root) {
		CompoundTag tag = root.getCompound("BionicEquipment"); saddles.clear(); chests.clear();
		ListTag list = tag.getList("Cubes", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i); var member = member(entry);
			ItemStack saddle = ItemStack.parseOptional(entity.registryAccess(), entry.getCompound("Saddle"));
			if (!saddle.isEmpty()) saddles.put(member, saddle.copyWithCount(1));
			if (entry.getBoolean("Chest")) { SimpleContainer cargo = newCargo(member); loadItems(cargo, entry); chests.put(member, cargo); }
		}
		if (!tag.contains("Cubes", Tag.TAG_LIST)) {
			// Migrate the original one-saddle/one-chest save onto its former donor carrier.
			var saddleCarrier = defaultCarrier(BionicTrait.RIDEABLE, Set.of());
			ItemStack saddle = ItemStack.parseOptional(entity.registryAccess(), tag.getCompound("Saddle"));
			if (saddleCarrier != null && !saddle.isEmpty()) saddles.put(saddleCarrier, saddle.copyWithCount(1));
			var chestCarrier = defaultCarrier(BionicTrait.CARGO_CHEST, Set.of());
			if (chestCarrier != null && tag.getBoolean("Chest")) {
				SimpleContainer cargo = newCargo(chestCarrier); loadItems(cargo, tag); chests.put(chestCarrier, cargo);
			}
		}
		loadPassengers(tag); sync();
	}
}

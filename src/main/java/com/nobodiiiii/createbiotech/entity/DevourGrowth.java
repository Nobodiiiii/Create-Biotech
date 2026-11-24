package com.nobodiiiii.createbiotech.entity;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.AttackGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.BodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.HitboxGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Joint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Seam;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Source;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.VisualBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.DevouredGrowthPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.entity.inventory.DevourerInventory;
import com.nobodiiiii.createbiotech.registry.CBAttachmentTypes;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Grow one intact mimic organ on the existing body. All previous sources and joints keep their indices. */
public final class DevourGrowth {
	private DevourGrowth() {}

	static boolean canDevour(Mob hunter) {
		return DevourerInventory.hasEmptySlot(hunter)
			&& (!(hunter instanceof SlimeBionicEntity bionic) || bionic.hasDevourCapacity());
	}

	/** Death loot is reached after totems and cancelled deaths, including kills by inherited attacks. */
	public static void handleDeathDrops(LivingDropsEvent event) {
		var prey = event.getEntity();
		if (!(prey.level() instanceof ServerLevel level)) return;
		if (!(event.getSource().getEntity() instanceof Mob hunter) || !hunter.isAlive()
			|| !DevourerInventory.canCollect(hunter) || !canDevour(hunter)) return;
		Mob recipient = hunter;
		if (DevourAttackGoal.isPrey(hunter, prey, DevourAttackGoal.size(hunter))) {
			var grown = absorb(level, hunter, prey);
			if (grown != null) {
				recipient = grown;
				if (event.getSource() instanceof DevourAttackGoal.CrushDamage crush) crush.confirmDeath(prey);
			}
		}
		if (!event.isCanceled() && !event.getDrops().isEmpty())
			recipient.getData(CBAttachmentTypes.DEVOURER_INVENTORY).collect(event.getDrops());
	}

	@Nullable
	private static Mob absorb(ServerLevel level, Mob hunter, LivingEntity prey) {
		SurgicalAssembly base = hunter instanceof SlimeBionicEntity body ? body.getAssembly() : slimeBody((DevourerEntity) hunter);
		if (base == null || base.attackGeometry() == null) return null;
		var anchor = shellAnchor(base, hunter instanceof SlimeBionicEntity body
			? body.getBionicTraits().members(BionicTrait.DEVOUR) : Set.of(new CombinationMember(0, 1)));
		if (anchor == null) return null;
		LivingEntity donor = prey;
		if (prey instanceof Player player) {
			var proxy = CBEntityTypes.PLAYER_MIMIC.get().create(level);
			if (proxy == null) return null;
			proxy.setImitatedPlayer(player.getGameProfile());
			donor = proxy;
		}
		var profile = MimicProfile.capture(donor);
		if (profile == null) return null;
		var meal = new DevourGrowthQueue.Meal(profile, prey.getBbWidth(), prey.getBbHeight(), UUID.randomUUID(),
			base.sources().get(anchor.source()).donorId(), anchor.cube(), hunter.getRandom().nextLong());
		if (!meal.valid()) return null;
		if (hunter instanceof SlimeBionicEntity bionic)
			return bionic.queueGrowth(meal) ? bionic : null;
		var bionic = CBEntityTypes.SLIME_BIONIC.get().create(level);
		if (bionic == null) return null;
		// Preserve vanilla mob state (persistence, effects, name, motion, equipment and custom loot).
		// The proxy needs its own UUID while the slime is still registered with the level.
		var state = hunter.saveWithoutId(new CompoundTag());
		state.remove("UUID");
		state.remove("Passengers");
		bionic.load(state);
		bionic.getPersistentData().putBoolean("WildDevourer", true);
		bionic.setAssembly(base);
		if (!bionic.queueGrowth(meal)) return null;
		bionic.setHealth(bionic.getMaxHealth() * hunter.getHealth() / hunter.getMaxHealth());
		SlimeMimicHandler.forceSetSlimeMimic(bionic, true);
		if (!level.addFreshEntity(bionic)) return null;
		hunter.discard();
		return bionic;
	}

	/** The old growth writer appended its proxies after all installed sources and never moved the existing cubes. */
	static SurgicalAssembly installedPrefix(SurgicalAssembly saved) {
		int count = 0;
		while (count < saved.sources().size() && !saved.sources().get(count).profile().needsPreyTopology()) count++;
		final int installed = count;
		if (installed == 0 || installed == saved.sources().size() || saved.attackGeometry() == null) return saved;
		var body = SurgicalAssembly.createComposite(saved.sources().subList(0, installed),
			saved.joints().stream().filter(joint -> joint.firstSource() < installed && joint.secondSource() < installed).toList(),
			saved.combinations(), saved.limbs(), saved.layoutFacing(), saved.layoutLayPose());
		if (body == null) return saved;
		var geometry = saved.attackGeometry();
		var origins = new HashMap<>(geometry.carrierOrigins());
		var cubes = new HashMap<>(geometry.carrierCubes());
		origins.keySet().removeIf(m -> m.source() >= installed);
		cubes.keySet().retainAll(origins.keySet());
		var attacks = AttackGeometry.create(geometry.right(), geometry.left(), origins, geometry.ridingSeats(), cubes, geometry.equipmentBounds());
		double volume = SurgicalVolumeSampler.unionVolume(cubes.values().stream().map(DevouredGrowthPose::orderedCorners).toList());
		if (attacks == null || !SurgicalHealthCalibration.validMeasuredVolume(volume, saved.hitboxGeometry()))
			return saved;
		var parts = new HashMap<>(geometry.carrierParts());
		parts.keySet().retainAll(origins.keySet());
		attacks = attacks.withCarrierParts(parts);
		body = body.withBodyGeometry(saved.bodyBounds(), saved.hitboxGeometry(), volume).withAttackGeometry(attacks);
		return saved.blockAttachmentCarrier() == null ? body : body.withBlockAttachmentCarrier(saved.blockAttachmentCarrier());
	}

	private static SurgicalAssembly slimeBody(DevourerEntity slime) {
		float edge = slime.getSize() * 0.5f;
		var profile = MimicProfile.capture(slime);
		var anatomy = BionicAnatomySnapshot.ofParts(Map.of(0, Set.of("devour_inner"), 1, Set.of("devour_outer")), 2)
			.withVolumes(new float[]{(float)Math.pow(edge * 0.75, 3), edge * edge * edge}, edge * edge * edge);
		BitSet both = new BitSet();
		both.set(0, 2);
		var base = SurgicalAssembly.create(profile, UUID.randomUUID(), 2, both, anatomy,
			List.of(Seam.of(0, 1)), new BitSet(), List.of());
		if (base == null) return null;
		var outer = new CombinationMember(0, 1);
		var inner = new CombinationMember(0, 0);
		Vec3 center = new Vec3(0, edge / 2, 0);
		var visual = VisualBounds.create(-edge / 2, 0, -edge / 2, edge / 2, edge, edge / 2);
		var bounds = BodyBounds.create(edge, edge, edge);
		if (visual == null || bounds == null) return null;
		return base.withBodyGeometry(bounds, new HitboxGeometry(visual, visual, List.of()), edge * edge * edge)
			.withAttackGeometry(new AttackGeometry(List.of(), List.of(), Map.of(outer, center, inner, center),
				Map.of(), Map.of(outer, corners(edge, 0, edge), inner, corners(edge * 0.75, edge / 8, edge * 0.75))));
	}

	private static CombinationMember shellAnchor(SurgicalAssembly base, Set<CombinationMember> eligible) {
		var geometry = base.attackGeometry();
		return eligible.stream().filter(member -> geometry.carrierCubes().containsKey(member)).filter(member -> {
			var source = base.sources().get(member.source());
			return source.profile().matches(CBEntityTypes.DEVOURER.getId())
				&& source.anatomy().parts().getOrDefault(member.cube(), Set.of()).contains("devour_outer");
		}).min(Comparator.<CombinationMember>comparingInt(m -> -base.sources().get(m.source()).profile().stableInt("Size"))
			.thenComparingInt(CombinationMember::source).thenComparingInt(CombinationMember::cube)).orElse(null);
	}

	/** Builds a hidden placement snapshot from immutable inputs. The calibrated body volume comes from native packing. */
	static SurgicalAssembly append(SurgicalAssembly base, DevourGrowthQueue.Meal meal) {
		var geometry = base.attackGeometry();
		if (geometry == null || base.sources().size() >= SurgicalAssembly.MAX_SOURCES) return null;
		int shellIndex = 0;
		while (shellIndex < base.sources().size()) {
			var candidate = base.sources().get(shellIndex);
			if (candidate.donorId().equals(meal.shellDonor()) && candidate.containsCube(meal.shellCube())) break;
			shellIndex++;
		}
		if (shellIndex == base.sources().size()) return null;
		var anchor = new CombinationMember(shellIndex, meal.shellCube());
		var shellCorners = geometry.carrierCubes().get(anchor);
		if (shellCorners == null) return null;
		var shell = base.sources().get(shellIndex);
		double shellCenter = (shell.profile().stableInt("Size") + 1) / 4d;
		Vec3 tableCenter = shell.originOffset().add(shell.layPose().translation())
			.add(shell.layPose().rotate(new Vec3(0, shellCenter, 0))).add(shell.cubeOffsets().getOrDefault(anchor.cube(), Vec3.ZERO));
		Vec3 bodyOrigin = base.layoutLayPose().inverseRotate(tableCenter).subtract(geometry.carrierOrigins().get(anchor));
		var previous = base.hitboxGeometry();
		float width = meal.width(), height = meal.height();
		GrowthPlacement growth = GrowthPlacement.sample(DevouredGrowthPose.orderedCorners(shellCorners), width, height,
			meal.donor(), RandomSource.create(meal.seed()));
		if (growth == null) return null;
		var prey = meal.profile().asDevouredForm(width, height, 0L, growth.pose().burial(), growth.pose().anchor(),
			growth.normal(), growth.localSlide());
		var pose = base.layoutLayPose();
		// Source rotations pivot around the intact prey's center. Its unrotated origin is at its feet.
		Vec3 origin = growth.center().subtract(0, height / 2d, 0);
		Vec3 offset = pose.rotate(bodyOrigin.add(origin)).subtract(pose.translation());
		var rotation = growth.pose().rotation().reframe(SurgicalLayPose.IDENTITY, pose);
		float volume = width * width * height;
		var anatomy = BionicAnatomySnapshot.ofParts(Map.of(0, Set.of("devoured_form")), 1).withVolumes(new float[]{volume});
		if (anatomy == null) return null;
		var source = Source.create(prey, meal.donor(), 1, BitSet.valueOf(new long[]{1}), anatomy, List.of(),
			new BitSet(), List.of(), base.layoutFacing(), pose, offset, Map.of(), Map.of(0, rotation));
		if (source == null) return null;
		var sources = new ArrayList<>(base.sources());
		var organ = new CombinationMember(sources.size(), 0);
		sources.add(source);
		var joints = new ArrayList<>(base.joints());
		joints.add(new Joint(anchor.source(), anchor.cube(), organ.source(), 0));
		var grown = SurgicalAssembly.createComposite(sources, joints, base.combinations(), base.limbs(), base.layoutFacing(), pose);
		if (grown == null) return null;
		var overall = extend(previous.overall(), growth.corners());
		var body = extend(previous.body(), growth.corners());
		if (overall == null || body == null) return null;
		var hitbox = new HitboxGeometry(overall, body, previous.limbs());
		var centers = new HashMap<>(geometry.carrierOrigins());
		var cubes = new HashMap<>(geometry.carrierCubes());
		centers.put(organ, growth.center());
		cubes.put(organ, growth.corners());
		var attacks = AttackGeometry.create(geometry.right(), geometry.left(), centers, geometry.ridingSeats(), cubes, geometry.equipmentBounds());
		if (attacks == null) return null;
		attacks = attacks.withCarrierParts(geometry.carrierParts());
		var old = base.bodyBounds();
		// Keep the original body's coordinate origin when a new lobe shifts the visible envelope.
		var before = previous.overall();
		double dx = (before.minX() + before.maxX() - overall.minX() - overall.maxX()) / 2;
		double dz = (before.minZ() + before.maxZ() - overall.minZ() - overall.maxZ()) / 2;
		var bounds = BodyBounds.create(old.width(), old.height(), old.depth(),
			old.centerX() + dx, old.minY(), old.centerZ() + dz, old.eyeHeight(), old.legLength(),
			old.groundedLegCount(), old.groundedKneeCount(), old.legVolumeRatio());
		if (bounds == null) return null;
		grown = grown.withBodyGeometry(bounds, hitbox, base.bodyVolume()).withAttackGeometry(attacks);
		if (base.blockAttachmentCarrier() != null) grown = grown.withBlockAttachmentCarrier(base.blockAttachmentCarrier());
		return grown.isReadyForEntity() ? grown : null;
	}

	private record GrowthPlacement(Vec3 center, DevouredGrowthPose pose, Vec3 normal, Vec3 localSlide, List<Vec3> corners) {
		static GrowthPlacement sample(List<Vec3> shell, double width, double height, UUID donor, RandomSource random) {
			Vec3 shellCenter = shell.get(0).lerp(shell.get(7), 0.5);
			double floor = shell.stream().mapToDouble(Vec3::y).min().orElseThrow();
			var local = DevourGrowth.corners(width, -height / 2, height);
			var faces = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
			GrowthPlacement fallback = null;
			double leastLift = Double.POSITIVE_INFINITY;
			while (!faces.isEmpty()) {
				int face = faces.remove(random.nextInt(faces.size()));
				int axis = face / 2;
				int axisBit = 1 << axis;
				int cornerIndex = (face % 2) * axisBit;
				Vec3 corner = shell.get(cornerIndex);
				Vec3 outward = corner.subtract(shell.get(cornerIndex ^ axisBit));
				Vec3 normal = outward.normalize();
				if (normal.y < -1e-7 || normal.lengthSqr() < 0.5) continue;
				Vec3 u = shell.get(cornerIndex ^ (1 << ((axis + 1) % 3))).subtract(corner);
				Vec3 v = shell.get(cornerIndex ^ (1 << ((axis + 2) % 3))).subtract(corner);
				Vec3 surface = corner.add(u.scale(random.nextDouble())).add(v.scale(random.nextDouble()));
				Vec3 highest = corner.add(u.y > 0 ? u : Vec3.ZERO).add(v.y > 0 ? v : Vec3.ZERO);
				double burial = random.nextDouble() * Math.min(outward.length(), Math.min(width, height) / 2);
				var pose = DevouredGrowthPose.create(width, height, normal, donor, new Vec3(0, 0, 1), burial);
				var rotated = local.stream().map(pose.rotation()::rotate).toList();
				Vec3 offset = pose.rotation().rotate(pose.anchor());
				double requiredY = floor - rotated.stream().mapToDouble(Vec3::y).min().orElseThrow() + offset.y;
				if (surface.y < requiredY && highest.y > surface.y) {
					double t = Math.min(1, (requiredY - surface.y) / (highest.y - surface.y));
					surface = surface.lerp(highest, t + random.nextDouble() * (1 - t));
				}
				double lift = Math.max(0, requiredY - surface.y);
				Vec3 center = surface.subtract(offset);
				Vec3 inside = surface.lerp(shellCenter, 0.5);
				Vec3 slide = BionicRiding.inverse(pose.rotation()).rotate(surface.subtract(inside));
				var placement = new GrowthPlacement(center, pose, normal, slide, rotated.stream().map(center::add).toList());
				if (lift <= 1e-8) return placement;
				if (lift < leastLift) { fallback = placement; leastLift = lift; }
			}
			return fallback;
		}
	}

	private static VisualBounds extend(VisualBounds base, List<Vec3> points) {
		double minX = base.minX(), minY = base.minY(), minZ = base.minZ();
		double maxX = base.maxX(), maxY = base.maxY(), maxZ = base.maxZ();
		for (Vec3 p : points) {
			minX = Math.min(minX, p.x);
			minY = Math.min(minY, p.y);
			minZ = Math.min(minZ, p.z);
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
			maxZ = Math.max(maxZ, p.z);
		}
		return VisualBounds.create(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static List<Vec3> corners(double width, double bottom, double height) {
		double r = width / 2, top = bottom + height;
		return List.of(new Vec3(-r, top, -r), new Vec3(r, top, -r), new Vec3(-r, top, r), new Vec3(r, top, r),
			new Vec3(-r, bottom, -r), new Vec3(r, bottom, -r), new Vec3(-r, bottom, r), new Vec3(r, bottom, r));
	}
}

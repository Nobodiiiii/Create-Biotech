package com.nobodiiiii.createbiotech.entity;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.nobodiiiii.createbiotech.CreateBiotech;
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
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Grow one intact mimic organ on the existing body. All previous sources and joints keep their indices. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class DevourGrowth {
	private DevourGrowth() {}

	/** Death loot is reached after totems and cancelled deaths, including kills by inherited attacks. */
	@SubscribeEvent(receiveCanceled = true)
	public static void onDeathDrops(LivingDropsEvent event) {
		if (!(event.getSource().getEntity() instanceof Mob hunter)
			|| !(hunter instanceof DevourerEntity || hunter instanceof SlimeBionicEntity)
			|| !(hunter.level() instanceof ServerLevel level) || !hunter.isAlive()) return;
		var prey = event.getEntity();
		if (DevourAttackGoal.isPrey(hunter, prey, DevourAttackGoal.size(hunter))) absorb(level, hunter, prey);
	}

	private static void absorb(ServerLevel level, Mob hunter, LivingEntity prey) {
		SurgicalAssembly base = hunter instanceof SlimeBionicEntity body ? body.getAssembly() : slimeBody((DevourerEntity) hunter);
		if (base == null || base.attackGeometry() == null) return;
		LivingEntity donor = prey;
		if (prey instanceof Player player) {
			var proxy = CBEntityTypes.PLAYER_MIMIC.get().create(level);
			if (proxy == null) return;
			proxy.setImitatedPlayer(player.getGameProfile());
			donor = proxy;
		}
		var profile = MimicProfile.capture(donor);
		if (profile == null) return;
		var grown = append(base, profile, prey.getBbWidth(), prey.getBbHeight(), level.getGameTime(), hunter.getRandom(),
			hunter instanceof SlimeBionicEntity b ? b.getBionicTraits().members(BionicTrait.DEVOUR) : Set.of(new CombinationMember(0, 1)));
		if (grown == null) return;
		if (hunter instanceof SlimeBionicEntity bionic) {
			bionic.setAssembly(grown);
			return;
		}
		var bionic = CBEntityTypes.SLIME_BIONIC.get().create(level);
		if (bionic == null) return;
		// Preserve vanilla mob state (persistence, effects, name, motion, equipment and custom loot).
		// The proxy needs its own UUID while the slime is still registered with the level.
		var state = hunter.saveWithoutId(new CompoundTag());
		state.remove("UUID");
		state.remove("Passengers");
		bionic.load(state);
		bionic.getPersistentData().putBoolean("WildDevourer", true);
		bionic.setAssembly(grown);
		bionic.setHealth(bionic.getMaxHealth() * hunter.getHealth() / hunter.getMaxHealth());
		SlimeMimicHandler.forceSetSlimeMimic(bionic, true);
		if (level.addFreshEntity(bionic)) hunter.discard();
	}

	private static SurgicalAssembly slimeBody(DevourerEntity slime) {
		float edge = slime.getSize() * 0.5f;
		var profile = MimicProfile.capture(slime);
		var anatomy = BionicAnatomySnapshot.ofParts(Map.of(0, Set.of("devour_inner"), 1, Set.of("devour_outer")), 2)
			.withVolumes(new float[]{(float)Math.pow(edge * 0.75, 3), edge * edge * edge});
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

	private static SurgicalAssembly append(SurgicalAssembly base, MimicProfile prey, float width, float height,
		long gameTime, RandomSource random, Set<CombinationMember> eligible) {
		var geometry = base.attackGeometry();
		var anchor = eligible.stream().filter(member -> geometry.carrierCubes().containsKey(member)).filter(member -> {
			var source = base.sources().get(member.source());
			return source.profile().matches(CBEntityTypes.DEVOURER.getId())
				&& source.anatomy().parts().getOrDefault(member.cube(), Set.of()).contains("devour_outer");
		}).min(Comparator.<CombinationMember>comparingInt(m -> -base.sources().get(m.source()).profile().stableInt("Size"))
			.thenComparingInt(CombinationMember::source).thenComparingInt(CombinationMember::cube)).orElse(null);
		if (anchor == null) return null;

		var shell = base.sources().get(anchor.source());
		double shellCenter = (shell.profile().stableInt("Size") + 1) / 4d;
		Vec3 tableCenter = shell.originOffset().add(shell.layPose().translation())
			.add(shell.layPose().rotate(new Vec3(0, shellCenter, 0))).add(shell.cubeOffsets().getOrDefault(anchor.cube(), Vec3.ZERO));
		Vec3 bodyOrigin = base.layoutLayPose().inverseRotate(tableCenter).subtract(geometry.carrierOrigins().get(anchor));
		var previous = base.hitboxGeometry();
		var shellCorners = geometry.carrierCubes().get(anchor);
		GrowthPlacement growth = GrowthPlacement.sample(volumeCorners(shellCorners), width, height, random);
		prey = prey.asDevouredForm(width, height, gameTime, growth.burial());
		var pose = base.layoutLayPose();
		// Source rotations pivot around the intact prey's center. Its unrotated origin is at its feet.
		Vec3 origin = growth.center().subtract(0, height / 2d, 0);
		Vec3 offset = pose.rotate(bodyOrigin.add(origin)).subtract(pose.translation());
		var rotation = growth.rotation().reframe(SurgicalLayPose.IDENTITY, pose);
		float volume = width * width * height;
		var anatomy = BionicAnatomySnapshot.ofParts(Map.of(0, Set.of("devoured_form")), 1).withVolumes(new float[]{volume});
		if (anatomy == null) return null;
		var source = Source.create(prey, UUID.randomUUID(), 1, BitSet.valueOf(new long[]{1}), anatomy, List.of(),
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
		var old = base.bodyBounds();
		// Keep the original body's coordinate origin when a new lobe shifts the visible envelope.
		var before = previous.overall();
		double dx = (before.minX() + before.maxX() - overall.minX() - overall.maxX()) / 2;
		double dz = (before.minZ() + before.maxZ() - overall.minZ() - overall.maxZ()) / 2;
		var bounds = BodyBounds.create(old.width(), old.height(), old.depth(),
			old.centerX() + dx, old.minY(), old.centerZ() + dz, old.eyeHeight(), old.legLength(),
			old.groundedLegCount(), old.groundedKneeCount(), old.legVolumeRatio());
		if (bounds == null) return null;
		grown = grown.withBodyGeometry(bounds, hitbox, SurgicalVolumeSampler.unionVolume(cubes.values().stream().map(DevourGrowth::volumeCorners).toList())).withAttackGeometry(attacks);
		if (base.blockAttachmentCarrier() != null) grown = grown.withBlockAttachmentCarrier(base.blockAttachmentCarrier());
		return grown.isReadyForEntity() ? grown : null;
	}

	/** Earlier proxy boxes enumerated the perimeter of each face instead of its binary corner indices. */
	private static List<Vec3> volumeCorners(List<Vec3> points) {
		Vec3 a = points.get(1).subtract(points.get(0));
		Vec3 b = points.get(2).subtract(points.get(0));
		Vec3 diagonal = points.get(3).subtract(points.get(0));
		if (b.distanceToSqr(a.add(diagonal)) < 1e-12 && Math.abs(a.dot(diagonal)) < 1e-6)
			return List.of(points.get(0), points.get(1), points.get(3), points.get(2),
				points.get(4), points.get(5), points.get(7), points.get(6));
		return points;
	}

	private record GrowthPlacement(Vec3 center, SurgicalCubeRotation rotation, double burial, List<Vec3> corners) {
		static GrowthPlacement sample(List<Vec3> shell, double width, double height, RandomSource random) {
			Vec3 highest = shell.stream().max(Comparator.comparingDouble(Vec3::y)).orElseThrow();
			double floor = shell.stream().mapToDouble(Vec3::y).min().orElseThrow();
			var faces = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
			while (!faces.isEmpty()) {
				int face = faces.remove(random.nextInt(faces.size()));
				int axis = face / 2;
				// Corner bits select the ends of the three edge vectors of the oriented box.
				int axisBit = 1 << axis;
				int cornerIndex = (face % 2) * axisBit;
				Vec3 corner = shell.get(cornerIndex);
				Vec3 outward = corner.subtract(shell.get(cornerIndex ^ axisBit));
				Vec3 normal = outward.normalize();
				if (normal.y < -1e-7 || normal.lengthSqr() < 0.5) continue;
				Vec3 u = shell.get(cornerIndex ^ (1 << ((axis + 1) % 3))).subtract(corner);
				Vec3 v = shell.get(cornerIndex ^ (1 << ((axis + 2) % 3))).subtract(corner);
				Vec3 surface = corner.add(u.scale(random.nextDouble())).add(v.scale(random.nextDouble()));
				Vec3 highestOnFace = corner.add(u.y > 0 ? u : Vec3.ZERO).add(v.y > 0 ? v : Vec3.ZERO);
				Vec3 cross = new Vec3(0, 1, 0).cross(normal);
				var align = new SurgicalCubeRotation(cross.x, cross.y, cross.z, 1 + normal.y);
				var rotation = SurgicalCubeRotation.around(Direction.UP, random.nextDouble() * 360).then(align);
				double burial = random.nextDouble() * Math.min(height / 2, outward.length());
				// Move the attachment up its own face if the rotated prey would extend below the shell.
				GrowthPlacement placement = at(surface, normal, rotation, burial, width, height);
				double lift = floor - placement.corners.stream().mapToDouble(Vec3::y).min().orElseThrow();
				if (lift <= 0) return placement;
				double room = highestOnFace.y - surface.y;
				if (room < lift) continue;
				surface = surface.lerp(highestOnFace, (lift + random.nextDouble() * (room - lift)) / room);
				return at(surface, normal, rotation, burial, width, height);
			}
			// A very wide donor on a sharply tilted shell can overhang every eligible face.
			return at(highest, new Vec3(0, 1, 0), SurgicalCubeRotation.around(Direction.UP, random.nextDouble() * 360),
				0, width, height);
		}

		private static GrowthPlacement at(Vec3 surface, Vec3 normal, SurgicalCubeRotation rotation,
			double burial, double width, double height) {
			Vec3 center = surface.add(normal.scale(height / 2 - burial));
			var points = DevourGrowth.corners(width, -height / 2, height).stream()
				.map(point -> center.add(rotation.rotate(point))).toList();
			return new GrowthPlacement(center, rotation, burial, points);
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

package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalPackingAlignment;
import com.nobodiiiii.createbiotech.entity.BionicRiding;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Six-face support and fixed velocity, following ref/1.21.1/Minecraft/.../monster/Shulker.java.
 * The ordinary bionic collision contract is retained: this entity never becomes a solid block.
 */
public final class BionicBlockAttachment {
	private static final Vec3 DOWN = new Vec3(0, -1, 0);
	private static final Comparator<SurgicalAssembly.CombinationMember> ORDER = Comparator
		.comparingInt(SurgicalAssembly.CombinationMember::source)
		.thenComparingInt(SurgicalAssembly.CombinationMember::cube);
	private final SlimeBionicEntity entity;
	@Nullable private SurgicalAssembly cachedAssembly;
	@Nullable private BionicTraitSet cachedTraits;
	@Nullable private Anchor anchor;
	private final Map<SurgicalAssembly.VisualBounds, AABB> rotatedBounds = new HashMap<>();
	@Nullable private Direction boundsFace;

	public BionicBlockAttachment(SlimeBionicEntity entity) { this.entity = entity; }

	public boolean locksMovement() {
		return !entity.isPassenger() && entity.getBionicTraits().has(BionicTrait.BLOCK_ATTACHMENT);
	}

	@Nullable public SurgicalAssembly.CombinationMember carrier() {
		Anchor selected = anchor();
		return selected == null ? null : selected.member();
	}

	/** A horizontal eye height cannot describe a head in a wall-mounted or tilted source. */
	public Vec3 eyePoint() {
		anchor();
		if (cachedAssembly != null && cachedAssembly.attackGeometry() != null) {
			var origins = cachedAssembly.attackGeometry().carrierOrigins();
			var head = cachedTraits.members(BionicTrait.INTELLIGENCE).stream()
				.filter(origins::containsKey).min(ORDER).orElse(null);
			if (head != null) return origins.get(head);
		}
		return new Vec3(0, entity.getEyeHeight(), 0);
	}

	@Nullable private Anchor anchor() {
		SurgicalAssembly assembly = entity.getAssembly();
		BionicTraitSet traits = entity.getBionicTraits();
		if (assembly == cachedAssembly && traits == cachedTraits) return anchor;
		cachedAssembly = assembly;
		cachedTraits = traits;
		anchor = null;
		rotatedBounds.clear();
		if (assembly == null || assembly.attackGeometry() == null) return null;
		var cubes = assembly.attackGeometry().carrierCubes();
		// Compare the upright assembly before any attachment rotation, with stable ties.
		var member = assembly.blockAttachmentCarrier();
		if (member == null || !traits.members(BionicTrait.BLOCK_ATTACHMENT).contains(member)
			|| !cubes.containsKey(member)) member = lowestCarrier(traits, cubes);
		if (member == null) return null;
		anchor = anchor(member, cubes.get(member));
		return anchor;
	}

	@Nullable private static SurgicalAssembly.CombinationMember lowestCarrier(BionicTraitSet traits,
		Map<SurgicalAssembly.CombinationMember, List<Vec3>> cubes) {
		return traits.members(BionicTrait.BLOCK_ATTACHMENT).stream()
			.filter(cubes::containsKey).min(Comparator
				.comparingDouble((SurgicalAssembly.CombinationMember key) -> cubes.get(key).stream()
					.mapToDouble(point -> point.y).min().orElse(Double.POSITIVE_INFINITY))
				.thenComparing(ORDER)).orElse(null);
	}

	/** Same base and rest frame as runtime attachment, resolved before physical data is baked. */
	@Nullable public static SurgicalPackingAlignment packingAlignment(BionicTraitSet traits,
		Map<SurgicalAssembly.CombinationMember, List<Vec3>> cubes) {
		var member = lowestCarrier(traits, cubes);
		if (member == null) return null;
		Anchor selected = anchor(member, cubes.get(member));
		if (selected == null) return null;
		return new SurgicalPackingAlignment(member, rotation(selected, Direction.DOWN));
	}

	/**
	 * ModelPart.Cube emits the model-Y-min face first; SurgicalCapturedRenderPlan.recoverSolid
	 * preserves its two ordered edges and the edge to model-Y-max. Thus corners 4..7 are the
	 * Shulker base's bottom face even after native wall attachment, glue, lay poses and cube tilts.
	 * See ref/1.21.1/Minecraft/net/minecraft/client/model/geom/ModelPart.java and model/ShulkerModel.java.
	 */
	private static Anchor anchor(SurgicalAssembly.CombinationMember member, List<Vec3> corners) {
		Vec3 x = corners.get(1).subtract(corners.getFirst()).normalize();
		Vec3 up = corners.getFirst().subtract(corners.get(4)).normalize();
		Vec3 z = x.cross(up).normalize();
		up = z.cross(x).normalize();
		if (x.lengthSqr() < 0.5 || up.lengthSqr() < 0.5 || z.lengthSqr() < 0.5) return null;
		Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(
			(float) x.x, (float) x.y, (float) x.z, (float) up.x, (float) up.y, (float) up.z,
			(float) z.x, (float) z.y, (float) z.z));
		Vec3 bottom = Vec3.ZERO;
		for (int corner = 4; corner < 8; corner++) bottom = bottom.add(corners.get(corner));
		return new Anchor(member, bottom.scale(0.25),
			new SurgicalCubeRotation(rotation.x, rotation.y, rotation.z, rotation.w));
	}

	public SurgicalCubeRotation rotation() {
		Anchor selected = anchor();
		if (!locksMovement() || selected == null || entity.getBlockAttachmentCell() == null)
			return SurgicalCubeRotation.IDENTITY;
		return rotation(selected, entity.getBlockAttachmentFace());
	}

	private static SurgicalCubeRotation rotation(Anchor anchor, Direction face) {
		var target = face.getOpposite().getRotation();
		return BionicRiding.inverse(anchor.rest()).then(
			new SurgicalCubeRotation(target.x, target.y, target.z, target.w));
	}

	public void tick() {
		if (entity.level().isClientSide) {
			entity.lockBlockAttachmentRotation();
			return;
		}
		Anchor selected = anchor();
		if (!locksMovement() || selected == null) {
			entity.setBlockAttachment(null, Direction.DOWN);
			return;
		}
		BlockPos cell = entity.getBlockAttachmentCell();
		// Explicit teleports may move the creature; ordinary movement is suppressed separately.
		// Compare even on the first tick: releasing a paper box relocates a saved entity.
		if (cell != null && entity.position().distanceToSqr(position(cell, entity.getBlockAttachmentFace(),
			selected, rotation(selected, entity.getBlockAttachmentFace()))) > 1.0e-6)
			cell = null;
		if (cell == null) {
			Vec3 down = selected.rest().rotate(DOWN).yRot(-entity.yBodyRot * (float) Math.PI / 180);
			Vec3 point = entity.position().add(selected.point().yRot(-entity.yBodyRot * (float) Math.PI / 180));
			cell = BlockPos.containing(point.subtract(down.scale(1.0e-4)));
		}
		Direction face = findFace(cell, entity.getBlockAttachmentFace(), selected);
		if (face != null) attach(cell, face, selected);
		else if (!findNewAttachment(cell, selected)) {
			// Like Shulker.tick, retry on the next tick if all nearby attempts fail.
			if (entity.getBlockAttachmentCell() == null || !entity.getBlockAttachmentCell().equals(cell))
				entity.setBlockAttachment(null, Direction.DOWN);
		}
		entity.setDeltaMovement(Vec3.ZERO);
		entity.getNavigation().stop();
		entity.resetFallDistance();
		entity.lockBlockAttachmentRotation();
	}

	/** Shulker.findNewAttachment / teleportSomewhere: try another local face first (above),
	 * then five nearby cells within eight blocks. Body/block overlap remains allowed. */
	private boolean findNewAttachment(BlockPos origin, Anchor selected) {
		if (entity.isNoAi() || !entity.isAlive()) return false;
		var level = entity.level();
		var random = entity.getRandom();
		for (int attempt = 0; attempt < 5; attempt++) {
			BlockPos cell = origin.offset(Mth.randomBetweenInclusive(random, -8, 8),
				Mth.randomBetweenInclusive(random, -8, 8), Mth.randomBetweenInclusive(random, -8, 8));
			if (!isTeleportCell(cell)) continue;
			Direction face = findFace(cell, Direction.DOWN, selected);
			if (face == null) continue;
			var event = EventHooks.onEnderTeleport(entity, cell.getX(), cell.getY(), cell.getZ());
			if (event.isCanceled() || !Double.isFinite(event.getTargetX())
				|| !Double.isFinite(event.getTargetY()) || !Double.isFinite(event.getTargetZ())) continue;
			cell = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
			// An event may move the destination; validate the actual support and orientation again.
			if (!isTeleportCell(cell)) continue;
			face = findFace(cell, face, selected);
			if (face == null) continue;
			Vec3 previous = entity.position();
			entity.unRide();
			attach(cell, face, selected);
			entity.hasImpulse = true;
			entity.setTarget(null);
			entity.playSound(SoundEvents.SHULKER_TELEPORT, 1, 1);
			level.gameEvent(GameEvent.TELEPORT, previous, GameEvent.Context.of(entity));
			return true;
		}
		return false;
	}

	private boolean isTeleportCell(BlockPos cell) {
		var level = entity.level();
		// Match the shulker's empty destination cell without rejecting the rotated body's overlap.
		return level.isInWorldBounds(cell) && level.hasChunk(cell.getX() >> 4, cell.getZ() >> 4)
			&& level.isEmptyBlock(cell);
	}

	@Nullable private Direction findFace(BlockPos cell, Direction preferred, Anchor selected) {
		if (canStay(cell, preferred, selected)) return preferred;
		for (Direction face : Direction.values())
			if (face != preferred && canStay(cell, face, selected)) return face;
		return null;
	}

	private boolean canStay(BlockPos cell, Direction face, Anchor selected) {
		var level = entity.level();
		BlockPos support = cell.relative(face);
		if (!level.isInWorldBounds(cell) || !level.hasChunk(support.getX() >> 4, support.getZ() >> 4)
			|| !level.loadedAndEntityCanStandOnFace(support, entity, face.getOpposite())) return false;
		SurgicalCubeRotation rotation = rotation(selected, face);
		Vec3 origin = position(cell, face, selected, rotation);
		// A surgical body may extend behind its base or into neighbouring blocks. Embedding
		// must not veto the attachment; LivingEntity still applies ordinary suffocation damage.
		for (var corners : cachedAssembly.attackGeometry().carrierCubes().values()) {
			AABB bounds = bounds(corners, rotation).move(origin).deflate(1.0e-5);
			if (!level.getWorldBorder().isWithinBounds(bounds)) return false;
		}
		return true;
	}

	/** Rotate measured cuboid vertices before taking their envelope, rather than rotating empty
	 * corners of an already enlarged upright AABB. Shared by physical, multipart and culling boxes. */
	@Nullable public AABB bounds(SurgicalAssembly.VisualBounds region) {
		Anchor selected = anchor();
		if (selected == null || entity.getBlockAttachmentCell() == null) return null;
		Direction face = entity.getBlockAttachmentFace();
		if (boundsFace != face) {
			rotatedBounds.clear();
			boundsFace = face;
		}
		return rotatedBounds.computeIfAbsent(region, key -> bounds(key,
			cachedAssembly.attackGeometry().carrierCubes().values(), rotation(selected, face)));
	}

	@Nullable private static AABB bounds(SurgicalAssembly.VisualBounds region,
		Collection<List<Vec3>> cubes, SurgicalCubeRotation rotation) {
		AABB result = null;
		for (var corners : cubes) {
			// Body and primary-limb envelopes contain their complete cuboids. Float-rounded
			// persisted envelopes need the same small tolerance as VisualBounds.contains.
			if (corners.stream().anyMatch(point -> point.x < region.minX() - 1.0e-3
				|| point.x > region.maxX() + 1.0e-3 || point.y < region.minY() - 1.0e-3
				|| point.y > region.maxY() + 1.0e-3 || point.z < region.minZ() - 1.0e-3
				|| point.z > region.maxZ() + 1.0e-3)) continue;
			AABB cube = bounds(corners, rotation);
			result = result == null ? cube : result.minmax(cube);
		}
		return result;
	}

	private static AABB bounds(List<Vec3> corners, SurgicalCubeRotation rotation) {
		Vec3 first = rotation.rotate(corners.getFirst());
		double minX = first.x, minY = first.y, minZ = first.z;
		double maxX = minX, maxY = minY, maxZ = minZ;
		for (Vec3 corner : corners) {
			Vec3 point = rotation.rotate(corner);
			minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
			maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static Vec3 position(BlockPos cell, Direction face, Anchor anchor, SurgicalCubeRotation rotation) {
		Vec3 contact = Vec3.atCenterOf(cell).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
		return contact.subtract(rotation.rotate(anchor.point()));
	}

	private void attach(BlockPos cell, Direction face, Anchor selected) {
		entity.setBlockAttachment(cell, face);
		Vec3 pinned = position(cell, face, selected, rotation(selected, face));
		entity.setPos(pinned.x, pinned.y, pinned.z);
		entity.lockBlockAttachmentRotation();
	}

	/** An ender teleport must land on a real six-face support rather than an upright ground point. */
	public boolean teleportTo(double x, double y, double z) {
		Anchor selected = anchor();
		if (selected == null) return false;
		BlockPos cell = BlockPos.containing(x, y, z);
		Direction face = findFace(cell, Direction.DOWN, selected);
		if (face == null) return false;
		attach(cell, face, selected);
		entity.getNavigation().stop();
		entity.setDeltaMovement(Vec3.ZERO);
		entity.hasImpulse = true;
		return true;
	}

	public void save(CompoundTag tag) {
		BlockPos cell = entity.getBlockAttachmentCell();
		if (cell == null) return;
		tag.putLong("BionicAttachCell", cell.asLong());
		tag.putByte("BionicAttachFace", (byte) entity.getBlockAttachmentFace().get3DDataValue());
	}

	public void load(CompoundTag tag) {
		entity.setBlockAttachment(tag.contains("BionicAttachCell", Tag.TAG_LONG)
			? BlockPos.of(tag.getLong("BionicAttachCell")) : null,
			Direction.from3DDataValue(tag.getByte("BionicAttachFace")));
	}

	private record Anchor(SurgicalAssembly.CombinationMember member, Vec3 point, SurgicalCubeRotation rest) {}
}

package com.nobodiiiii.createbiotech.content.surgery;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** SurgicalPackingOperations algorithms over the canonical SurgicalTableBlockEntity state. */
final class SurgicalPackingOperations {
	private SurgicalPackingOperations() {}


	static boolean packComponent(SurgicalTableBlockEntity operationOwner, Player player, ItemStack boxes, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		double bodyVolume,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || !CapturedEntityBoxHelper.isEmptyLargeBox(boxes)
			|| CapturedEntityBoxItem.hasCapturedEntity(boxes) || bodyBounds == null
			|| !SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry))
			return false;

		SurgicalTableBlockEntity.ComponentGroup group = SurgicalTableConnections.connectedGroup(operationOwner, subject, cubeId);
		BitSet component = group.components().get(subject.persistentId());
		if (component == null || component.isEmpty() || operationOwner.getLevel() == null)
			return false;
		var anchor = SurgicalTableConnections.packingAnchor(operationOwner, group);
		SurgicalAssembly assembly = SurgicalTableConnections.packedAssembly(operationOwner, group, anchor);
		SlimeBionicEntity bionic = assembly == null ? null : SurgicalPackingOperations.measuredBionic(operationOwner, assembly, bodyBounds, bodyVolume,
			hitboxGeometry, attackGeometry, packingAlignment);
		if (bionic == null || !CapturedEntityBoxHelper.captureEntityFromPlayerStack(boxes, player, bionic))
			return false;

		SurgicalPlacementOperations.removeGroup(operationOwner, group);
		operationOwner.setChangedAndSync();
		operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 0.85f);
		return true;
	}

	/** Captures a placement snapshot without removing the source component from its table. */
	static boolean captureTemporaryComponent(SurgicalTableBlockEntity operationOwner, Player player, ItemStack kit, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		double bodyVolume,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || !SurgicalKitItem.isEmptyTemporaryBox(kit)
			|| bodyBounds == null || operationOwner.getLevel() == null
			|| !SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry))
			return false;

		SurgicalTableBlockEntity.ComponentGroup group = SurgicalTableConnections.connectedGroup(operationOwner, subject, cubeId);
		BitSet component = group.components().get(subject.persistentId());
		if (component == null || component.isEmpty())
			return false;
		var anchor = SurgicalTableConnections.packingAnchor(operationOwner, group);
		SurgicalAssembly sourceAssembly = SurgicalTableConnections.packedAssembly(operationOwner, group, anchor);
		if (sourceAssembly == null)
			return false;
		SlimeBionicEntity bionic = SurgicalPackingOperations.measuredBionic(operationOwner, sourceAssembly, bodyBounds, bodyVolume, hitboxGeometry,
			attackGeometry, packingAlignment);
		if (bionic == null)
			return false;
		Map<UUID, CompoundTag> sourceSubjects = new HashMap<>();
		for (UUID subjectKey : group.components().keySet()) {
			SurgicalSubject sourceSubject = operationOwner.getSubjectByPersistentId(subjectKey);
			if (sourceSubject == null)
				return false;
			sourceSubjects.put(subjectKey, sourceSubject.save());
		}
		if (!CapturedEntityBoxHelper.captureEntity(kit, bionic))
			return false;
		SurgicalKitItem.setTemporaryMove(kit, operationOwner.getLevel().dimension().location(), operationOwner.getBlockPos(),
			anchor.persistentId(), group.components().get(anchor.persistentId()).nextSetBit(0),
			group.components(), sourceSubjects, sourceAssembly.save());
		operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 0.85f);
		return true;
	}

	/** Stores the connected component as an independent snapshot without changing its source. */
	static boolean captureCopiedComponent(SurgicalTableBlockEntity operationOwner, Player player, ItemStack kit, int subjectId, int cubeId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds, double bodyVolume,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		if (operationOwner.getLevel() == null || operationOwner.getLevel().isClientSide || !SurgicalKitItem.isEmptyCubeCopier(kit)
			|| bodyBounds == null || !SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry))
			return false;
		SurgicalAssembly assembly = SurgicalGlueOperations.previewPackedAssembly(operationOwner, subjectId, cubeId, observedCubeCount, observedSeams);
		SlimeBionicEntity bionic = assembly == null ? null : SurgicalPackingOperations.measuredBionic(operationOwner, assembly, bodyBounds, bodyVolume,
			hitboxGeometry, attackGeometry, packingAlignment);
		if (bionic == null || !CapturedEntityBoxHelper.captureEntity(kit, bionic))
			return false;
		player.displayClientMessage(Component.translatable("message.create_biotech.surgical_table.cube_copied"), true);
		operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 0.85f);
		return true;
	}

	/**
	 * A boxed-body entity carrying {@code assembly} with the client's measurements applied, or null
	 * when those measurements do not describe it. Combat geometry is the one optional part: stale or
	 * invalid values must not make an otherwise valid body unpackable, so it is kept only while it
	 * still describes exactly the effective shoulder joints.
	 */
	static @Nullable
	SlimeBionicEntity measuredBionic(SurgicalTableBlockEntity operationOwner, SurgicalAssembly assembly,
		SurgicalAssembly.BodyBounds bodyBounds, double bodyVolume,
		SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		assembly = measuredAssembly(operationOwner.getLevel(), assembly, bodyBounds, bodyVolume, hitboxGeometry,
			attackGeometry, packingAlignment);
		if (assembly == null) return null;
		SlimeBionicEntity bionic = CBEntityTypes.SLIME_BIONIC.get().create(operationOwner.getLevel());
		if (bionic != null)
			bionic.setAssembly(assembly);
		return bionic;
	}

	@Nullable
	static SurgicalAssembly measuredAssembly(Level level, SurgicalAssembly assembly,
		SurgicalAssembly.BodyBounds bodyBounds, double bodyVolume, SurgicalAssembly.HitboxGeometry hitboxGeometry,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry, @Nullable SurgicalPackingAlignment packingAlignment) {
		var measured = prepareMeasuredAssembly(assembly, bodyBounds, bodyVolume, hitboxGeometry, attackGeometry, packingAlignment);
		return measured == null ? null : measured.authorized(level);
	}

	record MeasuredBody(SurgicalAssembly installed, @Nullable SurgicalAssembly beforeAlignment) {
		boolean acceptsAlignment(BionicTraitSet traits) {
			return traits.members(BionicTrait.BLOCK_ATTACHMENT).contains(installed.blockAttachmentCarrier());
		}

		@Nullable SurgicalAssembly authorized(Level level) {
			if (beforeAlignment != null && !acceptsAlignment(BionicTraitResolver.resolve(beforeAlignment, level))) return null;
			return installed;
		}
	}

	/** Pure geometry preparation. Only the final carrier authorization reads the live trait catalog and level. */
	@Nullable
	static MeasuredBody prepareMeasuredAssembly(SurgicalAssembly assembly, SurgicalAssembly.BodyBounds bodyBounds,
		double bodyVolume, SurgicalAssembly.HitboxGeometry hitboxGeometry, @Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		if (bodyBounds == null || !SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry)) return null;
		if (!SurgicalPackingOperations.validHitboxGeometry(assembly, bodyBounds, hitboxGeometry)
			|| !SurgicalPackingOperations.validMobilityMeasurements(assembly, bodyBounds))
			return null;
		SurgicalAssembly beforeAlignment = null;
		boolean validArms = attackGeometry != null && attackGeometry.armCount() == limbCount(assembly, SurgicalLimbType.SHOULDER);
		if (packingAlignment != null) {
			if (!validArms) return null;
			beforeAlignment = assembly.withAttackGeometry(attackGeometry);
			assembly = packingAlignment.applyToPacked(beforeAlignment, attackGeometry);
			if (assembly == null) return null;
		}
		assembly = assembly.withBodyGeometry(bodyBounds, hitboxGeometry, bodyVolume);
		if (validArms) assembly = assembly.withAttackGeometry(attackGeometry);
		return new MeasuredBody(assembly, beforeAlignment);
	}

	static int limbCount(SurgicalAssembly assembly, SurgicalLimbType type) {
		int count = 0;
		for (SurgicalAssembly.Limb limb : assembly.effectiveLimbs())
			if (limb.type() == type)
				count++;
		return count;
	}

	static boolean validHitboxGeometry(SurgicalAssembly assembly,
		SurgicalAssembly.BodyBounds bodyBounds, SurgicalAssembly.HitboxGeometry hitboxGeometry) {
		long primaryLimbs = assembly.effectiveLimbs().stream()
			.filter(limb -> limb.type().primary()).count();
		if (primaryLimbs != hitboxGeometry.limbs().size())
			return false;
		SurgicalAssembly.VisualBounds collision = SurgicalAssembly.VisualBounds.create(
			-bodyBounds.width() * 0.5d, bodyBounds.minY(), -bodyBounds.depth() * 0.5d,
			bodyBounds.width() * 0.5d, bodyBounds.minY() + bodyBounds.height(),
			bodyBounds.depth() * 0.5d);
		return collision != null && hitboxGeometry.overall().contains(collision);
	}

	static boolean validMobilityMeasurements(SurgicalAssembly assembly,
		SurgicalAssembly.BodyBounds bodyBounds) {
		return bodyBounds.groundedLegCount() <= SurgicalPackingOperations.limbCount(assembly, SurgicalLimbType.HIP)
			&& bodyBounds.groundedKneeCount() <= SurgicalPackingOperations.limbCount(assembly, SurgicalLimbType.KNEE);
	}

	/** Assembly and membership from one connectivity traversal, for indexing a whole component's preview. */
	static @Nullable
	SurgicalTableBlockEntity.PackedAssemblyPreview previewPackedComponent(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalTableBlockEntity.ComponentGroup group = SurgicalPackingOperations.previewPackedGroup(operationOwner, subjectId, cubeId, observedCubeCount, observedSeams);
		if (group == null)
			return null;
		Map<Integer, BitSet> components = operationOwner.componentsBySubjectId(group);
		var anchor = SurgicalTableConnections.packingAnchor(operationOwner, group);
		SurgicalAssembly assembly = SurgicalTableConnections.packedAssembly(operationOwner, group, anchor);
		return assembly == null ? null : new SurgicalTableBlockEntity.PackedAssemblyPreview(assembly, components);
	}

	static @Nullable
	SurgicalTableBlockEntity.ComponentGroup previewPackedGroup(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId))
			return null;
		SurgicalTableBlockEntity.ComponentGroup group = SurgicalTableConnections.connectedGroup(operationOwner, subject, cubeId);
		BitSet component = group.components().get(subject.persistentId());
		return component == null || component.isEmpty() ? null : group;
	}
}

package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueTransform;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableFailure;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableCubeRotationPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalWrenchRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalWrenchController implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalWrenchController {
	private SurgicalWrenchController() {}

	static WrenchEditor wrenchEditor;

	/** Both editors use the same face axis, step sizes, and modifier interpretation. */
	static SurgicalGlueTransform scrollAdjustment(Vec3 axis, double scroll) {
		Vec3 direction = axis.scale(Math.copySign(1.0d, scroll));
		return Screen.hasControlDown()
			? new SurgicalGlueTransform(Vec3.ZERO, SurgicalCubeRotation.around(direction, SurgicalTableClientHandler.GLUE_EDIT_ROTATION_STEP))
			: new SurgicalGlueTransform(direction.scale(SurgicalTableClientHandler.GLUE_EDIT_TRANSLATION_STEP), SurgicalCubeRotation.IDENTITY);
	}

	static void adjustWrenchPreview(ClientLevel level, WrenchEditor editor,
		SurgicalGlueTransform adjustment, boolean straighten) {
		SurgicalWrenchRotation.Cube anchor = editor.preview.get(editor.subjectId).body.cube(editor.cubeId);
		if (straighten)
			adjustment = new SurgicalGlueTransform(Vec3.ZERO,
				SurgicalWrenchRotation.delta(anchor, Vec3.ZERO, 0.0d, true));
		Vec3 selectedTranslation = editor.selectedTranslation.add(adjustment.translation());
		Vec3 groupTranslation = editor.groupTranslation;
		if (!editor.single || straighten)
			groupTranslation = adjustment.apply(groupTranslation, editor.selectedTranslation);
		if (!SurgicalGlueTransform.withinEditRange(selectedTranslation)
			|| !SurgicalGlueTransform.withinEditRange(groupTranslation)) {
			SurgicalTableFailure.EDIT_LIMIT.display(Minecraft.getInstance().player);
			return;
		}
		if (!editor.single || straighten)
			editor.groupRotation = editor.groupRotation.then(adjustment.rotation());
		editor.selectedRotation = editor.selectedRotation.then(adjustment.rotation());
		editor.groupTranslation = groupTranslation;
		editor.selectedTranslation = selectedTranslation;
		editor.rebuildPreview(SurgicalWrenchRotation.preview(editor.original, editor.group,
			editor.subjectId, editor.cubeId, editor.groupRotation, editor.selectedRotation,
			SurgicalTablePlane.surfaceY(editor.pos.getY()), groupTranslation, selectedTranslation));
		AllSoundEvents.SCROLL_VALUE.playAt(level, editor.pos, 0.35f,
			adjustment.rotation().isIdentity() ? 1.0f : 0.85f, false);
	}

	/** Shift-use is a complete action; only keep its preview while awaiting the server reply. */
	static void alignAndConfirmWrench(LocalPlayer player, ClientLevel level, WrenchEditor editor) {
		editor.immediate = true;
		adjustWrenchPreview(level, editor, SurgicalGlueTransform.IDENTITY, true);
		confirmWrench(player, level, editor);
		if (!editor.committing)
			SurgicalSelectionResolver.clearSelections();
	}

	static void confirmWrench(LocalPlayer player, ClientLevel level, WrenchEditor editor) {
		if (!(level.getBlockEntity(editor.pos) instanceof SurgicalTableBlockEntity table)
			|| table.clientDataRevision() != editor.tableRevision) {
			SurgicalTableFailure.PREVIEW_CHANGED.display(player);
			SurgicalSelectionResolver.clearSelections();
			return;
		}
		if (editor.groupRotation.isIdentity() && editor.selectedRotation.isIdentity()
			&& editor.groupTranslation.lengthSqr() < 1.0e-20d && editor.selectedTranslation.lengthSqr() < 1.0e-20d) {
			SurgicalSelectionResolver.clearSelections();
			return;
		}
		SurgicalWrenchRotation.Plan plan = table.planWrenchRotation(editor.subjectKey, editor.cubeId,
			editor.groupRotation, editor.selectedRotation, editor.group, editor.original, SurgicalSelectionResolver.clientPlane(level, editor.pos),
			editor.groupTranslation, editor.selectedTranslation);
		if (!plan.valid()) {
			player.displayClientMessage(Component.translatable(plan.failure().messageKey()), true);
			return;
		}
		editor.committing = true;
		editor.sentTick = level.getGameTime();
		CBPackets.sendToServer(new SurgicalTableCubeRotationPacket(editor.pos, editor.requestId, editor.hand, editor.subjectKey,
			editor.cubeId, editor.groupRotation, editor.selectedRotation, editor.group, editor.original,
			editor.groupTranslation, editor.selectedTranslation));
	}

	static void acceptWrenchCommit(SurgicalTableCubeRotationPacket.Result result) {
		if (wrenchEditor == null || !wrenchEditor.requestId.equals(result.requestId()))
			return;
		if (result.accepted() || wrenchEditor.immediate)
			SurgicalSelectionResolver.clearSelections();
		else
			wrenchEditor.committing = false;
	}

	static boolean refreshWrenchEditor(LocalPlayer player, ClientLevel level) {
		WrenchEditor editor = wrenchEditor;
		if (editor == null)
			return false;
		SurgicalTableBlockEntity table = level.getBlockEntity(editor.pos)
			instanceof SurgicalTableBlockEntity found ? found : null;
		if (!SurgicalKitItem.isWrench(player.getItemInHand(editor.hand)) || table == null
			|| table.clientDataRevision() != editor.tableRevision
			|| player.getEyePosition().distanceTo(editor.originalCenter) > player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d
			|| editor.committing && level.getGameTime() - editor.sentTick > 40) {
			SurgicalSelectionResolver.clearSelections();
			return false;
		}
		if (editor.immediate) {
			SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
			return true;
		}
		SurgicalSelectionResolver.cubeSelection = editor.selection;
		SurgicalInteractionPresentation.highlightSelection(SurgicalSelectionResolver.cubeSelection);
		int face = wrenchPreviewFace(player, level, editor);
		if (face < 0) {
			SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
			return true;
		}
		boolean rotating = Screen.hasControlDown();
		if (editor.guideFace != face || editor.guideRotating != rotating) {
			SurgicalModelRenderContext.CubeGeometry cube = editor.selectedCube();
			Vec3 axis = SurgicalGluePreviewPlanner.cubeFaceAxis(cube, face);
			Vec3 center = SurgicalGluePreviewPlanner.cubeCenter(cube);
			Vec3 faceCenter = SurgicalGluePreviewPlanner.cubeFaceCenter(cube, face);
			editor.guideEdges = axis == null || faceCenter == null ? List.of()
				: SurgicalGluePreviewPlanner.cubeEditGuideEdges(axis, center, faceCenter, SurgicalGluePreviewPlanner.glueEditGuideRadius(cube, center, axis), rotating);
			editor.guideFace = face;
			editor.guideRotating = rotating;
		}
		SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.show(editor.guideEdges, SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
		return true;
	}

	/** Pick only the cached selected preview cube, rather than scanning all table subjects per frame. */
	static int wrenchPreviewFace(LocalPlayer player, ClientLevel level, WrenchEditor editor) {
		SurgicalSelectionResolver.Ray ray = SurgicalSelectionResolver.playerRay(player);
		SurgicalModelRenderContext.CubeGeometry cube = editor.selectedCube();
		int bestFace = -1;
		Vec3 bestHit = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (int face = 0; face < SurgicalClientTopology.CUBE_FACES.length; face++) {
			Vec3 hit = SurgicalSelectionResolver.intersectQuad(ray.start(), ray.end(), cube, SurgicalClientTopology.CUBE_FACES[face]);
			if (hit != null && ray.start().distanceToSqr(hit) < bestDistance) {
				bestFace = face;
				bestHit = hit;
				bestDistance = ray.start().distanceToSqr(hit);
			}
		}
		return bestHit == null || SurgicalSelectionResolver.isOccluded(level, player, ray.start(), bestHit, editor.pos) ? -1 : bestFace;
	}

	@Nullable
	static WrenchSubjectPreview wrenchPreviewFor(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		WrenchEditor editor = wrenchEditor;
		if (editor == null || !editor.pos.equals(table.getBlockPos()) || table.clientDataRevision() != editor.tableRevision)
			return null;
		WrenchSubjectPreview preview = editor.preview.get(subject.id());
		return preview != null && preview.body.key().equals(subject.persistentId()) ? preview : null;
	}

	static final class WrenchSubjectPreview {
		private final SurgicalWrenchRotation.Body body;
		final Map<Integer, Vec3> offsets;
		final Map<Integer, SurgicalCubeRotation> rotations;
		private final Map<Integer, SurgicalModelRenderContext.CubeGeometry> cubes;
		final AABB bounds;

		private WrenchSubjectPreview(SurgicalWrenchRotation.Body body) {
			this.body = body;
			Map<Integer, Vec3> offsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> cubes = new HashMap<>();
			AABB bounds = null;
			for (SurgicalWrenchRotation.Cube cube : body.cubes()) {
				offsets.put(cube.id(), cube.offset());
				rotations.put(cube.id(), cube.rotation());
				SurgicalModelRenderContext.CubeGeometry geometry = new SurgicalModelRenderContext.CubeGeometry(cube.id(), cube.corners());
				cubes.put(cube.id(), geometry);
				bounds = bounds == null ? SurgicalGluePreviewPlanner.cubeBounds(geometry) : bounds.minmax(SurgicalGluePreviewPlanner.cubeBounds(geometry));
			}
			this.offsets = Map.copyOf(offsets);
			this.rotations = Map.copyOf(rotations);
			this.cubes = Map.copyOf(cubes);
			this.bounds = bounds;
		}
	}

	static final class WrenchEditor {
		private final UUID requestId = UUID.randomUUID();
		private final BlockPos pos;
		private final UUID subjectKey;
		private final int subjectId;
		private final int cubeId;
		final InteractionHand hand;
		final boolean single;
		private final int tableRevision;
		private final Map<Integer, BitSet> group;
		private final List<SurgicalWrenchRotation.Body> original;
		private final Vec3 originalCenter;
		private Map<Integer, WrenchSubjectPreview> preview;
		private SurgicalCubeRotation groupRotation = SurgicalCubeRotation.IDENTITY;
		private SurgicalCubeRotation selectedRotation = SurgicalCubeRotation.IDENTITY;
		private Vec3 groupTranslation = Vec3.ZERO;
		private Vec3 selectedTranslation = Vec3.ZERO;
		private SurgicalSelectionResolver.Selection selection;
		private int guideFace = -1;
		private boolean guideRotating;
		private List<SurgicalClientTopology.Edge> guideEdges = List.of();
		boolean committing;
		private boolean immediate;
		private long sentTick;

		WrenchEditor(BlockPos pos, UUID subjectKey, int subjectId, int cubeId, InteractionHand hand,
			boolean single, int tableRevision, Map<Integer, BitSet> group, List<SurgicalWrenchRotation.Body> original) {
			this.pos = pos;
			this.subjectKey = subjectKey;
			this.subjectId = subjectId;
			this.cubeId = cubeId;
			this.hand = hand;
			this.single = single;
			this.tableRevision = tableRevision;
			this.group = group;
			this.original = original;
			rebuildPreview(original);
			this.originalCenter = SurgicalGluePreviewPlanner.cubeCenter(selectedCube());
		}

		SurgicalModelRenderContext.CubeGeometry selectedCube() {
			return preview.get(subjectId).cubes.get(cubeId);
		}

		private void rebuildPreview(List<SurgicalWrenchRotation.Body> bodies) {
			Map<Integer, WrenchSubjectPreview> previews = new HashMap<>();
			List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
			for (SurgicalWrenchRotation.Body body : bodies) {
				WrenchSubjectPreview subject = new WrenchSubjectPreview(body);
				previews.put(body.id(), subject);
				for (var cube : subject.cubes.values())
					if (single ? body.id() == subjectId && cube.cubeId() == cubeId : group.get(body.id()).get(cube.cubeId()))
						edges.addAll(SurgicalClientTopology.cubeEdges(cube));
			}
			preview = Map.copyOf(previews);
			SurgicalWrenchRotation.Body selected = preview.get(subjectId).body;
			selection = new SurgicalSelectionResolver.Selection(pos, subjectId, cubeId, selected.cubeCount(), selected.seams(), List.of(), List.copyOf(edges));
			guideFace = -1;
		}
	}
}

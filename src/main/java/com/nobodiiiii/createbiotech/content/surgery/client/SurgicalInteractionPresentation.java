package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombination;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlock;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.ponder.api.PonderPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalInteractionPresentation implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalInteractionPresentation {
	private SurgicalInteractionPresentation() {}

	static final OutlineState SEAM_OUTLINE = new OutlineState(SurgicalTableClientHandler.SEAM_HIGHLIGHT_LINE_WIDTH);
	static final OutlineState CUBE_OUTLINE = new OutlineState();
	static final OutlineState COMBINATION_OUTLINE = new OutlineState();
	static final OutlineState GLUE_JOINT_OUTLINE = new OutlineState(SurgicalTableClientHandler.SEAM_HIGHLIGHT_LINE_WIDTH);
	static final OutlineState LIMB_JOINT_OUTLINE = new OutlineState(SurgicalTableClientHandler.SEAM_HIGHLIGHT_LINE_WIDTH);
	static final OutlineState SYMMETRY_PLANE_OPTIONS_OUTLINE = new OutlineState();
	static final OutlineState SYMMETRY_PLANE_SELECTED_OUTLINE =
		new OutlineState(SurgicalTableClientHandler.SEAM_HIGHLIGHT_LINE_WIDTH);
	static final OutlineState GLUE_EDIT_OUTLINE = new OutlineState();
	static final OutlineState GLUE_POINT_OUTLINE = new OutlineState(SurgicalTableClientHandler.GLUE_POINT_LINE_WIDTH);
	static final OutlineState RESERVED_OUTLINE = new OutlineState();
	static final OutlineState RESERVED_SELECTION_OUTLINE = new OutlineState();
	static final Object PLACEMENT_OUTLINE_SLOT = new Object();
	static final Object SHOVEL_TILE_OUTLINE_SLOT = new Object();

	/**
	 * Draws one green box around every reserved column whose subject cannot be rendered, so the space
	 * the table keeps refusing is visible instead of empty. The one a shovel is aimed at takes the
	 * ordinary selection colour, exactly as a pickable component would.
	 *
	 * <p>{@link SurgicalEdgeOutline} centres its tubes on the edges it is given, so the box is pulled
	 * inward by half the line width first: the outer surface of the drawn lines then lands exactly on
	 * the reserved bounds rather than straddling them.</p>
	 */
	static void updateReservedOutline() {
		ClientLevel level = Minecraft.getInstance().level;
		if (SurgicalGeometryStore.RESERVED_SUBJECTS.isEmpty() || level == null) {
			RESERVED_OUTLINE.clear();
			RESERVED_SELECTION_OUTLINE.clear();
			return;
		}
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		List<SurgicalClientTopology.Edge> selectedEdges = new ArrayList<>();
		for (java.util.Iterator<Map.Entry<UUID, SurgicalGeometryStore.ReservedSubject>> iterator =
			SurgicalGeometryStore.RESERVED_SUBJECTS.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<UUID, SurgicalGeometryStore.ReservedSubject> entry = iterator.next();
			SurgicalGeometryStore.ReservedSubject reserved = entry.getValue();
			SurgicalSubject subject = level.getBlockEntity(reserved.tablePos())
				instanceof SurgicalTableBlockEntity table ? table.getSubject(reserved.subjectId()) : null;
			if (subject == null && !level.isLoaded(reserved.tablePos()))
				continue;
			// A subject that changed identity or stored topology has to be re-observed before anything
			// can be claimed about it again.
			if (subject == null || !subject.persistentId().equals(entry.getKey())
				|| subject.cubeCount() != reserved.storedCubeCount()) {
				iterator.remove();
				continue;
			}
			(entry.getKey().equals(SurgicalSelectionResolver.reservedSelection) ? selectedEdges : edges)
				.addAll(SurgicalClientTopology.cubeEdges(SurgicalSelectionResolver.boundsGeometry(
					SurgicalSelectionResolver.insetBox(reserved.bounds(), SurgicalTableClientHandler.HIGHLIGHT_LINE_WIDTH / 2.0d), Vec3.ZERO)));
		}
		RESERVED_OUTLINE.show(edges, SurgicalTableClientHandler.RESERVED_HIGHLIGHT_COLOR);
		RESERVED_SELECTION_OUTLINE.show(selectedEdges, SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
	}

	static void refreshGluePointHighlight() {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		if (SurgicalGlueController.pendingGlue != null)
			edges.addAll(SurgicalGlueController.pendingGlue.point().markerEdges());
		if (SurgicalSymmetryController.pendingSymmetry != null)
			edges.addAll(SurgicalSymmetryController.pendingSymmetry.point().markerEdges());
		if (SurgicalGlueController.hoveredGluePoint != null
			&& (SurgicalGlueController.pendingGlue == null || !SurgicalGlueController.hoveredGluePoint.markerEdges().equals(SurgicalGlueController.pendingGlue.point().markerEdges()))
			&& (SurgicalSymmetryController.pendingSymmetry == null
				|| !SurgicalGlueController.hoveredGluePoint.markerEdges().equals(SurgicalSymmetryController.pendingSymmetry.point().markerEdges())))
			edges.addAll(SurgicalGlueController.hoveredGluePoint.markerEdges());
		if (edges.isEmpty())
			GLUE_POINT_OUTLINE.clear();
		else
			GLUE_POINT_OUTLINE.show(edges, SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
	}

	/** During the second glue click, only the hovered target cube is outlined at its preview position. */
	static boolean highlightGlueTargetCube() {
		if (SurgicalGlueController.gluePreview == null || SurgicalGlueController.pendingGlue == null)
			return false;
		SurgicalModelRenderContext.CubeGeometry cube = SurgicalGluePreviewPlanner.previewCube(SurgicalGlueController.gluePreview.subjects(),
			SurgicalGlueController.gluePreview.targetSubjectId(), SurgicalGlueController.gluePreview.targetCubeId());
		if (cube == null)
			return false;
		SEAM_OUTLINE.clear();
		CUBE_OUTLINE.show(SurgicalClientTopology.cubeEdges(cube), SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
		LIMB_JOINT_OUTLINE.clear();
		clearSymmetryPlaneHighlight();
		return true;
	}

	static void refreshSymmetryPlaneHighlight() {
		if (SurgicalSymmetryController.pendingSymmetryReference == null) {
			clearSymmetryPlaneHighlight();
			return;
		}
		boolean singleCube = SurgicalSymmetryController.pendingSymmetryReference.singleCube(Screen.hasControlDown());
		List<SurgicalClientTopology.Edge> options = new ArrayList<>();
		for (SurgicalSymmetryController.SymmetryPlaneCandidate plane : SurgicalSymmetryController.pendingSymmetryReference.planes(singleCube))
			options.addAll(plane.edges());
		SYMMETRY_PLANE_OPTIONS_OUTLINE.show(options, PonderPalette.WHITE.getColor());
		if (SurgicalSymmetryController.symmetryPlaneSelection == null)
			SYMMETRY_PLANE_SELECTED_OUTLINE.clear();
		else
			SYMMETRY_PLANE_SELECTED_OUTLINE.show(SurgicalSymmetryController.symmetryPlaneSelection.edges(), SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
	}

	static void clearSymmetryPlaneHighlight() {
		SYMMETRY_PLANE_OPTIONS_OUTLINE.clear();
		SYMMETRY_PLANE_SELECTED_OUTLINE.clear();
	}

	/** A filled creature container can target the table surface before any model cube exists. */
	@Nullable
	static InteractionHand capturedSubjectPlacementPromptHand(LocalPlayer player, ClientLevel level) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.hitResult instanceof BlockHitResult hit)
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SurgicalTableBlock))
			return null;
		for (InteractionHand hand : SurgicalTableClientHandler.HANDS) {
			ItemStack stack = player.getItemInHand(hand);
			if ((SurgicalTableBlockEntity.isPlacementContainer(stack)
				|| SurgicalPlacementController.hasPlacementCapture(stack))
				&& CapturedEntityBoxHelper.hasCapturedEntity(stack)
				&& SurgicalPlacementController.placementCandidateFor(stack, level).result().succeeded())
				return hand;
		}
		return null;
	}

	@Nullable
	static InteractionHand interactionPromptHand(LocalPlayer player) {
		if (SurgicalWrenchController.wrenchEditor != null)
			return SurgicalWrenchController.wrenchEditor.hand;
		if (SurgicalCutController.pendingCut != null)
			return SurgicalCutController.pendingCut.hand;
		if (SurgicalCutController.pendingGlueCut != null)
			return SurgicalCutController.pendingGlueCut.hand;
		if (SurgicalGlueController.glueEditor != null)
			return SurgicalGlueController.glueEditor.hand;
		if (SurgicalGlueController.pendingGlue != null)
			return SurgicalGlueController.pendingGlue.hand();
		if (SurgicalSymmetryController.pendingSymmetry != null)
			return SurgicalSymmetryController.pendingSymmetry.hand();
		if (SurgicalJointController.pendingLimb != null)
			return SurgicalJointController.pendingLimb.hand();
		if (SurgicalJointController.pendingSlimeSeam != null)
			return SurgicalJointController.pendingSlimeSeam.hand();
		for (InteractionHand hand : SurgicalTableClientHandler.HANDS)
			if (isSurgicalInteractionItem(player.getItemInHand(hand)))
				return hand;
		return null;
	}

	static boolean isSurgicalInteractionItem(ItemStack stack) {
		return SurgicalTableBlockEntity.isPlacementContainer(stack) || CapturedEntityBoxHelper.isEmptyLargeBox(stack)
			|| SurgicalKitItem.isShears(stack)
			|| SurgicalSelectionResolver.isSurgicalGlue(stack) || SurgicalSelectionResolver.isSymmetryWand(stack) || SurgicalKitItem.isHoneyBottle(stack)
			|| SurgicalKitItem.isSlimeBall(stack) || SurgicalSelectionResolver.heldLimbType(stack) != null
			|| SurgicalKitItem.isWrench(stack) || SurgicalKitItem.isShovel(stack)
			|| SurgicalKitItem.isTemporaryBox(stack) || SurgicalKitItem.isCubeCopier(stack);
	}

	static void addCancelControl(List<Component> tooltip, boolean enabled) {
		addInteractionControl(tooltip, Component.keybind("key.attack"),
			"create_biotech.gui.surgical_table.action.cancel", enabled);
	}

	static void addBoxPackControls(List<Component> tooltip) {
		addInteractionControl(tooltip, Component.keybind("key.use"), "create_biotech.gui.surgical_table.action.pack");
		addInteractionControl(tooltip, Component.translatable("create_biotech.gui.surgical_table.control.ctrl"),
			"create_biotech.gui.surgical_table.action.preview_ground");
	}

	static void addGlueControls(List<Component> tooltip, boolean smartGlue, boolean hasFirstPoint, boolean editing) {
		addInteractionControl(tooltip, Component.keybind("key.use"),
			"create_biotech.gui.surgical_table.action.select_first_glue", !hasFirstPoint && !editing);
		addInteractionControl(tooltip, Component.keybind("key.use"),
			"create_biotech.gui.surgical_table.action.select_second_glue", hasFirstPoint && !editing);
		if (smartGlue) {
			addInteractionControl(tooltip, Component.translatable("create_biotech.gui.surgical_table.control.scroll"),
				"create_biotech.gui.surgical_table.action.move_glue", editing);
			addInteractionControl(tooltip, combinedControl(Component.translatable("create_biotech.gui.surgical_table.control.ctrl"),
				Component.translatable("create_biotech.gui.surgical_table.control.scroll")),
				"create_biotech.gui.surgical_table.action.rotate_glue", editing);
			addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.confirm_glue", editing);
		}
		addCancelControl(tooltip, hasFirstPoint);
	}

	static void addInteractionControl(List<Component> tooltip, Component control, String actionKey) {
		addInteractionControl(tooltip, control, actionKey, true);
	}

	static void addInteractionControl(List<Component> tooltip, Component control, String actionKey,
		boolean enabled) {
		ChatFormatting controlColor = enabled ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY;
		ChatFormatting actionColor = enabled ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;
		CreateLang.builder()
			.add(control.copy().withStyle(controlColor))
			.text(ChatFormatting.DARK_GRAY, "  ")
			.add(Component.translatable(actionKey).withStyle(actionColor))
			.forGoggles(tooltip, 1);
	}

	static MutableComponent combinedControl(Component first, Component second) {
		return first.copy()
			.append(Component.literal(" + "))
			.append(second.copy());
	}

	@Nullable
	static SurgicalSelectionResolver.SelectionHighlightEdges connectedSelectionEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components) {
		return connectedSelectionEdges(tablePos, table, components, false);
	}

	@Nullable
	static SurgicalSelectionResolver.SelectionHighlightEdges connectedSelectionEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components, boolean includeLimbJoints) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		List<SurgicalClientTopology.Edge> combinationEdges = new ArrayList<>();
		java.util.Set<UUID> collapsed = new java.util.HashSet<>();
		for (Map.Entry<Integer, BitSet> entry : components.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			BitSet ordinary = (BitSet) entry.getValue().clone();
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				SurgicalCombination combination = subject.combinationContaining(cube);
				if (combination == null || !SurgicalSelectionResolver.combinationFullySelected(table, components, combination))
					continue;
				for (SurgicalCombination.Member member : combination.members())
					if (member.subjectKey().equals(subject.persistentId()))
						ordinary.clear(member.cubeId());
				if (collapsed.add(combination.id()))
					combinationEdges.addAll(combinationCubeEdges(tablePos, table, combination));
			}
			edges.addAll(geometry.componentCubeEdges(ordinary));
		}
		List<SurgicalClientTopology.Edge> glueJointEdges = selectedGlueJointEdges(tablePos, table, components);
		List<SurgicalClientTopology.Edge> limbJointEdges = includeLimbJoints
			? selectedLimbJointEdges(tablePos, table, components) : List.of();
		return glueJointEdges == null || limbJointEdges == null ? null
			: new SurgicalSelectionResolver.SelectionHighlightEdges(edges, combinationEdges, glueJointEdges, limbJointEdges);
	}

	/** Keeps glue boundaries visible when separately honey-fused parts are selected as one rigid group. */
	@Nullable
	static List<SurgicalClientTopology.Edge> selectedGlueJointEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components) {
		Set<SurgicalGlueJoint> joints = new HashSet<>();
		for (SurgicalSubject subject : table.getSubjects())
			joints.addAll(subject.glueJoints());
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		for (SurgicalGlueJoint joint : joints) {
			if (table.isInternalCombinationJoint(joint)
				|| !SurgicalSelectionResolver.selectedEndpoint(table, components, joint.first())
				|| !SurgicalSelectionResolver.selectedEndpoint(table, components, joint.second()))
				continue;
			SurgicalSubject firstSubject = table.getSubjectByPersistentId(joint.first().subjectKey());
			SurgicalSubject secondSubject = table.getSubjectByPersistentId(joint.second().subjectKey());
			SurgicalGeometryStore.TableGeometry firstGeometry = firstSubject == null ? null
				: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, firstSubject.id()));
			SurgicalGeometryStore.TableGeometry secondGeometry = secondSubject == null ? null
				: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, secondSubject.id()));
			if (firstSubject == null || secondSubject == null
				|| !SurgicalGeometryStore.geometryReadyForUse(table, firstSubject, firstGeometry)
				|| !SurgicalGeometryStore.geometryReadyForUse(table, secondSubject, secondGeometry))
				return null;
			SurgicalModelRenderContext.CubeGeometry first =
				firstGeometry.cubesById.get(joint.first().cubeId());
			SurgicalModelRenderContext.CubeGeometry second =
				secondGeometry.cubesById.get(joint.second().cubeId());
			if (first == null || second == null)
				return null;
			SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
				SurgicalAssembly.Seam.of(0, 1), List.of(
					new SurgicalModelRenderContext.CubeGeometry(0, first.corners()),
					new SurgicalModelRenderContext.CubeGeometry(1, second.corners())));
			if (contact != null)
				edges.addAll(contact.edges());
		}
		return List.copyOf(edges);
	}

	/** Uses the same contact-edge geometry as the red cutting preview for selected anatomical joints. */
	@Nullable
	static List<SurgicalClientTopology.Edge> selectedLimbJointEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> components) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		for (SurgicalLimbJoint joint : table.limbJoints()) {
			if (!SurgicalSelectionResolver.selectedEndpoint(table, components, joint.child())
				|| !SurgicalSelectionResolver.selectedEndpoint(table, components, joint.parent()))
				continue;
			SurgicalSubject childSubject = table.getSubjectByPersistentId(joint.child().subjectKey());
			SurgicalSubject parentSubject = table.getSubjectByPersistentId(joint.parent().subjectKey());
			SurgicalGeometryStore.TableGeometry childGeometry = childSubject == null ? null
				: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, childSubject.id()));
			SurgicalGeometryStore.TableGeometry parentGeometry = parentSubject == null ? null
				: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, parentSubject.id()));
			if (childSubject == null || parentSubject == null
				|| !SurgicalGeometryStore.geometryReadyForUse(table, childSubject, childGeometry)
				|| !SurgicalGeometryStore.geometryReadyForUse(table, parentSubject, parentGeometry))
				return null;
			SurgicalModelRenderContext.CubeGeometry child =
				childGeometry.cubesById.get(joint.child().cubeId());
			SurgicalModelRenderContext.CubeGeometry parent =
				parentGeometry.cubesById.get(joint.parent().cubeId());
			if (child == null || parent == null)
				return null;

			SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
				SurgicalAssembly.Seam.of(0, 1), List.of(
					new SurgicalModelRenderContext.CubeGeometry(0, child.corners()),
					new SurgicalModelRenderContext.CubeGeometry(1, parent.corners())));
			if (contact != null)
				edges.addAll(contact.edges());
		}
		return List.copyOf(edges);
	}

	/** Uses the ordinary per-cube wireframe for honey combinations; only its colour is special. */
	static List<SurgicalClientTopology.Edge> combinationCubeEdges(BlockPos tablePos,
		SurgicalTableBlockEntity table, SurgicalCombination combination) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>(combination.members().size() * 12);
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			SurgicalGeometryStore.TableGeometry geometry = subject == null ? null
				: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, subject.id()));
			SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
				: geometry.cubesById.get(member.cubeId());
			if (cube != null)
				edges.addAll(SurgicalClientTopology.cubeEdges(cube));
		}
		return List.copyOf(edges);
	}

	/** Shift-shears renders every one-hop neighbour blue while reserving red for the hit cube. */
	@Nullable
	static List<SurgicalClientTopology.Edge> directConnectionEdges(SurgicalSelectionResolver.CubeHit hit,
		SurgicalTableBlockEntity table, Map<Integer, BitSet> direct) {
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		for (Map.Entry<Integer, BitSet> entry : direct.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(hit.tablePos(), entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return null;
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				if (entry.getKey() == hit.geometry().subjectId && cube == hit.cubeId())
					continue;
				SurgicalModelRenderContext.CubeGeometry connected = geometry.cubesById.get(cube);
				if (connected != null)
					edges.addAll(SurgicalClientTopology.cubeEdges(connected));
			}
		}
		return List.copyOf(edges);
	}

	static void highlightSelection(SurgicalSelectionResolver.Selection selection) {
		highlightSelection(selection, false);
	}

	static void highlightSelection(SurgicalSelectionResolver.Selection selection, boolean highlightLimbJoints) {
		SEAM_OUTLINE.show(selection.edges(), SurgicalTableClientHandler.SEAM_HIGHLIGHT_COLOR);
		CUBE_OUTLINE.show(selection.cubeEdges(), SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
		COMBINATION_OUTLINE.show(selection.combinationEdges(), SurgicalTableClientHandler.HONEY_HIGHLIGHT_COLOR);
		GLUE_JOINT_OUTLINE.show(selection.glueJointEdges(), SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
		if (highlightLimbJoints)
			LIMB_JOINT_OUTLINE.show(selection.limbJointEdges(), SurgicalTableClientHandler.LIMB_JOINT_HIGHLIGHT_COLOR);
		else
			LIMB_JOINT_OUTLINE.clear();
	}

	static void clearSeamHighlight() {
		SEAM_OUTLINE.clear();
		CUBE_OUTLINE.clear();
		COMBINATION_OUTLINE.clear();
		GLUE_JOINT_OUTLINE.clear();
		LIMB_JOINT_OUTLINE.clear();
	}

	/** Keeps one joined edge mesh per colour and only rewrites it when the selection changes. */
	static final class OutlineState {
		private final Object slot = new Object();
		private final SurgicalEdgeOutline outline = new SurgicalEdgeOutline();
		private final float lineWidth;
		private List<SurgicalClientTopology.Edge> edges = List.of();
		private int color = -1;
		private long lastRefreshTick = Long.MIN_VALUE;

		private OutlineState() {
			this(SurgicalTableClientHandler.HIGHLIGHT_LINE_WIDTH);
		}

		private OutlineState(float lineWidth) {
			this.lineWidth = lineWidth;
		}

		void show(List<SurgicalClientTopology.Edge> nextEdges, int color) {
			if (nextEdges.isEmpty()) {
				clear();
				return;
			}
			ClientLevel level = Minecraft.getInstance().level;
			long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
			// Reference identity first: these lists are immutable and the unchanged case hands back
			// the very same instance, so the element-wise compare below - two Vec3 per edge, hundreds
			// of edges, three outlines, every frame - is only reached when something actually moved.
			if ((edges == nextEdges || edges.equals(nextEdges)) && this.color == color) {
				if (tick != lastRefreshTick) {
					Outliner.getInstance().keep(slot);
					lastRefreshTick = tick;
				}
				return;
			}

			outline.setEdges(nextEdges);
			Outliner.getInstance().showOutline(slot, outline)
				.lineWidth(lineWidth)
				.disableLineNormals()
				.colored(color);
			edges = List.copyOf(nextEdges);
			this.color = color;
			lastRefreshTick = tick;
		}

		void clear() {
			if (!edges.isEmpty())
				Outliner.getInstance().remove(slot);
			edges = List.of();
			color = -1;
			lastRefreshTick = Long.MIN_VALUE;
		}
	}
}

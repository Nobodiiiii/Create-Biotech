package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;

import net.minecraft.client.model.ArmadilloModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;

/** Separates native cuboids sharing one ModelPart name; see the matching 1.21.1 models in ref/. */
final class SurgicalNativePartLabels {
	private SurgicalNativePartLabels() {}

	static void label(EntityModel<?> model, String field, ModelPart part, Map<ModelPart.Cube, Set<String>> names) {
		var cubes = ((ModelPartAccessor) (Object) part).createBiotech$getCubes();
		if (model instanceof IllagerModel<?> && field.equals("arms")) {
			// IllagerModel swaps the folded mesh for right_arm/left_arm while casting.
			// Give each retained shoulder its unfolded name so native poses keep the same surgical id.
			for (var cube : cubes)
				if (cube.maxX < 0)
					names.computeIfAbsent(cube, ignored -> new HashSet<>()).add("right_arm");
			ModelPart left = ((ModelPartAccessor) (Object) part).createBiotech$getChildren().get("left_shoulder");
			if (left != null)
				for (var cube : ((ModelPartAccessor) (Object) left).createBiotech$getCubes())
					names.computeIfAbsent(cube, ignored -> new HashSet<>()).add("left_arm");
		}
		if (model instanceof CowModel<?> && field.equals("body"))
			for (var cube : cubes)
				if (cube.maxX - cube.minX == 4 && cube.maxY - cube.minY == 6 && cube.maxZ - cube.minZ == 1)
					names.computeIfAbsent(cube, ignored -> new HashSet<>()).add("udder");
		if (model instanceof ArmadilloModel) {
			if (field.equals("body") && cubes.size() >= 2) {
				names.computeIfAbsent(cubes.get(0), ignored -> new HashSet<>()).add("outer_shell");
				names.computeIfAbsent(cubes.get(1), ignored -> new HashSet<>()).add("inner_body");
			}
			if (field.equals("cube"))
				for (var cube : cubes)
					names.computeIfAbsent(cube, ignored -> new HashSet<>()).add("rolled_shell");
		}
	}
}

package com.nobodiiiii.createbiotech.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import com.simibubi.create.foundation.model.BakedQuadHelper;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelData.Builder;
import net.minecraftforge.client.model.data.ModelProperty;

public class SlimeBeltFunnelModel extends BakedModelWrapperWithData {

	private static final ModelProperty<BeltSurface> SURFACE_PROPERTY = new ModelProperty<>();

	public SlimeBeltFunnelModel(BakedModel originalModel) {
		super(originalModel);
	}

	@Override
	protected Builder gatherModelData(Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state,
		ModelData blockEntityData) {
		BeltSurface surface = BeltSurfaceResolver.resolve(world, pos);
		return surface == null ? builder : builder.with(SURFACE_PROPERTY, surface);
	}

	@Override
	public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
		@NotNull RandomSource rand, @NotNull ModelData extraData, @Nullable RenderType renderType) {
		BeltSurface surface = extraData.get(SURFACE_PROPERTY);
		if (surface == null)
			return super.getQuads(state, side, rand, extraData, renderType);
		if (state == null)
			return Collections.emptyList();

		List<BakedQuad> templateQuads = super.getQuads(state, side, rand, extraData, renderType);
		if (templateQuads.isEmpty())
			return templateQuads;

		List<BakedQuad> quads = new ArrayList<>(templateQuads.size());
		for (BakedQuad templateQuad : templateQuads) {
			int[] transformedVertices = templateQuad.getVertices().clone();
			for (int vertex = 0; vertex < 4; vertex++) {
				Vec3 transformedPosition = surface.transformPosition(BakedQuadHelper.getXYZ(transformedVertices, vertex));
				Vec3 transformedNormal = surface.transformDirection(BakedQuadHelper.getNormalXYZ(transformedVertices, vertex))
					.normalize();
				BakedQuadHelper.setXYZ(transformedVertices, vertex, transformedPosition);
				BakedQuadHelper.setNormalXYZ(transformedVertices, vertex, transformedNormal);
			}

			Vec3 quadNormal = surface.transformDirection(Vec3.atLowerCornerOf(templateQuad.getDirection().getNormal()))
				.normalize();
			quads.add(new BakedQuad(transformedVertices, templateQuad.getTintIndex(),
				BeltSurface.nearestDirection(quadNormal), templateQuad.getSprite(), templateQuad.isShade()));
		}

		return quads;
	}
}

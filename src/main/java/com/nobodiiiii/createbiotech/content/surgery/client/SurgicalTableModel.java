package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlock;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import com.simibubi.create.foundation.model.BakedQuadHelper;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelData.Builder;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * The visual connection rules of Create's table cloth, restricted to surgical-table blocks.
 * No table-cloth block, item, block-entity, shop, or interaction behaviour is inherited.
 */
public class SurgicalTableModel extends BakedModelWrapperWithData {
	private static final ModelProperty<CullData> CULL_PROPERTY = new ModelProperty<>();
	private static final Map<Block, List<List<BakedQuad>>> CORNERS = new HashMap<>();

	public SurgicalTableModel(BakedModel originalModel) {
		super(originalModel);
	}

	public static void reload() {
		CORNERS.clear();
	}

	@Override
	public boolean useAmbientOcclusion() {
		return false;
	}

	private List<BakedQuad> getCorner(Block block, int corner, @NotNull RandomSource random,
		@Nullable RenderType renderType) {
		if (!CORNERS.containsKey(block)) {
			TextureAtlasSprite targetSprite = getParticleIcon(ModelData.EMPTY);
			List<List<BakedQuad>> corners = new ArrayList<>();
			for (PartialModel partial : List.of(AllPartialModels.TABLE_CLOTH_SW,
				AllPartialModels.TABLE_CLOTH_NW, AllPartialModels.TABLE_CLOTH_NE,
				AllPartialModels.TABLE_CLOTH_SE))
				corners.add(getCornerQuads(random, renderType, targetSprite, partial));
			CORNERS.put(block, corners);
		}
		return CORNERS.get(block).get(corner);
	}

	private static List<BakedQuad> getCornerQuads(RandomSource random, @Nullable RenderType renderType,
		TextureAtlasSprite targetSprite, PartialModel partial) {
		List<BakedQuad> quads = new ArrayList<>();
		for (BakedQuad quad : partial.get().getQuads(null, null, random, ModelData.EMPTY, renderType)) {
			TextureAtlasSprite original = quad.getSprite();
			BakedQuad retextured = BakedQuadHelper.clone(quad);
			int[] vertices = retextured.getVertices();
			for (int vertex = 0; vertex < 4; vertex++) {
				BakedQuadHelper.setU(vertices, vertex, targetSprite.getU(SpriteShiftEntry.getUnInterpolatedU(
					original, BakedQuadHelper.getU(vertices, vertex))));
				BakedQuadHelper.setV(vertices, vertex, targetSprite.getV(SpriteShiftEntry.getUnInterpolatedV(
					original, BakedQuadHelper.getV(vertices, vertex))));
			}
			quads.add(retextured);
		}
		return quads;
	}

	@Override
	protected Builder gatherModelData(Builder builder, BlockAndTintGetter world, BlockPos pos,
		BlockState state, ModelData blockEntityData) {
		List<Direction> culledSides = new ArrayList<>();
		for (Direction side : Iterate.horizontalDirections) {
			BlockPos adjacentPos = pos.relative(side);
			if (SurgicalTableBlock.connectsVisuallyTo(world.getBlockState(adjacentPos))
				|| !Block.shouldRenderFace(state, world, pos, side, adjacentPos))
				culledSides.add(side);
		}
		return culledSides.isEmpty() ? builder
			: builder.with(CULL_PROPERTY, new CullData(EnumSet.copyOf(culledSides)));
	}

	@Override
	public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
		@NotNull RandomSource random, @NotNull ModelData extraData, @Nullable RenderType renderType) {
		@NotNull List<BakedQuad> mainQuads = super.getQuads(state, side, random, extraData, renderType);
		if (side == null || side.getAxis() == Axis.Y)
			return mainQuads;

		CullData cullData = extraData.get(CULL_PROPERTY);
		if (cullData != null && cullData.culled().contains(side.getClockWise()))
			return mainQuads;
		if (state == null || !(state.getBlock() instanceof SurgicalTableBlock))
			return mainQuads;

		List<BakedQuad> connected = new ArrayList<>(mainQuads);
		connected.addAll(getCorner(state.getBlock(), side.get2DDataValue(), random, renderType));
		return connected;
	}

	private record CullData(EnumSet<Direction> culled) {}
}

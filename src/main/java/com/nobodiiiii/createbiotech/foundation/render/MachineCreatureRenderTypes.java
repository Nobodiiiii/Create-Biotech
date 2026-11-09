package com.nobodiiiii.createbiotech.foundation.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Double-sided block-atlas materials for thin and alpha-masked creature parts. */
final class MachineCreatureRenderTypes {

	static final RenderType CUTOUT_NO_CULL = RenderType.create("create_biotech_creature_cutout_no_cull",
		DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, true, false,
		RenderType.CompositeState.builder()
			.setShaderState(RenderStateShard.RENDERTYPE_CUTOUT_SHADER)
			.setTextureState(RenderStateShard.BLOCK_SHEET)
			.setCullState(RenderStateShard.NO_CULL)
			.setLightmapState(RenderStateShard.LIGHTMAP)
			.createCompositeState(true));

	static final RenderType TRANSLUCENT_NO_CULL = RenderType.create("create_biotech_creature_translucent_no_cull",
		DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, true, true,
		RenderType.CompositeState.builder()
			.setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
			.setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
			.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
			.setCullState(RenderStateShard.NO_CULL)
			.setLightmapState(RenderStateShard.LIGHTMAP)
			.setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
			.createCompositeState(true));

	private static final RendererReloadCache<PartialModel, Model> DOUBLE_SIDED_PARTIALS =
		new RendererReloadCache<>(partial -> new BakedModelBuilder(partial.get())
			.materialFunc((renderType, shaded, ao) -> {
				var material = ModelUtil.getMaterial(renderType, shaded, ao);
				return material == null ? null : SimpleMaterial.builderOf(material)
					.backfaceCulling(false)
					.build();
			})
			.build());

	private MachineCreatureRenderTypes() {}

	static Model doubleSidedPartial(PartialModel partial) {
		return DOUBLE_SIDED_PARTIALS.get(partial);
	}
}

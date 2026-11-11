package com.nobodiiiii.createbiotech.foundation.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Block-atlas materials that preserve creature alpha masks and optional double-sided geometry. */
final class MachineCreatureRenderTypes {

	static final RenderType CUTOUT_NO_CULL = RenderType.create("create_biotech_creature_cutout_no_cull",
		DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, true, false,
		RenderType.CompositeState.builder()
			.setShaderState(RenderStateShard.RENDERTYPE_CUTOUT_SHADER)
			.setTextureState(RenderStateShard.BLOCK_SHEET)
			.setCullState(RenderStateShard.NO_CULL)
			.setLightmapState(RenderStateShard.LIGHTMAP)
			.createCompositeState(true));

	static final RenderType TRANSLUCENT = translucent(false);
	static final RenderType TRANSLUCENT_NO_CULL = translucent(true);

	private static final RendererReloadCache<PartialModel, Model> CULLED_PARTIALS = partials(false);
	private static final RendererReloadCache<PartialModel, Model> DOUBLE_SIDED_PARTIALS =
		partials(true);

	private MachineCreatureRenderTypes() {}

	private static RenderType translucent(boolean doubleSided) {
		return RenderType.create("create_biotech_creature_translucent" + (doubleSided ? "_no_cull" : ""),
			DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, true, true,
			RenderType.CompositeState.builder()
				// The chunk translucent shader writes depth even for alpha=0. Use the cutout
				// shader's 0.1 discard with blending to match entity translucency without
				// changing Catnip's BLOCK vertex format or applying diffuse lighting twice.
				.setShaderState(RenderStateShard.RENDERTYPE_CUTOUT_SHADER)
				.setTextureState(RenderStateShard.BLOCK_SHEET)
				.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
				.setCullState(doubleSided ? RenderStateShard.NO_CULL : RenderStateShard.CULL)
				.setLightmapState(RenderStateShard.LIGHTMAP)
				.createCompositeState(true));
	}

	private static RendererReloadCache<PartialModel, Model> partials(boolean doubleSided) {
		return new RendererReloadCache<>(partial -> new BakedModelBuilder(partial.get())
			.materialFunc((renderType, shaded, ao) -> {
				var material = ModelUtil.getMaterial(renderType, shaded, ao);
				if (material == null)
					return null;
				var builder = SimpleMaterial.builderOf(material)
					.backfaceCulling(!doubleSided);
				if (renderType == RenderType.translucent())
					builder.cutout(CutoutShaders.ONE_TENTH)
						.mipmap(false);
				return builder.build();
			})
			.build());
	}

	static Model partial(PartialModel partial, boolean doubleSided) {
		return (doubleSided ? DOUBLE_SIDED_PARTIALS : CULLED_PARTIALS).get(partial);
	}
}

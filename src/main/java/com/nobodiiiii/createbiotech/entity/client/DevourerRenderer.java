package com.nobodiiiii.createbiotech.entity.client;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.monster.Slime;

/** Two named surgical carriers with the original slime UVs and squish animation. */
public final class DevourerRenderer extends SlimeRenderer {
	public DevourerRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new SlimeModel<>(new ModelPart(List.of(), Map.of("devour_inner",
			context.bakeLayer(ModelLayers.SLIME).getChild("cube"))));
		layers.clear();
		addLayer(new Outer(this, context));
	}

	@Override
	protected boolean shouldShowName(Slime slime) { return false; }

	private static final class Outer extends RenderLayer<Slime, SlimeModel<Slime>> {
		private final SlimeModel<Slime> shell;

		Outer(DevourerRenderer parent, EntityRendererProvider.Context context) {
			super(parent);
			shell = new SlimeModel<>(new ModelPart(List.of(), Map.of("devour_outer",
				context.bakeLayer(ModelLayers.SLIME_OUTER).getChild("cube"))));
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffer, int light, Slime slime, float swing,
			float amount, float partialTick, float age, float yaw, float pitch) {
			if (!slime.isInvisible())
				shell.renderToBuffer(pose, buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(slime))),
					light, LivingEntityRenderer.getOverlayCoords(slime, 0));
		}
	}
}

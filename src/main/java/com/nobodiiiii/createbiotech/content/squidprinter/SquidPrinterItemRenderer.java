package com.nobodiiiii.createbiotech.content.squidprinter;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SquidPrinterItemRenderer extends CustomRenderedItemModelRenderer {

	private static final float SPOUT_ITEM_HEIGHT_BLOCKS = 23.0f / 16.0f;
	private static final float TOTAL_RENDER_HEIGHT_BLOCKS =
		Math.max(SPOUT_ITEM_HEIGHT_BLOCKS,
			SquidPrinterSquidVisual.MODEL_HEIGHT_BLOCKS * SquidPrinterSquidVisual.RENDER_SCALE);
	// The spout item parent already fits one slot; shrink the combined spout+squid assembly to that same envelope.
	private static final float ITEM_ENVELOPE_SCALE = SPOUT_ITEM_HEIGHT_BLOCKS / TOTAL_RENDER_HEIGHT_BLOCKS;
	private static final float ITEM_Y_OFFSET = 0.15f;
	private static final float SQUID_ATTACHMENT_Y = 0.55f;

	@Nullable
	private SquidModel<Squid> squidModel;
	@Nullable
	private Squid cachedSquid;
	@Nullable
	private ClientLevel cachedLevel;

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		ms.pushPose();
		ms.translate(0, ITEM_Y_OFFSET, 0);
		ms.scale(ITEM_ENVELOPE_SCALE, ITEM_ENVELOPE_SCALE, ITEM_ENVELOPE_SCALE);
		renderer.render(model.getOriginalModel(), light);

		Squid squid = getOrCreateSquid(Minecraft.getInstance().level);
		boolean guiLighting = transformType == ItemDisplayContext.GUI;
		if (squid != null && getSquidModel() != null)
			renderSquidAssembly(squid, ms, buffer, light, guiLighting);
		ms.popPose();
	}

	private void renderSquidAssembly(Squid squid, PoseStack ms, MultiBufferSource buffer, int packedLight,
		boolean guiLighting) {
		ms.pushPose();
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			ms.translate(0, SQUID_ATTACHMENT_Y, 0);
			ms.scale(-SquidPrinterSquidVisual.RENDER_SCALE, -SquidPrinterSquidVisual.RENDER_SCALE,
				SquidPrinterSquidVisual.RENDER_SCALE);
			renderSquid(squid, ms, buffer, packedLight);
		} finally {
			ms.popPose();
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}

	private void renderSquid(Squid squid, PoseStack ms, MultiBufferSource buffer, int packedLight) {
		SquidModel<Squid> squidModel = getSquidModel();
		if (squidModel == null)
			return;
		SquidPrinterSquidVisual.prepareIdleModel(squidModel);
		SquidPrinterSquidVisual.renderModel(squidModel, ms, buffer, packedLight);
	}

	private @Nullable SquidModel<Squid> getSquidModel() {
		if (squidModel == null && Minecraft.getInstance().getEntityModels() != null)
			squidModel = new SquidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SQUID));
		return squidModel;
	}

	private @Nullable Squid getOrCreateSquid(@Nullable Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return null;

		if (cachedSquid == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedSquid = EntityType.SQUID.create(clientLevel);
			if (cachedSquid != null) {
				cachedSquid.setNoAi(true);
				cachedSquid.setSilent(true);
			}
		}

		return cachedSquid;
	}
}

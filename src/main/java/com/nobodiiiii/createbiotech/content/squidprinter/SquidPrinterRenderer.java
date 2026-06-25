package com.nobodiiiii.createbiotech.content.squidprinter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Squid;
import net.minecraftforge.fluids.FluidStack;

public class SquidPrinterRenderer extends SafeBlockEntityRenderer<SquidPrinterBlockEntity> {

	private final SquidModel<Squid> squidModel;

	public SquidPrinterRenderer(BlockEntityRendererProvider.Context context) {
		squidModel = new SquidModel<>(context.bakeLayer(ModelLayers.SQUID));
	}

	@Override
	protected void renderSafe(SquidPrinterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {

		SmartFluidTankBehaviour tank = be.tank;
		if (tank != null) {
			renderFluidInTank(tank, ms, buffer, light, partialTicks);
		}

		int processingTicks = be.processingTicks;
		float processingPT = processingTicks - partialTicks;
		float processingProgress = 1 - (processingPT - 5) / 10;
		processingProgress = Mth.clamp(processingProgress, 0, 1);

		if (tank != null) {
			FluidStack fluidStack = tank.getPrimaryTank().getRenderedFluid();
			float radius = 0;

			if (!fluidStack.isEmpty() && processingTicks != -1) {
				radius = (float) (Math.pow(((2 * processingProgress) - 1), 2) - 1);
				net.minecraft.world.phys.AABB bb =
					new net.minecraft.world.phys.AABB(0.5, 0.0, 0.5, 0.5, -1.2, 0.5).inflate(radius / 32f);
				ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, (float) bb.minX, (float) bb.minY,
					(float) bb.minZ, (float) bb.maxX, (float) bb.maxY, (float) bb.maxZ, buffer, ms, light, true, true);
			}
		}

		renderSquid(be, partialTicks, ms, buffer, light);
		FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
	}

	private void renderFluidInTank(SmartFluidTankBehaviour tank, PoseStack ms, MultiBufferSource buffer, int light,
		float partialTicks) {
		TankSegment primaryTank = tank.getPrimaryTank();
		FluidStack fluidStack = primaryTank.getRenderedFluid();
		float fluidLevel = primaryTank.getFluidLevel().getValue(partialTicks);
		if (fluidStack.isEmpty() || fluidLevel == 0)
			return;

		boolean top = fluidStack.getFluid().getFluidType().isLighterThanAir();

		fluidLevel = Math.max(fluidLevel, 0.175f);
		float min = 2.5f / 16f;
		float max = min + (11 / 16f);
		float yOffset = (11 / 16f) * fluidLevel;

		ms.pushPose();
		if (!top)
			ms.translate(0, yOffset, 0);
		else
			ms.translate(0, max - min, 0);

		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, min, min - yOffset, min, max, min, max, buffer,
			ms, light, false, true);

		ms.popPose();
	}

	private void renderSquid(SquidPrinterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light) {
		float scale = SquidPrinterSquidVisual.RENDER_SCALE;
		BlockEntityModelElement.builder()
			.atLocal(0.5d, SquidPrinterSquidVisual.HEAD_TOP_Y, 0.5d)
			.scale(-scale, -scale, scale)
			.packedLight(light)
			.render(ms, buffer, (poseStack, buf, lightArg) -> {
				SquidPrinterSquidVisual.prepareModel(squidModel, be.getSquidPose(partialTicks));
				SquidPrinterSquidVisual.renderModel(squidModel, poseStack, buf, lightArg);
			});
	}
}

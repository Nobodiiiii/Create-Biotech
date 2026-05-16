package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlockEntity.MachineKind;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.items.ItemStackHandler;

import org.joml.Quaternionf;

public class SpiderAssemblyTableRenderer extends KineticBlockEntityRenderer<SpiderAssemblyTableBlockEntity> {

	private static final ResourceLocation SPIDER_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/spider/spider.png");
	private static final float SPIDER_SCALE = 1.0f;
	private static final float SPIDER_Y_OFFSET = 0.5f + 15f / 16f * SPIDER_SCALE;
	private static final float ACTIVE_LEG_BEND = (float) Math.toRadians(20);
	private static final float SHAFT_OFFSET = 0.34f;
	private static final float MACHINE_SCALE = 0.4f;
	private static final float LEG_LENGTH_MODEL = 15.0f;
	private static final float LEG_PIVOT_X_MODEL = 4.0f;
	private static final int[] LEG_PIVOT_Z_MODEL = { -1, 0, 1, 2, -1, 0, 1, 2 };
	private static final float DEPOT_X_MODEL = 0f;
	private static final float DEPOT_Y_MODEL = 39f;
	private static final float DEPOT_Z_MODEL = 0f;

	private final SpiderModel<RenderSpider> spiderModel;
	private RenderSpider cachedSpider;
	private ClientLevel cachedLevel;

	public SpiderAssemblyTableRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		spiderModel = new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER));
	}

	@Override
	protected void renderSafe(SpiderAssemblyTableBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		BlockState state = be.getBlockState();
		if (!state.hasProperty(SpiderAssemblyTableBlock.FACING))
			return;

		Direction facing = state.getValue(SpiderAssemblyTableBlock.FACING);
		renderSpider(be, partialTicks, ms, buffer, light, facing);

		BlockState shaftState = shaft(getRotationAxisOf(be));
		RenderType shaftRenderType = getRenderType(be, shaftState);
		Direction tail = facing.getOpposite();
		ms.pushPose();
		ms.translate(tail.getStepX() * SHAFT_OFFSET, 0, tail.getStepZ() * SHAFT_OFFSET);
		renderRotatingBuffer(be, getRotatedModel(be, shaftState), ms, buffer.getBuffer(shaftRenderType), light);
		ms.popPose();
	}

	private void renderSpider(SpiderAssemblyTableBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, Direction facing) {
		RenderSpider spider = getOrCreateSpider(be.getLevel());
		if (spider == null)
			return;

		prepareSpiderModel(spider, be, partialTicks);

		ms.pushPose();
		ms.translate(0.5d, SPIDER_Y_OFFSET, 0.5d);
		ms.mulPose(Axis.YP.rotationDegrees(yRotation(facing)));
		ms.scale(-SPIDER_SCALE, -SPIDER_SCALE, SPIDER_SCALE);
		VertexConsumer spiderBuffer = buffer.getBuffer(spiderModel.renderType(SPIDER_TEXTURE));
		spiderModel.renderToBuffer(ms, spiderBuffer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
		renderLegMachines(be, ms, buffer, light);
		ms.popPose();
	}

	private void renderLegMachines(SpiderAssemblyTableBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
		ModelPart root = spiderModel.root();
		ItemStackHandler inventory = be.getInventory();

		for (int slot = 0; slot < SpiderAssemblyTableBlockEntity.LEG_COUNT; slot++) {
			ItemStack machineStack = inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.MACHINE_SLOT_START + slot);
			MachineKind kind = MachineKind.fromStack(machineStack);
			if (kind == null)
				continue;

			ModelPart leg = getAnimatedLeg(root, slot);
			if (leg == null)
				continue;

			BlockState machineState = machineStateFor(kind);
			boolean leftSide = slot < 4;
			float sign = leftSide ? 1f : -1f;

			float cz = Mth.cos(leg.zRot);
			float sz = Mth.sin(leg.zRot);
			float cy = Mth.cos(leg.yRot);
			float sy = Mth.sin(leg.yRot);

			float axisX = sign * cz * cy;
			float axisY = sign * sz * cy;
			float axisZ = -sign * sy;

			float tipMx = sign * LEG_PIVOT_X_MODEL + LEG_LENGTH_MODEL * axisX;
			float tipMy = 15f + LEG_LENGTH_MODEL * axisY;
			float tipMz = LEG_PIVOT_Z_MODEL[slot] + LEG_LENGTH_MODEL * axisZ;

			float dx = DEPOT_X_MODEL - tipMx;
			float dy = DEPOT_Y_MODEL - tipMy;
			float dz = DEPOT_Z_MODEL - tipMz;
			float dLen = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			if (dLen < 1e-5f)
				continue;
			dx /= dLen;
			dy /= dLen;
			dz /= dLen;

			Quaternionf orientation = shortestArcFromDownY(dx, dy, dz);

			ms.pushPose();
			ms.translate(tipMx / 16f, tipMy / 16f, tipMz / 16f);
			ms.mulPose(orientation);
			ms.scale(MACHINE_SCALE, MACHINE_SCALE, MACHINE_SCALE);
			ms.translate(-0.5d, -1.0d, -0.5d);
			Minecraft.getInstance()
				.getBlockRenderer()
				.renderSingleBlock(machineState, ms, buffer, light, OverlayTexture.NO_OVERLAY,
					ModelData.EMPTY, null);
			renderMachineParts(kind, machineState, ms, buffer, light);
			ms.popPose();
		}
	}

	private static Quaternionf shortestArcFromDownY(float dx, float dy, float dz) {
		float cosA = -dy;
		if (cosA > 0.99999f)
			return new Quaternionf();
		if (cosA < -0.99999f)
			return new Quaternionf().setAngleAxis((float) Math.PI, 1f, 0f, 0f);
		float axX = -dz;
		float axZ = dx;
		float axLen = Mth.sqrt(axX * axX + axZ * axZ);
		axX /= axLen;
		axZ /= axLen;
		float angle = (float) Math.acos(Mth.clamp(cosA, -1f, 1f));
		return new Quaternionf().setAngleAxis(angle, axX, 0f, axZ);
	}

	private static BlockState machineStateFor(MachineKind kind) {
		BlockState state = kind.block().defaultBlockState();
		if (state.hasProperty(BlockStateProperties.FACING))
			state = state.setValue(BlockStateProperties.FACING, Direction.DOWN);
		return state;
	}

	private static void renderMachineParts(MachineKind kind, BlockState state, PoseStack ms, MultiBufferSource buffer,
		int light) {
		VertexConsumer solid = buffer.getBuffer(RenderType.solid());
		switch (kind) {
		case DEPLOYER -> {
			transformDeployerPart(CachedBuffers.partial(AllPartialModels.DEPLOYER_POLE, state), state, true)
				.light(light)
				.renderInto(ms, solid);
			transformDeployerPart(CachedBuffers.partial(AllPartialModels.DEPLOYER_HAND_POINTING, state), state, false)
				.light(light)
				.renderInto(ms, solid);
		}
		case PRESS -> {
			Direction pressFacing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			CachedBuffers.partialFacing(AllPartialModels.MECHANICAL_PRESS_HEAD, state, pressFacing)
				.light(light)
				.renderInto(ms, solid);
		}
		case SAW -> {
			Direction sawFacing = state.getValue(BlockStateProperties.FACING);
			boolean horizontalSaw = sawFacing.getAxis()
				.isHorizontal();
			PartialModel bladeModel =
				horizontalSaw ? AllPartialModels.SAW_BLADE_HORIZONTAL_INACTIVE : AllPartialModels.SAW_BLADE_VERTICAL_INACTIVE;
			SuperByteBuffer blade = CachedBuffers.partialFacing(bladeModel, state);
			if (!horizontalSaw && state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE))
				blade.rotateCentered(AngleHelper.rad(90), Direction.UP);
			blade.color(0xFFFFFF)
				.light(light)
				.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
		}
		case SPOUT -> {
			CachedBuffers.partial(AllPartialModels.SPOUT_TOP, state)
				.light(light)
				.renderInto(ms, solid);
			CachedBuffers.partial(AllPartialModels.SPOUT_MIDDLE, state)
				.light(light)
				.renderInto(ms, solid);
			CachedBuffers.partial(AllPartialModels.SPOUT_BOTTOM, state)
				.light(light)
				.renderInto(ms, solid);
		}
		}
	}

	private static SuperByteBuffer transformDeployerPart(SuperByteBuffer buffer, BlockState state,
		boolean axisDirectionMatters) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		float yRot = AngleHelper.horizontalAngle(facing);
		float xRot = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
		float zRot = axisDirectionMatters
			&& (state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE)
				^ facing.getAxis() == Direction.Axis.Z) ? 90 : 0;
		buffer.rotateCentered((float) (yRot / 180 * Math.PI), Direction.UP);
		buffer.rotateCentered((float) (xRot / 180 * Math.PI), Direction.EAST);
		buffer.rotateCentered((float) (zRot / 180 * Math.PI), Direction.SOUTH);
		return buffer;
	}

	private void prepareSpiderModel(RenderSpider spider, SpiderAssemblyTableBlockEntity be, float partialTicks) {
		ModelPart root = spiderModel.root();
		root.getAllParts().forEach(ModelPart::resetPose);

		spiderModel.setupAnim(spider, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

		int activeSlot = be.getActiveSlot();
		ModelPart activeLeg = getAnimatedLeg(root, activeSlot);
		if (activeLeg == null)
			return;

		float progress = be.getProcessingProgress(partialTicks);
		float bend = Mth.sin(progress * Mth.PI) * ACTIVE_LEG_BEND;
		boolean leftSide = activeSlot < 4;
		activeLeg.zRot += leftSide ? bend : -bend;
	}

	private static ModelPart getAnimatedLeg(ModelPart root, int slot) {
		return switch (slot) {
		case 0 -> root.getChild("left_front_leg");
		case 1 -> root.getChild("left_middle_front_leg");
		case 2 -> root.getChild("left_middle_hind_leg");
		case 3 -> root.getChild("left_hind_leg");
		case 4 -> root.getChild("right_front_leg");
		case 5 -> root.getChild("right_middle_front_leg");
		case 6 -> root.getChild("right_middle_hind_leg");
		case 7 -> root.getChild("right_hind_leg");
		default -> null;
		};
	}

	private RenderSpider getOrCreateSpider(Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return null;

		if (cachedSpider == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedSpider = new RenderSpider(clientLevel);
			cachedSpider.setNoAi(true);
			cachedSpider.setSilent(true);
		}

		return cachedSpider;
	}

	private static float yRotation(Direction facing) {
		return switch (facing) {
		case EAST -> 270;
		case SOUTH -> 180;
		case WEST -> 90;
		default -> 0;
		};
	}

	private static class RenderSpider extends Spider {

		private RenderSpider(ClientLevel level) {
			super(EntityType.SPIDER, level);
		}
	}
}

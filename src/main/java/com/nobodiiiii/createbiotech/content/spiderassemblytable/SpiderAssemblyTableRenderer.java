package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlockEntity.MachineKind;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.platform.ForgeCatnipServices;
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
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SpiderAssemblyTableRenderer extends KineticBlockEntityRenderer<SpiderAssemblyTableBlockEntity> {

	private static final ResourceLocation SPIDER_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/spider/spider.png");
	private static final float SPIDER_SCALE = 1.0f;
	private static final float SPIDER_Y_OFFSET = 0.5f + 15f / 16f * SPIDER_SCALE;
	private static final float ACTIVE_LEG_BEND = (float) Math.toRadians(45);
	private static final float MACHINE_SCALE = 0.4f;
	private static final float LEG_LENGTH_MODEL = 15.0f;
	private static final float LEG_PIVOT_X_MODEL = 4.0f;
	private static final int[] LEG_PIVOT_Z_MODEL = { -1, 0, 1, 2, -1, 0, 1, 2 };
	private static final float DEPOT_X_MODEL = 0f;
	private static final float DEPOT_Y_MODEL = 39f;
	private static final float DEPOT_Z_MODEL = 0f;
	private static final float SAW_BLADE_ROOT_OFFSET = 3f / 16f;
	private static final float VECTOR_EPSILON = 1e-5f;
	private static final float OUTER_LEG_OUTWARD_ROTATION_DEGREES = 11.75f;
	private static final float INNER_LEG_OUTWARD_ROTATION_DEGREES = 0f;
	private static final float JOINT_GEAR_SCALE = 0.22f;
	private static final float JOINT_GEAR_Y_OFFSET = 0f;
	private static final float JOINT_GEAR_PERPENDICULAR_TO_Y_DEGREES = 90f;

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
		renderLegMachines(be, partialTicks, ms, buffer, light);
		ms.popPose();
	}

	private void renderLegMachines(SpiderAssemblyTableBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light) {
		ModelPart root = spiderModel.root();
		ItemStackHandler inventory = be.getInventory();
		int activeSlot = be.getActiveSlot();
		float progress = be.getProcessingProgress(partialTicks);

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
			boolean isActive = (slot == activeSlot);
			float sign = leftSide ? 1f : -1f;

			float cz = Mth.cos(leg.zRot);
			float sz = Mth.sin(leg.zRot);
			float cy = Mth.cos(leg.yRot);
			float sy = Mth.sin(leg.yRot);

			float restBend = isActive ? Mth.sin(progress * Mth.PI) * ACTIVE_LEG_BEND : 0f;
			float restZRot = leg.zRot - (leftSide ? restBend : -restBend);
			float restCz = Mth.cos(restZRot);
			float restSz = Mth.sin(restZRot);

			float axisX = sign * cz * cy;
			float axisY = sign * sz * cy;
			float axisZ = -sign * sy;
			float restAxisX = sign * restCz * cy;
			float restAxisY = sign * restSz * cy;
			float restAxisZ = -sign * sy;

			float anchorLength = LEG_LENGTH_MODEL - 1f;
			float tipMx = sign * LEG_PIVOT_X_MODEL + anchorLength * axisX;
			float tipMy = 15f + anchorLength * axisY;
			float tipMz = LEG_PIVOT_Z_MODEL[slot] + anchorLength * axisZ;

			float perpX = -sz;
			float perpY = cz;
			float perpZ = 0f;
			float outwardRotationDegrees = isOuterLeg(slot)
				? OUTER_LEG_OUTWARD_ROTATION_DEGREES
				: INNER_LEG_OUTWARD_ROTATION_DEGREES;
			Vector3f adjustedPerp = rotateTowardLegAxis(perpX, perpY, perpZ, axisX, axisY, axisZ, outwardRotationDegrees);
			perpX = adjustedPerp.x;
			perpY = adjustedPerp.y;
			perpZ = adjustedPerp.z;

			float depotX = DEPOT_X_MODEL - tipMx;
			float depotY = DEPOT_Y_MODEL - tipMy;
			float depotZ = DEPOT_Z_MODEL - tipMz;
			float depotLen = Mth.sqrt(depotX * depotX + depotY * depotY + depotZ * depotZ);
			if (depotLen < 1e-5f)
				continue;
			depotX /= depotLen;
			depotY /= depotLen;
			depotZ /= depotLen;

			float swing = isActive ? Mth.sin(progress * Mth.PI) : 0f;
			float reachFactor = swing * swing;
			float dx = perpX + (depotX - perpX) * reachFactor;
			float dy = perpY + (depotY - perpY) * reachFactor;
			float dz = perpZ + (depotZ - perpZ) * reachFactor;
			float dLen = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			if (dLen < VECTOR_EPSILON)
				continue;
			dx /= dLen;
			dy /= dLen;
			dz /= dLen;

			Quaternionf orientation = armOrientation(dx, dy, dz, restAxisX, restAxisY, restAxisZ);

			ms.pushPose();
			ms.translate(tipMx / 16f, tipMy / 16f, tipMz / 16f);
			ms.mulPose(orientation);
			renderJointGear(ms, buffer, light);
			ms.scale(MACHINE_SCALE, MACHINE_SCALE, MACHINE_SCALE);

			if (kind == MachineKind.SPOUT) {
				ms.scale(0.25f, 1.5f, 0.25f);
				ms.translate(-0.5d, -1.0d, -0.5d);
				Minecraft.getInstance()
					.getBlockRenderer()
					.renderSingleBlock(machineState, ms, buffer, light, OverlayTexture.NO_OVERLAY,
						ModelData.EMPTY, null);
				renderMachineParts(be, slot, kind, machineState, ms, buffer, light, partialTicks, isActive);
			} else {
				ms.translate(-0.5d, -1.625d, -0.5d);
				renderMachineParts(be, slot, kind, machineState, ms, buffer, light, partialTicks, isActive);
			}
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

	private static Quaternionf armOrientation(float dx, float dy, float dz, float frontRefX, float frontRefY,
		float frontRefZ) {
		Quaternionf baseOrientation = shortestArcFromDownY(dx, dy, dz);

		float refDot = frontRefX * dx + frontRefY * dy + frontRefZ * dz;
		float frontX = frontRefX - dx * refDot;
		float frontY = frontRefY - dy * refDot;
		float frontZ = frontRefZ - dz * refDot;
		float frontLen = Mth.sqrt(frontX * frontX + frontY * frontY + frontZ * frontZ);

		if (frontLen < VECTOR_EPSILON) {
			frontX = 0f;
			frontY = 0f;
			frontZ = -1f;
			refDot = frontZ * dz;
			frontX -= dx * refDot;
			frontY -= dy * refDot;
			frontZ -= dz * refDot;
			frontLen = Mth.sqrt(frontX * frontX + frontY * frontY + frontZ * frontZ);
		}

		if (frontLen < VECTOR_EPSILON) {
			frontX = 1f;
			frontY = 0f;
			frontZ = 0f;
			refDot = frontX * dx;
			frontX -= dx * refDot;
			frontY -= dy * refDot;
			frontZ -= dz * refDot;
			frontLen = Mth.sqrt(frontX * frontX + frontY * frontY + frontZ * frontZ);
		}

		if (frontLen < VECTOR_EPSILON)
			return baseOrientation;

		frontX /= frontLen;
		frontY /= frontLen;
		frontZ /= frontLen;

		Vector3f currentFront = new Vector3f(0f, 0f, -1f).rotate(baseOrientation);
		float currentDotAxis = currentFront.x * dx + currentFront.y * dy + currentFront.z * dz;
		currentFront.x -= dx * currentDotAxis;
		currentFront.y -= dy * currentDotAxis;
		currentFront.z -= dz * currentDotAxis;
		float currentFrontLen = currentFront.length();
		if (currentFrontLen < VECTOR_EPSILON)
			return baseOrientation;
		currentFront.div(currentFrontLen);

		float dot = Mth.clamp(currentFront.x * frontX + currentFront.y * frontY + currentFront.z * frontZ, -1f, 1f);
		float crossX = currentFront.y * frontZ - currentFront.z * frontY;
		float crossY = currentFront.z * frontX - currentFront.x * frontZ;
		float crossZ = currentFront.x * frontY - currentFront.y * frontX;
		float signedSin = crossX * dx + crossY * dy + crossZ * dz;
		float twistAngle = (float) Math.atan2(signedSin, dot);
		return Math.abs(twistAngle) < VECTOR_EPSILON
			? baseOrientation
			: new Quaternionf().setAngleAxis(twistAngle, dx, dy, dz)
				.mul(baseOrientation);
	}

	private static boolean isOuterLeg(int slot) {
		return slot == 0 || slot == 3 || slot == 4 || slot == 7;
	}

	private static void renderJointGear(PoseStack ms, MultiBufferSource buffer, int light) {
		ms.pushPose();
		ms.translate(0d, JOINT_GEAR_Y_OFFSET, 0d);
		ms.mulPose(Axis.ZP.rotationDegrees(JOINT_GEAR_PERPENDICULAR_TO_Y_DEGREES));
		ms.scale(JOINT_GEAR_SCALE, JOINT_GEAR_SCALE, JOINT_GEAR_SCALE);
		ms.translate(-0.5d, -0.5d, -0.5d);
		CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, AllBlocks.COGWHEEL.getDefaultState())
			.light(light)
			.renderInto(ms, buffer.getBuffer(RenderType.solid()));
		ms.popPose();
	}

	private static Vector3f rotateTowardLegAxis(float dirX, float dirY, float dirZ, float legX, float legY, float legZ,
		float extraDegrees) {
		Vector3f direction = new Vector3f(dirX, dirY, dirZ);
		if (Math.abs(extraDegrees) < VECTOR_EPSILON)
			return direction;

		float hingeX = dirY * legZ - dirZ * legY;
		float hingeY = dirZ * legX - dirX * legZ;
		float hingeZ = dirX * legY - dirY * legX;
		float hingeLen = Mth.sqrt(hingeX * hingeX + hingeY * hingeY + hingeZ * hingeZ);
		if (hingeLen < VECTOR_EPSILON)
			return direction;

		hingeX /= hingeLen;
		hingeY /= hingeLen;
		hingeZ /= hingeLen;
		direction.rotate(new Quaternionf().setAngleAxis(AngleHelper.rad(extraDegrees), hingeX, hingeY, hingeZ));
		float rotatedLen = direction.length();
		if (rotatedLen < VECTOR_EPSILON)
			return new Vector3f(dirX, dirY, dirZ);
		return direction.div(rotatedLen);
	}

	private static BlockState machineStateFor(MachineKind kind) {
		BlockState state = kind.block().defaultBlockState();
		if (state.hasProperty(BlockStateProperties.FACING))
			state = state.setValue(BlockStateProperties.FACING, Direction.DOWN);
		return state;
	}

	private static BlockState deployerPoleState() {
		BlockState state = AllBlocks.DEPLOYER.getDefaultState();
		if (state.hasProperty(BlockStateProperties.FACING))
			state = state.setValue(BlockStateProperties.FACING, Direction.DOWN);
		return state;
	}

	private static void renderMachineParts(SpiderAssemblyTableBlockEntity be, int slot, MachineKind kind,
		BlockState state, PoseStack ms, MultiBufferSource buffer, int light, float partialTicks, boolean isActive) {
		VertexConsumer solid = buffer.getBuffer(RenderType.solid());
		ItemStackHandler inventory = be.getInventory();
		switch (kind) {
		case DEPLOYER -> {
			ItemStack heldItem =
				inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + slot);
			PartialModel handPose = heldItem.isEmpty()
				? AllPartialModels.DEPLOYER_HAND_POINTING
				: AllPartialModels.DEPLOYER_HAND_HOLDING;
			transformDeployerPart(CachedBuffers.partial(AllPartialModels.DEPLOYER_POLE, state), state, true)
				.light(light)
				.renderInto(ms, solid);
			transformDeployerPart(CachedBuffers.partial(handPose, state), state, false)
				.light(light)
				.renderInto(ms, solid);
			if (!heldItem.isEmpty())
				renderDeployerHeldItem(be, heldItem, ms, buffer, light);
		}
		case PRESS -> {
			BlockState poleState = deployerPoleState();
			transformDeployerPart(CachedBuffers.partial(AllPartialModels.DEPLOYER_POLE, poleState), poleState, true)
				.light(light)
				.renderInto(ms, solid);

			ms.pushPose();
			ms.translate(0.5d, 0d, 0.5d);
			ms.scale(0.9f, 1.0f, 0.9f);
			ms.translate(-0.5d, 0d, -0.5d);
			Direction pressFacing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			CachedBuffers.partialFacing(AllPartialModels.MECHANICAL_PRESS_HEAD, state, pressFacing)
				.translate(0, -0.55f, 0)
				.light(light)
				.renderInto(ms, solid);
			ms.popPose();
		}
		case SAW -> {
			BlockState poleState = deployerPoleState();
			transformDeployerPart(CachedBuffers.partial(AllPartialModels.DEPLOYER_POLE, poleState), poleState, true)
				.light(light)
				.renderInto(ms, solid);

			boolean axisAlongFirst = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
			Direction spinAxis = axisAlongFirst ? Direction.EAST : Direction.SOUTH;
			float spinAngle = isActive
				? AngleHelper.rad((AnimationTickHolder.getRenderTime(be.getLevel()) + partialTicks) * 24f)
				: 0f;
			renderSawBlade(state, ms, buffer, light, axisAlongFirst, spinAxis, spinAngle, 0f);
			ms.pushPose();
			ms.translate(0, -SAW_BLADE_ROOT_OFFSET * 2f, 0);
			renderSawBlade(state, ms, buffer, light, axisAlongFirst, spinAxis, spinAngle, AngleHelper.rad(180));
			ms.popPose();
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
			renderSpoutFluid(be.getFluidTank(slot), ms, buffer, light);
		}
		}
	}

	private static void renderSawBlade(BlockState state, PoseStack ms, MultiBufferSource buffer, int light,
		boolean axisAlongFirst, Direction spinAxis, float spinAngle, float offsetAngle) {
		SuperByteBuffer blade = CachedBuffers.partialFacing(AllPartialModels.SAW_BLADE_VERTICAL_INACTIVE, state);
		if (axisAlongFirst)
			blade.rotateCentered(AngleHelper.rad(90), Direction.UP);
		float totalAngle = spinAngle + offsetAngle;
		if (totalAngle != 0f)
			blade.rotateCentered(totalAngle, spinAxis);
		blade.color(0xFFFFFF)
			.light(light)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	private static void renderDeployerHeldItem(SpiderAssemblyTableBlockEntity be, ItemStack heldItem, PoseStack ms,
		MultiBufferSource buffer, int light) {
		ItemRenderer itemRenderer = Minecraft.getInstance()
			.getItemRenderer();
		BakedModel model = itemRenderer.getModel(heldItem, be.getLevel(), null, 0);
		ms.pushPose();
		ms.translate(0.5d, 0.5d, 0.5d);
		ms.mulPose(Axis.YP.rotationDegrees(180));
		ms.mulPose(Axis.XP.rotationDegrees(270));
		ms.translate(0, 0, -11f / 16f);
		boolean isBlockItem = (heldItem.getItem() instanceof BlockItem) && model.isGui3d();
		float scale = isBlockItem ? 0.75f - 1f / 64f : 0.5f;
		ms.scale(scale, scale, scale);
		itemRenderer.render(heldItem, ItemDisplayContext.FIXED, false, ms, buffer, light, OverlayTexture.NO_OVERLAY,
			model);
		ms.popPose();
	}

	private static void renderSpoutFluid(FluidTank tank, PoseStack ms, MultiBufferSource buffer, int light) {
		FluidStack fluid = tank.getFluid();
		if (fluid.isEmpty() || tank.getCapacity() <= 0)
			return;
		float level = Mth.clamp((float) tank.getFluidAmount() / tank.getCapacity(), 0f, 1f);
		if (level < 1e-3f)
			return;
		level = Math.max(level, 0.175f);
		float min = 2.5f / 16f;
		float max = min + 11f / 16f;
		float yOffset = (11f / 16f) * level;
		boolean top = fluid.getFluid()
			.getFluidType()
			.isLighterThanAir();
		ms.pushPose();
		if (!top)
			ms.translate(0, yOffset, 0);
		else
			ms.translate(0, max - min, 0);
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, min, min - yOffset, min, max, min, max, buffer, ms,
			light, false, true);
		ms.popPose();
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

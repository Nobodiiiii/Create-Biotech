package com.nobodiiiii.createbiotech.content.evokertank;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;

import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class EvokerTankRenderer implements BlockEntityRenderer<EvokerTankBlockEntity> {

	private static final ResourceLocation EVOKER_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/illager/evoker.png");
	private static final float EVOKER_SCALE = 0.7f;
	private static final float RIGHT_LEG_X_ROT = -1.4137167f;
	private static final float RIGHT_LEG_Y_ROT = 0.31415927f;
	private static final float RIGHT_LEG_Z_ROT = 0.07853982f;
	private static final float LEFT_LEG_X_ROT = -1.4137167f;
	private static final float LEFT_LEG_Y_ROT = -0.31415927f;
	private static final float LEFT_LEG_Z_ROT = -0.07853982f;

	private final BlockRenderDispatcher blockRenderer;
	private final IllagerModel<RenderEvoker> evokerModel;
	private final BlockState tankState;
	private RenderEvoker cachedEvoker;
	private ClientLevel cachedLevel;

	public EvokerTankRenderer(BlockEntityRendererProvider.Context context) {
		blockRenderer = context.getBlockRenderDispatcher();
		evokerModel = new IllagerModel<>(context.bakeLayer(ModelLayers.EVOKER));
		tankState = configureTankState(AllBlocks.FLUID_TANK.getDefaultState());
	}

	@Override
	public void render(EvokerTankBlockEntity blockEntity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		renderTank(poseStack, buffer, packedLight, packedOverlay);

		RenderEvoker evoker = getOrCreateEvoker(blockEntity.getLevel());
		if (evoker == null)
			return;

		prepareEvokerModel(evoker, blockEntity, partialTick);

		poseStack.pushPose();
		poseStack.translate(0.5d, 1.55d, 0.5d);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - blockEntity.getBlockState().getValue(EvokerTankBlock.FACING).toYRot()));
		poseStack.scale(-EVOKER_SCALE, -EVOKER_SCALE, EVOKER_SCALE);

		VertexConsumer consumer = buffer.getBuffer(evokerModel.renderType(EVOKER_TEXTURE));
		evokerModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f,
			1.0f);
		poseStack.popPose();
	}

	private void renderTank(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		blockRenderer.renderSingleBlock(tankState, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private void prepareEvokerModel(RenderEvoker evoker, EvokerTankBlockEntity blockEntity, float partialTick) {
		evoker.setCasting(blockEntity.isCastingSpell());

		ModelPart root = evokerModel.root();
		root.getAllParts().forEach(ModelPart::resetPose);
		evokerModel.setupAnim(evoker, 0.0f, 0.0f, blockEntity.getAnimationTime(partialTick), 0.0f, 0.0f);

		ModelPart head = root.getChild("head");
		ModelPart rightLeg = root.getChild("right_leg");
		ModelPart leftLeg = root.getChild("left_leg");

		head.xRot = 0.08726646f;
		rightLeg.xRot = RIGHT_LEG_X_ROT;
		rightLeg.yRot = RIGHT_LEG_Y_ROT;
		rightLeg.zRot = RIGHT_LEG_Z_ROT;
		leftLeg.xRot = LEFT_LEG_X_ROT;
		leftLeg.yRot = LEFT_LEG_Y_ROT;
		leftLeg.zRot = LEFT_LEG_Z_ROT;
	}

	private RenderEvoker getOrCreateEvoker(Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return null;

		if (cachedEvoker == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedEvoker = new RenderEvoker(clientLevel);
			if (cachedEvoker != null) {
				cachedEvoker.setNoAi(true);
				cachedEvoker.setSilent(true);
			}
		}

		if (cachedEvoker != null) {
			cachedEvoker.setYRot(0.0f);
			cachedEvoker.setYBodyRot(0.0f);
			cachedEvoker.yBodyRotO = 0.0f;
			cachedEvoker.yHeadRot = 0.0f;
			cachedEvoker.yHeadRotO = 0.0f;
		}

		return cachedEvoker;
	}

	private static BlockState configureTankState(BlockState state) {
		state = setBooleanProperty(state, "bottom", true);
		state = setBooleanProperty(state, "top", true);
		return setNamedProperty(state, "shape", "window");
	}

	private static BlockState setBooleanProperty(BlockState state, String propertyName, boolean value) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(propertyName))
				return setPropertyValue(state, property, value);
		}
		return state;
	}

	private static BlockState setNamedProperty(BlockState state, String propertyName, String valueName) {
		for (Property<?> property : state.getProperties()) {
			if (!property.getName().equals(propertyName))
				continue;
			for (Comparable<?> possibleValue : property.getPossibleValues()) {
				if (getValueName(property, possibleValue).equals(valueName))
					return setPropertyValue(state, property, possibleValue);
			}
		}
		return state;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static String getValueName(Property property, Comparable value) {
		return property.getName(value);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static BlockState setPropertyValue(BlockState state, Property property, Comparable value) {
		return state.setValue(property, value);
	}

	private static class RenderEvoker extends Evoker {

		private RenderEvoker(ClientLevel level) {
			super(EntityType.EVOKER, level);
		}

		private void setCasting(boolean casting) {
			setIsCastingSpell(casting ? SpellcasterIllager.IllagerSpell.FANGS : SpellcasterIllager.IllagerSpell.NONE);
		}
	}
}

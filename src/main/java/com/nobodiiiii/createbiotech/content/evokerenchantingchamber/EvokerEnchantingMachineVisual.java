package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class EvokerEnchantingMachineVisual
	extends AbstractBlockEntityVisual<EvokerEnchantingChamberBlockEntity>
	implements SimpleDynamicVisual {

	private final MachineCreatureVisualModel evoker;
	private final MachineCreatureVisualModel book;

	public EvokerEnchantingMachineVisual(VisualizationContext context,
		EvokerEnchantingChamberBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		evoker = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.evoker());
		book = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.book());
		updatePose(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updatePose(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		int light = computePackedLight();
		evoker.light(light);
		book.light(light);
	}

	@Override
	protected void _delete() {
		evoker.delete();
		book.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		evoker.collectCrumblingInstances(consumer);
		book.collectCrumblingInstances(consumer);
	}

	private void updatePose(float partialTick) {
		if (blockState.getValue(EvokerEnchantingChamberBlock.HALF) != DoubleBlockHalf.LOWER) {
			evoker.hide();
			book.hide();
			return;
		}
		Direction facing = blockState.getValue(EvokerEnchantingChamberBlock.FACING);
		EvokerEnchantingVisual.prepareModel(evoker.model(), blockEntity.isCastingSpell());
		double evokerX = 0.5 - facing.getStepX()
			* EvokerEnchantingChamberRenderer.EVOKER_BACK_OFFSET_FROM_CENTER;
		double evokerZ = 0.5 - facing.getStepZ()
			* EvokerEnchantingChamberRenderer.EVOKER_BACK_OFFSET_FROM_CENTER;
		PoseStack evokerPose = atBlockOrigin();
		evokerPose.translate(evokerX, EvokerEnchantingChamberRenderer.EVOKER_ROOT_Y, evokerZ);
		evokerPose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
		float scale = EvokerEnchantingChamberRenderer.EVOKER_SCALE;
		evokerPose.scale(-scale, -scale, scale);
		evoker.setTransform(evokerPose);

		float time = AnimationTickHolder.getRenderTime(blockEntity.getLevel());
		float bob = Mth.sin(time * EvokerEnchantingChamberRenderer.BOOK_BOB_SPEED)
			* EvokerEnchantingChamberRenderer.BOOK_BOB_AMPLITUDE;
		float pageFlutter = Mth.sin(time * 0.135f) * 0.05f;
		EvokerEnchantingChamberRenderer.prepareBookModel(book.model(), time, pageFlutter);
		PoseStack bookPose = atBlockOrigin();
		bookPose.translate(0.5 + facing.getStepX() * EvokerEnchantingChamberRenderer.BOOK_FRONT_OFFSET,
			EvokerEnchantingChamberRenderer.BOOK_BASE_Y + bob,
			0.5 + facing.getStepZ() * EvokerEnchantingChamberRenderer.BOOK_FRONT_OFFSET);
		bookPose.mulPose(Axis.YP.rotationDegrees((180f - facing.toYRot()) - 90f));
		bookPose.mulPose(Axis.ZP.rotationDegrees(EvokerEnchantingChamberRenderer.BOOK_Z_ROTATION));
		book.setTransform(bookPose);
	}

	private PoseStack atBlockOrigin() {
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ());
		return pose;
	}
}

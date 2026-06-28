package com.nobodiiiii.createbiotech.content.buttercat.block;

import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModPartialModels;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.function.Consumer;

public class ButterCatEngineVisual extends ShaftVisual<ButterCatEngineBlockEntity> implements SimpleDynamicVisual {
    private final RotatingInstance cat;
    private final RotatingInstance bread;
    private final RotatingInstance rope;
    private final RotatingInstance butter;

    private final Quaternionf blockOrientation;

    private int catModelCode;
    private int currentButterLevel;
    private float lastAttachmentSpeed = Float.NaN;
    private float lastAttachmentRotationOffset = Float.NaN;


    public ButterCatEngineVisual(VisualizationContext context, ButterCatEngineBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        Direction facing = blockState.getValue(ButterCatEngineBlock.HORIZONTAL_FACING);
        blockOrientation =  Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(facing));

        PartialModel catModel = blockEntity.getCatModel();
        catModelCode = catModel.hashCode();
        cat = createAttachmentInstance(catModel);

        bread = createAttachmentInstance(ModPartialModels.BCE_EMPTY);

        rope = createAttachmentInstance(ModPartialModels.BCE_EMPTY);

        butter = createAttachmentInstance(ModPartialModels.BCE_EMPTY);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        updateModels();
        updateAttachmentKinetics(false);
    }

    @Override
    public void update(float pt) {
        super.update(pt);
        updateAttachmentKinetics(true);
    }

    private RotatingInstance createAttachmentInstance(PartialModel model) {
        RotatingInstance instance =
            instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(model)).createInstance();
        setupAttachmentInstance(instance);
        return instance;
    }

    private void setupAttachmentInstance(RotatingInstance instance) {
        instance.rotation.set(blockOrientation);
        instance.setPosition(getVisualPosition());
        updateAttachmentKinetics(instance);
        instance.setChanged();
    }

    private void updateAttachmentKinetics(boolean force) {
        float speed = blockEntity.getSpeed();
        float rotationOffset = ButterCatEngineRenderer.getAttachmentRotationOffsetForBe(blockEntity,
            blockEntity.getBlockPos(), rotationAxis());
        if (!force && Mth.equal(lastAttachmentSpeed, speed) && Mth.equal(lastAttachmentRotationOffset, rotationOffset))
            return;

        lastAttachmentSpeed = speed;
        lastAttachmentRotationOffset = rotationOffset;
        updateAttachmentKinetics(cat);
        updateAttachmentKinetics(bread);
        updateAttachmentKinetics(rope);
        updateAttachmentKinetics(butter);
    }

    private void updateAttachmentKinetics(RotatingInstance instance) {
        instance.setRotationAxis(rotationAxis())
            .setRotationalSpeed(blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER)
            .setRotationOffset(ButterCatEngineRenderer.getAttachmentRotationOffsetForBe(blockEntity,
                blockEntity.getBlockPos(), rotationAxis()))
            .setChanged();
    }
    private void updateModels() {

        PartialModel newCatModel = blockEntity.getCatModel();
        if (newCatModel.hashCode() != catModelCode) {
            catModelCode = newCatModel.hashCode();
            instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(newCatModel)).stealInstance(cat);
            setupAttachmentInstance(cat);
        }

        if(blockEntity.hasBread()){
            instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(blockEntity.getBreadModel())).stealInstance(bread);
            setupAttachmentInstance(bread);

            instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(blockEntity.getRopeModel())).stealInstance(rope);
            setupAttachmentInstance(rope);
        }
        if(currentButterLevel != blockEntity.getButterLevel()){
            currentButterLevel = blockEntity.getButterLevel();
            instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(blockEntity.getButterModel())).stealInstance(butter);
            setupAttachmentInstance(butter);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight(cat);
        relight(butter);
        relight(bread);
        relight(rope);
    }
    @Override
    protected void _delete() {
        super._delete();
        cat.delete();
        bread.delete();
        rope.delete();
        butter.delete();
    }


    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(bread);
        consumer.accept(rope);
    }

    @Override
    protected Direction.Axis rotationAxis() {
        return super.rotationAxis();
    }
}


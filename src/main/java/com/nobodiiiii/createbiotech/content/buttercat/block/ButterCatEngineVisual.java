package com.nobodiiiii.createbiotech.content.buttercat.block;

import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;

import java.util.function.Consumer;

public class ButterCatEngineVisual extends ShaftVisual<ButterCatEngineBlockEntity> implements SimpleDynamicVisual {
    private final OrientedInstance cat;
    private final OrientedInstance bread;
    private final OrientedInstance butter;

    private final Quaternionf blockOrientation;
    private final Axis rotationAxis;

    private int catModelCode;
    private int currentButterLevel;


    public ButterCatEngineVisual(VisualizationContext context, ButterCatEngineBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        Direction facing = blockState.getValue(ButterCatEngineBlock.HORIZONTAL_FACING);
        blockOrientation =  Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(facing));
        rotationAxis = Axis.of(Direction.get(Direction.AxisDirection.POSITIVE, rotationAxis()).step());

        PartialModel catModel = blockEntity.getCatModel();
        catModelCode = catModel.hashCode();
        cat = instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(catModel)).createInstance();
        cat.position(getVisualPosition()).rotation(blockOrientation).setChanged();

        bread = instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(ModPartialModels.BCE_EMPTY)).createInstance();
        bread.position(getVisualPosition()).rotation(blockOrientation).setChanged();

        butter = instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(ModPartialModels.BCE_EMPTY)).createInstance();
        butter.position(getVisualPosition()).rotation(blockOrientation).setChanged();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        updateModels();

        if (!isVisible(ctx.frustum()) || doDistanceLimitThisFrame(ctx)) return;
        rotateModels(ctx.partialTick());
    }

    private void rotateModels(float pt) {

        float interpolatedAngle = blockEntity.getInterpolatedAngle(pt - 1);

        Quaternionf dynamicRotation = rotationAxis.rotationDegrees(interpolatedAngle);

        dynamicRotation.mul(blockOrientation);

        cat.rotation(dynamicRotation).setChanged();
        bread.rotation(dynamicRotation).setChanged();
        butter.rotation(dynamicRotation).setChanged();
    }
    private void updateModels() {

        PartialModel newCatModel = blockEntity.getCatModel();
        if (newCatModel.hashCode() != catModelCode) {
            catModelCode = newCatModel.hashCode();
            instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(newCatModel)).stealInstance(cat);
            cat.position(getVisualPosition()).rotation(blockOrientation).setChanged();
        }

        if(blockEntity.hasBread()){
            instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(blockEntity.getBreadModel())).stealInstance(bread);
            bread.position(getVisualPosition()).rotation(blockOrientation).setChanged();

        }
        if(currentButterLevel != blockEntity.getButterLevel()){
            currentButterLevel = blockEntity.getButterLevel();
            instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(blockEntity.getButterModel())).stealInstance(butter);
            butter.position(getVisualPosition()).rotation(blockOrientation).setChanged();
        }
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight(cat);
        relight(butter);
        relight(bread);
    }
    @Override
    protected void _delete() {
        super._delete();
        cat.delete();
        bread.delete();
        butter.delete();
    }


    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(bread);
    }

    @Override
    protected Direction.Axis rotationAxis() {
        return super.rotationAxis();
    }
}


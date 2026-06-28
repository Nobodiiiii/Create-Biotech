package com.nobodiiiii.createbiotech.content.buttercat.block;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModPartialModels;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;


public class  ButterCatEngineBlockEntity  extends GeneratingKineticBlockEntity {
    private static final int OVERFLOW_BUTTER_COUNT = 1;

    protected ResourceKey<CatVariant> catVariant = CatVariant.TABBY;
    protected boolean infinite =false;
    protected int butterCount = 0;
    protected int overflowCount = 0;
    protected int cd = 0;
    protected float clientAttachmentRotationOffset;
    protected boolean clientAttachmentRotationOffsetInitialized;

    public ButterCatEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }
    ///================getter/setter================
    public void addButterCount(int count) {
        if (count == 0 || infinite) return;
        int total = Math.max(0, getTotalCount() + count);
        int maxStoredButterCount = getMaxStoredButterCount();
        if (total > maxStoredButterCount)
            total = maxStoredButterCount;
        butterCount = Math.min(total, getMaxButterCount());
        overflowCount = total - butterCount;
        updateGeneratedRotation();
    }
    public int getButterCount() {
        return butterCount;
    }
    public int getTotalCount() {
        return butterCount + overflowCount;
    }

    public ResourceKey<CatVariant> getCatVariant() {
        return catVariant;
    }

    public boolean hasBread(){
        return getBlockState() != null && ModBlocks.BUTTER_CAT_ENGINE.has(getBlockState());
    }

    public void setInfinite(boolean bool) {
        infinite = bool;
        overflowCount = 0;
        if(bool)
            butterCount = getMaxButterCount();
        else
            butterCount = 0;
        updateGeneratedRotation();

    }
    public boolean isInfinite(){
        return infinite;
    }
    public boolean isFull() {
        return isInfinite() || getTotalCount() >= getMaxStoredButterCount();
    }

    public boolean canAcceptButter(int count) {
        return !isInfinite() && count > 0 && getTotalCount() + count <= getMaxStoredButterCount();
    }

    public int getCd(boolean remaining) {
        return remaining ? Math.max(0, getButterDecayTicks() - cd) : cd;
    }

    public void tick(){
        super.tick();
        if(isInfinite())return;
        if(butterCount > 0){
            cd++;
        }
        if(cd >= getButterDecayTicks() ){
            if(butterCount > 0) butterCount --;
            if(overflowCount > 0){
                butterCount ++;
                overflowCount--;
            }
            cd = 0;
            updateGeneratedRotation();
        }
    }
    ///================ speed ================
    //getSpeed() 返回最终速度
    //应力生产速度，黄油输入16个后达到最大速度，超出部分继续累加在应力系数上
    @Override
    public float getGeneratedSpeed() {
        float maxGeneratedRpm = (float) CBConfigs.SERVER.butterCat.maxGeneratedRpm.get().doubleValue();
        if (isInfinite()) return getDirectionalGeneratedSpeed(maxGeneratedRpm);
        int butterForMaxRpm = Math.max(1, CBConfigs.SERVER.butterCat.butterForMaxRpm.get());
        float speed = butterCount <= butterForMaxRpm
            ? butterCount * (float) CBConfigs.SERVER.butterCat.rpmPerButter.get().doubleValue()
            : maxGeneratedRpm;
        speed = Math.min(speed, maxGeneratedRpm);
        return getDirectionalGeneratedSpeed(speed);
    }
    //应力系数
    @Override
    public float calculateAddedStressCapacity() {
        float speed = Math.abs(getGeneratedSpeed());
        float capacity = 0;
        if (speed > 0) {
            float maxStressCapacity = getMaxStressCapacity();
            float stressPerRpm = getStressCapacityPerRpm();
            capacity = Math.min(maxStressCapacity, stressPerRpm * speed) / speed;
        }
        this.lastCapacityProvided = capacity;
        return capacity;
    }
    protected float getDirectionalGeneratedSpeed(float speed) {
        return convertToDirection(speed, getBlockState().getValue(HORIZONTAL_FACING));
    }

    float getAttachmentRotationOffset() {
        return clientAttachmentRotationOffset;
    }

    ///================serialize================
    @Override
    protected void write(CompoundTag compound,boolean clientPacket) {
        super.write(compound,  clientPacket);

        compound.putBoolean("infinite",infinite);
        compound.putInt("cd",cd);
        compound.putInt("butterCount",butterCount);
        compound.putInt("overflowCount",overflowCount);

        compound.putString("catVariant",catVariant.location().toString());
    }
    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        float previousSpeed = getSpeed();
        super.read(compound,clientPacket);

        if(compound.contains("infinite")) infinite = compound.getBoolean("infinite");
        if(compound.contains("cd")) cd = compound.getInt("cd");
        if(compound.contains("butterCount")) butterCount = compound.getInt("butterCount");
        if(compound.contains("overflowCount")) overflowCount = compound.getInt("overflowCount");

        if (compound.contains("catVariant"))
            catVariant = ResourceKey.create(Registries.CAT_VARIANT, new ResourceLocation(compound.getString("catVariant")));

        normalizeStoredButter();

        if (level != null && level.isClientSide)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ButterCatEngineClientRotation.sync(this, previousSpeed, clientPacket));

    }
    ///================get models================
    public int getButterLevel(){
        if(butterCount==0) return 0;
        else if(butterCount>8 && butterCount<=16) return 2;
        else if(butterCount>16) return 3;
        return 1;
    }
    public PartialModel getCatModel() {
        return ModPartialModels.getCatModel(catVariant);
    }
    public PartialModel getButterModel() {
        if(isInfinite()) return ModPartialModels.BCE_SUPER_BUTTER;
        return switch (getButterLevel()) {
            case 0 -> ModPartialModels.BCE_EMPTY;
            case 2 -> ModPartialModels.BCE_BUTTER;
            case 3 -> ModPartialModels.BCE_BUTTER_BIG;
            default -> ModPartialModels.BCE_BUTTER_SMALL;
        };
    }
    public PartialModel getBreadModel() {
        return hasBread()? ModPartialModels.BCE_BREAD:ModPartialModels.BCE_EMPTY;
    }
    public PartialModel getRopeModel() {
        return hasBread() ? ModPartialModels.BCE_ROPE : ModPartialModels.BCE_EMPTY;
    }
    public int getMaxButterCount(){
        return CBConfigs.SERVER.butterCat.maxButterCount.get();
    }
    public int getMaxStoredButterCount() {
        return getMaxButterCount() + OVERFLOW_BUTTER_COUNT;
    }
    private int getButterDecayTicks() {
        return CBConfigs.SERVER.butterCat.butterDecayTicks.get();
    }
    public float getStressCapacityPerRpm() {
        return CBConfigs.SERVER.butterCat.stressCapacityPerRpm.get().floatValue();
    }
    public float getMaxStressCapacity() {
        return CBConfigs.SERVER.butterCat.maxStressCapacity.get().floatValue();
    }

    private void normalizeStoredButter() {
        if (infinite) {
            butterCount = getMaxButterCount();
            overflowCount = 0;
            return;
        }

        int total = Math.max(0, butterCount) + Math.max(0, overflowCount);
        int maxStoredButterCount = getMaxStoredButterCount();
        if (total > maxStoredButterCount)
            total = maxStoredButterCount;
        butterCount = Math.min(total, getMaxButterCount());
        overflowCount = total - butterCount;
    }
}


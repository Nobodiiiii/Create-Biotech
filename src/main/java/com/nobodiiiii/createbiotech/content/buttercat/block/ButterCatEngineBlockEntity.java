package com.nobodiiiii.createbiotech.content.buttercat.block;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModConfigs;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;


public class  ButterCatEngineBlockEntity  extends GeneratingKineticBlockEntity {
    protected ResourceKey<CatVariant> catVariant = CatVariant.TABBY;
    protected boolean bread =false;
    protected boolean infinite =false;
    protected int butterCount = 0;
    protected int overflowCount = 0;
    protected int cd = 0;
    protected Cat cat;
    protected float clientVisualRotationOffset;
    protected boolean clientVisualRotationOffsetInitialized;

    public ButterCatEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }
    ///================getter/setter================
    public void addButterCount(int count) {
        this.butterCount += count;
        if (this.butterCount < 0) this.butterCount = 0;
        if (this.butterCount > getMaxButterCount()) {
            this.overflowCount += this.butterCount - getMaxButterCount();
            this.butterCount = getMaxButterCount();
        }

        updateGeneratedRotation();
    }
    public int getButterCount() {
        return butterCount;
    }
    public int getTotalCount() {
        return butterCount + overflowCount;
    }

    public void setCat(Cat cat) {
        this.cat = cat;
        catVariant= BuiltInRegistries.CAT_VARIANT.getResourceKey(cat.getVariant()).get();
    }

    public Cat getCat(Level level) {
        if(cat == null) cat =EntityType.CAT.create(level);
        cat.setVariant(BuiltInRegistries.CAT_VARIANT.get(catVariant));
        cat.setPos(getBlockPos().getCenter());
        if (cat.isLeashed()) {
            cat.dropLeash(true, false);
        }
        cat.revive();
        return cat;
    }
    public void addBread(){
        bread = true;
        updateGeneratedRotation();
    }
    public boolean hasBread(){
        return bread;
    }

    public void setInfinite(boolean bool) {
        bread = bool;
        infinite = bool;
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
        return overflowCount !=0 || isInfinite();
    }

    public int getCd(boolean remaining) {
        return remaining ? 200 - cd : cd;
    }

    public void tick(){
        super.tick();
        if(isInfinite())return;
        if(butterCount > 0){
            cd++;
        }
        if(cd > 200 ){
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
        if (isInfinite()) return getDirectionalGeneratedSpeed(256);
        int speed = butterCount <= 16  ? butterCount * 16 : 256;
        return getDirectionalGeneratedSpeed(speed);
    }
    //应力系数
    @Override
    public float calculateAddedStressCapacity() {
        float capacity = this.butterCount * 2;
        if(isInfinite()) capacity = getMaxInfiniteOutput();
        this.lastCapacityProvided = capacity;
        return capacity;
    }
    protected float getDirectionalGeneratedSpeed(float speed) {
        return convertToDirection(speed, getBlockState().getValue(HORIZONTAL_FACING));
    }

    @Override
    public int getRotationAngleOffset(Direction.Axis axis) {
        return super.getRotationAngleOffset(axis) + Math.round(clientVisualRotationOffset);
    }
    ///================serialize================
    @Override
    protected void write(CompoundTag compound,boolean clientPacket) {
        super.write(compound,  clientPacket);

        compound.putBoolean("infinite",infinite);
        compound.putBoolean("bread",bread);
        compound.putInt("cd",cd);
        compound.putInt("butterCount",butterCount);
        compound.putInt("overflowCount",overflowCount);

        compound.putString("catVariant",catVariant.location().toString());
    }
    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        float previousSpeed = getTheoreticalSpeed();
        super.read(compound,clientPacket);

        if(compound.contains("infinite")) infinite = compound.getBoolean("infinite");
        if(compound.contains("bread")) bread = compound.getBoolean("bread");
        if(compound.contains("cd")) cd = compound.getInt("cd");
        if(compound.contains("butterCount")) butterCount = compound.getInt("butterCount");
        if(compound.contains("overflowCount")) overflowCount = compound.getInt("overflowCount");

        if (compound.contains("catVariant"))
            catVariant = ResourceKey.create(Registries.CAT_VARIANT, new ResourceLocation(compound.getString("catVariant")));

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
        return ModConfigs.COMMON.maxButterCount.get();
    }
    public int getMaxInfiniteOutput(){
        return ModConfigs.COMMON.maxInfiniteCapacity.get();
    }
}


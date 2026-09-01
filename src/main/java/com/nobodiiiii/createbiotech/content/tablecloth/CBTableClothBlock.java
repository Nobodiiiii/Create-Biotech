package com.nobodiiiii.createbiotech.content.tablecloth;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlock;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlock;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Create table cloth backed by Create: Biotech's compatible block entity type.
 *
 * <p>Create's own table-cloth block entity type only accepts Create's registered
 * table cloth blocks, so addon variants need their own valid block entity type.</p>
 */
public class CBTableClothBlock extends TableClothBlock {
	private final boolean biotechSurface;

	public CBTableClothBlock(Properties properties, String type) {
		super(properties, type);
		biotechSurface = "biotech".equals(type);
	}

	public boolean isBiotechSurface() {
		return biotechSurface;
	}

	@Override
	public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
		if (side.getAxis().isHorizontal() && biotechSurface
			&& adjacentState.getBlock() instanceof SurgicalTableBlock)
			return true;
		return super.skipRendering(state, adjacentState, side);
	}

	@Override
	public BlockEntityType<? extends TableClothBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.TABLE_CLOTH.get();
	}
}

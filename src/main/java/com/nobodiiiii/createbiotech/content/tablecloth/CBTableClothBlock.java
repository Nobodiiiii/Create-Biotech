package com.nobodiiiii.createbiotech.content.tablecloth;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlock;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A Create table cloth backed by Create: Biotech's compatible block entity type.
 *
 * <p>Create's own table-cloth block entity type only accepts Create's registered
 * table cloth blocks, so addon variants need their own valid block entity type.</p>
 */
public class CBTableClothBlock extends TableClothBlock {

	public CBTableClothBlock(Properties properties, String type) {
		super(properties, type);
	}

	@Override
	public BlockEntityType<? extends TableClothBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.TABLE_CLOTH.get();
	}
}

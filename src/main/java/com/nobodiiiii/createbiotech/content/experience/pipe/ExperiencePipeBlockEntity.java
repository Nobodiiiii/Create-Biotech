package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.util.List;

import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ExperiencePipeBlockEntity extends SmartBlockEntity implements TransformableBlockEntity {

	public ExperiencePipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new StandardPipeTransportBehaviour(this));
		behaviours.add(new BracketedBlockEntityBehaviour(this, this::canHaveBracket));
	}

	@Override
	public void transform(BlockEntity blockEntity, StructureTransform transform) {
		BracketedBlockEntityBehaviour bracket = getBehaviour(BracketedBlockEntityBehaviour.TYPE);
		if (bracket != null)
			bracket.transformBracket(transform);
	}

	private boolean canHaveBracket(BlockState state) {
		return !(state.getBlock() instanceof ExperienceEncasedPipeBlock);
	}

	class StandardPipeTransportBehaviour extends ExperienceTransportBehaviour {

		public StandardPipeTransportBehaviour(SmartBlockEntity blockEntity) {
			super(blockEntity);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return (state.getBlock() instanceof ExperiencePipeBlock || state.getBlock() instanceof ExperienceEncasedPipeBlock)
				&& state.getValue(ExperiencePipeBlock.PROPERTY_BY_DIRECTION.get(direction));
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
			Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
			BlockPos offsetPos = pos.relative(direction);
			BlockState otherState = world.getBlockState(offsetPos);

			if (state.getBlock() instanceof ExperienceEncasedPipeBlock && attachment != AttachmentTypes.DRAIN)
				return AttachmentTypes.NONE;

			if (attachment == AttachmentTypes.RIM) {
				if (!ExperiencePipeBlock.isPipe(otherState)) {
					ExperienceTransportBehaviour pipeBehaviour =
						BlockEntityBehaviour.get(world, offsetPos, ExperienceTransportBehaviour.TYPE);
					if (pipeBehaviour != null && pipeBehaviour.canHaveFlowToward(otherState, direction.getOpposite()))
						return AttachmentTypes.DETAILED_CONNECTION;
				}

				if (!ExperiencePipeBlock.shouldDrawRim(world, pos, state, direction))
					return ExperiencePropagator.getStraightPipeAxis(state) == direction.getAxis()
						? AttachmentTypes.CONNECTION
						: AttachmentTypes.DETAILED_CONNECTION;
			}

			if (attachment == AttachmentTypes.NONE
				&& state.getValue(ExperiencePipeBlock.PROPERTY_BY_DIRECTION.get(direction)))
				return AttachmentTypes.DETAILED_CONNECTION;

			return attachment;
		}
	}
}

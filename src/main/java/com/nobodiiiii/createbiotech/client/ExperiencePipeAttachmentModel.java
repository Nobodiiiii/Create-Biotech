package com.nobodiiiii.createbiotech.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperiencePipeBlock;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperienceTransportBehaviour;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperienceTransportBehaviour.AttachmentTypes;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperienceTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;

import net.createmod.catnip.data.Iterate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelData.Builder;
import net.minecraftforge.client.model.data.ModelProperty;

public class ExperiencePipeAttachmentModel extends BakedModelWrapperWithData {

	private static final ModelProperty<PipeModelData> PIPE_PROPERTY = new ModelProperty<>();
	private static final ResourceLocation CASING_MODEL = CreateBiotech.asResource("block/experience_pipe/casing");

	private final boolean ao;

	public static ExperiencePipeAttachmentModel withAO(BakedModel template) {
		return new ExperiencePipeAttachmentModel(template, true);
	}

	public ExperiencePipeAttachmentModel(BakedModel template, boolean ao) {
		super(template);
		this.ao = ao;
	}

	@Override
	protected Builder gatherModelData(Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state,
		ModelData blockEntityData) {
		PipeModelData data = new PipeModelData();
		ExperienceTransportBehaviour transport = BlockEntityBehaviour.get(world, pos, ExperienceTransportBehaviour.TYPE);
		BracketedBlockEntityBehaviour bracket = BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);

		if (transport != null)
			for (Direction direction : Iterate.directions)
				data.putAttachment(direction, transport.getRenderedRimAttachment(world, pos, state, direction));
		if (bracket != null)
			data.putBracket(bracket.getBracket());

		data.setCasing(ExperiencePipeBlock.shouldDrawCasing(world, pos, state));
		return builder.with(PIPE_PROPERTY, data);
	}

	@Override
	public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
		@NotNull ModelData data) {
		List<ChunkRenderTypeSet> sets = new ArrayList<>();
		sets.add(super.getRenderTypes(state, rand, data));
		sets.add(getModel(CASING_MODEL).getRenderTypes(state, rand, data));

		if (data.has(PIPE_PROPERTY)) {
			PipeModelData pipeData = data.get(PIPE_PROPERTY);
			for (Direction direction : Iterate.directions) {
				AttachmentTypes type = pipeData.getAttachment(direction);
				for (ComponentPartials partial : type.partials)
					sets.add(getModel(modelFor(partial, direction)).getRenderTypes(state, rand, data));
			}
		}

		return ChunkRenderTypeSet.union(sets);
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
		RenderType renderType) {
		List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
		if (!data.has(PIPE_PROPERTY))
			return quads;

		PipeModelData pipeData = data.get(PIPE_PROPERTY);
		quads = new ArrayList<>(quads);
		addQuads(quads, state, side, rand, data, pipeData, renderType);
		return quads;
	}

	@Override
	public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
		return ao;
	}

	@Override
	public boolean useAmbientOcclusion(BlockState state) {
		return ao;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return ao;
	}

	private void addQuads(List<BakedQuad> quads, BlockState state, Direction side, RandomSource rand, ModelData data,
		PipeModelData pipeData, RenderType renderType) {
		BakedModel bracket = pipeData.getBracket();
		if (bracket != null)
			quads.addAll(bracket.getQuads(state, side, rand, data, renderType));

		for (Direction direction : Iterate.directions) {
			AttachmentTypes type = pipeData.getAttachment(direction);
			for (ComponentPartials partial : type.partials)
				quads.addAll(getModel(modelFor(partial, direction)).getQuads(state, side, rand, data, renderType));
		}

		if (pipeData.isCasing())
			quads.addAll(getModel(CASING_MODEL).getQuads(state, side, rand, data, renderType));
	}

	private static BakedModel getModel(ResourceLocation modelLocation) {
		return Minecraft.getInstance().getModelManager().getModel(modelLocation);
	}

	private static ResourceLocation modelFor(ComponentPartials partial, Direction direction) {
		String folder = switch (partial) {
			case CONNECTION -> "connection";
			case RIM_CONNECTOR -> "rim_connector";
			case RIM -> "rim";
			case DRAIN -> "drain";
		};
		return CreateBiotech.asResource("block/experience_pipe/" + folder + "/" + direction.getName());
	}

	private static class PipeModelData {
		private final AttachmentTypes[] attachments;
		private boolean casing;
		private BakedModel bracket;

		private PipeModelData() {
			attachments = new AttachmentTypes[6];
			Arrays.fill(attachments, AttachmentTypes.NONE);
		}

		private void putAttachment(Direction face, AttachmentTypes attachment) {
			attachments[face.get3DDataValue()] = attachment;
		}

		private AttachmentTypes getAttachment(Direction face) {
			return attachments[face.get3DDataValue()];
		}

		private void putBracket(BlockState state) {
			if (state != null)
				bracket = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
		}

		private BakedModel getBracket() {
			return bracket;
		}

		private void setCasing(boolean casing) {
			this.casing = casing;
		}

		private boolean isCasing() {
			return casing;
		}
	}
}

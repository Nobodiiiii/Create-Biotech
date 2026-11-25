package com.nobodiiiii.createbiotech.content.surgery;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public record SurgicalToolSource(GlobalPos location, UUID kitId) {
	public static final Codec<SurgicalToolSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		GlobalPos.CODEC.fieldOf("location").forGetter(SurgicalToolSource::location),
		UUIDUtil.CODEC.fieldOf("kit_id").forGetter(SurgicalToolSource::kitId)
	).apply(instance, SurgicalToolSource::new));

	public static final StreamCodec<ByteBuf, SurgicalToolSource> STREAM_CODEC = StreamCodec.composite(
		GlobalPos.STREAM_CODEC, SurgicalToolSource::location,
		UUIDUtil.STREAM_CODEC, SurgicalToolSource::kitId,
		SurgicalToolSource::new);

	boolean isAvailable(ServerLevel currentLevel) {
		ServerLevel sourceLevel = currentLevel.getServer().getLevel(location.dimension());
		BlockPos pos = location.pos();
		if (sourceLevel == null || !sourceLevel.isInWorldBounds(pos))
			return false;
		LevelChunk chunk = sourceLevel.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ()));
		return chunk == null || chunk.getBlockEntity(pos) instanceof SurgicalKitBlockEntity kit && kit.matchesSource(kitId);
	}
}

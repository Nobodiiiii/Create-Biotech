package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import java.util.Locale;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;

import org.joml.Vector3f;

/** Carries the cone wave's axis, reach and per-particle spawn delay to the client. */
public record SonicConeWaveParticleOption(Vector3f direction, float range, int delay) implements ParticleOptions {

	public static final Codec<SonicConeWaveParticleOption> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			ExtraCodecs.VECTOR3F.fieldOf("direction").forGetter(SonicConeWaveParticleOption::direction),
			Codec.FLOAT.fieldOf("range").forGetter(SonicConeWaveParticleOption::range),
			Codec.INT.fieldOf("delay").forGetter(SonicConeWaveParticleOption::delay))
			.apply(instance, SonicConeWaveParticleOption::new));

	@SuppressWarnings("deprecation")
	public static final ParticleOptions.Deserializer<SonicConeWaveParticleOption> DESERIALIZER =
		new ParticleOptions.Deserializer<>() {
			@Override
			public SonicConeWaveParticleOption fromCommand(ParticleType<SonicConeWaveParticleOption> type,
				StringReader reader) throws CommandSyntaxException {
				reader.expect(' ');
				float x = reader.readFloat();
				reader.expect(' ');
				float y = reader.readFloat();
				reader.expect(' ');
				float z = reader.readFloat();
				reader.expect(' ');
				float range = reader.readFloat();
				reader.expect(' ');
				int delay = reader.readInt();
				return new SonicConeWaveParticleOption(new Vector3f(x, y, z), range, delay);
			}

			@Override
			public SonicConeWaveParticleOption fromNetwork(ParticleType<SonicConeWaveParticleOption> type,
				FriendlyByteBuf buffer) {
				return new SonicConeWaveParticleOption(
					new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()),
					buffer.readFloat(), buffer.readVarInt());
			}
		};

	public SonicConeWaveParticleOption {
		direction = new Vector3f(direction);
		range = Math.max(1.0f, range);
		delay = Math.max(0, delay);
	}

	@Override
	public ParticleType<?> getType() {
		return CBParticleTypes.SONIC_CONE_WAVE.get();
	}

	@Override
	public void writeToNetwork(FriendlyByteBuf buffer) {
		buffer.writeFloat(direction.x());
		buffer.writeFloat(direction.y());
		buffer.writeFloat(direction.z());
		buffer.writeFloat(range);
		buffer.writeVarInt(delay);
	}

	@Override
	public String writeToString() {
		return String.format(Locale.ROOT, "%s %.2f %.2f %.2f %.2f %d",
			BuiltInRegistries.PARTICLE_TYPE.getKey(getType()),
			direction.x(), direction.y(), direction.z(), range, delay);
	}
}

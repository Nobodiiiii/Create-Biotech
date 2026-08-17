package com.nobodiiiii.createbiotech.content.squidprinter;

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

/**
 * Carries how far a single ink particle may sink before it vanishes.
 *
 * <p>The limit travels with the particle rather than living in the particle
 * class because the printer releases ink from two different heights, and both
 * streams have to stop at the same depth below the machine.
 *
 * @param fallLimit distance in blocks, measured down from where the ink spawned
 */
public record SquidPrinterInkParticleOption(float fallLimit) implements ParticleOptions {

	public static final Codec<SquidPrinterInkParticleOption> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(Codec.FLOAT.fieldOf("fall_limit")
			.forGetter(SquidPrinterInkParticleOption::fallLimit))
			.apply(instance, SquidPrinterInkParticleOption::new));

	@SuppressWarnings("deprecation")
	public static final ParticleOptions.Deserializer<SquidPrinterInkParticleOption> DESERIALIZER =
		new ParticleOptions.Deserializer<>() {
			@Override
			public SquidPrinterInkParticleOption fromCommand(ParticleType<SquidPrinterInkParticleOption> type,
				StringReader reader) throws CommandSyntaxException {
				reader.expect(' ');
				return new SquidPrinterInkParticleOption(reader.readFloat());
			}

			@Override
			public SquidPrinterInkParticleOption fromNetwork(ParticleType<SquidPrinterInkParticleOption> type,
				FriendlyByteBuf buffer) {
				return new SquidPrinterInkParticleOption(buffer.readFloat());
			}
		};

	public SquidPrinterInkParticleOption {
		fallLimit = Math.max(0.0f, fallLimit);
	}

	@Override
	public ParticleType<?> getType() {
		return CBParticleTypes.SQUID_PRINTER_INK.get();
	}

	@Override
	public void writeToNetwork(FriendlyByteBuf buffer) {
		buffer.writeFloat(fallLimit);
	}

	@Override
	public String writeToString() {
		return String.format(Locale.ROOT, "%s %.2f", BuiltInRegistries.PARTICLE_TYPE.getKey(getType()), fallLimit);
	}
}

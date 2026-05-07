package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.Locale;

import com.google.gson.JsonSyntaxException;

import net.minecraft.network.FriendlyByteBuf;

public enum BasinEntityProcessingOperation {
	PRESSING("pressing");

	private final String serializedName;

	BasinEntityProcessingOperation(String serializedName) {
		this.serializedName = serializedName;
	}

	public String getSerializedName() {
		return serializedName;
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeUtf(serializedName);
	}

	public static BasinEntityProcessingOperation read(FriendlyByteBuf buffer) {
		return fromSerializedName(buffer.readUtf());
	}

	public static BasinEntityProcessingOperation fromSerializedName(String name) {
		String normalized = name.toLowerCase(Locale.ROOT);
		for (BasinEntityProcessingOperation operation : values())
			if (operation.serializedName.equals(normalized))
				return operation;
		throw new JsonSyntaxException("Unknown basin entity processing operation: " + name);
	}
}

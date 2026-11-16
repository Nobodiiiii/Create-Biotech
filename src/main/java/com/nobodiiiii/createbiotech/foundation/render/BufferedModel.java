package com.nobodiiiii.createbiotech.foundation.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.vertex.NoOverlayVertexView;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Bakes existing block-atlas render code once for Flywheel, retaining its authored shading. */
public final class BufferedModel {
	private BufferedModel() {}

	public static Model create(BiConsumer<PoseStack, MultiBufferSource> renderer) {
		Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
		Map<RenderType, BufferBuilder> buffers = new LinkedHashMap<>();
		try {
			renderer.accept(new PoseStack(), type -> buffers.computeIfAbsent(type, key -> {
				ByteBufferBuilder bytes = new ByteBufferBuilder(1536);
				memory.put(key, bytes);
				return new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
			}));
			var meshes = new ArrayList<Model.ConfiguredMesh>();
			for (var entry : buffers.entrySet()) {
				// Colors already include the original renderer's diffuse shading.
				var material = ModelUtil.getMaterial(entry.getKey(), false, false);
				if (material == null) material = ModelUtil.getItemMaterial(entry.getKey());
				if (material == null) throw new IllegalArgumentException("Unsupported block render type: " + entry.getKey());
				try (MeshData data = entry.getValue().build()) {
					if (data != null) meshes.add(new Model.ConfiguredMesh(material, copyMesh(data)));
				}
			}
			return new SimpleModel(meshes);
		} finally {
			memory.values().forEach(ByteBufferBuilder::close);
		}
	}

	private static SimpleQuadMesh copyMesh(MeshData data) {
		// Same BLOCK-prefix contract as Flywheel 1.0.6 MeshHelper.blockVerticesToMesh:
		// extended shader formats may append fields, but the first 31 bytes are stable.
		var vertices = new NoOverlayVertexView();
		int count = data.drawState().vertexCount();
		long stride = vertices.stride();
		MemoryBlock copy = MemoryBlock.mallocTracked(count * stride);
		long source = MemoryUtil.memAddress(data.vertexBuffer());
		int sourceStride = data.drawState().format().getVertexSize();
		for (int i = 0; i < count; i++)
			MemoryUtil.memCopy(source + (long) i * sourceStride, copy.ptr() + i * stride, Math.min(stride, 31));
		vertices.ptr(copy.ptr());
		vertices.vertexCount(count);
		vertices.nativeMemoryOwner(copy);
		return new SimpleQuadMesh(vertices);
	}
}

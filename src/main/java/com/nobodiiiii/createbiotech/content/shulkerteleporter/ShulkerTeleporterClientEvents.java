package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public class ShulkerTeleporterClientEvents {

	private static boolean renderingClippedPlayer;
	private static boolean playerClippingDisabledThisSession;

	@SubscribeEvent
	public static void clipPlayerAboveClosingShell(RenderPlayerEvent.Pre event) {
		if (renderingClippedPlayer)
			return;
		if (playerClippingDisabledThisSession)
			return;

		try {
			clipPlayerAboveClosingShellSafely(event);
		} catch (Throwable throwable) {
			playerClippingDisabledThisSession = true;
			renderingClippedPlayer = false;
			event.setCanceled(false);
		}
	}

	private static void clipPlayerAboveClosingShellSafely(RenderPlayerEvent.Pre event) {
		if (!CBConfigs.CLIENT.enableShulkerTeleporterPlayerClipping.get())
			return;

		Player player = event.getEntity();
		ShulkerTeleporterBlockEntity teleporter = findRelevantTeleporter(player, event.getPartialTick());
		if (teleporter == null)
			return;

		if (player == Minecraft.getInstance().player
			&& Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON)
			return;
		if (!(player instanceof AbstractClientPlayer clientPlayer))
			return;

		event.setCanceled(true);

		Vec3 camera = Minecraft.getInstance()
			.gameRenderer
			.getMainCamera()
			.getPosition();
		double clipY = teleporter.getTopShellTopY(event.getPartialTick()) - camera.y;
		MultiBufferSource clippedBuffer = renderType -> new YClippingVertexConsumer(event.getMultiBufferSource()
			.getBuffer(renderType), clipY);
		float yaw = Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot());

		try {
			renderingClippedPlayer = true;
			event.getRenderer()
				.render(clientPlayer, yaw, event.getPartialTick(), event.getPoseStack(), clippedBuffer,
					event.getPackedLight());
		} finally {
			renderingClippedPlayer = false;
		}
	}

	public static double getFirstPersonCameraYOffset(Player player, float partialTick) {
		ShulkerTeleporterBlockEntity teleporter = findRelevantTeleporter(player, partialTick);
		if (teleporter == null)
			return 0.0d;
		return teleporter.getTopShellYOffset(partialTick) - ShulkerTeleporterBlockEntity.TOP_SHELL_OPEN_Y;
	}

	private static ShulkerTeleporterBlockEntity findRelevantTeleporter(Player player, float partialTick) {
		Level level = player.level();
		BlockPos center = player.blockPosition();
		ShulkerTeleporterBlockEntity best = null;
		float bestProgress = 0.0f;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 4, 1))) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!(blockEntity instanceof ShulkerTeleporterBlockEntity teleporter))
				continue;
			float progress = teleporter.getClosingProgress(partialTick);
			if (progress <= 0)
				continue;
			if (!teleporter.isPlayerInTeleportArea(player))
				continue;
			if (best == null || progress > bestProgress) {
				best = teleporter;
				bestProgress = progress;
			}
		}
		return best;
	}

	private static class YClippingVertexConsumer implements VertexConsumer {
		private final VertexConsumer wrapped;
		private final double clipY;
		private final List<ClippedVertex> quad = new ArrayList<>(4);
		private ClippedVertex current = new ClippedVertex();

		private YClippingVertexConsumer(VertexConsumer wrapped, double clipY) {
			this.wrapped = wrapped;
			this.clipY = clipY;
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			current = new ClippedVertex();
			current.x = x;
			current.y = y;
			current.z = z;
			return this;
		}

		@Override
		public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
			return vertex(matrix.transformPosition(x, y, z, new org.joml.Vector3f()));
		}

		private VertexConsumer vertex(org.joml.Vector3f vec) {
			return vertex(vec.x(), vec.y(), vec.z());
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			current.red = red;
			current.green = green;
			current.blue = blue;
			current.alpha = alpha;
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			current.u = u;
			current.v = v;
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			current.overlay = u | v << 16;
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			current.light = u | v << 16;
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			current.normalX = x;
			current.normalY = y;
			current.normalZ = z;
			return this;
		}

		@Override
		public void endVertex() {
			quad.add(current.copy());
			if (quad.size() < 4)
				return;
			emitClippedQuad(quad);
			quad.clear();
		}

		@Override
		public void defaultColor(int red, int green, int blue, int alpha) {
			wrapped.defaultColor(red, green, blue, alpha);
		}

		@Override
		public void unsetDefaultColor() {
			wrapped.unsetDefaultColor();
		}

		private void emitClippedQuad(List<ClippedVertex> source) {
			List<ClippedVertex> clipped = clip(source);
			if (clipped.size() < 3)
				return;
			for (int i = 1; i < clipped.size() - 1; i++)
				emitDegenerateQuad(clipped.get(0), clipped.get(i), clipped.get(i + 1));
		}

		private List<ClippedVertex> clip(List<ClippedVertex> source) {
			List<ClippedVertex> result = new ArrayList<>();
			for (int i = 0; i < source.size(); i++) {
				ClippedVertex current = source.get(i);
				ClippedVertex previous = source.get((i + source.size() - 1) % source.size());
				boolean currentInside = current.y <= clipY;
				boolean previousInside = previous.y <= clipY;

				if (currentInside != previousInside)
					result.add(ClippedVertex.lerp(previous, current, (clipY - previous.y) / (current.y - previous.y)));
				if (currentInside)
					result.add(current);
			}
			return result;
		}

		private void emitDegenerateQuad(ClippedVertex a, ClippedVertex b, ClippedVertex c) {
			emit(a);
			emit(b);
			emit(c);
			emit(c);
		}

		private void emit(ClippedVertex vertex) {
			wrapped.vertex(vertex.x, vertex.y, vertex.z)
				.color(vertex.red, vertex.green, vertex.blue, vertex.alpha)
				.uv(vertex.u, vertex.v)
				.overlayCoords(vertex.overlay)
				.uv2(vertex.light)
				.normal(vertex.normalX, vertex.normalY, vertex.normalZ)
				.endVertex();
		}
	}

	private static class ClippedVertex {
		private double x;
		private double y;
		private double z;
		private int red = 255;
		private int green = 255;
		private int blue = 255;
		private int alpha = 255;
		private float u;
		private float v;
		private int overlay;
		private int light;
		private float normalX;
		private float normalY = 1;
		private float normalZ;

		private ClippedVertex copy() {
			ClippedVertex copy = new ClippedVertex();
			copy.x = x;
			copy.y = y;
			copy.z = z;
			copy.red = red;
			copy.green = green;
			copy.blue = blue;
			copy.alpha = alpha;
			copy.u = u;
			copy.v = v;
			copy.overlay = overlay;
			copy.light = light;
			copy.normalX = normalX;
			copy.normalY = normalY;
			copy.normalZ = normalZ;
			return copy;
		}

		private static ClippedVertex lerp(ClippedVertex from, ClippedVertex to, double t) {
			ClippedVertex result = new ClippedVertex();
			result.x = Mth.lerp(t, from.x, to.x);
			result.y = Mth.lerp(t, from.y, to.y);
			result.z = Mth.lerp(t, from.z, to.z);
			result.red = Mth.floor(Mth.lerp(t, from.red, to.red));
			result.green = Mth.floor(Mth.lerp(t, from.green, to.green));
			result.blue = Mth.floor(Mth.lerp(t, from.blue, to.blue));
			result.alpha = Mth.floor(Mth.lerp(t, from.alpha, to.alpha));
			result.u = (float) Mth.lerp(t, from.u, to.u);
			result.v = (float) Mth.lerp(t, from.v, to.v);
			result.overlay = from.overlay;
			result.light = from.light;
			result.normalX = (float) Mth.lerp(t, from.normalX, to.normalX);
			result.normalY = (float) Mth.lerp(t, from.normalY, to.normalY);
			result.normalZ = (float) Mth.lerp(t, from.normalZ, to.normalZ);
			return result;
		}
	}

	private ShulkerTeleporterClientEvents() {}
}

package com.nobodiiiii.createbiotech.foundation.fluid;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Base fluid type for this mod's fluids: it wires the client extensions once so
 * subclasses only state what makes their fluid look different.
 *
 * <p>Everything here is presentation. Physics, sounds, light level and the rest
 * still come from the {@link Properties} handed to the constructor, and nothing
 * on this class affects how the fluid meets other fluids in the world.
 *
 * <p>Plays the same role as {@code AllFluids.TintedFluidType} in Create, but
 * routes every tint overload through {@link #getTintColor()} instead of leaving
 * the argument-less one at its white default, and exposes the two overlay
 * textures so a fluid can dress its edges and the camera while submerged.
 */
public class CBFluidType extends FluidType {

	/** ARGB white: textures are drawn exactly as authored. */
	public static final int NO_TINT = 0xFFFFFFFF;

	/** Fog distance vanilla water renders with, scaled by {@link #getFogDistanceModifier()}. */
	private static final float BASE_FOG_DISTANCE = 96F;
	private static final float FOG_START = -8F;

	private final ResourceLocation stillTexture;
	private final ResourceLocation flowingTexture;

	public CBFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
		super(properties);
		this.stillTexture = stillTexture;
		this.flowingTexture = flowingTexture;
	}

	@OnlyIn(Dist.CLIENT)
	public IClientFluidTypeExtensions createClientExtensions() {
		return new IClientFluidTypeExtensions() {

			@Override
			public ResourceLocation getStillTexture() {
				return stillTexture;
			}

			@Override
			public ResourceLocation getFlowingTexture() {
				return flowingTexture;
			}

			@Override
			@Nullable
			public ResourceLocation getOverlayTexture() {
				return CBFluidType.this.getOverlayTexture();
			}

			@Override
			@Nullable
			public ResourceLocation getRenderOverlayTexture(Minecraft minecraft) {
				return CBFluidType.this.getSubmersionTexture();
			}

			@Override
			public int getTintColor() {
				return CBFluidType.this.getTintColor();
			}

			@Override
			public int getTintColor(FluidStack stack) {
				return CBFluidType.this.getTintColor(stack);
			}

			@Override
			public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
				return CBFluidType.this.getTintColor(state, getter, pos);
			}

			@Override
			public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
				float darkenWorldAmount, Vector3f fluidFogColor) {
				Vector3f customFogColor = CBFluidType.this.getCustomFogColor();
				return customFogColor == null ? fluidFogColor : customFogColor;
			}

			@Override
			public void modifyFogRender(Camera camera, FogMode mode, float renderDistance, float partialTick,
				float nearDistance, float farDistance, FogShape shape) {
				float modifier = CBFluidType.this.getFogDistanceModifier();
				if (modifier == 1F)
					return;
				RenderSystem.setShaderFogShape(FogShape.CYLINDER);
				RenderSystem.setShaderFogStart(FOG_START);
				RenderSystem.setShaderFogEnd(BASE_FOG_DISTANCE * modifier);
			}
		};
	}

	/** Tint every unqualified lookup falls back to, in ARGB. */
	protected int getTintColor() {
		return NO_TINT;
	}

	/** Tint for stack-based rendering: tanks, pipes, JEI, tooltips. */
	protected int getTintColor(FluidStack stack) {
		return getTintColor();
	}

	/** Tint for the fluid's blocks in the world. */
	protected int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
		return getTintColor();
	}

	/** Texture drawn where the fluid touches a non-opaque block; null keeps the still texture. */
	@Nullable
	protected ResourceLocation getOverlayTexture() {
		return null;
	}

	/** Texture pasted over the camera while it is inside the fluid; null draws nothing. */
	@Nullable
	protected ResourceLocation getSubmersionTexture() {
		return null;
	}

	/** Fog color while the camera is inside the fluid; null keeps whatever the renderer picked. */
	@Nullable
	protected Vector3f getCustomFogColor() {
		return null;
	}

	/** Scales how far the camera sees while submerged; 1 leaves the fog render untouched. */
	protected float getFogDistanceModifier() {
		return 1F;
	}
}

package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Keeps a selected surgical tool visible everywhere except as a dropped item. */
public final class SurgicalKitItemModel extends BakedModelWrapper<BakedModel> {

	private final ItemOverrides overrides;

	public SurgicalKitItemModel(BakedModel originalModel) {
		super(originalModel);
		overrides = new GroundFallbackOverrides(originalModel, originalModel.getOverrides());
	}

	@Override
	public ItemOverrides getOverrides() {
		return overrides;
	}

	private static final class GroundFallbackOverrides extends ItemOverrides {
		private final BakedModel defaultModel;
		private final ItemOverrides delegate;
		private final Map<BakedModel, BakedModel> wrappedModels = new IdentityHashMap<>();

		private GroundFallbackOverrides(BakedModel defaultModel, ItemOverrides delegate) {
			this.defaultModel = defaultModel;
			this.delegate = delegate;
		}

		@Override
		public @Nullable BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
			@Nullable LivingEntity entity, int seed) {
			BakedModel resolved = delegate.resolve(defaultModel, stack, level, entity, seed);
			if (resolved == null || resolved == defaultModel)
				return resolved;
			return wrappedModels.computeIfAbsent(resolved,
				selectedModel -> new GroundFallbackModel(selectedModel, defaultModel));
		}
	}

	private static final class GroundFallbackModel extends BakedModelWrapper<BakedModel> {
		private final BakedModel defaultModel;

		private GroundFallbackModel(BakedModel selectedModel, BakedModel defaultModel) {
			super(selectedModel);
			this.defaultModel = defaultModel;
		}

		@Override
		public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack,
			boolean leftHanded) {
			BakedModel renderedModel = displayContext == ItemDisplayContext.GROUND ? defaultModel : originalModel;
			return renderedModel.applyTransform(displayContext, poseStack, leftHanded);
		}
	}
}

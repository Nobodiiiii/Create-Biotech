package com.nobodiiiii.createbiotech.entity.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Rotation;
import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Samples the donor's native wing tracks on dedicated, unspawned preview models. */
public final class BionicWingAnimation {
	private static final Map<MimicProfile, LivingEntity> PREVIEWS = new WeakHashMap<>();
	private static final Map<String, NativeModel> MODELS = new HashMap<>();
	private static final Map<MimicProfile, Map<String, Rotation>> REST_POSES = new WeakHashMap<>();
	private record NativeModel(EntityModel<LivingEntity> model, Map<String, ModelPart> parts) {}
	private BionicWingAnimation() {}
	public static void clear() { PREVIEWS.clear(); MODELS.clear(); REST_POSES.clear(); }

	/** The animated hinge channel in the donor's model frame, independent of its installed pose. */
	static Vec3 flapAxis(MimicProfile profile) {
		return switch (profile.entityTypeId().getPath()) {
		case "bat", "allay", "vex" -> new Vec3(0, 1, 0);
		default -> new Vec3(0, 0, 1);
		};
	}

	public static Rotation sample(MimicProfile profile, Set<String> labels, boolean left, float age) {
		String type = profile.entityTypeId().getPath();
		if (type.equals("ender_dragon")) {
			// DragonModel computes its wing track in renderToBuffer rather than setupAnim.
			float phase = age * 0.2f * (float) (Math.PI * 2);
			boolean tip = labels.stream().anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("tip"));
			if (tip) return new Rotation(0, 0, (float) (Math.sin(phase + 2) - Math.sin(2)) * 0.75f * (left ? 1 : -1));
			return new Rotation((float) (1 - Math.cos(phase)) * 0.2f, 0,
				(float) -Math.sin(phase) * 0.8f * (left ? 1 : -1));
		}
		var level = Minecraft.getInstance().level;
		if (level == null) return new Rotation(0, 0, 0);
		LivingEntity preview = PREVIEWS.computeIfAbsent(profile, key -> key.createPreviewEntity(level));
		if (preview == null) return new Rotation(0, 0, 0);
		preview.setId(0);
		NativeModel nativeModel = MODELS.computeIfAbsent(type, ignored -> createModel(preview));
		if (nativeModel == null) return new Rotation(0, 0, 0);
		ModelPart part = labels.stream().filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("wing"))
			.map(nativeModel.parts::get).filter(java.util.Objects::nonNull).findFirst().orElseGet(() ->
				nativeModel.parts.entrySet().stream().filter(entry -> {
					String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
					return key.contains("wing") && key.contains(left ? "left" : "right") && !key.contains("tip");
				}).map(Map.Entry::getValue).findFirst().orElse(null));
		if (part == null) return new Rotation(0, 0, 0);
		Map<String, Rotation> rest = REST_POSES.computeIfAbsent(profile, ignored -> {
			float captureAge = com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer.captureAnimationAge(preview);
			nativeModel.model.attackTime = 0; nativeModel.model.riding = false; nativeModel.model.young = preview.isBaby();
			nativeModel.model.prepareMobModel(preview, 0, 0, 0);
			nativeModel.model.setupAnim(preview, 0, 0, captureAge, 0, 0);
			Map<String, Rotation> sampled = new HashMap<>();
			nativeModel.parts.forEach((name, nativePart) -> sampled.put(name, new Rotation(nativePart.xRot, nativePart.yRot, nativePart.zRot)));
			return Map.copyOf(sampled);
		});
		Rotation neutral = nativeModel.parts.entrySet().stream().filter(entry -> entry.getValue() == part)
			.map(entry -> rest.get(entry.getKey())).findFirst().orElse(Rotation.IDENTITY);
		preview.setOnGround(false);
		nativeModel.model.attackTime = 0; nativeModel.model.riding = false; nativeModel.model.young = preview.isBaby();
		if (preview instanceof net.minecraft.world.entity.ambient.Bat bat) {
			bat.setResting(false); bat.restAnimationState.stop(); bat.flyAnimationState.start(0);
		}
		nativeModel.model.prepareMobModel(preview, 0, 0, 0);
		float animationAge = type.equals("chicken") || type.equals("parrot") ? (float) (Math.sin(age * 1.8f) + 1) : age;
		nativeModel.model.setupAnim(preview, 0, 0, animationAge, 0, 0);
		return new Rotation(part.xRot - neutral.x(), part.yRot - neutral.y(), part.zRot - neutral.z());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static NativeModel createModel(LivingEntity preview) {
		var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(preview);
		if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> livingRenderer)) return null;
		EntityModel model = livingRenderer.getModel();
		Map<String, ModelPart> parts = new HashMap<>();
		for (Class<?> cls = model.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass())
			for (Field field : cls.getDeclaredFields()) {
				if (field.getType() != ModelPart.class || !field.trySetAccessible()) continue;
				try { if (field.get(model) instanceof ModelPart part) collect(field.getName(), part, parts); }
				catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
			}
		return new NativeModel(model, Map.copyOf(parts));
	}
	private static void collect(String name, ModelPart part, Map<String, ModelPart> parts) {
		parts.put(name, part);
		((ModelPartAccessor) (Object) part).createBiotech$getChildren().forEach((childName, child) -> collect(childName, child, parts));
	}
}

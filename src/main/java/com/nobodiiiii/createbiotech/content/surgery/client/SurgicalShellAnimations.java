package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState;
import net.minecraft.world.entity.monster.Shulker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Per-body native animation entities. Only state inputs are adapted to the assembled body;
 * animation updates and every rendered frame come from Minecraft's entities and renderers.
 * See ref/1.21.1/Minecraft/net/minecraft/world/entity/animal/armadillo/Armadillo.java and
 * ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Shulker.java.
 */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class SurgicalShellAnimations {
	private static final Map<SlimeBionicEntity, BodyAnimations> BODIES = new WeakHashMap<>();

	private SurgicalShellAnimations() {}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (event.getEntity() instanceof SlimeBionicEntity owner && owner.level().isClientSide)
			animations(owner);
	}

	static void clear() {
		BODIES.clear();
	}

	static SurgicalCapturedRenderPlan plan(SlimeBionicEntity owner, int source, float partialTick,
		SurgicalCapturedRenderPlan base) {
		BodyAnimations body = animations(owner);
		SourceAnimation animation = body == null ? null : body.sources.get(source);
		return animation == null ? base : animation.plan(partialTick, base);
	}

	@Nullable
	private static BodyAnimations animations(SlimeBionicEntity owner) {
		SurgicalAssembly assembly = owner.getAssembly();
		BionicTraitSet traits = owner.getBionicTraits();
		if (assembly == null || !traits.has(BionicTrait.SHELL_DEFENSE)) {
			BODIES.remove(owner);
			return null;
		}
		BodyAnimations body = BODIES.get(owner);
		if (body == null || body.assembly != assembly || body.traits != traits) {
			body = new BodyAnimations(assembly, traits);
			for (var member : traits.members(BionicTrait.SHELL_DEFENSE)) {
				int source = member.source();
				if (body.sources.containsKey(source)) continue;
				LivingEntity entity = assembly.sources().get(source).profile().createPreviewEntity(owner.level());
				if (entity instanceof Armadillo armadillo) {
					armadillo.switchToState(ArmadilloState.IDLE);
					body.sources.put(source, new SourceAnimation(armadillo, owner.getShellPeekSequence()));
				} else if (entity instanceof Shulker) {
					body.sources.put(source, new SourceAnimation(entity, owner.getShellPeekSequence()));
				}
			}
			BODIES.put(owner, body);
		}
		if (body.lastTick != owner.tickCount) {
			body.lastTick = owner.tickCount;
			for (SourceAnimation source : body.sources.values())
				source.tick(owner);
		}
		return body;
	}

	private static final class BodyAnimations {
		private final SurgicalAssembly assembly;
		private final BionicTraitSet traits;
		private final Map<Integer, SourceAnimation> sources = new HashMap<>();
		private int lastTick = Integer.MIN_VALUE;

		private BodyAnimations(SurgicalAssembly assembly, BionicTraitSet traits) {
			this.assembly = assembly;
			this.traits = traits;
		}
	}

	private static final class SourceAnimation {
		private final LivingEntity entity;
		private int peekSequence;
		private float framePartialTick;
		private SurgicalCapturedRenderPlan frameBase;
		private SurgicalCapturedRenderPlan frame;

		private SourceAnimation(LivingEntity entity, int peekSequence) {
			this.entity = entity;
			this.peekSequence = peekSequence;
		}

		private void tick(SlimeBionicEntity owner) {
			entity.tickCount = owner.tickCount;
			if (entity instanceof Armadillo armadillo) {
				armadillo.switchToState(owner.getShellState());
				if (peekSequence != owner.getShellPeekSequence()) {
					peekSequence = owner.getShellPeekSequence();
					armadillo.setPos(owner.position());
					armadillo.handleEntityEvent((byte) 64);
				}
				// The client animation portion of Armadillo.tick, in the same order.
				armadillo.setupAnimationStates();
				armadillo.inStateTicks++;
			} else if (entity instanceof Shulker shulker) {
				ArmadilloState state = owner.getShellState();
				// Native idle peek: staying within 0..0.3 also keeps ShulkerModel's breathing branch inactive.
				shulker.setRawPeekAmount(state == ArmadilloState.ROLLING || state == ArmadilloState.SCARED ? 0 : 30);
				// Native tick endpoints; ShulkerModel reads getClientPeekAmount(partialTick) itself.
				shulker.updatePeekAmount();
			}
			frame = null;
		}

		@SuppressWarnings("unchecked")
		private SurgicalCapturedRenderPlan plan(float partialTick, SurgicalCapturedRenderPlan base) {
			if (frame != null && frameBase == base && framePartialTick == partialTick)
				return frame;
			EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) Minecraft.getInstance()
				.getEntityRenderDispatcher().getRenderer(entity);
			frame = base.withTraitAppearance(SurgicalCapturedRenderPlan.capture(renderer, entity, 0, partialTick, true));
			frameBase = base;
			framePartialTick = partialTick;
			return frame;
		}
	}
}

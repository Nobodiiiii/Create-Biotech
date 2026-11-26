package com.nobodiiiii.createbiotech.entity.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.DevouredPlayer;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class DevouredPlayerClient {
	private static final int MOUNT_HINT_TICKS = 60;
	private static int hideMountHintUntil;
	@Nullable private static Component hiddenMountHint;

	private DevouredPlayerClient() {}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMount(EntityMountEvent event) {
		if (!event.isMounting() || !event.getLevel().isClientSide) return;
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		if (player == null || event.getEntityMounting() != player) return;
		hiddenMountHint = null;
		hideMountHintUntil = DevouredPlayer.isMarkedCaptive(event.getEntityBeingMounted(), player)
			? minecraft.gui.getGuiTicks() + MOUNT_HINT_TICKS : 0;
	}

	@SubscribeEvent
	public static void wrapMountHint(RegisterGuiLayersEvent event) {
		event.wrapLayer(VanillaGuiLayers.OVERLAY_MESSAGE, layer -> (graphics, delta) -> {
			var minecraft = Minecraft.getInstance();
			if (minecraft.gui.getGuiTicks() < hideMountHintUntil) {
				layer.render(new MountHintGraphics(minecraft, graphics), delta);
			} else {
				hiddenMountHint = null;
				layer.render(graphics, delta);
			}
		});
	}

	private static final class MountHintGraphics extends GuiGraphics {
		private final GuiGraphics graphics;

		private MountHintGraphics(Minecraft minecraft, GuiGraphics graphics) {
			super(minecraft, graphics.bufferSource());
			this.graphics = graphics;
		}

		@Override
		public PoseStack pose() {
			return graphics.pose();
		}

		@Override
		public int drawStringWithBackdrop(Font font, Component text, int x, int y, int width, int color) {
			// Keep the same hint hidden if the player escapes before its overlay expires.
			if (hiddenMountHint == null && text.getContents() instanceof TranslatableContents contents
				&& contents.getKey().equals("mount.onboard")) hiddenMountHint = text;
			return text == hiddenMountHint ? 0 : graphics.drawStringWithBackdrop(font, text, x, y, width, color);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void attackFromInside(InputEvent.InteractionKeyMappingTriggered event) {
		if (!event.isAttack()) return;
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		if (player == null || !player.isAlive() || minecraft.gameMode == null) return;
		var hunter = player.getVehicle();
		if (!DevouredPlayer.isInside(hunter, player)) return;

		// The camera may be inside the shell or its multipart body. Send the ordinary attack for the host.
		event.setCanceled(true);
		// Held mining input also fires this event. Let the attack cooldown recover between swings.
		if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK
			&& player.getAttackStrengthScale(0.5f) < 1) {
			event.setSwingHand(false);
			return;
		}
		Entity target = hunter;
		// An off-center carrier can be beyond the main collision box's interaction range.
		if (!player.canInteractWithEntity(hunter, 1) && hunter instanceof SlimeBionicEntity bionic) {
			for (Entity part : bionic.getParts()) {
				if (part.isPickable() && player.canInteractWithEntity(part, 1)) {
					target = part;
					break;
				}
			}
		}
		minecraft.gameMode.attack(player, target);
	}
}

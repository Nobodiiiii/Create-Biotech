package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.List;

import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxStatsTooltip;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Real game screens rendered by Lib's isolated child-interface host. */
public final class SurgicalPonderInterfaces {
    private SurgicalPonderInterfaces() {}

    public static void wheel(SceneBuilder scene, boolean joints, int duration) {
        panel(scene, duration, session -> {
            ItemStack stack = new ItemStack(joints ? CBItems.SURGICAL_JOINT.get() : CBItems.SURGICAL_KIT.get());
            session.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
            var menu = (net.minecraft.world.inventory.ChestMenu) session.menu();
            Minecraft.getInstance().setScreen(new ContainerScreen(menu, session.player().getInventory(), Component.empty()) {
                // Expose a native container rectangle around only the wheel's central controls.
                // Lib follows this rectangle on resize instead of cropping the whole game window.
                { imageWidth = 144; imageHeight = 144; }
                private int age;
                private final SurgicalKitRadialScreen wheel = new SurgicalKitRadialScreen(InteractionHand.MAIN_HAND, stack);
                @Override protected void init() { super.init(); wheel.init(minecraft, width, height); }
                @Override public void containerTick() { age++; wheel.tick(); }
                @Override public void removed() { wheel.removed(); super.removed(); }
                @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float pt) {
                    int count = joints ? SurgicalJointItem.TYPES.size() : SurgicalKitItem.availableTools(stack).size();
                    int selected = Math.min(count - 1, age / 32);
                    int x = width / 2 + SurgicalRadialLayout.slotX(selected, count, 1) + 11;
                    int y = height / 2 + SurgicalRadialLayout.slotY(selected, count, 1) + 11;
                    wheel.render(graphics, x, y, pt);
                }
            });
        });
    }

    public static void stats(SceneBuilder scene, ItemStack box, int duration) {
        panel(scene, duration, session -> {
            var menu = (net.minecraft.world.inventory.ChestMenu) session.menu();
            menu.getSlot(13).set(box.copy());
            var inventory = session.player().getInventory();
            Minecraft.getInstance().setScreen(new ContainerScreen(menu, inventory, box.getHoverName()) {
                { imageWidth = 176; imageHeight = 120; }
                @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float pt) {
                    graphics.renderItem(menu.getSlot(13).getItem(), leftPos + imageWidth / 2 - 8, topPos + 4);
                    renderTooltip(graphics, mouseX, mouseY);
                }
                @Override protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(box.getHoverName());
                    List<Component> details = new ArrayList<>();
                    CapturedEntityBoxStatsTooltip.append(box, session.level(), details, true);
                    // Keep the native base-stat section inside the inventory panel.
                    int emptyLines = 0;
                    for (Component line : details) {
                        if (line.getString().isEmpty() && ++emptyLines == 2) break;
                        lines.add(line);
                    }
                    graphics.renderComponentTooltip(font, lines, leftPos + 4, topPos + 42);
                }
            });
        });
    }

    private static void panel(SceneBuilder builder, int duration, java.util.function.Consumer<SandboxSession> open) {
        builder.addInstruction(new TickingInstruction(false, duration) {
            private InterfaceHost.Target target;
            private SandboxSession session;

            @Override public void reset(PonderScene scene) {
                super.reset(scene);
                if (session != null) InterfaceHost.withTarget(target, () -> InterfaceHost.hide(scene));
                target = null;
                session = null;
            }

            @Override protected void firstTick(PonderScene scene) {
                target = new InterfaceHost.Target();
                InterfaceDescriptor descriptor = new InterfaceDescriptor();
                descriptor.targetKind = InterfaceDescriptor.TargetKind.MENU;
                descriptor.menuType = "minecraft:generic_9x3";
                descriptor.interaction = InterfaceDescriptor.Interaction.VIEW;
                session = InterfaceHost.withTarget(target, () -> InterfaceHost.openPanel(scene, descriptor,
                    SurgicalPonderActors.ZOMBIE_POSITION.add(0, 1, 0), Pointing.RIGHT,
                    List.of(0.0, 0.0, 0.75), null, true, false));
                if (session.currentScreen() != null)
                    session.dispatch("surgical tutorial screen", () -> { open.accept(session); return true; }, false);
            }

            @Override public void tick(PonderScene scene) {
                super.tick(scene);
                if (remainingTicks == 0 && session != null)
                    InterfaceHost.withTarget(target, () -> InterfaceHost.hide(scene));
            }
        });
    }
}

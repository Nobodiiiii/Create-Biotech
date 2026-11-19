package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.joml.Vector2i;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxStatsTooltip;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalPonderMenu;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceOverlayElement;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxPlayer;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Screens opened by Ponderer's native menu-ID interface steps. */
public final class SurgicalPonderInterfaces {
    private SurgicalPonderInterfaces() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.<SurgicalPonderMenu, WheelScreen>register(CBMenuTypes.PONDER_SURGICAL_TOOL_WHEEL.get(),
            (menu, inventory, title) -> new WheelScreen(menu, inventory, title, false));
        event.<SurgicalPonderMenu, WheelScreen>register(CBMenuTypes.PONDER_SURGICAL_JOINT_WHEEL.get(),
            (menu, inventory, title) -> new WheelScreen(menu, inventory, title, true));
        event.register(CBMenuTypes.PONDER_SURGICAL_BOX_STATS.get(), StatsScreen::new);
        event.register(CBMenuTypes.PONDER_SURGICAL_BOX_HOVER.get(), BoxHoverScreen::new);
        event.register(CBMenuTypes.PONDER_SURGICAL_SMART_GLUE_HOVER.get(), SmartGlueHoverScreen::new);
    }

    public static void setCapturedBox(PonderScene scene, ItemStack box) {
        scene.forEach(InterfaceOverlayElement.class, element -> {
            if (element.session().currentScreen() instanceof StatsScreen screen)
                screen.setBox(box);
        });
    }

    /** Applies source textures to an owned preview snapshot, including its donor profiles. */
    public static void useSourceAppearance(CompoundTag entity) {
        removeMimicMarkers(entity);
        entity.putBoolean("BionicSourceForm", true);
    }

    private static void removeMimicMarkers(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            compound.remove("CreateBiotechSlimeMimic");
            for (String key : compound.getAllKeys())
                removeMimicMarkers(compound.get(key));
        } else if (tag instanceof ListTag list) {
            for (Tag child : list)
                removeMimicMarkers(child);
        }
    }

    private abstract static class PageScreen extends AbstractContainerScreen<SurgicalPonderMenu> {
        PageScreen(SurgicalPonderMenu menu, Inventory inventory, Component title, int width, int height) {
            super(menu, inventory, title);
            imageWidth = width;
            imageHeight = height;
        }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}
    }

    private static final class WheelScreen extends PageScreen {
        private final SurgicalKitRadialScreen wheel;
        private final int optionCount;
        private int age;

        WheelScreen(SurgicalPonderMenu menu, Inventory inventory, Component title, boolean joints) {
            super(menu, inventory, title, 144, 144);
            ItemStack stack = new ItemStack(joints ? CBItems.SURGICAL_JOINT.get() : CBItems.SURGICAL_KIT.get());
            if (inventory.player instanceof SandboxPlayer player)
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            wheel = new SurgicalKitRadialScreen(InteractionHand.MAIN_HAND, stack);
            optionCount = joints ? SurgicalJointItem.TYPES.size() : SurgicalKitItem.availableTools(stack).size();
        }

        @Override
        protected void init() {
            super.init();
            wheel.init(minecraft, width, height);
        }

        @Override
        protected void containerTick() {
            age++;
            wheel.tick();
        }

        @Override
        public void removed() {
            wheel.removed();
            super.removed();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int selected = Math.min(optionCount - 1, age / 32);
            int x = width / 2 + SurgicalRadialLayout.slotX(selected, optionCount, 1) + SurgicalRadialLayout.SLOT_HALF_SIZE;
            int y = height / 2 + SurgicalRadialLayout.slotY(selected, optionCount, 1) + SurgicalRadialLayout.SLOT_HALF_SIZE;
            wheel.render(graphics, x, y, partialTick);
        }
    }

    private static final class StatsScreen extends TooltipScreen {
        private final Level level;
        private final ItemStack emptyBox = new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get());

        StatsScreen(SurgicalPonderMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            level = inventory.player.level();
            var assembly = SurgicalPonderGreenprint.loadAssembly("surgical_table_zombie_golem");
            ItemStack box = CapturedEntityBoxHelper.createFilledBox(
                CBItems.LARGE_CARDBOARD_BOX.get(), CBEntityTypes.SLIME_BIONIC.get());
            CBItemData.edit(box, tag -> {
                var entity = tag.getCompound("CapturedEntity");
                entity.put("SurgicalAssembly", assembly.save());
                useSourceAppearance(entity);
            });
            menu.getSlot(0).set(box);
        }

        void setBox(ItemStack box) {
            menu.getSlot(0).set(box.copy());
            init();
        }

        @Override
        protected List<Component> tooltip() {
            var slot = menu.getSlot(0);
            ItemStack box = slot.hasItem() ? slot.getItem() : emptyBox;

            List<Component> lines = new ArrayList<>();
            lines.add(box.getHoverName());
            List<Component> details = new ArrayList<>();
            CapturedEntityBoxStatsTooltip.append(box, level, details, true);
            // The second blank line ends the native base-stat section.
            int emptyLines = 0;
            for (Component line : details) {
                if (line.getString().isEmpty() && ++emptyLines == 2)
                    break;
                lines.add(line);
            }
            return lines;
        }
    }

    private static final class BoxHoverScreen extends TooltipScreen {
        private final ItemStack box = new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get());
        private final SurgicalAssembly assembly;
        private final Level level;

        BoxHoverScreen(SurgicalPonderMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            assembly = SurgicalPonderGreenprint.loadAssembly("surgical_table_creeper_phantom");
            level = inventory.player.level();
        }

        @Override
        protected List<Component> tooltip() {
            List<Component> tooltip = new ArrayList<>();
            CreateLang.builder().add(box.getHoverName()).forGoggles(tooltip);
            SurgicalInteractionPresentation.addBoxPackControls(tooltip);
            CapturedEntityBoxStatsTooltip.appendPropertiesSection(tooltip, BionicTraitResolver.resolve(assembly, level));
            return tooltip;
        }

        @Override
        protected void renderIcon(GuiGraphics graphics) {
            GuiGameElement.of(box).at(TOOLTIP_INSET - 2, TOOLTIP_INSET - 4, 450).render(graphics);
        }
    }

    private static final class SmartGlueHoverScreen extends TooltipScreen {
        private final ItemStack glue = new ItemStack(CBItems.SMART_SUPER_GLUE.get());

        SmartGlueHoverScreen(SurgicalPonderMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected List<Component> tooltip() {
            List<Component> tooltip = new ArrayList<>();
            CreateLang.builder().add(glue.getHoverName()).forGoggles(tooltip);
            SurgicalInteractionPresentation.addGlueControls(tooltip, true, false, false);
            return tooltip;
        }

        @Override
        protected void renderIcon(GuiGraphics graphics) {
            GuiGameElement.of(glue).at(TOOLTIP_INSET - 2, TOOLTIP_INSET - 4, 450).render(graphics);
        }
    }

    private abstract static class TooltipScreen extends PageScreen {
        private static final int MAX_TEXT_WIDTH = 250;
        protected static final int TOOLTIP_INSET = 8;
        private static final int TOOLTIP_LINE_HEIGHT = 10;

        private List<FormattedCharSequence> lines = List.of();
        private float scale;

        TooltipScreen(SurgicalPonderMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, 0, 0);
        }

        protected abstract List<Component> tooltip();

        protected void renderIcon(GuiGraphics graphics) {}

        @Override
        protected void init() {
            lines = tooltip().stream()
                .flatMap(line -> font.width(line) <= MAX_TEXT_WIDTH
                    ? Stream.of(line.getVisualOrderText()) : font.split(line, MAX_TEXT_WIDTH).stream())
                .toList();

            int tooltipWidth = lines.stream().mapToInt(font::width).max().orElse(0);
            int contentWidth = tooltipWidth + 2 * TOOLTIP_INSET;
            int contentHeight = lines.size() * TOOLTIP_LINE_HEIGHT + 2 * TOOLTIP_INSET;
            scale = Math.min(1, Math.min(width / (float) contentWidth, height / (float) contentHeight));
            imageWidth = Math.min(width, (int) Math.ceil(contentWidth * scale));
            imageHeight = Math.min(height, (int) Math.ceil(contentHeight * scale));
            super.init();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(leftPos, topPos, 0);
            pose.scale(scale, scale, 1);
            graphics.renderTooltip(font, lines, (screenWidth, screenHeight, x, y, w, h) -> new Vector2i(x, y),
                TOOLTIP_INSET, TOOLTIP_INSET);
            renderIcon(graphics);
            pose.popPose();
        }
    }
}

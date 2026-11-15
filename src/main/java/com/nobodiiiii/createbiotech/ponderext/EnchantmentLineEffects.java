package com.nobodiiiii.createbiotech.ponderext;

import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Moves the demonstrated book between the printer, chamber and output depot. */
final class EnchantmentLineEffects {
    private static final BlockPos BELT = new BlockPos(6, 1, 4);
    private static final BlockPos PRINTER = new BlockPos(5, 3, 4);
    private static final BlockPos CHAMBER = new BlockPos(2, 1, 4);
    private static final BlockPos INTAKE_FUNNEL = new BlockPos(3, 2, 4);
    private static final BlockPos OUTPUT_DEPOT = new BlockPos(1, 1, 4);
    private static final BlockPos OUTPUT_FUNNEL = OUTPUT_DEPOT.above();

    private EnchantmentLineEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/enchantment_line/release_printed_book",
            "打印并放行传送带上的书", EnchantmentLineEffects::releasePrintedBook);
        engine.steps().registerJava("create_biotech:java/enchantment_line/receive_printed_book",
            "漏斗将打印的复印本送入附魔室", EnchantmentLineEffects::receivePrintedBook);
        engine.steps().registerJava("create_biotech:java/enchantment_line/output_enchanted_book",
            "完成附魔并从漏斗输出一本附魔书", EnchantmentLineEffects::outputEnchantedBook);
    }

    private static void releasePrintedBook(StepBuildContext ctx) {
        ctx.builder().addInstruction(scene -> {
            var world = scene.getWorld();
            if (!(world.getBlockEntity(BELT) instanceof BeltBlockEntity belt)
                || !(world.getBlockEntity(PRINTER) instanceof SquidPrinterBlockEntity printer))
                return;
            ItemStack template = printer.filtering.getFilter();
            if (!EnchantmentBookCopyItem.hasCopyableEnchantments(template))
                return;
            for (var item : belt.getInventory().getTransportedItems()) {
                if (!item.stack.is(Items.BOOK) || !item.locked)
                    continue;
                // Change the held belt item in place, then let the real belt carry it onward.
                item.stack = EnchantmentBookCopyItem.fromTemplate(template, CBItems.ENCHANTMENT_BOOK_COPY.get());
                item.locked = false;
                return;
            }
        });
    }

    private static void receivePrintedBook(StepBuildContext ctx) {
        ctx.builder().addInstruction(scene -> {
            var world = scene.getWorld();
            if (!(world.getBlockEntity(BELT) instanceof BeltBlockEntity belt)
                || !(world.getBlockEntity(CHAMBER) instanceof EvokerEnchantingChamberBlockEntity chamber)
                || chamber.isBlocked())
                return;
            var items = belt.getInventory().getTransportedItems().iterator();
            while (items.hasNext()) {
                var item = items.next();
                if (!item.stack.is(CBItems.ENCHANTMENT_BOOK_COPY.get())
                    || item.beltPosition < 3.5f - 1 / 16f)
                    continue;
                // Ponder's client world blocks automatic funnel intake; transfer this same item.
                chamber.setRenderPreviewState(item.stack.copyWithCount(1), ItemStack.EMPTY,
                    chamber.getStoredFluidAmount(), 0, 0, false);
                item.stack.shrink(1);
                if (item.stack.isEmpty())
                    items.remove();
                if (world.getBlockEntity(INTAKE_FUNNEL) instanceof FunnelBlockEntity funnel)
                    funnel.flap(true);
                return;
            }
        });
    }

    private static void outputEnchantedBook(StepBuildContext ctx) {
        ctx.builder().addInstruction(scene -> {
            var world = scene.getWorld();
            if (!(world.getBlockEntity(CHAMBER) instanceof EvokerEnchantingChamberBlockEntity chamber)
                || !(world.getBlockEntity(OUTPUT_DEPOT) instanceof DepotBlockEntity depot)
                || !EnchantmentBookCopyItem.hasStoredEnchantments(chamber.getHeldItem()))
                return;
            var input = depot.getBehaviour(DirectBeltInputBehaviour.TYPE);
            ItemStack result = EnchantmentBookCopyItem.toEnchantedBook(chamber.getHeldItem()).copyWithCount(1);
            if (input == null || !input.handleInsertion(result, Direction.WEST, true).isEmpty())
                return;
            chamber.setRenderPreviewState(ItemStack.EMPTY, result, 0, 0, 0, false);
            ItemStack output = chamber.getItemCapability(Direction.WEST).extractItem(1, 1, false);
            input.handleInsertion(output, Direction.WEST, false);
            if (world.getBlockEntity(OUTPUT_FUNNEL) instanceof FunnelBlockEntity funnel)
                funnel.flap(false);
        });
    }
}

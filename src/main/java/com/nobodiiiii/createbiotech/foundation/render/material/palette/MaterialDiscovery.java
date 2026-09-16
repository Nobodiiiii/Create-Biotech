package com.nobodiiiii.createbiotech.foundation.render.material.palette;

import com.nobodiiiii.createbiotech.foundation.render.material.MaterialRenderingModule;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class MaterialDiscovery {
    public static final Comparator<ResourceLocation> ID_ORDER =
            Comparator.comparing(ResourceLocation::getNamespace)
                    .thenComparing(ResourceLocation::getPath);

    private MaterialDiscovery() {
    }

    public static MaterialValidationReport validate(
            Map<ResourceLocation, Item> items,
            Map<ResourceLocation, Block> blocks,
            Set<ResourceLocation> itemTagIds,
            Set<ResourceLocation> blockTagIds) {
        var candidates = new TreeSet<>(ID_ORDER);
        candidates.addAll(itemTagIds);
        candidates.addAll(blockTagIds);

        var valid = new ArrayList<ResourceLocation>();
        var issues = new ArrayList<MaterialValidationReport.Issue>();
        for (ResourceLocation id : candidates) {
            ErrorCode error = validateCandidate(id, items, blocks, itemTagIds, blockTagIds);
            if (error == null) {
                valid.add(id);
            } else {
                issues.add(new MaterialValidationReport.Issue(id, error));
            }
        }
        return new MaterialValidationReport(valid, issues);
    }

    private static ErrorCode validateCandidate(
            ResourceLocation id,
            Map<ResourceLocation, Item> items,
            Map<ResourceLocation, Block> blocks,
            Set<ResourceLocation> itemTagIds,
            Set<ResourceLocation> blockTagIds) {
        if (!itemTagIds.contains(id)) {
            return ErrorCode.BLOCK_TAG_ONLY;
        }
        if (!blockTagIds.contains(id)) {
            return ErrorCode.ITEM_TAG_ONLY;
        }

        Item item = items.get(id);
        if (item == null) {
            return ErrorCode.MISSING_ITEM;
        }
        Block block = blocks.get(id);
        if (block == null) {
            return ErrorCode.MISSING_BLOCK;
        }
        if (!(item instanceof BlockItem blockItem)) {
            return ErrorCode.NOT_BLOCK_ITEM;
        }
        if (blockItem.getBlock() != block) {
            return ErrorCode.BLOCK_ITEM_POINTS_ELSEWHERE;
        }
        return null;
    }

    public enum ErrorCode {
        ITEM_TAG_ONLY,
        BLOCK_TAG_ONLY,
        MISSING_ITEM,
        MISSING_BLOCK,
        NOT_BLOCK_ITEM,
        BLOCK_ITEM_POINTS_ELSEWHERE
    }

    public static TagSnapshot snapshot() {
        Set<ResourceLocation> itemIds = tagIds(BuiltInRegistries.ITEM, MaterialRenderingModule.CASING_ITEMS);
        Set<ResourceLocation> blockIds = tagIds(BuiltInRegistries.BLOCK, MaterialRenderingModule.CASING_BLOCKS);
        Set<ResourceLocation> union = new HashSet<>(itemIds);
        union.addAll(blockIds);
        Map<ResourceLocation, Item> items = new HashMap<>();
        Map<ResourceLocation, Block> blocks = new HashMap<>();
        union.forEach(id -> {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> items.put(id, item));
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> blocks.put(id, block));
        });
        return new TagSnapshot(items, blocks, itemIds, blockIds);
    }

    private static <T> Set<ResourceLocation> tagIds(Registry<T> registry, TagKey<T> tag) {
        Set<ResourceLocation> ids = new HashSet<>();
        registry.getTag(tag).ifPresent(named -> named.forEach(holder -> ids.add(registry.getKey(holder.value()))));
        return Set.copyOf(ids);
    }

    public record MaterialValidationReport(List<ResourceLocation> validIds, List<Issue> issues) {
        public MaterialValidationReport {
            validIds = List.copyOf(validIds);
            issues = List.copyOf(issues);
        }

        public record Issue(ResourceLocation id, MaterialDiscovery.ErrorCode code) {
        }
    }

    public record TagSnapshot(
            Map<ResourceLocation, Item> items,
            Map<ResourceLocation, Block> blocks,
            Set<ResourceLocation> itemTagIds,
            Set<ResourceLocation> blockTagIds) {
        public TagSnapshot {
            items = Map.copyOf(items);
            blocks = Map.copyOf(blocks);
            itemTagIds = Set.copyOf(itemTagIds);
            blockTagIds = Set.copyOf(blockTagIds);
        }
    }
}

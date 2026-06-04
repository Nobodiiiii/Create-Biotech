package com.nobodiiiii.createbiotech.content.buttercat.register;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfigs {

    public static class Common {
        public final ForgeConfigSpec.IntValue maxButterCount;
        public final ForgeConfigSpec.IntValue maxInfiniteCapacity;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("ButterCat Mod Configuration")
                    .push("general");

            maxButterCount = builder
                    .comment("Max butter count [1-8192]")
                    .comment("Default:64")
                    .defineInRange("maxButterCount", 64, 1, 8192);

            maxInfiniteCapacity = builder
                    .comment(String.format("Max infinite capacity [2-%d]", Integer.MAX_VALUE / 256))
                    .comment("Default:576")
                    .defineInRange("maxInfiniteCapacity", 576, 2, Integer.MAX_VALUE / 256);

            builder.pop();
        }
    }

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> commonSpecPair =
                new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = commonSpecPair.getLeft();
        COMMON_SPEC = commonSpecPair.getRight();
    }
}

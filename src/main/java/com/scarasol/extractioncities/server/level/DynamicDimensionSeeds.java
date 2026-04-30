package com.scarasol.extractioncities.server.level;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicDimensionSeeds {
    private static final Map<ResourceKey<Level>, Long> SEEDS = new ConcurrentHashMap<>();

    private DynamicDimensionSeeds() {
    }

    public static void register(ResourceKey<Level> level, long seed) {
        SEEDS.put(level, seed);
    }

    public static void unregister(ResourceKey<Level> level) {
        SEEDS.remove(level);
    }

    public static void clear() {
        SEEDS.clear();
    }

    public static OptionalLong get(ResourceKey<Level> level) {
        Long seed = SEEDS.get(level);
        return seed == null ? OptionalLong.empty() : OptionalLong.of(seed);
    }
}

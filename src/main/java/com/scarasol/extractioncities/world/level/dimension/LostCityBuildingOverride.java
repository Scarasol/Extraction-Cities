package com.scarasol.extractioncities.world.level.dimension;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record LostCityBuildingOverride(
        int anchorX,
        int anchorZ,
        ResourceLocation buildingId,
        LostCityBuildingOverrideType type,
        int width,
        int height,
        Set<LostCityChunkPos> suppressedChunks
) {
    public LostCityBuildingOverride {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Lost Cities building override dimensions must be positive");
        }
        suppressedChunks = Set.copyOf(suppressedChunks);
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= anchorX
                && chunkX < anchorX + width
                && chunkZ >= anchorZ
                && chunkZ < anchorZ + height;
    }

    public boolean suppresses(int chunkX, int chunkZ) {
        return suppressedChunks.contains(new LostCityChunkPos(chunkX, chunkZ));
    }

    public boolean affects(int chunkX, int chunkZ) {
        return contains(chunkX, chunkZ) || suppresses(chunkX, chunkZ);
    }
}

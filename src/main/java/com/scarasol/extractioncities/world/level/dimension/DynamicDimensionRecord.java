package com.scarasol.extractioncities.world.level.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public record DynamicDimensionRecord(
        ResourceLocation id,
        ResourceLocation dimensionType,
        ResourceLocation generator,
        @Nullable ResourceLocation biome,
        DynamicDimensionStorageMode storage,
        long seed,
        boolean generateStructures,
        Set<ResourceLocation> structureWhitelist,
        boolean generateLostCities,
        String lostCitiesProfile,
        String lostCitiesWorldStyle,
        @Nullable GameType gameMode,
        @Nullable BlockPos teleportPoint,
        boolean allowRespawn,
        boolean save,
        List<LostCityBuildingOverride> lostCityBuildingOverrides
) {
    public DynamicDimensionRecord {
        lostCityBuildingOverrides = List.copyOf(lostCityBuildingOverrides);
    }
}

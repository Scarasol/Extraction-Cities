package com.scarasol.extractioncities.compat.tlc;

import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverride;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverrideType;
import com.scarasol.extractioncities.world.level.dimension.LostCityChunkPos;
import mcjty.lostcities.api.LostChunkCharacteristics;
import mcjty.lostcities.api.LostCityEvent;
import mcjty.lostcities.api.MultiPos;
import mcjty.lostcities.setup.Registration;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.cityassets.AssetRegistries;
import mcjty.lostcities.worldgen.lost.cityassets.Building;
import mcjty.lostcities.worldgen.lost.cityassets.MultiBuilding;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class TlcBuildingOverrides {
    private static final Map<ResourceLocation, IndexedOverrides> INDEXES = new HashMap<>();

    private TlcBuildingOverrides() {
    }

    public static synchronized void clearCache() {
        INDEXES.clear();
    }

    public static void applyCharacteristics(LostCityEvent.CharacteristicsEvent event) {
        OverridePiece piece = findPiece(event.getWorld(), event.getChunkX(), event.getChunkZ());
        if (piece == null) {
            return;
        }

        LostChunkCharacteristics characteristics = event.getCharacteristics();
        characteristics.isCity = true;
        if (piece.suppressed()) {
            characteristics.couldHaveBuilding = false;
            characteristics.multiPos = MultiPos.SINGLE;
            characteristics.multiBuilding = null;
            characteristics.multiBuildingId = null;
            return;
        }

        LostCityBuildingOverride override = piece.override();
        characteristics.cityLevel = cityLevelForOverride(event.getWorld(), characteristics.cityLevel, override);
        if (override.type() == LostCityBuildingOverrideType.BUILDING) {
            Building building = AssetRegistries.BUILDINGS.get(event.getWorld(), override.buildingId());
            if (building == null) {
                return;
            }
            characteristics.couldHaveBuilding = true;
            characteristics.multiPos = MultiPos.SINGLE;
            characteristics.multiBuilding = null;
            characteristics.multiBuildingId = null;
            characteristics.buildingType = building;
            characteristics.buildingTypeId = building.getId();
            return;
        }

        MultiBuilding multiBuilding = AssetRegistries.MULTI_BUILDINGS.get(event.getWorld(), override.buildingId());
        if (multiBuilding == null) {
            return;
        }

        String buildingName = multiBuilding.getBuilding(piece.offsetX(), piece.offsetZ());
        Building building = AssetRegistries.BUILDINGS.getOrThrow(event.getWorld(), buildingName);
        characteristics.couldHaveBuilding = true;
        characteristics.multiPos = new MultiPos(piece.offsetX(), piece.offsetZ(), multiBuilding.getDimX(), multiBuilding.getDimZ());
        characteristics.multiBuilding = multiBuilding;
        characteristics.multiBuildingId = multiBuilding.getId();
        characteristics.buildingType = building;
        characteristics.buildingTypeId = building.getId();
    }

    public static void enforceBuildingInfo(BuildingInfo info) {
        OverridePiece piece = findPiece(info.provider.getWorld(), info.coord.chunkX(), info.coord.chunkZ());
        if (piece == null) {
            return;
        }

        info.isCity = true;
        info.hasBuilding = !piece.suppressed();
    }

    private static int cityLevelForOverride(WorldGenLevel level, int fallbackCityLevel, LostCityBuildingOverride override) {
        IDimensionInfo dimensionInfo = Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
        if (dimensionInfo == null) {
            return fallbackCityLevel;
        }
        if (override.width() <= 1 && override.height() <= 1) {
            return BuildingInfo.getCityLevel(chunkCoord(dimensionInfo, override.anchorX(), override.anchorZ()), dimensionInfo);
        }
        if (dimensionInfo.getProfile().MULTI_USE_CORNER) {
            return BuildingInfo.getCityLevel(chunkCoord(dimensionInfo, override.anchorX(), override.anchorZ()), dimensionInfo);
        }

        int total = 0;
        for (int x = 0; x < override.width(); x++) {
            for (int z = 0; z < override.height(); z++) {
                total += BuildingInfo.getCityLevel(chunkCoord(dimensionInfo, override.anchorX() + x, override.anchorZ() + z), dimensionInfo);
            }
        }
        return total / (override.width() * override.height());
    }

    @Nullable
    private static OverridePiece findPiece(WorldGenLevel level, int chunkX, int chunkZ) {
        ResourceLocation dimension = level.getLevel().dimension().location();
        DynamicDimensionRecord record = DynamicDimensionManager.getRecord(dimension).orElse(null);
        if (record == null || !record.generateLostCities() || record.lostCityBuildingOverrides().isEmpty()) {
            return null;
        }

        return indexFor(record).pieces().get(chunkKey(chunkX, chunkZ));
    }

    private static synchronized IndexedOverrides indexFor(DynamicDimensionRecord record) {
        return INDEXES.computeIfAbsent(record.id(), id -> {
            Map<Long, OverridePiece> pieces = new HashMap<>();
            for (LostCityBuildingOverride override : record.lostCityBuildingOverrides()) {
                for (int x = 0; x < override.width(); x++) {
                    for (int z = 0; z < override.height(); z++) {
                        pieces.put(chunkKey(override.anchorX() + x, override.anchorZ() + z), new OverridePiece(override, x, z, false));
                    }
                }
            }

            for (LostCityBuildingOverride override : record.lostCityBuildingOverrides()) {
                for (LostCityChunkPos chunk : override.suppressedChunks()) {
                    pieces.putIfAbsent(chunkKey(chunk.x(), chunk.z()), new OverridePiece(override, 0, 0, true));
                }
            }
            return new IndexedOverrides(pieces);
        });
    }

    static ChunkCoord chunkCoord(IDimensionInfo dimensionInfo, int chunkX, int chunkZ) {
        return new ChunkCoord(dimensionInfo.getType(), chunkX, chunkZ);
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private record IndexedOverrides(Map<Long, OverridePiece> pieces) {
    }

    private record OverridePiece(LostCityBuildingOverride override, int offsetX, int offsetZ, boolean suppressed) {
    }
}

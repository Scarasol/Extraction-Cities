package com.scarasol.extractioncities.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.configuration.CommonConfig;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionStorageMode;
import com.scarasol.extractioncities.world.level.dimension.ExtractionCitiesDimensions;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverride;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverrideType;
import com.scarasol.extractioncities.world.level.dimension.LostCityChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DynamicDimensionManifestStorage {
    private static final int MANIFEST_SCHEMA = 13;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DynamicDimensionManifestStorage() {
    }

    public static List<DynamicDimensionRecord> readPersistent(MinecraftServer server) throws IOException {
        Path path = manifestPath(server);
        if (!Files.isRegularFile(path)) {
            return List.of();
        }

        ManifestFile manifest;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            manifest = GSON.fromJson(reader, ManifestFile.class);
        }

        if (manifest == null || manifest.dimensions == null) {
            return List.of();
        }

        long defaultSeed = server.getWorldData().worldGenOptions().seed();
        List<DynamicDimensionRecord> records = new ArrayList<>();
        for (ManifestEntry entry : manifest.dimensions) {
            DynamicDimensionRecord record = toRecord(entry, defaultSeed);
            if (record != null && record.storage() == DynamicDimensionStorageMode.PERSISTENT) {
                records.add(record);
            }
        }
        return records;
    }

    public static void savePersistent(MinecraftServer server, Collection<DynamicDimensionRecord> records) throws IOException {
        ManifestFile manifest = new ManifestFile();
        manifest.schema = MANIFEST_SCHEMA;
        manifest.dimensions = records.stream()
                .filter(record -> record.storage() == DynamicDimensionStorageMode.PERSISTENT)
                .sorted(Comparator.comparing(record -> record.id().toString()))
                .map(DynamicDimensionManifestStorage::toManifestEntry)
                .toList();

        Path path = manifestPath(server);
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(manifest, writer);
        }
    }

    private static Path manifestPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(ExtractionCitiesMod.MODID)
                .resolve("dynamic_dimensions.json")
                .normalize();
    }

    private static DynamicDimensionRecord toRecord(ManifestEntry entry, long defaultSeed) {
        if (entry == null || entry.id == null || entry.id.isBlank()) {
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(entry.id);
        ResourceLocation dimensionType = entry.dimensionType == null || entry.dimensionType.isBlank()
                ? ExtractionCitiesDimensions.DIMENSION_TYPE_ID
                : ResourceLocation.tryParse(entry.dimensionType);
        ResourceLocation generator = entry.generator == null || entry.generator.isBlank()
                ? ExtractionCitiesDimensions.OVERWORLD_GENERATOR_ID
                : ResourceLocation.tryParse(entry.generator);
        ResourceLocation biome = entry.biome == null || entry.biome.isBlank()
                ? null
                : ResourceLocation.tryParse(entry.biome);
        DynamicDimensionStorageMode storage = entry.storage == null || entry.storage.isBlank()
                ? DynamicDimensionStorageMode.PERSISTENT
                : DynamicDimensionStorageMode.byId(entry.storage).orElse(DynamicDimensionStorageMode.PERSISTENT);
        long seed = entry.seed == null ? defaultSeed : entry.seed;
        boolean generateStructures = entry.generateStructures == null
                ? CommonConfig.DEFAULT_GENERATE_STRUCTURES.get()
                : entry.generateStructures;
        Set<ResourceLocation> structureWhitelist = toResourceLocationSet(entry.structureWhitelist, "structure whitelist", id);
        boolean generateLostCities = entry.generateLostCities == null
                ? CommonConfig.DEFAULT_GENERATE_LOST_CITIES.get()
                : entry.generateLostCities;
        String lostCitiesProfile = entry.lostCitiesProfile == null || entry.lostCitiesProfile.isBlank()
                ? CommonConfig.defaultLostCitiesProfile()
                : entry.lostCitiesProfile.trim();
        String lostCitiesWorldStyle = entry.lostCitiesWorldStyle == null || entry.lostCitiesWorldStyle.isBlank()
                ? CommonConfig.defaultLostCitiesWorldStyle()
                : entry.lostCitiesWorldStyle.trim();
        GameType gameMode = entry.gameMode == null || entry.gameMode.isBlank()
                ? CommonConfig.defaultGameMode()
                : GameType.byName(entry.gameMode, null);
        BlockPos teleportPoint = toBlockPos(entry.teleportPoint);
        boolean allowRespawn = entry.allowRespawn == null
                ? CommonConfig.DEFAULT_ALLOW_RESPAWN.get()
                : entry.allowRespawn;
        boolean save = entry.save == null
                ? CommonConfig.DEFAULT_SAVE.get()
                : entry.save;
        List<LostCityBuildingOverride> lostCityBuildingOverrides = toLostCityBuildingOverrides(entry.lostCityBuildingOverrides, id);

        if (id == null || dimensionType == null || generator == null || (entry.biome != null && !entry.biome.isBlank() && biome == null)
                || (entry.gameMode != null && !entry.gameMode.isBlank() && gameMode == null)) {
            ExtractionCitiesMod.LOGGER.warn("Skipping invalid dynamic dimension manifest entry {}", entry.id);
            return null;
        }

        if (entry.teleportPoint != null && teleportPoint == null) {
            ExtractionCitiesMod.LOGGER.warn("Ignoring invalid teleport point for dynamic dimension {}", id);
        }

        if (!ExtractionCitiesDimensions.DIMENSION_TYPE_ID.equals(dimensionType)) {
            ExtractionCitiesMod.LOGGER.warn("Skipping dynamic dimension {} because it uses unsupported dimension type {}", id, dimensionType);
            return null;
        }

        return new DynamicDimensionRecord(id, dimensionType, generator, biome, storage, seed, generateStructures, structureWhitelist, generateLostCities, lostCitiesProfile, lostCitiesWorldStyle, gameMode, teleportPoint, allowRespawn, save, lostCityBuildingOverrides);
    }

    private static ManifestEntry toManifestEntry(DynamicDimensionRecord record) {
        ManifestEntry entry = new ManifestEntry();
        entry.id = record.id().toString();
        entry.dimensionType = record.dimensionType().toString();
        entry.generator = record.generator().toString();
        entry.biome = record.biome() == null ? null : record.biome().toString();
        entry.storage = record.storage().id();
        entry.seed = record.seed();
        entry.generateStructures = record.generateStructures();
        entry.structureWhitelist = record.structureWhitelist().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
        entry.generateLostCities = record.generateLostCities();
        entry.lostCitiesProfile = record.lostCitiesProfile();
        entry.lostCitiesWorldStyle = record.lostCitiesWorldStyle();
        entry.gameMode = record.gameMode() == null ? null : record.gameMode().getName();
        entry.teleportPoint = toManifestBlockPos(record.teleportPoint());
        entry.allowRespawn = record.allowRespawn();
        entry.save = record.save();
        entry.lostCityBuildingOverrides = record.lostCityBuildingOverrides().stream()
                .sorted(Comparator.comparingInt(LostCityBuildingOverride::anchorX)
                        .thenComparingInt(LostCityBuildingOverride::anchorZ)
                        .thenComparing(override -> override.buildingId().toString()))
                .map(DynamicDimensionManifestStorage::toManifestLostCityBuildingOverride)
                .toList();
        return entry;
    }

    private static BlockPos toBlockPos(ManifestBlockPos position) {
        if (position == null || position.x == null || position.y == null || position.z == null) {
            return null;
        }

        BlockPos blockPos = new BlockPos(position.x, position.y, position.z);
        return Level.isInSpawnableBounds(blockPos) ? blockPos : null;
    }

    private static ManifestBlockPos toManifestBlockPos(BlockPos position) {
        if (position == null) {
            return null;
        }

        ManifestBlockPos blockPos = new ManifestBlockPos();
        blockPos.x = position.getX();
        blockPos.y = position.getY();
        blockPos.z = position.getZ();
        return blockPos;
    }

    private static Set<ResourceLocation> toResourceLocationSet(List<String> values, String fieldName, ResourceLocation dimensionId) {
        if (values == null) {
            return CommonConfig.defaultStructureWhitelist();
        }

        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                ExtractionCitiesMod.LOGGER.warn("Ignoring invalid {} entry '{}' for dynamic dimension {}", fieldName, value, dimensionId);
                continue;
            }

            result.add(id);
        }
        return Set.copyOf(result);
    }

    private static List<LostCityBuildingOverride> toLostCityBuildingOverrides(List<ManifestLostCityBuildingOverride> entries, ResourceLocation dimensionId) {
        if (entries == null) {
            return List.of();
        }

        List<LostCityBuildingOverride> result = new ArrayList<>();
        for (ManifestLostCityBuildingOverride entry : entries) {
            LostCityBuildingOverride override = toLostCityBuildingOverride(entry, dimensionId);
            if (override != null) {
                result.add(override);
            }
        }
        return List.copyOf(result);
    }

    private static LostCityBuildingOverride toLostCityBuildingOverride(ManifestLostCityBuildingOverride entry, ResourceLocation dimensionId) {
        if (entry == null || entry.x == null || entry.z == null || entry.id == null || entry.id.isBlank()) {
            return null;
        }

        ResourceLocation buildingId = ResourceLocation.tryParse(entry.id);
        LostCityBuildingOverrideType type = entry.type == null || entry.type.isBlank()
                ? LostCityBuildingOverrideType.BUILDING
                : LostCityBuildingOverrideType.byId(entry.type).orElse(null);
        int width = entry.width == null ? 1 : entry.width;
        int height = entry.height == null ? 1 : entry.height;
        if (buildingId == null || type == null || width < 1 || height < 1) {
            ExtractionCitiesMod.LOGGER.warn("Ignoring invalid Lost Cities building override '{}' for dynamic dimension {}", entry.id, dimensionId);
            return null;
        }

        return new LostCityBuildingOverride(entry.x, entry.z, buildingId, type, width, height, toLostCityChunkSet(entry.suppressedChunks));
    }

    private static Set<LostCityChunkPos> toLostCityChunkSet(List<ManifestChunkPos> positions) {
        if (positions == null) {
            return Set.of();
        }

        Set<LostCityChunkPos> result = new LinkedHashSet<>();
        for (ManifestChunkPos position : positions) {
            if (position != null && position.x != null && position.z != null) {
                result.add(new LostCityChunkPos(position.x, position.z));
            }
        }
        return Set.copyOf(result);
    }

    private static ManifestLostCityBuildingOverride toManifestLostCityBuildingOverride(LostCityBuildingOverride override) {
        ManifestLostCityBuildingOverride entry = new ManifestLostCityBuildingOverride();
        entry.x = override.anchorX();
        entry.z = override.anchorZ();
        entry.id = override.buildingId().toString();
        entry.type = override.type().id();
        entry.width = override.width();
        entry.height = override.height();
        entry.suppressedChunks = override.suppressedChunks().stream()
                .sorted(Comparator.comparingInt(LostCityChunkPos::x).thenComparingInt(LostCityChunkPos::z))
                .map(DynamicDimensionManifestStorage::toManifestChunkPos)
                .toList();
        return entry;
    }

    private static ManifestChunkPos toManifestChunkPos(LostCityChunkPos position) {
        ManifestChunkPos entry = new ManifestChunkPos();
        entry.x = position.x();
        entry.z = position.z();
        return entry;
    }

    private static final class ManifestFile {
        int schema = MANIFEST_SCHEMA;
        List<ManifestEntry> dimensions = List.of();
    }

    private static final class ManifestEntry {
        String id;
        String dimensionType;
        String generator;
        String biome;
        String storage;
        Long seed;
        Boolean generateStructures;
        List<String> structureWhitelist;
        Boolean generateLostCities;
        String lostCitiesProfile;
        String lostCitiesWorldStyle;
        String gameMode;
        ManifestBlockPos teleportPoint;
        Boolean allowRespawn;
        Boolean save;
        List<ManifestLostCityBuildingOverride> lostCityBuildingOverrides;
    }

    private static final class ManifestBlockPos {
        Integer x;
        Integer y;
        Integer z;
    }

    private static final class ManifestLostCityBuildingOverride {
        Integer x;
        Integer z;
        String id;
        String type;
        Integer width;
        Integer height;
        List<ManifestChunkPos> suppressedChunks;
    }

    private static final class ManifestChunkPos {
        Integer x;
        Integer z;
    }
}

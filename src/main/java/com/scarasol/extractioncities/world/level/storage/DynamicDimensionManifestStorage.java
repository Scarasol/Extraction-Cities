package com.scarasol.extractioncities.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.configuration.CommonConfig;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionStorageMode;
import com.scarasol.extractioncities.world.level.dimension.ExtractionCitiesDimensions;
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
import java.util.List;

public final class DynamicDimensionManifestStorage {
    private static final int MANIFEST_SCHEMA = 8;
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
        boolean generateLostCities = entry.generateLostCities == null
                ? CommonConfig.DEFAULT_GENERATE_LOST_CITIES.get()
                : entry.generateLostCities;
        GameType gameMode = entry.gameMode == null || entry.gameMode.isBlank()
                ? CommonConfig.defaultGameMode()
                : GameType.byName(entry.gameMode, null);
        BlockPos teleportPoint = toBlockPos(entry.teleportPoint);
        boolean allowRespawn = entry.allowRespawn == null
                ? CommonConfig.DEFAULT_ALLOW_RESPAWN.get()
                : entry.allowRespawn;

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

        return new DynamicDimensionRecord(id, dimensionType, generator, biome, storage, seed, generateStructures, generateLostCities, gameMode, teleportPoint, allowRespawn);
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
        entry.generateLostCities = record.generateLostCities();
        entry.gameMode = record.gameMode() == null ? null : record.gameMode().getName();
        entry.teleportPoint = toManifestBlockPos(record.teleportPoint());
        entry.allowRespawn = record.allowRespawn();
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
        Boolean generateLostCities;
        String gameMode;
        ManifestBlockPos teleportPoint;
        Boolean allowRespawn;
    }

    private static final class ManifestBlockPos {
        Integer x;
        Integer y;
        Integer z;
    }
}

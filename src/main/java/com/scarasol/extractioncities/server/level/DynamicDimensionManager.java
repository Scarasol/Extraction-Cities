package com.scarasol.extractioncities.server.level;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.configuration.CommonConfig;
import com.scarasol.extractioncities.mixin.accessor.MinecraftServerAccessor;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionStorageMode;
import com.scarasol.extractioncities.world.level.dimension.ExtractionCitiesDimensions;
import com.scarasol.extractioncities.world.level.storage.DynamicDimensionManifestStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class DynamicDimensionManager {
    private static final int EXTRACTION_CITY_MIN_Y = -64;
    private static final int FLAT_TERRAIN_BASE_Y = 0;
    private static final Map<ResourceLocation, DynamicDimensionRecord> DYNAMIC_DIMENSIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, BorderChangeListener> BORDER_LISTENERS = new LinkedHashMap<>();

    private DynamicDimensionManager() {
    }

    public static void onServerStarting(MinecraftServer server) {
        try {
            List<DynamicDimensionRecord> records = DynamicDimensionManifestStorage.readPersistent(server);
            cleanupStaleSessionDimensionFiles(server, records);
            restorePersistentDimensions(server, records);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.error("Failed to restore dynamic dimensions", exception);
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        try {
            cleanupSessionDimensionFiles(server);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to clean session dynamic dimension files", exception);
        }
        DYNAMIC_DIMENSIONS.clear();
        BORDER_LISTENERS.clear();
        DynamicDimensionSeeds.clear();
    }

    public static synchronized ServerLevel createDimension(MinecraftServer server, ResourceLocation id, DynamicDimensionStorageMode storage, long seed) throws IOException {
        return createDimension(server, id, storage, seed, ExtractionCitiesDimensions.OVERWORLD_GENERATOR_ID, null);
    }

    public static synchronized ServerLevel createDimension(MinecraftServer server, ResourceLocation id, DynamicDimensionStorageMode storage, long seed, ResourceLocation generator, @Nullable ResourceLocation biome) throws IOException {
        DynamicDimensionRecord record = new DynamicDimensionRecord(
                id,
                ExtractionCitiesDimensions.DIMENSION_TYPE_ID,
                generator,
                biome,
                storage,
                seed,
                CommonConfig.DEFAULT_GENERATE_STRUCTURES.get(),
                CommonConfig.defaultGameMode(),
                null,
                CommonConfig.DEFAULT_ALLOW_RESPAWN.get());
        return createDimension(server, record, true);
    }

    public static synchronized Collection<DynamicDimensionRecord> dynamicDimensions() {
        return List.copyOf(DYNAMIC_DIMENSIONS.values());
    }

    public static synchronized Optional<DynamicDimensionRecord> getRecord(ResourceLocation id) {
        return Optional.ofNullable(DYNAMIC_DIMENSIONS.get(id));
    }

    public static synchronized DynamicDimensionRecord setGenerateStructures(MinecraftServer server, ResourceLocation id, boolean generateStructures) throws IOException {
        DynamicDimensionRecord record = DYNAMIC_DIMENSIONS.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Unknown dynamic dimension: " + id);
        }

        DynamicDimensionRecord updated = new DynamicDimensionRecord(
                record.id(),
                record.dimensionType(),
                record.generator(),
                record.biome(),
                record.storage(),
                record.seed(),
                generateStructures,
                record.gameMode(),
                record.teleportPoint(),
                record.allowRespawn());
        DYNAMIC_DIMENSIONS.put(id, updated);
        if (updated.storage() == DynamicDimensionStorageMode.PERSISTENT) {
            saveManifest(server);
        }
        return updated;
    }

    public static synchronized DynamicDimensionRecord setGameMode(MinecraftServer server, ResourceLocation id, @Nullable GameType gameMode) throws IOException {
        DynamicDimensionRecord record = DYNAMIC_DIMENSIONS.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Unknown dynamic dimension: " + id);
        }

        DynamicDimensionRecord updated = new DynamicDimensionRecord(
                record.id(),
                record.dimensionType(),
                record.generator(),
                record.biome(),
                record.storage(),
                record.seed(),
                record.generateStructures(),
                gameMode,
                record.teleportPoint(),
                record.allowRespawn());
        DYNAMIC_DIMENSIONS.put(id, updated);
        if (updated.storage() == DynamicDimensionStorageMode.PERSISTENT) {
            saveManifest(server);
        }
        return updated;
    }

    public static synchronized DynamicDimensionRecord setTeleportPoint(MinecraftServer server, ResourceLocation id, @Nullable BlockPos teleportPoint) throws IOException {
        DynamicDimensionRecord record = DYNAMIC_DIMENSIONS.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Unknown dynamic dimension: " + id);
        }

        DynamicDimensionRecord updated = new DynamicDimensionRecord(
                record.id(),
                record.dimensionType(),
                record.generator(),
                record.biome(),
                record.storage(),
                record.seed(),
                record.generateStructures(),
                record.gameMode(),
                teleportPoint,
                record.allowRespawn());
        DYNAMIC_DIMENSIONS.put(id, updated);
        if (updated.storage() == DynamicDimensionStorageMode.PERSISTENT) {
            saveManifest(server);
        }
        return updated;
    }

    public static synchronized DynamicDimensionRecord setAllowRespawn(MinecraftServer server, ResourceLocation id, boolean allowRespawn) throws IOException {
        DynamicDimensionRecord record = DYNAMIC_DIMENSIONS.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Unknown dynamic dimension: " + id);
        }

        DynamicDimensionRecord updated = new DynamicDimensionRecord(
                record.id(),
                record.dimensionType(),
                record.generator(),
                record.biome(),
                record.storage(),
                record.seed(),
                record.generateStructures(),
                record.gameMode(),
                record.teleportPoint(),
                allowRespawn);
        DYNAMIC_DIMENSIONS.put(id, updated);
        if (updated.storage() == DynamicDimensionStorageMode.PERSISTENT) {
            saveManifest(server);
        }
        return updated;
    }

    public static boolean shouldGenerateStructures(ServerLevel level, boolean fallback) {
        return getRecord(level.dimension().location())
                .map(DynamicDimensionRecord::generateStructures)
                .orElse(fallback);
    }

    public static boolean shouldGenerateStructures(LevelAccessor level, boolean fallback) {
        if (level instanceof ServerLevel serverLevel) {
            return shouldGenerateStructures(serverLevel, fallback);
        }

        if (level instanceof WorldGenRegion region) {
            return shouldGenerateStructures(region.getLevel(), fallback);
        }

        return fallback;
    }

    public static Optional<ServerLevel> getLevel(MinecraftServer server, ResourceLocation id) {
        return Optional.ofNullable(server.getLevel(ExtractionCitiesDimensions.levelKey(id)));
    }

    public static synchronized DeleteResult deleteDimension(MinecraftServer server, ResourceLocation id, boolean force) throws IOException, DynamicDimensionOccupiedException {
        DynamicDimensionRecord record = DYNAMIC_DIMENSIONS.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Unknown dynamic dimension: " + id);
        }

        ResourceKey<Level> levelKey = ExtractionCitiesDimensions.levelKey(id);
        List<ServerPlayer> players = playersInDimension(server, levelKey);
        if (!force && !players.isEmpty()) {
            throw new DynamicDimensionOccupiedException(players.size());
        }

        for (ServerPlayer player : players) {
            DynamicDimensionRespawns.teleportToOverworldRespawnOrSpawn(player);
        }

        ServerLevel level = server.getLevel(levelKey);
        removeBorderListener(server, id);
        if (level != null) {
            unloadDynamicLevel(server, levelKey, level);
        }

        DYNAMIC_DIMENSIONS.remove(id);
        DynamicDimensionSeeds.unregister(levelKey);
        if (record.storage() == DynamicDimensionStorageMode.PERSISTENT) {
            saveManifest(server);
        }
        DynamicDimensionRespawns.removeDimension(server, id);
        deleteDimensionDirectory(server, levelKey);
        return new DeleteResult(players.size());
    }

    private static void restorePersistentDimensions(MinecraftServer server, List<DynamicDimensionRecord> records) {
        for (DynamicDimensionRecord record : records) {
            try {
                createDimension(server, record, false);
                ExtractionCitiesMod.LOGGER.info("Restored dynamic dimension {}", record.id());
            } catch (Exception exception) {
                ExtractionCitiesMod.LOGGER.warn("Could not restore dynamic dimension {}", record.id(), exception);
            }
        }
    }

    private static ServerLevel createDimension(MinecraftServer server, DynamicDimensionRecord record, boolean writeManifest) throws IOException {
        validateManagedId(record.id());

        ResourceKey<Level> levelKey = ExtractionCitiesDimensions.levelKey(record.id());
        if (server.getLevel(levelKey) != null) {
            throw new IllegalArgumentException("Dimension already exists: " + record.id());
        }

        Holder<DimensionType> dimensionType = getDimensionType(server, record.dimensionType());
        ChunkGenerator generator = getGenerator(server, record.generator(), record.biome());
        LevelStem stem = new LevelStem(dimensionType, generator);
        DynamicDimensionSeeds.register(levelKey, record.seed());
        ServerLevel level;
        try {
            level = ExtractionCitiesLevelFactory.createDynamicLevel(server, levelKey, stem, record.seed());
        } catch (RuntimeException exception) {
            DynamicDimensionSeeds.unregister(levelKey);
            throw exception;
        }

        BorderChangeListener borderListener = new BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder());
        server.overworld().getWorldBorder().addListener(borderListener);
        BORDER_LISTENERS.put(record.id(), borderListener);
        server.forgeGetWorldMap().put(levelKey, level);
        server.markWorldsDirty();
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(level));

        DYNAMIC_DIMENSIONS.put(record.id(), record);
        if (writeManifest && record.storage() == DynamicDimensionStorageMode.PERSISTENT) {
            saveManifest(server);
        }

        return level;
    }

    private static void unloadDynamicLevel(MinecraftServer server, ResourceKey<Level> levelKey, ServerLevel level) throws IOException {
        level.noSave = true;
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        try {
            level.close();
        } finally {
            server.forgeGetWorldMap().remove(levelKey);
            server.markWorldsDirty();
        }
    }

    private static void removeBorderListener(MinecraftServer server, ResourceLocation id) {
        BorderChangeListener listener = BORDER_LISTENERS.remove(id);
        if (listener != null) {
            server.overworld().getWorldBorder().removeListener(listener);
        }
    }

    private static Holder<DimensionType> getDimensionType(MinecraftServer server, ResourceLocation id) {
        ResourceKey<DimensionType> key = ExtractionCitiesDimensions.dimensionTypeKey(id);
        return server.registryAccess()
                .registryOrThrow(Registries.DIMENSION_TYPE)
                .getHolder(key)
                .orElseThrow(() -> new IllegalStateException("Missing dimension type: " + id));
    }

    private static ChunkGenerator getGenerator(MinecraftServer server, ResourceLocation id, @Nullable ResourceLocation biome) {
        if (ExtractionCitiesDimensions.OVERWORLD_GENERATOR_ID.equals(id)) {
            return getOverworldGenerator(server);
        }

        if (ExtractionCitiesDimensions.FLAT_GENERATOR_ID.equals(id)) {
            return createFlatGenerator(server, biome);
        }

        if (ExtractionCitiesDimensions.SINGLE_BIOME_GENERATOR_ID.equals(id)) {
            return createSingleBiomeGenerator(server, biome);
        }

        throw new IllegalArgumentException("Unsupported generator: " + id);
    }

    private static ChunkGenerator getOverworldGenerator(MinecraftServer server) {
        Registry<LevelStem> stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        LevelStem overworld = stems.get(LevelStem.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Missing overworld LevelStem");
        }
        return overworld.generator();
    }

    private static ChunkGenerator createFlatGenerator(MinecraftServer server, @Nullable ResourceLocation biomeId) {
        HolderGetter<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        HolderGetter<StructureSet> structureSets = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        HolderGetter<PlacedFeature> placedFeatures = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        Holder<Biome> biome = getBiome(server, biomeId == null ? ExtractionCitiesDimensions.DEFAULT_BIOME_ID : biomeId);

        FlatLevelGeneratorSettings settings = FlatLevelGeneratorSettings.getDefault(biomes, structureSets, placedFeatures);
        settings = settings.withBiomeAndLayers(settings.getLayersInfo(), settings.structureOverrides(), biome);
        raiseFlatTerrain(settings);
        return new FlatLevelSource(settings);
    }

    private static void raiseFlatTerrain(FlatLevelGeneratorSettings settings) {
        int airPadding = FLAT_TERRAIN_BASE_Y - EXTRACTION_CITY_MIN_Y;
        if (airPadding > 0) {
            settings.getLayersInfo().add(0, new FlatLayerInfo(airPadding, Blocks.AIR));
            settings.updateLayers();
        }
    }

    private static ChunkGenerator createSingleBiomeGenerator(MinecraftServer server, @Nullable ResourceLocation biomeId) {
        if (biomeId == null) {
            throw new IllegalArgumentException("Single biome generator requires a biome");
        }

        ChunkGenerator overworldGenerator = getOverworldGenerator(server);
        if (!(overworldGenerator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException("Single biome generator requires a noise-based overworld generator");
        }

        return new NoiseBasedChunkGenerator(new FixedBiomeSource(getBiome(server, biomeId)), noiseGenerator.generatorSettings());
    }

    private static Holder<Biome> getBiome(MinecraftServer server, ResourceLocation id) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
        return server.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(key)
                .orElseThrow(() -> new IllegalArgumentException("Missing biome: " + id));
    }

    private static void validateManagedId(ResourceLocation id) {
        if (!ExtractionCitiesDimensions.isManagedNamespace(id)) {
            throw new IllegalArgumentException("Dynamic dimensions must use namespace " + ExtractionCitiesMod.MODID + ": " + id);
        }
    }

    private static void saveManifest(MinecraftServer server) throws IOException {
        DynamicDimensionManifestStorage.savePersistent(server, DYNAMIC_DIMENSIONS.values());
    }

    private static List<ServerPlayer> playersInDimension(MinecraftServer server, ResourceKey<Level> levelKey) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> levelKey.equals(player.level().dimension()))
                .toList();
    }

    private static void cleanupSessionDimensionFiles(MinecraftServer server) throws IOException {
        List<ResourceLocation> sessions = DYNAMIC_DIMENSIONS.values().stream()
                .filter(record -> record.storage() == DynamicDimensionStorageMode.SESSION)
                .map(DynamicDimensionRecord::id)
                .toList();

        for (ResourceLocation id : sessions) {
            deleteDimensionDirectory(server, ExtractionCitiesDimensions.levelKey(id));
        }
    }

    private static void cleanupStaleSessionDimensionFiles(MinecraftServer server, Collection<DynamicDimensionRecord> persistentRecords) throws IOException {
        Path namespaceRoot = managedNamespaceRoot(server);
        if (!Files.isDirectory(namespaceRoot)) {
            return;
        }

        Set<Path> persistentPaths = new HashSet<>();
        for (DynamicDimensionRecord record : persistentRecords) {
            if (record.storage() == DynamicDimensionStorageMode.PERSISTENT) {
                persistentPaths.add(checkedManagedDimensionPath(server, ExtractionCitiesDimensions.levelKey(record.id())));
            }
        }

        try (Stream<Path> stream = Files.list(namespaceRoot)) {
            List<Path> staleRoots = stream
                    .filter(Files::isDirectory)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> persistentPaths.stream().noneMatch(persistentPath -> persistentPath.startsWith(path)))
                    .toList();
            for (Path staleRoot : staleRoots) {
                deleteRecursively(staleRoot, namespaceRoot);
            }
        }
    }

    private static void deleteDimensionDirectory(MinecraftServer server, ResourceKey<Level> levelKey) throws IOException {
        Path namespaceRoot = managedNamespaceRoot(server);
        Path dimensionPath = checkedManagedDimensionPath(server, levelKey);
        deleteRecursively(dimensionPath, namespaceRoot);
    }

    private static Path checkedManagedDimensionPath(MinecraftServer server, ResourceKey<Level> levelKey) throws IOException {
        Path namespaceRoot = managedNamespaceRoot(server);
        Path dimensionPath = ((MinecraftServerAccessor) server).extractioncities$getStorageSource()
                .getDimensionPath(levelKey)
                .toAbsolutePath()
                .normalize();
        if (!dimensionPath.startsWith(namespaceRoot) || dimensionPath.equals(namespaceRoot)) {
            throw new IOException("Refusing to delete unmanaged dimension path: " + dimensionPath);
        }
        return dimensionPath;
    }

    private static Path managedNamespaceRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve(ExtractionCitiesMod.MODID)
                .toAbsolutePath()
                .normalize();
    }

    private static void deleteRecursively(Path path, Path allowedRoot) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Path root = allowedRoot.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Refusing to delete path outside managed dimensions root: " + target);
        }

        if (!Files.exists(target)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(target)) {
            List<Path> paths = stream
                    .sorted((left, right) -> right.compareTo(left))
                    .toList();
            for (Path entry : paths) {
                Files.deleteIfExists(entry);
            }
        }
    }

    public record DeleteResult(int movedPlayers) {
    }

    public static final class DynamicDimensionOccupiedException extends Exception {
        private final int playerCount;

        private DynamicDimensionOccupiedException(int playerCount) {
            this.playerCount = playerCount;
        }

        public int playerCount() {
            return playerCount;
        }
    }
}

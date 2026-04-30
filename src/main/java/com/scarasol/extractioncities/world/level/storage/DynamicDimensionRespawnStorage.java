package com.scarasol.extractioncities.world.level.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DynamicDimensionRespawnStorage {
    private static final int RESPAWN_SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ResourceLocation, Map<UUID, RespawnPoint>> RESPAWNS = new LinkedHashMap<>();

    private DynamicDimensionRespawnStorage() {
    }

    public static synchronized void readPersistent(MinecraftServer server, Collection<ResourceLocation> persistentDimensions) throws IOException {
        RESPAWNS.clear();

        Path path = respawnPath(server);
        if (!Files.isRegularFile(path)) {
            return;
        }

        RespawnFile file;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            file = GSON.fromJson(reader, RespawnFile.class);
        }

        if (file == null || file.respawns == null) {
            return;
        }

        Set<ResourceLocation> persistentIds = new HashSet<>(persistentDimensions);
        for (RespawnEntry entry : file.respawns) {
            RespawnRecord record = toRecord(entry);
            if (record != null && persistentIds.contains(record.dimension())) {
                set(record.dimension(), record.player(), record.respawn());
            }
        }
    }

    public static synchronized void savePersistent(MinecraftServer server, Collection<ResourceLocation> persistentDimensions) throws IOException {
        Set<ResourceLocation> persistentIds = new HashSet<>(persistentDimensions);
        ArrayList<RespawnEntry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Map<UUID, RespawnPoint>> dimensionEntry : RESPAWNS.entrySet()) {
            if (!persistentIds.contains(dimensionEntry.getKey())) {
                continue;
            }

            for (Map.Entry<UUID, RespawnPoint> playerEntry : dimensionEntry.getValue().entrySet()) {
                entries.add(toEntry(dimensionEntry.getKey(), playerEntry.getKey(), playerEntry.getValue()));
            }
        }

        entries.sort(Comparator
                .comparing((RespawnEntry entry) -> entry.dimension)
                .thenComparing(entry -> entry.player));

        RespawnFile file = new RespawnFile();
        file.schema = RESPAWN_SCHEMA;
        file.respawns = entries;

        Path path = respawnPath(server);
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(file, writer);
        }
    }

    public static synchronized Optional<RespawnPoint> get(ResourceLocation dimension, UUID player) {
        Map<UUID, RespawnPoint> dimensionRespawns = RESPAWNS.get(dimension);
        if (dimensionRespawns == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(dimensionRespawns.get(player));
    }

    public static synchronized void set(ResourceLocation dimension, UUID player, RespawnPoint respawn) {
        RESPAWNS.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).put(player, respawn);
    }

    public static synchronized void remove(ResourceLocation dimension, UUID player) {
        Map<UUID, RespawnPoint> dimensionRespawns = RESPAWNS.get(dimension);
        if (dimensionRespawns == null) {
            return;
        }

        dimensionRespawns.remove(player);
        if (dimensionRespawns.isEmpty()) {
            RESPAWNS.remove(dimension);
        }
    }

    public static synchronized void removeDimension(ResourceLocation dimension) {
        RESPAWNS.remove(dimension);
    }

    public static synchronized void clear() {
        RESPAWNS.clear();
    }

    private static Path respawnPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(ExtractionCitiesMod.MODID)
                .resolve("dynamic_dimension_respawns.json")
                .normalize();
    }

    private static RespawnRecord toRecord(RespawnEntry entry) {
        if (entry == null || entry.dimension == null || entry.dimension.isBlank() || entry.player == null || entry.player.isBlank()) {
            return null;
        }

        ResourceLocation dimension = ResourceLocation.tryParse(entry.dimension);
        UUID player;
        try {
            player = UUID.fromString(entry.player);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        RespawnPoint respawn = toRespawnPoint(entry);
        if (dimension == null || respawn == null) {
            return null;
        }

        return new RespawnRecord(dimension, player, respawn);
    }

    private static RespawnEntry toEntry(ResourceLocation dimension, UUID player, RespawnPoint respawn) {
        RespawnEntry entry = new RespawnEntry();
        entry.dimension = dimension.toString();
        entry.player = player.toString();
        entry.position = toManifestBlockPos(respawn.position());
        entry.angle = respawn.angle();
        entry.forced = respawn.forced();
        return entry;
    }

    private static RespawnPoint toRespawnPoint(RespawnEntry entry) {
        BlockPos position = toBlockPos(entry.position);
        if (position == null) {
            return null;
        }

        return new RespawnPoint(position, entry.angle == null ? 0.0F : entry.angle, entry.forced != null && entry.forced);
    }

    private static BlockPos toBlockPos(ManifestBlockPos position) {
        if (position == null || position.x == null || position.y == null || position.z == null) {
            return null;
        }

        BlockPos blockPos = new BlockPos(position.x, position.y, position.z);
        return Level.isInSpawnableBounds(blockPos) ? blockPos : null;
    }

    private static ManifestBlockPos toManifestBlockPos(BlockPos position) {
        ManifestBlockPos blockPos = new ManifestBlockPos();
        blockPos.x = position.getX();
        blockPos.y = position.getY();
        blockPos.z = position.getZ();
        return blockPos;
    }

    public record RespawnPoint(BlockPos position, float angle, boolean forced) {
    }

    private record RespawnRecord(ResourceLocation dimension, UUID player, RespawnPoint respawn) {
    }

    private static final class RespawnFile {
        int schema = RESPAWN_SCHEMA;
        Collection<RespawnEntry> respawns = java.util.List.of();
    }

    private static final class RespawnEntry {
        String dimension;
        String player;
        ManifestBlockPos position;
        Float angle;
        Boolean forced;
    }

    private static final class ManifestBlockPos {
        Integer x;
        Integer y;
        Integer z;
    }
}

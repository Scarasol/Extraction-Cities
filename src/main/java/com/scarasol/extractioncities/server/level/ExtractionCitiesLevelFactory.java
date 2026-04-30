package com.scarasol.extractioncities.server.level;

import com.google.common.collect.ImmutableList;
import com.scarasol.extractioncities.mixin.accessor.MinecraftServerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;

import javax.annotation.Nullable;

public final class ExtractionCitiesLevelFactory {
    private static final ChunkProgressListener NOOP_PROGRESS = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos pos) {
        }

        @Override
        public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    private ExtractionCitiesLevelFactory() {
    }

    public static ServerLevel createDynamicLevel(MinecraftServer server, ResourceKey<Level> levelKey, LevelStem stem, long seed) {
        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
        ServerLevelData overworldData = server.getWorldData().overworldData();
        DerivedLevelData levelData = new DerivedLevelData(server.getWorldData(), overworldData);
        long obfuscatedSeed = BiomeManager.obfuscateSeed(seed);

        return new ServerLevel(
                server,
                accessor.extractioncities$getExecutor(),
                accessor.extractioncities$getStorageSource(),
                levelData,
                levelKey,
                stem,
                NOOP_PROGRESS,
                server.getWorldData().isDebugWorld(),
                obfuscatedSeed,
                ImmutableList.of(),
                false,
                server.overworld().getRandomSequences()
        );
    }
}

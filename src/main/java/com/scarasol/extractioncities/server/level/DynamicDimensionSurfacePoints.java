package com.scarasol.extractioncities.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

public final class DynamicDimensionSurfacePoints {
    private static final int NEARBY_CHUNK_SEARCH_RADIUS = 2;

    private DynamicDimensionSurfacePoints() {
    }

    public static Optional<BlockPos> findAt(ServerLevel level, BlockPos position) {
        if (!Level.isInSpawnableBounds(position)) {
            return Optional.empty();
        }

        return findColumn(level, position.getX(), position.getZ());
    }

    public static Optional<BlockPos> findNear(ServerLevel level, BlockPos position) {
        Optional<BlockPos> center = findAt(level, position);
        if (center.isPresent()) {
            return center;
        }

        ChunkPos centerChunk = new ChunkPos(position);
        for (int distance = 0; distance <= NEARBY_CHUNK_SEARCH_RADIUS; distance++) {
            Optional<BlockPos> result = findInChunkRing(level, centerChunk, distance);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    public static BlockPos findNearOrFallback(ServerLevel level, BlockPos position) {
        return findNear(level, position).orElseGet(() -> findFluidSurfaceOrOriginal(level, position));
    }

    public static BlockPos findFluidSurfaceOrOriginal(ServerLevel level, BlockPos position) {
        if (!Level.isInSpawnableBounds(position)) {
            return clampY(level, position);
        }

        level.getChunk(position);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ());
        int scannedY = scanForFluidSurfaceY(level, position.getX(), position.getZ(), surfaceY);
        return scannedY == Integer.MIN_VALUE
                ? clampY(level, position)
                : new BlockPos(position.getX(), scannedY, position.getZ());
    }

    private static Optional<BlockPos> findColumn(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int localX = x & 15;
        int localZ = z & 15;
        int surfaceY = level.dimensionType().hasCeiling()
                ? level.getChunkSource().getGenerator().getSpawnHeight(level)
                : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ);
        if (surfaceY < level.getMinBuildHeight()) {
            return Optional.empty();
        }

        int worldSurfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        int oceanFloorY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, localX, localZ);
        if (worldSurfaceY <= surfaceY && worldSurfaceY > oceanFloorY) {
            return Optional.empty();
        }

        int minY = level.getMinBuildHeight();
        int startY = Math.min(level.getMaxBuildHeight() - 3, surfaceY + 1);
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos head = new BlockPos.MutableBlockPos();
        for (int y = startY; y >= minY; y--) {
            below.set(x, y, z);
            BlockState state = level.getBlockState(below);
            if (!state.getFluidState().isEmpty()) {
                break;
            }

            feet.set(x, y + 1, z);
            head.set(x, y + 2, z);
            if (isSolidSurface(level, below) && isEmptyForPlayer(level, feet) && isEmptyForPlayer(level, head)) {
                return Optional.of(feet.immutable());
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findInChunkRing(ServerLevel level, ChunkPos center, int distance) {
        if (distance == 0) {
            return Optional.ofNullable(PlayerRespawnLogic.getSpawnPosInChunk(level, center));
        }

        for (int dx = -distance; dx <= distance; dx++) {
            Optional<BlockPos> north = findSpawnPosInChunk(level, center.x + dx, center.z - distance);
            if (north.isPresent()) {
                return north;
            }

            Optional<BlockPos> south = findSpawnPosInChunk(level, center.x + dx, center.z + distance);
            if (south.isPresent()) {
                return south;
            }
        }

        for (int dz = -distance + 1; dz < distance; dz++) {
            Optional<BlockPos> west = findSpawnPosInChunk(level, center.x - distance, center.z + dz);
            if (west.isPresent()) {
                return west;
            }

            Optional<BlockPos> east = findSpawnPosInChunk(level, center.x + distance, center.z + dz);
            if (east.isPresent()) {
                return east;
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findSpawnPosInChunk(ServerLevel level, int chunkX, int chunkZ) {
        return Optional.ofNullable(PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(chunkX, chunkZ)));
    }

    private static int scanForFluidSurfaceY(ServerLevel level, int x, int z, int surfaceY) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int startY = Math.min(maxY - 2, Math.max(minY + 1, surfaceY));

        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos head = new BlockPos.MutableBlockPos();
        for (int y = startY; y > minY; y--) {
            below.set(x, y - 1, z);
            feet.set(x, y, z);
            head.set(x, y + 1, z);
            if (isFluidOrSolidSurface(level, below) && isCollisionEmpty(level, feet) && isCollisionEmpty(level, head)) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean isSolidSurface(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && Block.isFaceFull(state.getCollisionShape(level, pos), Direction.UP);
    }

    private static boolean isEmptyForPlayer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isFluidOrSolidSurface(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty();
    }

    private static boolean isCollisionEmpty(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static BlockPos clampY(ServerLevel level, BlockPos position) {
        return new BlockPos(
                position.getX(),
                Math.max(level.getMinBuildHeight() + 1, Math.min(position.getY(), level.getMaxBuildHeight() - 2)),
                position.getZ());
    }
}

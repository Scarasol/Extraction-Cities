package com.scarasol.extractioncities.server.level;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.configuration.CommonConfig;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionStorageMode;
import com.scarasol.extractioncities.world.level.dimension.ExtractionCitiesDimensions;
import com.scarasol.extractioncities.world.level.storage.DynamicDimensionRespawnStorage;
import com.scarasol.extractioncities.world.level.storage.DynamicDimensionRespawnStorage.RespawnPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DynamicDimensionRespawns {
    private static final String LAST_DYNAMIC_DIMENSION_TAG = ExtractionCitiesMod.MODID + ".last_dynamic_dimension";
    private static final Map<UUID, RespawnPlan> PENDING_RESPAWNS = new HashMap<>();

    private DynamicDimensionRespawns() {
    }

    public static void onServerStarting(MinecraftServer server) {
        try {
            DynamicDimensionRespawnStorage.readPersistent(server, persistentDynamicDimensionIds());
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to load dynamic dimension respawns", exception);
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        ResourceKey<Level> currentDimension = player.level().dimension();
        if (rememberIfDynamicDimension(player, currentDimension)) {
            return;
        }

        ResourceLocation lastDynamicDimension = getRememberedDynamicDimension(player);
        if (lastDynamicDimension == null) {
            return;
        }

        if (DynamicDimensionManager.getRecord(lastDynamicDimension).isEmpty()) {
            clearRememberedDynamicDimension(player);
            if (CommonConfig.FORCE_DEATH_ON_MISSING_SESSION_DIMENSION.get()) {
                DynamicDimensionForcedReturnDeaths.kill(player);
                return;
            }
            teleportToOverworldRespawnOrSpawn(player);
            clearVanillaDynamicRespawn(player);
            return;
        }

        clearRememberedDynamicDimension(player);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        rememberIfDynamicDimension(player, player.level().dimension());
        PENDING_RESPAWNS.remove(player.getUUID());
    }

    public static void onPlayerChangedDimension(ServerPlayer player, ResourceKey<Level> targetDimension) {
        if (!rememberIfDynamicDimension(player, targetDimension)) {
            clearRememberedDynamicDimension(player);
        }
    }

    public static void removeDimension(MinecraftServer server, ResourceLocation dimension) throws IOException {
        PENDING_RESPAWNS.entrySet().removeIf(entry -> entry.getValue().dynamicDimension() != null
                && dimension.equals(entry.getValue().dynamicDimension().location()));
        DynamicDimensionRespawnStorage.removeDimension(dimension);
        savePersistentRespawns(server);
    }

    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && disallowsRespawn(player.level().dimension())) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("commands.extractioncities.ecdim.respawn.bed_disabled"), true);
        }
    }

    public static void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceKey<Level> spawnLevel = event.getSpawnLevel();
        Optional<DynamicDimensionRecord> record = DynamicDimensionManager.getRecord(spawnLevel.location());
        if (record.isEmpty()) {
            return;
        }

        if (!record.get().allowRespawn()) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("commands.extractioncities.ecdim.respawn.bed_disabled"), true);
            return;
        }

        event.setCanceled(true);
        try {
            if (event.getNewSpawn() == null) {
                clearStoredDynamicRespawn(player.server, player.getUUID(), spawnLevel);
            } else {
                setStoredDynamicRespawn(player.server, player.getUUID(), spawnLevel, new RespawnPoint(event.getNewSpawn(), player.getYRot(), event.isForced()));
                clearVanillaDynamicRespawn(player);
                player.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
            }
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension respawn for {}", player.getScoreboardName(), exception);
        }
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getOriginal() instanceof ServerPlayer original)) {
            return;
        }

        ResourceKey<Level> deathDimension = original.level().dimension();
        Optional<DynamicDimensionRecord> record = DynamicDimensionManager.getRecord(deathDimension.location());
        if (record.isEmpty()) {
            return;
        }

        if (record.get().allowRespawn()) {
            PENDING_RESPAWNS.put(original.getUUID(), RespawnPlan.dynamic(deathDimension, getStoredDynamicRespawn(original.getUUID(), deathDimension)));
        } else {
            PENDING_RESPAWNS.put(original.getUUID(), RespawnPlan.overworld(captureOverworldRespawn(original)));
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        RespawnPlan plan = PENDING_RESPAWNS.remove(player.getUUID());
        if (plan == null) {
            if (DynamicDimensionManager.getRecord(player.level().dimension().location()).isPresent()) {
                teleportToOverworldRespawnOrSpawn(player);
                clearVanillaDynamicRespawn(player);
            }
            return;
        }

        if (plan.isOverworld()) {
            teleportToOverworldRespawnOrSpawn(player, plan.respawn);
            clearVanillaDynamicRespawn(player);
            return;
        }

        teleportToDynamicRespawnOrFallback(player, plan.dynamicDimension, plan.respawn);
    }

    public static void clear() {
        PENDING_RESPAWNS.clear();
        DynamicDimensionRespawnStorage.clear();
    }

    public static void teleportToOverworldRespawnOrSpawn(ServerPlayer player) {
        teleportToOverworldRespawnOrSpawn(player, captureOverworldRespawn(player));
    }

    public static boolean teleportToDynamicRespawn(ServerPlayer player, ServerLevel level) {
        Optional<RespawnDestination> destination = findDynamicRespawnDestination(player, level);
        if (destination.isEmpty()) {
            return false;
        }

        RespawnDestination target = destination.get();
        player.teleportTo(level, target.position().x(), target.position().y(), target.position().z(), Set.of(), target.yRot(), player.getXRot());
        rememberOrClearDynamicDimension(player, level.dimension());
        DynamicDimensionGameModes.applyForCurrentDimension(player);
        return true;
    }

    public static Optional<BlockPos> dynamicRespawnPosition(ServerPlayer player, ServerLevel level) {
        return findDynamicRespawnDestination(player, level)
                .map(destination -> new BlockPos(
                        (int) Math.floor(destination.position().x()),
                        (int) Math.floor(destination.position().y()),
                        (int) Math.floor(destination.position().z())));
    }

    private static boolean disallowsRespawn(ResourceKey<Level> dimension) {
        return DynamicDimensionManager.getRecord(dimension.location())
                .map(record -> !record.allowRespawn())
                .orElse(false);
    }

    private static Optional<RespawnDestination> findDynamicRespawnDestination(ServerPlayer player, ServerLevel level) {
        return findRespawnDestination(level, getStoredDynamicRespawn(player.getUUID(), level.dimension()));
    }

    private static void teleportToOverworldRespawnOrSpawn(ServerPlayer player, @Nullable RespawnPoint respawn) {
        ServerLevel overworld = player.server.overworld();
        Optional<RespawnDestination> destination = findRespawnDestination(overworld, respawn);
        if (destination.isPresent()) {
            RespawnDestination target = destination.get();
            player.teleportTo(overworld, target.position().x(), target.position().y(), target.position().z(), Set.of(), target.yRot(), player.getXRot());
            clearRememberedDynamicDimension(player);
            DynamicDimensionGameModes.applyForCurrentDimension(player);
            return;
        }

        teleportToExactPoint(player, overworld, overworld.getSharedSpawnPos());
    }

    private static void teleportToDynamicRespawnOrFallback(ServerPlayer player, ResourceKey<Level> dimension, @Nullable RespawnPoint respawn) {
        ServerLevel level = player.server.getLevel(dimension);
        Optional<DynamicDimensionRecord> record = DynamicDimensionManager.getRecord(dimension.location());
        if (level == null || record.isEmpty()) {
            teleportToOverworldRespawnOrSpawn(player);
            return;
        }

        Optional<RespawnDestination> destination = findRespawnDestination(level, respawn);
        if (destination.isPresent()) {
            RespawnDestination target = destination.get();
            player.teleportTo(level, target.position().x(), target.position().y(), target.position().z(), Set.of(), target.yRot(), player.getXRot());
            rememberOrClearDynamicDimension(player, level.dimension());
            DynamicDimensionGameModes.applyForCurrentDimension(player);
            return;
        }

        BlockPos target = record.get().teleportPoint();
        if (target == null) {
            target = findSurfacePoint(level, level.getSharedSpawnPos());
        }

        teleportToExactPoint(player, level, target);
    }

    private static Optional<RespawnDestination> findRespawnDestination(ServerLevel level, @Nullable RespawnPoint respawn) {
        if (respawn == null) {
            return Optional.empty();
        }

        return Player.findRespawnPositionAndUseSpawnBlock(level, respawn.position(), respawn.angle(), respawn.forced(), true)
                .map(position -> new RespawnDestination(position, respawn.angle()));
    }

    private static void teleportToExactPoint(ServerPlayer player, ServerLevel level, BlockPos position) {
        level.getChunk(position);
        player.teleportTo(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot());
        rememberOrClearDynamicDimension(player, level.dimension());
        DynamicDimensionGameModes.applyForCurrentDimension(player);
    }

    private static BlockPos findSurfacePoint(ServerLevel level, BlockPos position) {
        return DynamicDimensionSurfacePoints.findNearOrFallback(level, position);
    }

    @Nullable
    private static RespawnPoint captureOverworldRespawn(ServerPlayer player) {
        BlockPos position = player.getRespawnPosition();
        if (position == null || !Level.OVERWORLD.equals(player.getRespawnDimension())) {
            return null;
        }

        return new RespawnPoint(position, player.getRespawnAngle(), player.isRespawnForced());
    }

    @Nullable
    private static RespawnPoint getStoredDynamicRespawn(UUID player, ResourceKey<Level> dimension) {
        return DynamicDimensionRespawnStorage.get(dimension.location(), player).orElse(null);
    }

    private static void setStoredDynamicRespawn(MinecraftServer server, UUID player, ResourceKey<Level> dimension, RespawnPoint respawn) throws IOException {
        DynamicDimensionRespawnStorage.set(dimension.location(), player, respawn);
        if (isPersistentDynamicDimension(dimension.location())) {
            savePersistentRespawns(server);
        }
    }

    private static void clearStoredDynamicRespawn(MinecraftServer server, UUID player, ResourceKey<Level> dimension) throws IOException {
        DynamicDimensionRespawnStorage.remove(dimension.location(), player);
        if (isPersistentDynamicDimension(dimension.location())) {
            savePersistentRespawns(server);
        }
    }

    private static void savePersistentRespawns(MinecraftServer server) throws IOException {
        DynamicDimensionRespawnStorage.savePersistent(server, persistentDynamicDimensionIds());
    }

    private static Collection<ResourceLocation> persistentDynamicDimensionIds() {
        return DynamicDimensionManager.dynamicDimensions().stream()
                .filter(record -> record.storage() == DynamicDimensionStorageMode.PERSISTENT)
                .map(DynamicDimensionRecord::id)
                .toList();
    }

    private static boolean isPersistentDynamicDimension(ResourceLocation dimension) {
        return DynamicDimensionManager.getRecord(dimension)
                .map(record -> record.storage() == DynamicDimensionStorageMode.PERSISTENT)
                .orElse(false);
    }

    private static void clearVanillaDynamicRespawn(ServerPlayer player) {
        BlockPos position = player.getRespawnPosition();
        ResourceLocation dimension = player.getRespawnDimension().location();
        if (position == null || (DynamicDimensionManager.getRecord(dimension).isEmpty() && !ExtractionCitiesDimensions.isManagedNamespace(dimension))) {
            return;
        }

        clearVanillaRespawnPoint(player);
    }

    private static void clearVanillaRespawnPoint(ServerPlayer player) {
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
    }

    private static boolean rememberIfDynamicDimension(ServerPlayer player, ResourceKey<Level> dimension) {
        if (DynamicDimensionManager.getRecord(dimension.location()).isEmpty()) {
            return false;
        }

        player.getPersistentData().putString(LAST_DYNAMIC_DIMENSION_TAG, dimension.location().toString());
        return true;
    }

    private static void rememberOrClearDynamicDimension(ServerPlayer player, ResourceKey<Level> dimension) {
        if (!rememberIfDynamicDimension(player, dimension)) {
            clearRememberedDynamicDimension(player);
        }
    }

    @Nullable
    private static ResourceLocation getRememberedDynamicDimension(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(LAST_DYNAMIC_DIMENSION_TAG)) {
            return null;
        }

        ResourceLocation dimension = ResourceLocation.tryParse(data.getString(LAST_DYNAMIC_DIMENSION_TAG));
        if (dimension == null) {
            clearRememberedDynamicDimension(player);
        }
        return dimension;
    }

    private static void clearRememberedDynamicDimension(ServerPlayer player) {
        player.getPersistentData().remove(LAST_DYNAMIC_DIMENSION_TAG);
    }

    private record RespawnPlan(@Nullable ResourceKey<Level> dynamicDimension, @Nullable RespawnPoint respawn) {
        private static RespawnPlan dynamic(ResourceKey<Level> dimension, @Nullable RespawnPoint respawn) {
            return new RespawnPlan(dimension, respawn);
        }

        private static RespawnPlan overworld(@Nullable RespawnPoint respawn) {
            return new RespawnPlan(null, respawn);
        }

        private boolean isOverworld() {
            return dynamicDimension == null;
        }
    }

    private record RespawnDestination(Vec3 position, float yRot) {
    }
}

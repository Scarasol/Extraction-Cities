package com.scarasol.extractioncities.server.level;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DynamicDimensionGameModes {
    private static final String AUTOMATIC_GAME_MODE_TAG = ExtractionCitiesMod.MODID + ".automatic_game_mode";
    private static final Map<UUID, GameType> AUTOMATIC_GAME_MODES = new HashMap<>();

    private DynamicDimensionGameModes() {
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        applyForDimension(player, player.level().dimension());
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        AUTOMATIC_GAME_MODES.remove(player.getUUID());
    }

    public static void onPlayerChangedDimension(ServerPlayer player, ResourceKey<Level> targetDimension) {
        applyForDimension(player, targetDimension);
    }

    public static void clear() {
        AUTOMATIC_GAME_MODES.clear();
    }

    private static void applyForDimension(ServerPlayer player, ResourceKey<Level> targetDimension) {
        GameType current = player.gameMode.getGameModeForPlayer();
        boolean automaticallyManaged = isAutomaticallyManaged(player, current);
        if (shouldKeepCurrentMode(player, current, automaticallyManaged)) {
            clearAutomaticGameMode(player);
            return;
        }

        GameType target = targetGameMode(player, targetDimension);
        boolean applied = current == target || player.setGameMode(target);
        if (applied && player.gameMode.getGameModeForPlayer() == target) {
            setAutomaticGameMode(player, target);
        } else {
            clearAutomaticGameMode(player);
        }
    }

    private static boolean isAutomaticallyManaged(ServerPlayer player, GameType current) {
        GameType automaticGameMode = AUTOMATIC_GAME_MODES.get(player.getUUID());
        if (automaticGameMode == current) {
            return true;
        }

        GameType persistentGameMode = getPersistentAutomaticGameMode(player);
        if (persistentGameMode == current) {
            AUTOMATIC_GAME_MODES.put(player.getUUID(), persistentGameMode);
            return true;
        }

        clearAutomaticGameMode(player);
        return false;
    }

    private static boolean shouldKeepCurrentMode(ServerPlayer player, GameType current, boolean automaticallyManaged) {
        if (automaticallyManaged) {
            return false;
        }

        return current == GameType.SPECTATOR
                || current == GameType.CREATIVE && player.createCommandSourceStack().hasPermission(3);
    }

    private static GameType targetGameMode(ServerPlayer player, ResourceKey<Level> targetDimension) {
        Optional<DynamicDimensionRecord> record = DynamicDimensionManager.getRecord(targetDimension.location());
        if (record.isPresent() && record.get().gameMode() != null) {
            return record.get().gameMode();
        }

        return player.server.getDefaultGameType();
    }

    private static void setAutomaticGameMode(ServerPlayer player, GameType gameMode) {
        AUTOMATIC_GAME_MODES.put(player.getUUID(), gameMode);
        player.getPersistentData().putString(AUTOMATIC_GAME_MODE_TAG, gameMode.getName());
    }

    private static void clearAutomaticGameMode(ServerPlayer player) {
        AUTOMATIC_GAME_MODES.remove(player.getUUID());
        player.getPersistentData().remove(AUTOMATIC_GAME_MODE_TAG);
    }

    private static GameType getPersistentAutomaticGameMode(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(AUTOMATIC_GAME_MODE_TAG)) {
            return null;
        }

        return GameType.byName(data.getString(AUTOMATIC_GAME_MODE_TAG), null);
    }
}

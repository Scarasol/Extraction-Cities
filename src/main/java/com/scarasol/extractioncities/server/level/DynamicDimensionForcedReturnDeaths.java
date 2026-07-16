package com.scarasol.extractioncities.server.level;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DynamicDimensionForcedReturnDeaths {
    private static final Set<UUID> ACTIVE_DEATHS = new HashSet<>();

    private DynamicDimensionForcedReturnDeaths() {
    }

    public static void kill(ServerPlayer player) {
        ACTIVE_DEATHS.add(player.getUUID());
        try {
            player.kill();
        } finally {
            ACTIVE_DEATHS.remove(player.getUUID());
        }
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && shouldVoidDeathDrops(player)) {
            event.getDrops().clear();
        }
    }

    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && shouldVoidDeathDrops(player)) {
            event.setDroppedExperience(0);
        }
    }

    public static void clear() {
        ACTIVE_DEATHS.clear();
    }

    private static boolean shouldVoidDeathDrops(ServerPlayer player) {
        return ACTIVE_DEATHS.contains(player.getUUID())
                && !player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
    }
}

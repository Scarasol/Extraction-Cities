package com.scarasol.extractioncities.event;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.server.level.DynamicDimensionGameModes;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.server.level.DynamicDimensionRespawns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExtractionCitiesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExtractionCitiesServerEvents {
    private ExtractionCitiesServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        DynamicDimensionManager.onServerStarting(event.getServer());
        DynamicDimensionRespawns.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DynamicDimensionManager.onServerStopped(event.getServer());
        DynamicDimensionGameModes.clear();
        DynamicDimensionRespawns.clear();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DynamicDimensionGameModes.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DynamicDimensionGameModes.onPlayerLoggedOut(player);
            DynamicDimensionRespawns.onPlayerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DynamicDimensionGameModes.onPlayerChangedDimension(player, event.getTo());
        }
    }

    @SubscribeEvent
    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        DynamicDimensionRespawns.onPlayerSleepInBed(event);
    }

    @SubscribeEvent
    public static void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        DynamicDimensionRespawns.onPlayerSetSpawn(event);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        DynamicDimensionRespawns.onPlayerClone(event);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        DynamicDimensionRespawns.onPlayerRespawn(event);
    }
}

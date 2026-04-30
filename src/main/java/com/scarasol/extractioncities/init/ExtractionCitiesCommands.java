package com.scarasol.extractioncities.init;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.server.commands.DynamicDimensionCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExtractionCitiesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExtractionCitiesCommands {
    private ExtractionCitiesCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DynamicDimensionCommands.register(event.getDispatcher());
    }
}

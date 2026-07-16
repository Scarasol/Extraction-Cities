package com.scarasol.extractioncities.compat.tlc;

import mcjty.lostcities.api.LostCityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TlcLostCityEvents {
    private TlcLostCityEvents() {
    }

    @SubscribeEvent
    public static void onCharacteristics(LostCityEvent.CharacteristicsEvent event) {
        TlcBuildingOverrides.applyCharacteristics(event);
    }
}

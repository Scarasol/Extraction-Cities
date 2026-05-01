package com.scarasol.extractioncities.compat;

import net.minecraftforge.fml.ModList;

/**
 * @author Scarasol
 */
public class ModCompat {
    public static final String LOST_CITIES_MODID = "lostcities";

    public static boolean isLoadTlc() {
        return ModList.get().isLoaded(LOST_CITIES_MODID);
    }
}

package com.scarasol.extractioncities;

import com.mojang.logging.LogUtils;
import com.scarasol.extractioncities.configuration.CommonConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ExtractionCitiesMod.MODID)
public class ExtractionCitiesMod {
    public static final String MODID = "extractioncities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExtractionCitiesMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, "extractioncities-common.toml");
    }
}

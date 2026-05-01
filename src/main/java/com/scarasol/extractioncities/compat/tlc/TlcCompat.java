package com.scarasol.extractioncities.compat.tlc;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.configuration.CommonConfig;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import mcjty.lostcities.config.ProfileSetup;
import mcjty.lostcities.setup.Registration;
import mcjty.lostcities.worldgen.LostCityFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/**
 * @author Scarasol
 */
public class TlcCompat {
    private static final String FALLBACK_PROFILE = "onlycities";
    private static final ThreadLocal<Boolean> DIRECT_DYNAMIC_GENERATION = ThreadLocal.withInitial(() -> false);

    public static void refreshDynamicDimensionProfiles() {
        LostCityFeature.globalDimensionInfoDirtyCounter++;
    }

    public static String profileForDynamicDimension(ResourceKey<Level> dimension) {
        return DynamicDimensionManager.getRecord(dimension.location())
                .filter(DynamicDimensionRecord::generateLostCities)
                .map(record -> configuredProfile())
                .orElse(null);
    }

    public static void placeLostCityFeature(WorldGenLevel level, ChunkGenerator generator, ChunkAccess chunk) {
        if (!DynamicDimensionManager.shouldGenerateLostCities(level, false)) {
            return;
        }

        BlockPos origin = SectionPos.of(chunk.getPos(), level.getMinSection()).origin();
        FeaturePlaceContext<NoneFeatureConfiguration> context = new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                generator,
                RandomSource.create(level.getSeed()),
                origin,
                NoneFeatureConfiguration.INSTANCE);
        DIRECT_DYNAMIC_GENERATION.set(true);
        try {
            Registration.LOSTCITY_FEATURE.get().place(context);
        } finally {
            DIRECT_DYNAMIC_GENERATION.set(false);
        }
    }

    public static boolean shouldSkipBiomePlacedFeature(WorldGenLevel level) {
        return !DIRECT_DYNAMIC_GENERATION.get() && DynamicDimensionManager.shouldGenerateLostCities(level, false);
    }

    private static String configuredProfile() {
        String profile = CommonConfig.DEFAULT_LOST_CITIES_PROFILE.get().trim();
        if (profile.isEmpty()) {
            return FALLBACK_PROFILE;
        }

        if (!ProfileSetup.STANDARD_PROFILES.isEmpty() && !ProfileSetup.STANDARD_PROFILES.containsKey(profile)) {
            ExtractionCitiesMod.LOGGER.warn("The Lost Cities profile '{}' is not registered. Dynamic city generation may be skipped.", profile);
        }
        return profile;
    }
}

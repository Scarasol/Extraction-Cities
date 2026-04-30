package com.scarasol.extractioncities.world.level.dimension;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public final class ExtractionCitiesDimensions {
    public static final ResourceLocation DIMENSION_TYPE_ID = new ResourceLocation(ExtractionCitiesMod.MODID, "extraction_city");
    public static final ResourceLocation OVERWORLD_GENERATOR_ID = new ResourceLocation("minecraft", "overworld");
    public static final ResourceLocation FLAT_GENERATOR_ID = new ResourceLocation("minecraft", "flat");
    public static final ResourceLocation SINGLE_BIOME_GENERATOR_ID = new ResourceLocation("minecraft", "single_biome");
    public static final ResourceLocation DEFAULT_BIOME_ID = new ResourceLocation("minecraft", "plains");

    private ExtractionCitiesDimensions() {
    }

    public static ResourceKey<Level> levelKey(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static ResourceKey<DimensionType> dimensionTypeKey(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION_TYPE, id);
    }

    public static boolean isManagedNamespace(ResourceLocation id) {
        return ExtractionCitiesMod.MODID.equals(id.getNamespace());
    }
}

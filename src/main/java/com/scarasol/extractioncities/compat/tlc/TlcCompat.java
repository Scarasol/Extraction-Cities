package com.scarasol.extractioncities.compat.tlc;

import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverride;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverrideType;
import com.scarasol.extractioncities.world.level.dimension.LostCityChunkPos;
import mcjty.lostcities.config.ProfileSetup;
import mcjty.lostcities.setup.CustomRegistries;
import mcjty.lostcities.setup.Registration;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.LostCityFeature;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.cityassets.AssetRegistries;
import mcjty.lostcities.worldgen.lost.cityassets.Building;
import mcjty.lostcities.worldgen.lost.cityassets.MultiBuilding;
import mcjty.lostcities.worldgen.lost.cityassets.WorldStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Scarasol
 */
public class TlcCompat {
    private static final String FALLBACK_PROFILE = "onlycities";
    private static final String FALLBACK_WORLD_STYLE = "standard";
    private static final String LOST_CITIES_NAMESPACE = "lostcities";
    private static final int BUILDING_SEARCH_RADIUS = 64;
    private static final ThreadLocal<Boolean> DIRECT_DYNAMIC_GENERATION = ThreadLocal.withInitial(() -> false);
    private static boolean eventHandlersRegistered;

    public static void refreshDynamicDimensionProfiles() {
        TlcBuildingOverrides.clearCache();
        LostCityFeature.globalDimensionInfoDirtyCounter++;
    }

    public static synchronized void registerEventHandlers() {
        if (!eventHandlersRegistered) {
            MinecraftForge.EVENT_BUS.register(TlcLostCityEvents.class);
            eventHandlersRegistered = true;
        }
    }

    public static String profileForDynamicDimension(ResourceKey<Level> dimension) {
        return DynamicDimensionManager.getRecord(dimension.location())
                .filter(DynamicDimensionRecord::generateLostCities)
                .map(DynamicDimensionRecord::lostCitiesProfile)
                .map(TlcCompat::fallbackProfileIfBlank)
                .map(TlcCompat::warnIfUnknownProfile)
                .orElse(null);
    }

    public static Optional<String> worldStyleForDynamicDimension(ResourceKey<Level> dimension) {
        return DynamicDimensionManager.getRecord(dimension.location())
                .filter(DynamicDimensionRecord::generateLostCities)
                .map(DynamicDimensionRecord::lostCitiesWorldStyle)
                .map(TlcCompat::fallbackIfBlank);
    }

    public static boolean isWorldStyleRegistered(LevelAccessor level, String worldStyle) {
        try {
            return AssetRegistries.WORLDSTYLES.get(level, fallbackIfBlank(worldStyle)) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static List<String> worldStyleSuggestions(LevelAccessor level) {
        try {
            AssetRegistries.WORLDSTYLES.loadAll(level);
            List<String> suggestions = new ArrayList<>();
            for (WorldStyle worldStyle : AssetRegistries.WORLDSTYLES.getIterable()) {
                suggestions.add(worldStyle.getName());
            }
            return suggestions;
        } catch (RuntimeException exception) {
            return List.of("standard", "standard_everywhere");
        }
    }

    public static boolean isProfileRegistered(String profile) {
        return ProfileSetup.STANDARD_PROFILES.containsKey(fallbackProfileIfBlank(profile));
    }

    public static List<String> profileSuggestions() {
        if (ProfileSetup.STANDARD_PROFILES.isEmpty()) {
            return List.of(FALLBACK_PROFILE);
        }
        return new ArrayList<>(ProfileSetup.STANDARD_PROFILES.keySet());
    }

    public static List<String> buildingSuggestions(LevelAccessor level) {
        try {
            AssetRegistries.BUILDINGS.loadAll(level);
            AssetRegistries.MULTI_BUILDINGS.loadAll(level);
            Set<String> suggestions = new LinkedHashSet<>();
            for (Building building : AssetRegistries.BUILDINGS.getIterable()) {
                addBuildingSuggestion(suggestions, building.getId());
            }
            for (MultiBuilding multiBuilding : AssetRegistries.MULTI_BUILDINGS.getIterable()) {
                addBuildingSuggestion(suggestions, multiBuilding.getId());
            }
            return new ArrayList<>(suggestions);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private static void addBuildingSuggestion(Set<String> suggestions, ResourceLocation id) {
        if (LOST_CITIES_NAMESPACE.equals(id.getNamespace())) {
            suggestions.add(id.getPath());
        }
        suggestions.add(id.toString());
    }

    public static BuildingOverridePlacement createBuildingOverride(
            ServerLevel level,
            DynamicDimensionRecord record,
            int requestedChunkX,
            int requestedChunkZ,
            ResourceLocation rawBuildingId
    ) throws UnknownLostCityBuildingException, LostCityBuildingPlacementException {
        registerEventHandlers();
        ResourceLocation buildingId = normalizeLostCitiesAssetId(rawBuildingId);
        BuildingTarget target = resolveBuildingTarget(level, buildingId)
                .orElseThrow(() -> new UnknownLostCityBuildingException(buildingId));
        IDimensionInfo dimensionInfo = Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
        if (dimensionInfo == null) {
            throw new LostCityBuildingPlacementException(target.id());
        }

        Set<Long> visited = new HashSet<>();
        Optional<LostCityBuildingOverride> preferred = preferredBuildingOverride(dimensionInfo, record, target, requestedChunkX, requestedChunkZ, visited);
        if (preferred.isPresent()) {
            return new BuildingOverridePlacement(preferred.get(), requestedChunkX, requestedChunkZ);
        }

        for (int radius = 0; radius <= BUILDING_SEARCH_RADIUS; radius++) {
            for (int x = requestedChunkX - radius; x <= requestedChunkX + radius; x++) {
                Optional<LostCityBuildingOverride> north = buildingOverrideAt(dimensionInfo, record, target, x, requestedChunkZ - radius, visited);
                if (north.isPresent()) {
                    return new BuildingOverridePlacement(north.get(), requestedChunkX, requestedChunkZ);
                }
                if (radius > 0) {
                    Optional<LostCityBuildingOverride> south = buildingOverrideAt(dimensionInfo, record, target, x, requestedChunkZ + radius, visited);
                    if (south.isPresent()) {
                        return new BuildingOverridePlacement(south.get(), requestedChunkX, requestedChunkZ);
                    }
                }
            }

            for (int z = requestedChunkZ - radius + 1; z <= requestedChunkZ + radius - 1; z++) {
                Optional<LostCityBuildingOverride> west = buildingOverrideAt(dimensionInfo, record, target, requestedChunkX - radius, z, visited);
                if (west.isPresent()) {
                    return new BuildingOverridePlacement(west.get(), requestedChunkX, requestedChunkZ);
                }
                if (radius > 0) {
                    Optional<LostCityBuildingOverride> east = buildingOverrideAt(dimensionInfo, record, target, requestedChunkX + radius, z, visited);
                    if (east.isPresent()) {
                        return new BuildingOverridePlacement(east.get(), requestedChunkX, requestedChunkZ);
                    }
                }
            }
        }

        throw new LostCityBuildingPlacementException(target.id());
    }

    private static Optional<LostCityBuildingOverride> preferredBuildingOverride(
            IDimensionInfo dimensionInfo,
            DynamicDimensionRecord record,
            BuildingTarget target,
            int requestedChunkX,
            int requestedChunkZ,
            Set<Long> visited
    ) {
        ChunkCoord requested = TlcBuildingOverrides.chunkCoord(dimensionInfo, requestedChunkX, requestedChunkZ);
        if (!BuildingInfo.isCity(requested, dimensionInfo)) {
            return Optional.empty();
        }

        OldFootprint oldFootprint = oldFootprint(dimensionInfo, requestedChunkX, requestedChunkZ);
        if (oldFootprint.width() > 1 || oldFootprint.height() > 1) {
            Optional<LostCityBuildingOverride> fromOldTopLeft = buildingOverrideAt(dimensionInfo, record, target, oldFootprint.anchorX(), oldFootprint.anchorZ(), visited);
            if (fromOldTopLeft.isPresent()) {
                return fromOldTopLeft;
            }
        }

        return buildingOverrideAt(dimensionInfo, record, target, requestedChunkX, requestedChunkZ, visited);
    }

    private static Optional<LostCityBuildingOverride> buildingOverrideAt(
            IDimensionInfo dimensionInfo,
            DynamicDimensionRecord record,
            BuildingTarget target,
            int anchorX,
            int anchorZ,
            Set<Long> visited
    ) {
        if (!visited.add(TlcBuildingOverrides.chunkKey(anchorX, anchorZ))) {
            return Optional.empty();
        }

        if (!isFootprintCity(dimensionInfo, anchorX, anchorZ, target.width(), target.height())) {
            return Optional.empty();
        }

        OldFootprint oldFootprint = oldFootprint(dimensionInfo, anchorX, anchorZ);
        Set<LostCityChunkPos> suppressed = suppressedChunks(oldFootprint, anchorX, anchorZ, target.width(), target.height());
        LostCityBuildingOverride override = new LostCityBuildingOverride(anchorX, anchorZ, target.id(), target.type(), target.width(), target.height(), suppressed);
        return conflicts(record, override) ? Optional.empty() : Optional.of(override);
    }

    private static boolean isFootprintCity(IDimensionInfo dimensionInfo, int anchorX, int anchorZ, int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                if (!BuildingInfo.isCity(TlcBuildingOverrides.chunkCoord(dimensionInfo, anchorX + x, anchorZ + z), dimensionInfo)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static OldFootprint oldFootprint(IDimensionInfo dimensionInfo, int chunkX, int chunkZ) {
        BuildingInfo info = BuildingInfo.getBuildingInfo(TlcBuildingOverrides.chunkCoord(dimensionInfo, chunkX, chunkZ), dimensionInfo);
        var multiBuildingInfo = info.getMultiBuildingInfo();
        if (multiBuildingInfo == null) {
            return new OldFootprint(chunkX, chunkZ, 1, 1);
        }

        return new OldFootprint(
                chunkX - multiBuildingInfo.offsetX(),
                chunkZ - multiBuildingInfo.offsetZ(),
                multiBuildingInfo.w(),
                multiBuildingInfo.h());
    }

    private static Set<LostCityChunkPos> suppressedChunks(OldFootprint oldFootprint, int anchorX, int anchorZ, int width, int height) {
        Set<LostCityChunkPos> suppressed = new LinkedHashSet<>();
        for (int x = 0; x < oldFootprint.width(); x++) {
            for (int z = 0; z < oldFootprint.height(); z++) {
                int chunkX = oldFootprint.anchorX() + x;
                int chunkZ = oldFootprint.anchorZ() + z;
                if (chunkX < anchorX || chunkX >= anchorX + width || chunkZ < anchorZ || chunkZ >= anchorZ + height) {
                    suppressed.add(new LostCityChunkPos(chunkX, chunkZ));
                }
            }
        }
        return Set.copyOf(suppressed);
    }

    private static boolean conflicts(DynamicDimensionRecord record, LostCityBuildingOverride candidate) {
        Set<LostCityChunkPos> affected = affectedChunks(candidate);
        for (LostCityBuildingOverride existing : record.lostCityBuildingOverrides()) {
            for (LostCityChunkPos chunk : affected) {
                if (existing.affects(chunk.x(), chunk.z())) {
                    return true;
                }
            }
            for (LostCityChunkPos chunk : affectedChunks(existing)) {
                if (candidate.affects(chunk.x(), chunk.z())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<LostCityChunkPos> affectedChunks(LostCityBuildingOverride override) {
        Set<LostCityChunkPos> affected = new LinkedHashSet<>(override.suppressedChunks());
        for (int x = 0; x < override.width(); x++) {
            for (int z = 0; z < override.height(); z++) {
                affected.add(new LostCityChunkPos(override.anchorX() + x, override.anchorZ() + z));
            }
        }
        return affected;
    }

    private static Optional<BuildingTarget> resolveBuildingTarget(LevelAccessor level, ResourceLocation rawBuildingId) {
        ResourceLocation id = normalizeLostCitiesAssetId(rawBuildingId);
        if (id == null) {
            return Optional.empty();
        }

        if (hasRegistryEntry(level, CustomRegistries.BUILDING_REGISTRY_KEY, id)) {
            Building building = AssetRegistries.BUILDINGS.get(level, id);
            return Optional.of(new BuildingTarget(building.getId(), LostCityBuildingOverrideType.BUILDING, 1, 1));
        }

        if (hasRegistryEntry(level, CustomRegistries.MULTIBUILDINGS_REGISTRY_KEY, id)) {
            MultiBuilding multiBuilding = AssetRegistries.MULTI_BUILDINGS.get(level, id);
            return Optional.of(new BuildingTarget(multiBuilding.getId(), LostCityBuildingOverrideType.MULTI_BUILDING, multiBuilding.getDimX(), multiBuilding.getDimZ()));
        }

        return Optional.empty();
    }

    private static <T> boolean hasRegistryEntry(LevelAccessor level, ResourceKey<Registry<T>> registryKey, ResourceLocation id) {
        return level.registryAccess().registryOrThrow(registryKey).containsKey(id);
    }

    private static ResourceLocation normalizeLostCitiesAssetId(ResourceLocation rawBuildingId) {
        if (rawBuildingId == null) {
            return null;
        }
        return ResourceLocation.DEFAULT_NAMESPACE.equals(rawBuildingId.getNamespace())
                ? new ResourceLocation(LOST_CITIES_NAMESPACE, rawBuildingId.getPath())
                : rawBuildingId;
    }

    public static void placeLostCityFeature(WorldGenLevel level, ChunkGenerator generator, ChunkAccess chunk) {
        if (!DynamicDimensionManager.shouldGenerateLostCities(level, false)) {
            return;
        }
        registerEventHandlers();

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

    private static String warnIfUnknownProfile(String profile) {
        if (!ProfileSetup.STANDARD_PROFILES.isEmpty() && !ProfileSetup.STANDARD_PROFILES.containsKey(profile)) {
            ExtractionCitiesMod.LOGGER.warn("The Lost Cities profile '{}' is not registered. Dynamic city generation may be skipped.", profile);
        }
        return profile;
    }

    private static String fallbackProfileIfBlank(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? FALLBACK_PROFILE : trimmed;
    }

    private static String fallbackIfBlank(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? FALLBACK_WORLD_STYLE : trimmed;
    }

    public record BuildingOverridePlacement(LostCityBuildingOverride override, int requestedChunkX, int requestedChunkZ) {
        public boolean relocated() {
            return override.anchorX() != requestedChunkX || override.anchorZ() != requestedChunkZ;
        }
    }

    public static final class UnknownLostCityBuildingException extends Exception {
        private final String buildingId;

        private UnknownLostCityBuildingException(ResourceLocation buildingId) {
            this.buildingId = String.valueOf(buildingId);
        }

        public String buildingId() {
            return buildingId;
        }
    }

    public static final class LostCityBuildingPlacementException extends Exception {
        private final String buildingId;

        private LostCityBuildingPlacementException(ResourceLocation buildingId) {
            this.buildingId = String.valueOf(buildingId);
        }

        public String buildingId() {
            return buildingId;
        }
    }

    private record BuildingTarget(ResourceLocation id, LostCityBuildingOverrideType type, int width, int height) {
    }

    private record OldFootprint(int anchorX, int anchorZ, int width, int height) {
    }
}

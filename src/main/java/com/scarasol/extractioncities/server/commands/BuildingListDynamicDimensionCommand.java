package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.compat.ModCompat;
import com.scarasol.extractioncities.compat.tlc.TlcCompat;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverride;
import com.scarasol.extractioncities.world.level.dimension.LostCityBuildingOverrideType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class BuildingListDynamicDimensionCommand {
    private static final String ARG_BUILDING = "building";
    private static final String ARG_CHUNK_X = "chunkX";
    private static final String ARG_CHUNK_Z = "chunkZ";
    private static final String ARG_ID = "id";

    private static final SimpleCommandExceptionType ERROR_LOST_CITIES_NOT_INSTALLED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.lostcities_not_installed"));
    private static final SimpleCommandExceptionType ERROR_UPDATE_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.set_failed"));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_DYNAMIC_DIMENSION = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_dynamic_dimension", id));
    private static final DynamicCommandExceptionType ERROR_LOST_CITIES_DISABLED = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.lostcities_disabled", id));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_LOST_CITIES_BUILDING = new DynamicCommandExceptionType(building ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_lostcities_building", building));
    private static final DynamicCommandExceptionType ERROR_NO_AVAILABLE_CITY_CHUNK = new DynamicCommandExceptionType(building ->
            Component.translatable("commands.extractioncities.ecdim.error.no_available_lostcities_city_chunk", building));
    private static final DynamicCommandExceptionType ERROR_NO_BUILDING_OVERRIDE = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.no_lostcities_building_override", id));

    private BuildingListDynamicDimensionCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("building_list")
                .then(Commands.argument(ARG_ID, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                DynamicDimensionManager.dynamicDimensions().stream().map(record -> record.id().getPath()),
                                builder))
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_CHUNK_X, IntegerArgumentType.integer())
                                        .then(Commands.argument(ARG_CHUNK_Z, IntegerArgumentType.integer())
                                                .then(Commands.argument(ARG_BUILDING, ResourceLocationArgument.id())
                                                        .suggests((context, builder) -> suggestLostCityBuildings(context.getSource(), builder))
                                                        .executes(context -> add(
                                                                context.getSource(),
                                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                                IntegerArgumentType.getInteger(context, ARG_CHUNK_X),
                                                                IntegerArgumentType.getInteger(context, ARG_CHUNK_Z),
                                                                ResourceLocationArgument.getId(context, ARG_BUILDING)))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_CHUNK_X, IntegerArgumentType.integer())
                                        .then(Commands.argument(ARG_CHUNK_Z, IntegerArgumentType.integer())
                                                .executes(context -> remove(
                                                        context.getSource(),
                                                        DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                        IntegerArgumentType.getInteger(context, ARG_CHUNK_X),
                                                        IntegerArgumentType.getInteger(context, ARG_CHUNK_Z))))))
                        .then(Commands.literal("list")
                                .executes(context -> list(
                                        context.getSource(),
                                        DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)))))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(
                                        context.getSource(),
                                        DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)))))));
    }

    private static int add(CommandSourceStack source, ResourceLocation id, int chunkX, int chunkZ, ResourceLocation buildingId) throws CommandSyntaxException {
        if (!ModCompat.isLoadTlc()) {
            throw ERROR_LOST_CITIES_NOT_INSTALLED.create();
        }

        var record = DynamicDimensionManager.getRecord(id)
                .orElseThrow(() -> ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id));
        if (!record.generateLostCities()) {
            throw ERROR_LOST_CITIES_DISABLED.create(id);
        }

        ServerLevel level = DynamicDimensionManager.getLevel(source.getServer(), id)
                .orElseThrow(() -> ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id));

        TlcCompat.BuildingOverridePlacement placement;
        try {
            placement = TlcCompat.createBuildingOverride(level, record, chunkX, chunkZ, buildingId);
        } catch (TlcCompat.UnknownLostCityBuildingException exception) {
            throw ERROR_UNKNOWN_LOST_CITIES_BUILDING.create(exception.buildingId());
        } catch (TlcCompat.LostCityBuildingPlacementException exception) {
            throw ERROR_NO_AVAILABLE_CITY_CHUNK.create(exception.buildingId());
        } catch (RuntimeException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to add Lost Cities building override {} -> {}", id, buildingId, exception);
            throw ERROR_NO_AVAILABLE_CITY_CHUNK.create(buildingId);
        }

        try {
            DynamicDimensionManager.addLostCityBuildingOverride(source.getServer(), id, placement.override());
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to add Lost Cities building override {} -> {}", id, buildingId, exception);
            throw ERROR_UPDATE_FAILED.create();
        }

        LostCityBuildingOverride override = placement.override();
        if (placement.relocated()) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.building_list.add.relocated.success",
                    chunkX,
                    chunkZ,
                    override.buildingId(),
                    id,
                    override.anchorX(),
                    override.anchorZ(),
                    override.width(),
                    override.height(),
                    override.suppressedChunks().size()), true);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.building_list.add.success",
                    override.buildingId(),
                    id,
                    override.anchorX(),
                    override.anchorZ(),
                    override.width(),
                    override.height(),
                    override.suppressedChunks().size()), true);
        }
        return 1;
    }

    private static int remove(CommandSourceStack source, ResourceLocation id, int chunkX, int chunkZ) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        Optional<LostCityBuildingOverride> removed;
        try {
            removed = DynamicDimensionManager.removeLostCityBuildingOverride(source.getServer(), id, chunkX, chunkZ);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to remove Lost Cities building override {} at {},{}", id, chunkX, chunkZ, exception);
            throw ERROR_UPDATE_FAILED.create();
        }

        if (removed.isEmpty()) {
            throw ERROR_NO_BUILDING_OVERRIDE.create(id);
        }

        LostCityBuildingOverride override = removed.get();
        if (override.anchorX() == chunkX && override.anchorZ() == chunkZ) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.building_list.remove.success",
                    override.buildingId(),
                    id,
                    override.anchorX(),
                    override.anchorZ()), true);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.building_list.remove.by_chunk.success",
                    override.buildingId(),
                    id,
                    chunkX,
                    chunkZ,
                    override.anchorX(),
                    override.anchorZ()), true);
        }
        return 1;
    }

    private static int list(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        var record = DynamicDimensionManager.getRecord(id)
                .orElseThrow(() -> ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id));
        if (record.lostCityBuildingOverrides().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.building_list.list.empty", id), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.building_list.list.header", id), false);
        for (LostCityBuildingOverride override : record.lostCityBuildingOverrides()) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.building_list.list.entry",
                    override.anchorX(),
                    override.anchorZ(),
                    override.buildingId(),
                    typeName(override.type()),
                    override.width(),
                    override.height(),
                    override.suppressedChunks().size()), false);
        }
        return record.lostCityBuildingOverrides().size();
    }

    private static int clear(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        int count;
        try {
            count = DynamicDimensionManager.clearLostCityBuildingOverrides(source.getServer(), id);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to clear Lost Cities building overrides {}", id, exception);
            throw ERROR_UPDATE_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.building_list.clear.success",
                id,
                count), true);
        return count;
    }

    private static CompletableFuture<Suggestions> suggestLostCityBuildings(CommandSourceStack source, SuggestionsBuilder builder) {
        if (!ModCompat.isLoadTlc()) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(TlcCompat.buildingSuggestions(source.getServer().overworld()), builder);
    }

    private static Component typeName(LostCityBuildingOverrideType type) {
        return Component.translatable("commands.extractioncities.ecdim.building_list.type." + type.id());
    }
}

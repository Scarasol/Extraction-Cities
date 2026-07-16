package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.compat.ModCompat;
import com.scarasol.extractioncities.compat.tlc.TlcCompat;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class SetDynamicDimensionCommand {
    private static final String ARG_ID = "id";
    private static final String ARG_ENABLED = "enabled";
    private static final String ARG_POS = "pos";
    private static final String ARG_PROFILE = "profile";
    private static final String ARG_STRUCTURE = "structure";
    private static final String ARG_WORLD_STYLE = "worldstyle";

    private static final SimpleCommandExceptionType ERROR_LOST_CITIES_NOT_INSTALLED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.lostcities_not_installed"));
    private static final SimpleCommandExceptionType ERROR_SET_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.set_failed"));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_DYNAMIC_DIMENSION = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_dynamic_dimension", id));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_STRUCTURE = new DynamicCommandExceptionType(structure ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_structure", structure));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_LOST_CITIES_WORLD_STYLE = new DynamicCommandExceptionType(worldStyle ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_lostcities_worldstyle", worldStyle));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_LOST_CITIES_PROFILE = new DynamicCommandExceptionType(profile ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_lostcities_profile", profile));

    private SetDynamicDimensionCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("set")
                .then(Commands.argument(ARG_ID, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                DynamicDimensionManager.dynamicDimensions().stream().map(record -> record.id().getPath()),
                                builder))
                        .then(Commands.literal("structure")
                                .then(Commands.argument(ARG_ENABLED, BoolArgumentType.bool())
                                        .executes(context -> setStructure(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                BoolArgumentType.getBool(context, ARG_ENABLED)))))
                        .then(Commands.literal("structure_whitelist")
                                .then(Commands.literal("add")
                                        .then(Commands.argument(ARG_STRUCTURE, ResourceLocationArgument.id())
                                                .suggests((context, builder) -> suggestStructures(context.getSource(), builder))
                                                .executes(context -> addStructureWhitelist(
                                                        context.getSource(),
                                                        DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                        ResourceLocationArgument.getId(context, ARG_STRUCTURE)))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument(ARG_STRUCTURE, ResourceLocationArgument.id())
                                                .suggests((context, builder) -> suggestStructures(context.getSource(), builder))
                                                .executes(context -> removeStructureWhitelist(
                                                        context.getSource(),
                                                        DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                        ResourceLocationArgument.getId(context, ARG_STRUCTURE)))))
                                .then(Commands.literal("clear")
                                        .executes(context -> clearStructureWhitelist(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID))))))
                        .then(Commands.literal("lostcities")
                                .then(Commands.argument(ARG_ENABLED, BoolArgumentType.bool())
                                        .executes(context -> setLostCities(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                BoolArgumentType.getBool(context, ARG_ENABLED)))))
                        .then(Commands.literal("profile")
                                .then(Commands.argument(ARG_PROFILE, StringArgumentType.word())
                                        .suggests((context, builder) -> suggestLostCitiesProfiles(builder))
                                        .executes(context -> setLostCitiesProfile(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                StringArgumentType.getString(context, ARG_PROFILE)))))
                        .then(Commands.literal("worldstyle")
                                .then(Commands.argument(ARG_WORLD_STYLE, ResourceLocationArgument.id())
                                        .suggests((context, builder) -> suggestLostCitiesWorldStyles(context.getSource(), builder))
                                        .executes(context -> setLostCitiesWorldStyle(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                readWorldStyle(context)))))
                        .then(Commands.literal("gamemode")
                                .then(Commands.argument("gamemode", GameModeArgument.gameMode())
                                        .executes(context -> setGameMode(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                GameModeArgument.getGameMode(context, "gamemode")))))
                        .then(Commands.literal("respawn")
                                .then(Commands.argument(ARG_ENABLED, BoolArgumentType.bool())
                                        .executes(context -> setAllowRespawn(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                BoolArgumentType.getBool(context, ARG_ENABLED)))))
                        .then(Commands.literal("save")
                                .then(Commands.argument(ARG_ENABLED, BoolArgumentType.bool())
                                        .executes(context -> setSave(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                BoolArgumentType.getBool(context, ARG_ENABLED)))))
                        .then(Commands.literal("teleport_point")
                                .then(Commands.literal("clear")
                                        .executes(context -> setTeleportPoint(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                null)))
                                .then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                                        .executes(context -> setTeleportPoint(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                BlockPosArgument.getSpawnablePos(context, ARG_POS)))))));
    }

    private static int setStructure(CommandSourceStack source, ResourceLocation id, boolean generateStructures) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        try {
            DynamicDimensionManager.setGenerateStructures(source.getServer(), id, generateStructures);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension structure setting {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.structure.success",
                id,
                DynamicDimensionCommands.booleanName(generateStructures)), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestStructures(CommandSourceStack source, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                source.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE)
                        .keySet()
                        .stream()
                        .map(ResourceLocation::toString),
                builder);
    }

    private static int addStructureWhitelist(CommandSourceStack source, ResourceLocation id, ResourceLocation structureId) throws CommandSyntaxException {
        validateStructureSetTarget(source, id, structureId);

        try {
            DynamicDimensionManager.addStructureWhitelist(source.getServer(), id, structureId);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to add dynamic dimension structure whitelist entry {} -> {}", id, structureId, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.structure_whitelist.add.success",
                structureId,
                id), true);
        return 1;
    }

    private static int removeStructureWhitelist(CommandSourceStack source, ResourceLocation id, ResourceLocation structureId) throws CommandSyntaxException {
        validateStructureSetTarget(source, id, structureId);

        try {
            DynamicDimensionManager.removeStructureWhitelist(source.getServer(), id, structureId);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to remove dynamic dimension structure whitelist entry {} -> {}", id, structureId, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.structure_whitelist.remove.success",
                structureId,
                id), true);
        return 1;
    }

    private static int clearStructureWhitelist(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        try {
            DynamicDimensionManager.clearStructureWhitelist(source.getServer(), id);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to clear dynamic dimension structure whitelist {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.structure_whitelist.clear.success",
                id), true);
        return 1;
    }

    private static void validateStructureSetTarget(CommandSourceStack source, ResourceLocation id, ResourceLocation structureId) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }
        if (!source.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(structureId)) {
            throw ERROR_UNKNOWN_STRUCTURE.create(structureId);
        }
    }

    private static int setLostCities(CommandSourceStack source, ResourceLocation id, boolean generateLostCities) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }
        if (!ModCompat.isLoadTlc()) {
            throw ERROR_LOST_CITIES_NOT_INSTALLED.create();
        }

        try {
            DynamicDimensionManager.setGenerateLostCities(source.getServer(), id, generateLostCities);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension Lost Cities setting {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.lostcities.success",
                id,
                DynamicDimensionCommands.booleanName(generateLostCities)), true);
        return 1;
    }

    private static String readWorldStyle(CommandContext<CommandSourceStack> context) {
        ResourceLocation worldStyle = ResourceLocationArgument.getId(context, ARG_WORLD_STYLE);
        return ResourceLocation.DEFAULT_NAMESPACE.equals(worldStyle.getNamespace()) ? worldStyle.getPath() : worldStyle.toString();
    }

    private static CompletableFuture<Suggestions> suggestLostCitiesWorldStyles(CommandSourceStack source, SuggestionsBuilder builder) {
        if (!ModCompat.isLoadTlc()) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(TlcCompat.worldStyleSuggestions(source.getServer().overworld()), builder);
    }

    private static CompletableFuture<Suggestions> suggestLostCitiesProfiles(SuggestionsBuilder builder) {
        if (!ModCompat.isLoadTlc()) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(TlcCompat.profileSuggestions(), builder);
    }

    private static int setLostCitiesProfile(CommandSourceStack source, ResourceLocation id, String profile) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }
        if (!ModCompat.isLoadTlc()) {
            throw ERROR_LOST_CITIES_NOT_INSTALLED.create();
        }
        if (!TlcCompat.isProfileRegistered(profile)) {
            throw ERROR_UNKNOWN_LOST_CITIES_PROFILE.create(profile);
        }

        try {
            DynamicDimensionManager.setLostCitiesProfile(source.getServer(), id, profile);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension Lost Cities profile {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.profile.success",
                id,
                profile.trim()), true);
        return 1;
    }

    private static int setLostCitiesWorldStyle(CommandSourceStack source, ResourceLocation id, String worldStyle) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }
        if (!ModCompat.isLoadTlc()) {
            throw ERROR_LOST_CITIES_NOT_INSTALLED.create();
        }
        if (!TlcCompat.isWorldStyleRegistered(source.getServer().overworld(), worldStyle)) {
            throw ERROR_UNKNOWN_LOST_CITIES_WORLD_STYLE.create(worldStyle);
        }

        try {
            DynamicDimensionManager.setLostCitiesWorldStyle(source.getServer(), id, worldStyle);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension Lost Cities worldstyle {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.worldstyle.success",
                id,
                worldStyle.trim()), true);
        return 1;
    }

    private static int setGameMode(CommandSourceStack source, ResourceLocation id, GameType gameMode) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        try {
            DynamicDimensionManager.setGameMode(source.getServer(), id, gameMode);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension game mode setting {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.gamemode.success",
                id,
                DynamicDimensionCommands.gameModeName(gameMode)), true);
        return 1;
    }

    private static int setAllowRespawn(CommandSourceStack source, ResourceLocation id, boolean allowRespawn) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        try {
            DynamicDimensionManager.setAllowRespawn(source.getServer(), id, allowRespawn);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension respawn setting {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.respawn.success",
                id,
                DynamicDimensionCommands.booleanName(allowRespawn)), true);
        return 1;
    }

    private static int setSave(CommandSourceStack source, ResourceLocation id, boolean save) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        try {
            DynamicDimensionManager.setSave(source.getServer(), id, save);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension save setting {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        source.sendSuccess(() -> Component.translatable(
                "commands.extractioncities.ecdim.set.save.success",
                id,
                DynamicDimensionCommands.booleanName(save)), true);
        return 1;
    }

    private static int setTeleportPoint(CommandSourceStack source, ResourceLocation id, BlockPos teleportPoint) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        ServerLevel level = DynamicDimensionManager.getLevel(source.getServer(), id)
                .orElseThrow(() -> ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id));
        if (teleportPoint != null && !level.isInWorldBounds(teleportPoint)) {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }

        try {
            DynamicDimensionManager.setTeleportPoint(source.getServer(), id, teleportPoint);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to update dynamic dimension teleport point {}", id, exception);
            throw ERROR_SET_FAILED.create();
        }

        if (teleportPoint == null) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.set.teleport_point.clear.success",
                    id), true);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.set.teleport_point.success",
                    id,
                    DynamicDimensionCommands.positionName(teleportPoint)), true);
        }
        return 1;
    }
}

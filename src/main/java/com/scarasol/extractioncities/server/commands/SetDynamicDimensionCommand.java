package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.compat.ModCompat;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;

import java.io.IOException;

public final class SetDynamicDimensionCommand {
    private static final String ARG_ID = "id";
    private static final String ARG_ENABLED = "enabled";
    private static final String ARG_POS = "pos";

    private static final SimpleCommandExceptionType ERROR_LOST_CITIES_NOT_INSTALLED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.lostcities_not_installed"));
    private static final SimpleCommandExceptionType ERROR_SET_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.set_failed"));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_DYNAMIC_DIMENSION = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_dynamic_dimension", id));

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
                        .then(Commands.literal("lostcities")
                                .then(Commands.argument(ARG_ENABLED, BoolArgumentType.bool())
                                        .executes(context -> setLostCities(
                                                context.getSource(),
                                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                                BoolArgumentType.getBool(context, ARG_ENABLED)))))
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

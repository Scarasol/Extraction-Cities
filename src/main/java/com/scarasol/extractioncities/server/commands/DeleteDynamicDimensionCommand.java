package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

public final class DeleteDynamicDimensionCommand {
    private static final String ARG_ID = "id";

    private static final SimpleCommandExceptionType ERROR_DELETE_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.delete_failed"));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_DYNAMIC_DIMENSION = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_dynamic_dimension", id));
    private static final Dynamic2CommandExceptionType ERROR_DIMENSION_OCCUPIED = new Dynamic2CommandExceptionType((id, count) ->
            Component.translatable("commands.extractioncities.ecdim.error.dimension_occupied", id, count));

    private DeleteDynamicDimensionCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("delete")
                .then(Commands.argument(ARG_ID, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                DynamicDimensionManager.dynamicDimensions().stream().map(record -> record.id().getPath()),
                                builder))
                        .executes(context -> delete(
                                context.getSource(),
                                DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                false))
                        .then(Commands.literal("force")
                                .executes(context -> delete(
                                        context.getSource(),
                                        DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID)),
                                        true)))));
    }

    private static int delete(CommandSourceStack source, ResourceLocation id, boolean force) throws CommandSyntaxException {
        if (DynamicDimensionManager.getRecord(id).isEmpty()) {
            throw ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id);
        }

        try {
            DynamicDimensionManager.DeleteResult result = DynamicDimensionManager.deleteDimension(source.getServer(), id, force);
            if (force) {
                source.sendSuccess(() -> Component.translatable(
                        "commands.extractioncities.ecdim.delete.force.success",
                        id,
                        result.movedPlayers()), true);
            } else {
                source.sendSuccess(() -> Component.translatable(
                        "commands.extractioncities.ecdim.delete.success",
                        id), true);
            }
            return 1;
        } catch (DynamicDimensionManager.DynamicDimensionOccupiedException exception) {
            throw ERROR_DIMENSION_OCCUPIED.create(id, exception.playerCount());
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to delete dynamic dimension {}", id, exception);
            throw ERROR_DELETE_FAILED.create();
        }
    }
}

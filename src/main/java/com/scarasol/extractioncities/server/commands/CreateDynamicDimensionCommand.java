package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionStorageMode;
import com.scarasol.extractioncities.world.level.dimension.ExtractionCitiesDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public final class CreateDynamicDimensionCommand {
    private static final String ARG_ID = "id";
    private static final String ARG_PERSISTENT = "persistent";
    private static final String ARG_SEED = "seed";
    private static final String ARG_BIOME = "biome";

    private static final SimpleCommandExceptionType ERROR_CREATE_FAILED = new SimpleCommandExceptionType(
            Component.translatable("commands.extractioncities.ecdim.error.create_failed"));
    private static final DynamicCommandExceptionType ERROR_DIMENSION_EXISTS = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.dimension_exists", id));
    private static final DynamicCommandExceptionType ERROR_INVALID_NAMESPACE = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.invalid_namespace", id, ExtractionCitiesMod.MODID));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_BIOME = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_biome", id));

    private CreateDynamicDimensionCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("create")
                .then(Commands.argument(ARG_ID, StringArgumentType.word())
                        .then(Commands.argument(ARG_PERSISTENT, BoolArgumentType.bool())
                                .executes(context -> execute(context, CreateShape.overworld(false)))
                                .then(seedBranch())
                                .then(flatBranch(false))
                                .then(singleBiomeBranch(false)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Long> seedBranch() {
        return Commands.argument(ARG_SEED, LongArgumentType.longArg())
                .executes(context -> execute(context, CreateShape.overworld(true)))
                .then(flatBranch(true))
                .then(singleBiomeBranch(true));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flatBranch(boolean readsSeed) {
        return Commands.literal("flat")
                .executes(context -> execute(context, CreateShape.flat(readsSeed, false)))
                .then(Commands.argument(ARG_BIOME, ResourceLocationArgument.id())
                        .suggests(CreateDynamicDimensionCommand::suggestBiomes)
                        .executes(context -> execute(context, CreateShape.flat(readsSeed, true))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> singleBiomeBranch(boolean readsSeed) {
        return Commands.literal("single_biome")
                .then(Commands.argument(ARG_BIOME, ResourceLocationArgument.id())
                        .suggests(CreateDynamicDimensionCommand::suggestBiomes)
                        .executes(context -> execute(context, CreateShape.singleBiome(readsSeed))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, CreateShape shape) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CreateRequest request = readRequest(context, shape);
        validateRequest(source, request);

        try {
            ServerLevel level = DynamicDimensionManager.createDimension(
                    source.getServer(),
                    request.id(),
                    request.storage(),
                    request.seed(),
                    request.generator(),
                    request.biome());
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.create.success",
                    DynamicDimensionCommands.storageName(request.storage()),
                    level.dimension().location(),
                    request.seed()), true);
            return 1;
        } catch (Exception exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to create dynamic dimension {}", request.id(), exception);
            throw ERROR_CREATE_FAILED.create();
        }
    }

    private static CreateRequest readRequest(CommandContext<CommandSourceStack> context, CreateShape shape) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation id = DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID));
        DynamicDimensionStorageMode storage = BoolArgumentType.getBool(context, ARG_PERSISTENT)
                ? DynamicDimensionStorageMode.PERSISTENT
                : DynamicDimensionStorageMode.SESSION;
        long seed = shape.readsSeed()
                ? LongArgumentType.getLong(context, ARG_SEED)
                : source.getServer().getWorldData().worldGenOptions().seed();
        ResourceLocation biome = shape.readsBiome()
                ? ResourceLocationArgument.getId(context, ARG_BIOME)
                : null;

        return new CreateRequest(id, storage, seed, shape.generator(), biome);
    }

    private static void validateRequest(CommandSourceStack source, CreateRequest request) throws CommandSyntaxException {
        if (!ExtractionCitiesMod.MODID.equals(request.id().getNamespace())) {
            throw ERROR_INVALID_NAMESPACE.create(request.id());
        }

        if (DynamicDimensionManager.getLevel(source.getServer(), request.id()).isPresent()) {
            throw ERROR_DIMENSION_EXISTS.create(request.id());
        }

        if (request.biome() != null && !hasBiome(source, request.biome())) {
            throw ERROR_UNKNOWN_BIOME.create(request.biome());
        }
    }

    private static boolean hasBiome(CommandSourceStack source, ResourceLocation biome) {
        return source.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, biome))
                .isPresent();
    }

    private static CompletableFuture<Suggestions> suggestBiomes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                context.getSource().registryAccess().registryOrThrow(Registries.BIOME).keySet(),
                builder);
    }

    private record CreateShape(boolean readsSeed, ResourceLocation generator, boolean readsBiome) {
        private static CreateShape overworld(boolean readsSeed) {
            return new CreateShape(readsSeed, ExtractionCitiesDimensions.OVERWORLD_GENERATOR_ID, false);
        }

        private static CreateShape flat(boolean readsSeed, boolean readsBiome) {
            return new CreateShape(readsSeed, ExtractionCitiesDimensions.FLAT_GENERATOR_ID, readsBiome);
        }

        private static CreateShape singleBiome(boolean readsSeed) {
            return new CreateShape(readsSeed, ExtractionCitiesDimensions.SINGLE_BIOME_GENERATOR_ID, true);
        }
    }

    private record CreateRequest(
            ResourceLocation id,
            DynamicDimensionStorageMode storage,
            long seed,
            ResourceLocation generator,
            @Nullable ResourceLocation biome
    ) {
    }
}

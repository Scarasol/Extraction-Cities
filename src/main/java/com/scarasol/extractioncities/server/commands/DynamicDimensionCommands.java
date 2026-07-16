package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.server.level.DynamicDimensionGameModes;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.server.level.DynamicDimensionRespawns;
import com.scarasol.extractioncities.server.level.DynamicDimensionSurfacePoints;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionRecord;
import com.scarasol.extractioncities.world.level.dimension.DynamicDimensionStorageMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DynamicDimensionCommands {
    private static final String ARG_ID = "id";
    private static final String ARG_TARGETS = "targets";
    private static final String ARG_RANGE = "range";
    private static final int RANDOM_ATTEMPTS = 16;
    private static final int MAX_RANDOM_RANGE = 30_000_000;
    private static final int MIN_WORLD_COORDINATE = -30_000_000;
    private static final int MAX_WORLD_COORDINATE = 29_999_999;

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_DYNAMIC_DIMENSION = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.unknown_dynamic_dimension", id));
    private static final DynamicCommandExceptionType ERROR_UNMANAGED_DIMENSION = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.unmanaged_dimension", id));
    private DynamicDimensionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ecdim")
                .requires(source -> source.hasPermission(2));

        CreateDynamicDimensionCommand.register(root);
        DeleteDynamicDimensionCommand.register(root);
        BuildingListDynamicDimensionCommand.register(root);
        SetDynamicDimensionCommand.register(root);
        registerList(root);
        registerTeleport(root);

        dispatcher.register(root);
    }

    private static void registerList(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("list")
                .executes(context -> list(context.getSource())));
    }

    private static void registerTeleport(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("tp")
                .executes(context -> teleportToOverworld(context.getSource(), sourceEntity(context.getSource())))
                .then(Commands.literal("overworld")
                        .executes(context -> teleportToOverworld(context.getSource(), sourceEntity(context.getSource())))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.entities())
                                .executes(context -> teleportToOverworld(
                                        context.getSource(),
                                        EntityArgument.getEntities(context, ARG_TARGETS)))))
                .then(Commands.argument(ARG_ID, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                DynamicDimensionManager.dynamicDimensions().stream().map(record -> record.id().getPath()),
                                builder))
                        .executes(context -> teleport(
                                context.getSource(),
                                readManagedId(context),
                                sourceEntity(context.getSource()),
                                null))
                        .then(Commands.literal("random")
                                .then(Commands.argument(ARG_RANGE, IntegerArgumentType.integer(1, MAX_RANDOM_RANGE))
                                        .executes(context -> teleport(
                                                context.getSource(),
                                                readManagedId(context),
                                                sourceEntity(context.getSource()),
                                                IntegerArgumentType.getInteger(context, ARG_RANGE)))
                                        .then(Commands.argument(ARG_TARGETS, EntityArgument.entities())
                                                .executes(context -> teleport(
                                                        context.getSource(),
                                                        readManagedId(context),
                                                        EntityArgument.getEntities(context, ARG_TARGETS),
                                                        IntegerArgumentType.getInteger(context, ARG_RANGE))))))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.entities())
                                .executes(context -> teleport(
                                        context.getSource(),
                                        readManagedId(context),
                                        EntityArgument.getEntities(context, ARG_TARGETS),
                                        null)))));
    }

    private static int list(CommandSourceStack source) {
        var records = DynamicDimensionManager.dynamicDimensions();
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.list.empty"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.list.header"), false);
        for (DynamicDimensionRecord record : records) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.extractioncities.ecdim.list.entry",
                    record.id(),
                    storageName(record.storage()),
                    record.seed(),
                    record.dimensionType(),
                    record.generator(),
                    record.biome() == null ? "-" : record.biome(),
                    booleanName(record.generateStructures()),
                    structureWhitelistName(record.structureWhitelist()),
                    booleanName(record.generateLostCities()),
                    record.lostCitiesProfile(),
                    record.lostCitiesWorldStyle(),
                    record.gameMode() == null ? "-" : gameModeName(record.gameMode()),
                    record.teleportPoint() == null ? "-" : positionName(record.teleportPoint()),
                    booleanName(record.allowRespawn()),
                    booleanName(record.save())), false);
        }
        return records.size();
    }

    private static int teleport(CommandSourceStack source, ResourceLocation id, Collection<? extends Entity> targets, @Nullable Integer randomRange) throws CommandSyntaxException {
        if (Level.OVERWORLD.location().equals(id)) {
            return teleportToOverworld(source, targets);
        }

        MinecraftServer server = source.getServer();
        ServerLevel target = DynamicDimensionManager.getLevel(server, id)
                .orElseThrow(() -> ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id));
        DynamicDimensionRecord record = DynamicDimensionManager.getRecord(target.dimension().location())
                .orElseThrow(() -> ERROR_UNMANAGED_DIMENSION.create(id));

        for (Entity entity : targets) {
            if (randomRange == null) {
                teleportEntityToDynamicDestination(source.getServer(), entity, target, record);
            } else {
                teleportEntityToRandomDestination(source.getServer(), entity, target, record, randomRange);
            }
        }

        int count = targets.size();
        if (randomRange == null) {
            source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.tp.success", count, id), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.tp.random.success", count, id, randomRange), true);
        }
        return count;
    }

    private static int teleportToOverworld(CommandSourceStack source, Collection<? extends Entity> targets) {
        ServerLevel overworld = source.getServer().overworld();
        BlockPos fallback = findSurfacePoint(overworld, overworld.getSharedSpawnPos());
        for (Entity entity : targets) {
            if (entity instanceof ServerPlayer player) {
                DynamicDimensionRespawns.teleportToOverworldRespawnOrSpawn(player);
            } else {
                teleportEntityToPoint(entity, overworld, fallback);
            }
        }

        int count = targets.size();
        source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.tp.overworld.success", count), true);
        return count;
    }

    private static void teleportEntityToDynamicDestination(MinecraftServer server, Entity entity, ServerLevel target, DynamicDimensionRecord record) {
        if (entity instanceof ServerPlayer player && record.allowRespawn() && DynamicDimensionRespawns.teleportToDynamicRespawn(player, target)) {
            return;
        }

        BlockPos teleportPoint = record.teleportPoint();
        if (teleportPoint == null) {
            teleportEntityToSurfaceSpawn(server, entity, target, record);
            return;
        }

        teleportEntityToPoint(entity, target, teleportPoint);
    }

    private static void teleportEntityToRandomDestination(MinecraftServer server, Entity entity, ServerLevel target, DynamicDimensionRecord record, int range) {
        BlockPos base = dynamicTeleportBase(server, entity, target, record);
        teleportEntityToPoint(entity, target, findRandomSurfacePoint(target, base, range));
    }

    private static BlockPos dynamicTeleportBase(MinecraftServer server, Entity entity, ServerLevel target, DynamicDimensionRecord record) {
        if (entity instanceof ServerPlayer player && record.allowRespawn()) {
            Optional<BlockPos> respawnPosition = DynamicDimensionRespawns.dynamicRespawnPosition(player, target);
            if (respawnPosition.isPresent()) {
                return respawnPosition.get();
            }
        }

        BlockPos teleportPoint = record.teleportPoint();
        return teleportPoint == null ? resolveSpawnTeleportPoint(server, target, record) : teleportPoint;
    }

    private static void teleportEntityToSurfaceSpawn(MinecraftServer server, Entity entity, ServerLevel target, DynamicDimensionRecord record) {
        teleportEntityToPoint(entity, target, resolveSpawnTeleportPoint(server, target, record));
    }

    private static BlockPos resolveSpawnTeleportPoint(MinecraftServer server, ServerLevel target, DynamicDimensionRecord record) {
        BlockPos spawn = target.getSharedSpawnPos();
        Optional<BlockPos> exactSurface = DynamicDimensionSurfacePoints.findAt(target, spawn);
        if (exactSurface.isPresent()) {
            return exactSurface.get();
        }

        Optional<BlockPos> nearbySurface = DynamicDimensionSurfacePoints.findNear(target, spawn);
        if (nearbySurface.isPresent()) {
            BlockPos position = nearbySurface.get();
            rememberTeleportPoint(server, record, position);
            return position;
        }

        return DynamicDimensionSurfacePoints.findFluidSurfaceOrOriginal(target, spawn);
    }

    private static void teleportEntityToPoint(Entity entity, ServerLevel target, BlockPos position) {
        target.getChunk(position);
        if (entity.teleportTo(target, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, Set.of(), entity.getYRot(), entity.getXRot())
                && entity instanceof ServerPlayer player) {
            DynamicDimensionGameModes.applyForCurrentDimension(player);
        }
    }

    private static BlockPos findRandomSurfacePoint(ServerLevel target, BlockPos base, int range) {
        RandomSource random = target.getRandom();
        for (int attempt = 0; attempt < RANDOM_ATTEMPTS; attempt++) {
            BlockPos candidate = new BlockPos(
                    randomCoordinate(random, base.getX(), range),
                    base.getY(),
                    randomCoordinate(random, base.getZ(), range));
            if (Level.isInSpawnableBounds(candidate)) {
                Optional<BlockPos> surface = DynamicDimensionSurfacePoints.findAt(target, candidate);
                if (surface.isPresent()) {
                    return surface.get();
                }
            }
        }

        return findSurfacePoint(target, base);
    }

    private static int randomCoordinate(RandomSource random, int origin, int range) {
        long coordinate = (long) origin + random.nextIntBetweenInclusive(-range, range);
        return (int) Math.max(MIN_WORLD_COORDINATE, Math.min(MAX_WORLD_COORDINATE, coordinate));
    }

    private static BlockPos findSurfacePoint(ServerLevel target, BlockPos position) {
        return DynamicDimensionSurfacePoints.findNearOrFallback(target, position);
    }

    private static void rememberTeleportPoint(MinecraftServer server, DynamicDimensionRecord record, BlockPos position) {
        try {
            DynamicDimensionManager.setTeleportPoint(server, record.id(), position);
        } catch (IOException exception) {
            ExtractionCitiesMod.LOGGER.warn("Failed to remember solid teleport point for dynamic dimension {}", record.id(), exception);
        }
    }

    static Component storageName(DynamicDimensionStorageMode storage) {
        return Component.translatable("commands.extractioncities.ecdim.storage." + storage.id());
    }

    static Component booleanName(boolean enabled) {
        return Component.translatable("commands.extractioncities.ecdim.boolean." + (enabled ? "enabled" : "disabled"));
    }

    static Component gameModeName(GameType gameMode) {
        return Component.translatable("commands.extractioncities.ecdim.gamemode." + gameMode.getName());
    }

    static Component positionName(BlockPos position) {
        return Component.literal(position.getX() + " " + position.getY() + " " + position.getZ());
    }

    static Component structureWhitelistName(Collection<ResourceLocation> structures) {
        if (structures.isEmpty()) {
            return Component.literal("-");
        }

        return Component.literal(String.join(", ", structures.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList()));
    }

    private static ResourceLocation readManagedId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID));
    }

    private static Collection<? extends Entity> sourceEntity(CommandSourceStack source) throws CommandSyntaxException {
        return List.of(source.getEntityOrException());
    }
}

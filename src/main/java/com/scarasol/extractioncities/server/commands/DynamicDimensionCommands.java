package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import com.scarasol.extractioncities.server.level.DynamicDimensionRespawns;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
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
                .executes(context -> teleportToOverworld(context.getSource(), sourcePlayer(context.getSource())))
                .then(Commands.literal("overworld")
                        .executes(context -> teleportToOverworld(context.getSource(), sourcePlayer(context.getSource())))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(context -> teleportToOverworld(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, ARG_TARGETS)))))
                .then(Commands.argument(ARG_ID, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                DynamicDimensionManager.dynamicDimensions().stream().map(record -> record.id().getPath()),
                                builder))
                        .executes(context -> teleport(
                                context.getSource(),
                                readManagedId(context),
                                sourcePlayer(context.getSource()),
                                null))
                        .then(Commands.literal("random")
                                .then(Commands.argument(ARG_RANGE, IntegerArgumentType.integer(1, MAX_RANDOM_RANGE))
                                        .executes(context -> teleport(
                                                context.getSource(),
                                                readManagedId(context),
                                                sourcePlayer(context.getSource()),
                                                IntegerArgumentType.getInteger(context, ARG_RANGE)))
                                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                                .executes(context -> teleport(
                                                        context.getSource(),
                                                        readManagedId(context),
                                                        EntityArgument.getPlayers(context, ARG_TARGETS),
                                                        IntegerArgumentType.getInteger(context, ARG_RANGE))))))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(context -> teleport(
                                        context.getSource(),
                                        readManagedId(context),
                                        EntityArgument.getPlayers(context, ARG_TARGETS),
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
                    record.gameMode() == null ? "-" : gameModeName(record.gameMode()),
                    record.teleportPoint() == null ? "-" : positionName(record.teleportPoint()),
                    booleanName(record.allowRespawn())), false);
        }
        return records.size();
    }

    private static int teleport(CommandSourceStack source, ResourceLocation id, Collection<ServerPlayer> targets, @Nullable Integer randomRange) throws CommandSyntaxException {
        if (Level.OVERWORLD.location().equals(id)) {
            return teleportToOverworld(source, targets);
        }

        MinecraftServer server = source.getServer();
        ServerLevel target = DynamicDimensionManager.getLevel(server, id)
                .orElseThrow(() -> ERROR_UNKNOWN_DYNAMIC_DIMENSION.create(id));
        DynamicDimensionRecord record = DynamicDimensionManager.getRecord(target.dimension().location())
                .orElseThrow(() -> ERROR_UNMANAGED_DIMENSION.create(id));

        for (ServerPlayer player : targets) {
            if (randomRange == null) {
                teleportPlayerToDynamicDestination(player, target, record);
            } else {
                teleportPlayerToRandomDestination(player, target, record, randomRange);
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

    private static int teleportToOverworld(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            DynamicDimensionRespawns.teleportToOverworldRespawnOrSpawn(player);
        }

        int count = targets.size();
        source.sendSuccess(() -> Component.translatable("commands.extractioncities.ecdim.tp.overworld.success", count), true);
        return count;
    }

    private static void teleportPlayerToDynamicDestination(ServerPlayer player, ServerLevel target, DynamicDimensionRecord record) {
        if (record.allowRespawn() && DynamicDimensionRespawns.teleportToDynamicRespawn(player, target)) {
            return;
        }

        BlockPos teleportPoint = record.teleportPoint();
        if (teleportPoint == null) {
            teleportPlayerToSurfaceSpawn(player, target);
            return;
        }

        teleportPlayerToPoint(player, target, teleportPoint);
    }

    private static void teleportPlayerToRandomDestination(ServerPlayer player, ServerLevel target, DynamicDimensionRecord record, int range) {
        BlockPos base = dynamicTeleportBase(player, target, record);
        teleportPlayerToPoint(player, target, findRandomSurfacePoint(target, base, range));
    }

    private static BlockPos dynamicTeleportBase(ServerPlayer player, ServerLevel target, DynamicDimensionRecord record) {
        if (record.allowRespawn()) {
            Optional<BlockPos> respawnPosition = DynamicDimensionRespawns.dynamicRespawnPosition(player, target);
            if (respawnPosition.isPresent()) {
                return respawnPosition.get();
            }
        }

        return record.teleportPoint() == null ? target.getSharedSpawnPos() : record.teleportPoint();
    }

    private static void teleportPlayerToSurfaceSpawn(ServerPlayer player, ServerLevel target) {
        BlockPos spawn = target.getSharedSpawnPos();
        teleportPlayerToPoint(player, target, findSurfacePoint(target, spawn));
    }

    private static void teleportPlayerToPoint(ServerPlayer player, ServerLevel target, BlockPos position) {
        target.getChunk(position);
        player.teleportTo(target, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot());
    }

    private static BlockPos findRandomSurfacePoint(ServerLevel target, BlockPos base, int range) {
        RandomSource random = target.getRandom();
        for (int attempt = 0; attempt < RANDOM_ATTEMPTS; attempt++) {
            BlockPos candidate = new BlockPos(
                    randomCoordinate(random, base.getX(), range),
                    base.getY(),
                    randomCoordinate(random, base.getZ(), range));
            if (Level.isInSpawnableBounds(candidate)) {
                return findSurfacePoint(target, candidate);
            }
        }

        return findSurfacePoint(target, base);
    }

    private static int randomCoordinate(RandomSource random, int origin, int range) {
        long coordinate = (long) origin + random.nextIntBetweenInclusive(-range, range);
        return (int) Math.max(MIN_WORLD_COORDINATE, Math.min(MAX_WORLD_COORDINATE, coordinate));
    }

    private static BlockPos findSurfacePoint(ServerLevel target, BlockPos spawn) {
        target.getChunk(spawn);
        return new BlockPos(spawn.getX(), findSurfaceY(target, spawn), spawn.getZ());
    }

    private static int findSurfaceY(ServerLevel target, BlockPos spawn) {
        int surfaceY = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        int scannedY = scanForSurfaceY(target, spawn.getX(), spawn.getZ(), surfaceY);
        if (scannedY != Integer.MIN_VALUE) {
            return scannedY;
        }

        return Math.max(target.getMinBuildHeight() + 1, Math.min(spawn.getY(), target.getMaxBuildHeight() - 2));
    }

    private static int scanForSurfaceY(ServerLevel target, int x, int z, int surfaceY) {
        int minY = target.getMinBuildHeight();
        int maxY = target.getMaxBuildHeight();
        int startY = Math.min(maxY - 2, Math.max(minY + 1, surfaceY));

        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos head = new BlockPos.MutableBlockPos();
        for (int y = startY; y > minY; y--) {
            below.set(x, y - 1, z);
            feet.set(x, y, z);
            head.set(x, y + 1, z);
            if (isSurface(target, below) && isPassable(target, feet) && isPassable(target, head)) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean isSurface(ServerLevel target, BlockPos pos) {
        BlockState state = target.getBlockState(pos);
        return !state.getCollisionShape(target, pos).isEmpty() || !state.getFluidState().isEmpty();
    }

    private static boolean isPassable(ServerLevel target, BlockPos pos) {
        return target.getBlockState(pos).getCollisionShape(target, pos).isEmpty();
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

    private static ResourceLocation readManagedId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return DynamicDimensionCommandIds.parseManagedId(StringArgumentType.getString(context, ARG_ID));
    }

    private static Collection<ServerPlayer> sourcePlayer(CommandSourceStack source) throws CommandSyntaxException {
        return List.of(source.getPlayerOrException());
    }
}

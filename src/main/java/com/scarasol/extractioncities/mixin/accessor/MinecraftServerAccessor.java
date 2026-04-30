package com.scarasol.extractioncities.mixin.accessor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess extractioncities$getStorageSource();

    @Accessor("executor")
    Executor extractioncities$getExecutor();
}

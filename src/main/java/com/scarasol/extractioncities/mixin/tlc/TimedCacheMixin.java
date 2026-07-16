package com.scarasol.extractioncities.mixin.tlc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mcjty.lostcities.varia.TimedCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.IntSupplier;

@Mixin(value = TimedCache.class, remap = false)
public abstract class TimedCacheMixin {
    private static final int DEFAULT_CACHE_CLEANUP_SECONDS = 300;

    @WrapOperation(method = "getTtlMillis", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int extractioncities$useDefaultTtlBeforeConfigLoads(IntSupplier supplier, Operation<Integer> original) {
        try {
            return original.call(supplier);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("Cannot get config value before config is loaded")) {
                return DEFAULT_CACHE_CLEANUP_SECONDS;
            }
            throw exception;
        }
    }
}

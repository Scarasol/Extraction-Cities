package com.scarasol.extractioncities.mixin;

import com.scarasol.extractioncities.server.level.DynamicDimensionManager;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureManager.class)
public abstract class StructureManagerMixin {
    @Shadow
    @Final
    private LevelAccessor level;

    @Shadow
    @Final
    private WorldOptions worldOptions;

    @Inject(method = "shouldGenerateStructures", at = @At("HEAD"), cancellable = true)
    private void extractioncities$useDynamicStructureSetting(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(DynamicDimensionManager.shouldGenerateStructures(this.level, this.worldOptions.generateStructures()));
    }
}

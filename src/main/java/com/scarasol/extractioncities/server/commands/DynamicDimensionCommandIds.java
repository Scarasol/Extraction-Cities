package com.scarasol.extractioncities.server.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.scarasol.extractioncities.ExtractionCitiesMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class DynamicDimensionCommandIds {
    private static final DynamicCommandExceptionType ERROR_INVALID_ID = new DynamicCommandExceptionType(id ->
            Component.translatable("commands.extractioncities.ecdim.error.invalid_id", id));

    private DynamicDimensionCommandIds() {
    }

    static ResourceLocation parseManagedId(String input) throws CommandSyntaxException {
        String fullId = input.contains(":") ? input : ExtractionCitiesMod.MODID + ":" + input;
        ResourceLocation id = ResourceLocation.tryParse(fullId);
        if (id == null) {
            throw ERROR_INVALID_ID.create(input);
        }
        return id;
    }
}

package com.scarasol.extractioncities.configuration;

import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * @author Scarasol
 */
public class CommonConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Boolean> DEFAULT_GENERATE_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DEFAULT_GENERATE_LOST_CITIES;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LOST_CITIES_PROFILE;
    public static final ForgeConfigSpec.ConfigValue<Integer> DEFAULT_GAME_MODE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DEFAULT_ALLOW_RESPAWN;

    static {
        BUILDER.push("Dynamic Dimension Defaults");
        DEFAULT_GENERATE_STRUCTURES = BUILDER.comment(
                "Whether newly created dynamic dimensions generate structures by default."
        ).define("Default Generate Structures", false);
        DEFAULT_GENERATE_LOST_CITIES = BUILDER.comment(
                "Whether newly created dynamic dimensions generate The Lost Cities cities by default.",
                "Only takes effect when The Lost Cities mod is installed."
        ).define("Default Generate Lost Cities", true);
        DEFAULT_LOST_CITIES_PROFILE = BUILDER.comment(
                "The Lost Cities profile used by dynamic dimensions that generate Lost Cities cities.",
                "Common built-in examples: default, largecities, onlycities.",
                "Only takes effect when The Lost Cities mod is installed."
        ).define("Default Lost Cities Profile", "onlycities", value -> value instanceof String string && !string.isBlank());
        DEFAULT_GAME_MODE = BUILDER.comment(
                "Default game mode for newly created dynamic dimensions.",
                "Uses vanilla game mode IDs: 0 = survival, 1 = creative, 2 = adventure, 3 = spectator."
        ).defineInRange("Default Game Mode", GameType.ADVENTURE.getId(), GameType.SURVIVAL.getId(), GameType.SPECTATOR.getId());
        DEFAULT_ALLOW_RESPAWN = BUILDER.comment(
                "Whether newly created dynamic dimensions allow players to set and use in-dimension respawn points by default."
        ).define("Default Allow Respawn", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static GameType defaultGameMode() {
        return GameType.byId(DEFAULT_GAME_MODE.get());
    }
}

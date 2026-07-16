package com.scarasol.extractioncities.configuration;

import net.minecraft.world.level.GameType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Scarasol
 */
public class CommonConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Boolean> DEFAULT_GENERATE_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEFAULT_STRUCTURE_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DEFAULT_GENERATE_LOST_CITIES;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LOST_CITIES_PROFILE;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LOST_CITIES_WORLD_STYLE;
    public static final ForgeConfigSpec.ConfigValue<Integer> DEFAULT_GAME_MODE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DEFAULT_ALLOW_RESPAWN;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DEFAULT_SAVE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> FORCE_DEATH_ON_MISSING_SESSION_DIMENSION;

    static {
        BUILDER.push("Dynamic Dimension Defaults");
        DEFAULT_GENERATE_STRUCTURES = BUILDER.comment(
                "Whether newly created dynamic dimensions generate structures by default."
        ).define("Default Generate Structures", false);
        DEFAULT_STRUCTURE_WHITELIST = BUILDER.comment(
                "Structures in this list can still generate when structure generation is disabled.",
                "Use structure ids such as minecraft:ancient_city or minecraft:village_plains.",
                "An empty list means no structures are allowed when structure generation is disabled."
        ).defineList("Default Structure Whitelist", List.of(), value -> value instanceof String string && ResourceLocation.tryParse(string) != null);
        DEFAULT_GAME_MODE = BUILDER.comment(
                "Default game mode for newly created dynamic dimensions.",
                "Uses vanilla game mode IDs: 0 = survival, 1 = creative, 2 = adventure, 3 = spectator."
        ).defineInRange("Default Game Mode", GameType.ADVENTURE.getId(), GameType.SURVIVAL.getId(), GameType.SPECTATOR.getId());
        DEFAULT_ALLOW_RESPAWN = BUILDER.comment(
                "Whether newly created dynamic dimensions allow players to set and use in-dimension respawn points by default."
        ).define("Default Allow Respawn", false);
        DEFAULT_SAVE = BUILDER.comment(
                "Whether newly created dynamic dimensions save world changes by default.",
                "When disabled, the dimension can still run normally, but its ServerLevel is marked noSave and world changes are not written during normal saves."
        ).define("Default Save", true);
        BUILDER.pop();

        BUILDER.push("Dynamic Dimension Runtime");
        FORCE_DEATH_ON_MISSING_SESSION_DIMENSION = BUILDER.comment(
                "Whether players who log back in after their last session dynamic dimension was removed should die through the vanilla death flow.",
                "When enabled, normal death events still run, vanilla keepInventory is respected, and remaining drops/experience are voided instead of appearing in the Overworld."
        ).define("Force Death On Missing Session Dimension", true);
        BUILDER.pop();

        BUILDER.push("Compat - The Lost Cities");
        DEFAULT_GENERATE_LOST_CITIES = BUILDER.comment(
                "Whether newly created dynamic dimensions generate The Lost Cities cities by default.",
                "Only takes effect when The Lost Cities mod is installed."
        ).define("Default Generate Lost Cities", true);
        DEFAULT_LOST_CITIES_PROFILE = BUILDER.comment(
                "The Lost Cities profile used by newly created dynamic dimensions.",
                "Common built-in examples: default, largecities, onlycities.",
                "Only takes effect when The Lost Cities mod is installed."
        ).define("Default Lost Cities Profile", "onlycities", value -> value instanceof String string && !string.isBlank());
        DEFAULT_LOST_CITIES_WORLD_STYLE = BUILDER.comment(
                "The Lost Cities worldstyle used by newly created dynamic dimensions.",
                "Common built-in examples: standard, standard_everywhere.",
                "Names without a namespace are resolved by The Lost Cities as lostcities:<name>.",
                "Only takes effect when The Lost Cities mod is installed."
        ).define("Default Lost Cities Worldstyle", "standard", value -> value instanceof String string && !string.isBlank());
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static GameType defaultGameMode() {
        return GameType.byId(DEFAULT_GAME_MODE.get());
    }

    public static Set<ResourceLocation> defaultStructureWhitelist() {
        Set<ResourceLocation> structures = new LinkedHashSet<>();
        for (String value : DEFAULT_STRUCTURE_WHITELIST.get()) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                structures.add(id);
            }
        }
        return Set.copyOf(structures);
    }

    public static String defaultLostCitiesWorldStyle() {
        return DEFAULT_LOST_CITIES_WORLD_STYLE.get().trim();
    }

    public static String defaultLostCitiesProfile() {
        return DEFAULT_LOST_CITIES_PROFILE.get().trim();
    }
}

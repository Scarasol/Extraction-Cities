package com.scarasol.extractioncities.world.level.dimension;

import java.util.Locale;
import java.util.Optional;

public enum DynamicDimensionStorageMode {
    PERSISTENT("persistent"),
    SESSION("session");

    private final String id;

    DynamicDimensionStorageMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static Optional<DynamicDimensionStorageMode> byId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        for (DynamicDimensionStorageMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}

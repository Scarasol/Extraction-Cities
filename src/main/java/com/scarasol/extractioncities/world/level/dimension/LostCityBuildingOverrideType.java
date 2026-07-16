package com.scarasol.extractioncities.world.level.dimension;

import java.util.Optional;

public enum LostCityBuildingOverrideType {
    BUILDING("building"),
    MULTI_BUILDING("multi_building");

    private final String id;

    LostCityBuildingOverrideType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<LostCityBuildingOverrideType> byId(String id) {
        for (LostCityBuildingOverrideType type : values()) {
            if (type.id.equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

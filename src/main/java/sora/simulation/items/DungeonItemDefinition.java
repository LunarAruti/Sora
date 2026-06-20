package sora.simulation.items;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;

public record DungeonItemDefinition(
        String id,
        String name,
        DungeonItemKind kind,
        DungeonItemCategory category,
        DungeonItemPlacement placement,
        boolean requiresWall,
        DungeonItemSize inventorySize,
        DungeonItemVisual visual,
        Map<String, Object> defaultProperties
) {
    public DungeonItemDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item id cannot be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be blank.");
        }
        id = id.trim();
        name = name.trim();
        kind = Objects.requireNonNull(kind, "kind");
        category = Objects.requireNonNull(category, "category");
        placement = Objects.requireNonNull(placement, "placement");
        visual = Objects.requireNonNull(visual, "visual");
        defaultProperties = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(defaultProperties, "defaultProperties")
        ));
        if (kind == DungeonItemKind.INTERACTABLE && inventorySize == null) {
            throw new IllegalArgumentException("Interactable item " + id + " must define inventory size.");
        }
    }

    public boolean isMapBased() {
        return kind == DungeonItemKind.MAP;
    }

    public boolean isInteractable() {
        return kind == DungeonItemKind.INTERACTABLE;
    }

    public boolean isCellBased() {
        return placement == DungeonItemPlacement.CELL;
    }

    public boolean isGridBased() {
        return placement == DungeonItemPlacement.GRID;
    }

    public boolean isDetail() {
        return category == DungeonItemCategory.DETAIL;
    }
}

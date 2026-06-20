package sora.simulation.items;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DungeonInventoryItem(
        String itemId,
        int x,
        int y,
        int quantity,
        Map<String, Object> properties
) {
    public DungeonInventoryItem {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Inventory item id cannot be blank.");
        }
        itemId = itemId.trim();
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Inventory item coordinates cannot be negative.");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Inventory item quantity must be positive.");
        }
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(
                properties == null ? Map.of() : properties
        ));
    }
}

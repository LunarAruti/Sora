package sora.simulation.equipment;

import sora.simulation.items.DungeonItemSize;
import sora.simulation.items.DungeonItemVisual;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DungeonEquipmentDefinition(
        String id,
        String name,
        EquipmentSlot apparelSlot,
        DungeonItemSize inventorySize,
        DungeonItemVisual visual,
        double equipmentWeight,
        Map<String, Object> defaultProperties
) {
    public DungeonEquipmentDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Equipment id cannot be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Equipment name cannot be blank.");
        }
        id = id.trim();
        name = name.trim();
        apparelSlot = Objects.requireNonNull(apparelSlot, "apparelSlot");
        if (!apparelSlot.isBodySlot()) {
            throw new IllegalArgumentException("Equipment " + id + " must target a body apparel slot.");
        }
        inventorySize = Objects.requireNonNull(inventorySize, "inventorySize");
        visual = Objects.requireNonNull(visual, "visual");
        equipmentWeight = Math.max(0.0, equipmentWeight);
        defaultProperties = Collections.unmodifiableMap(new LinkedHashMap<>(
                defaultProperties == null ? Map.of() : defaultProperties
        ));
    }
}

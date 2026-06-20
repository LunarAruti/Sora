package sora.simulation.equipment;

import sora.simulation.items.DungeonItemDefinition;
import sora.simulation.items.DungeonItemSize;
import sora.simulation.items.DungeonItemVisual;

import java.util.Map;
import java.util.Objects;

public record DungeonCarryableDefinition(
        DungeonItemDefinition itemDefinition,
        DungeonEquipmentDefinition equipmentDefinition
) {
    public DungeonCarryableDefinition {
        if ((itemDefinition == null) == (equipmentDefinition == null)) {
            throw new IllegalArgumentException("Carryable definition must wrap exactly one source definition.");
        }
    }

    public static DungeonCarryableDefinition item(DungeonItemDefinition definition) {
        return new DungeonCarryableDefinition(Objects.requireNonNull(definition, "definition"), null);
    }

    public static DungeonCarryableDefinition equipment(DungeonEquipmentDefinition definition) {
        return new DungeonCarryableDefinition(null, Objects.requireNonNull(definition, "definition"));
    }

    public boolean isEquipment() {
        return equipmentDefinition != null;
    }

    public boolean isItem() {
        return itemDefinition != null;
    }

    public String id() {
        return isEquipment() ? equipmentDefinition.id() : itemDefinition.id();
    }

    public String name() {
        return isEquipment() ? equipmentDefinition.name() : itemDefinition.name();
    }

    public DungeonItemSize inventorySize() {
        return isEquipment() ? equipmentDefinition.inventorySize() : itemDefinition.inventorySize();
    }

    public DungeonItemVisual visual() {
        return isEquipment() ? equipmentDefinition.visual() : itemDefinition.visual();
    }

    public Map<String, Object> defaultProperties() {
        return isEquipment() ? equipmentDefinition.defaultProperties() : itemDefinition.defaultProperties();
    }
}

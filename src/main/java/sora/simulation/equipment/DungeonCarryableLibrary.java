package sora.simulation.equipment;

import sora.simulation.items.DungeonItemLibrary;

import java.util.Optional;

public final class DungeonCarryableLibrary {
    private final DungeonEquipmentLibrary equipmentLibrary;

    public DungeonCarryableLibrary(DungeonEquipmentLibrary equipmentLibrary) {
        this.equipmentLibrary = equipmentLibrary == null
                ? DungeonEquipmentLibrary.instance()
                : equipmentLibrary;
    }

    public static DungeonCarryableLibrary instance() {
        return new DungeonCarryableLibrary(DungeonEquipmentLibrary.instance());
    }

    public Optional<DungeonCarryableDefinition> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Optional<DungeonCarryableDefinition> item = DungeonItemLibrary.find(id)
                .map(DungeonCarryableDefinition::item);
        if (item.isPresent()) {
            return item;
        }
        return equipmentLibrary.find(id).map(DungeonCarryableDefinition::equipment);
    }
}

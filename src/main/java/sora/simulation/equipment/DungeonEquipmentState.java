package sora.simulation.equipment;

import sora.simulation.items.DungeonInventoryItem;
import sora.simulation.items.DungeonItemSize;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DungeonEquipmentState {
    public static final int BASE_INVENTORY_WIDTH = 10;
    public static final int BASE_INVENTORY_HEIGHT = 5;
    public static final int MAX_INVENTORY_WIDTH = 10;
    public static final int MAX_INVENTORY_HEIGHT = 15;
    public static final int BASE_SECONDARY_SLOTS = 2;
    public static final int MAX_SECONDARY_SLOTS = 6;

    private final EnumMap<EquipmentSlot, DungeonInventoryItem> equipped = new EnumMap<>(EquipmentSlot.class);

    public Optional<DungeonInventoryItem> get(EquipmentSlot slot) {
        if (slot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(equipped.get(slot));
    }

    public Map<EquipmentSlot, DungeonInventoryItem> equippedItems() {
        return Map.copyOf(equipped);
    }

    public List<DungeonInventoryItem> equippedItemList() {
        return List.copyOf(equipped.values());
    }

    public DungeonInventoryItem set(EquipmentSlot slot, DungeonInventoryItem item) {
        if (slot == null) {
            return item;
        }
        if (item == null) {
            return equipped.remove(slot);
        }
        return equipped.put(slot, item);
    }

    public DungeonInventoryItem remove(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }
        return equipped.remove(slot);
    }

    public void clear() {
        equipped.clear();
    }

    public DungeonItemSize currentInventorySize(DungeonEquipmentLibrary equipmentLibrary) {
        int height = BASE_INVENTORY_HEIGHT;
        DungeonInventoryItem backItem = equipped.get(EquipmentSlot.BACK);
        if (backItem != null && equipmentLibrary != null) {
            height = equipmentLibrary.find(backItem.itemId())
                    .map(definition -> propertyInt(
                            definition.defaultProperties(),
                            "inventory_rows",
                            BASE_INVENTORY_HEIGHT
                    ))
                    .orElse(BASE_INVENTORY_HEIGHT);
        }
        return new DungeonItemSize(
                MAX_INVENTORY_WIDTH,
                clamp(height, BASE_INVENTORY_HEIGHT, MAX_INVENTORY_HEIGHT)
        );
    }

    public int unlockedSecondarySlots(DungeonEquipmentLibrary equipmentLibrary) {
        int slots = BASE_SECONDARY_SLOTS;
        if (equipmentLibrary != null) {
            for (DungeonInventoryItem item : equipped.values()) {
                slots += equipmentLibrary.find(item.itemId())
                        .map(definition -> propertyInt(definition.defaultProperties(), "secondary_slots", 0))
                        .orElse(0);
            }
        }
        return clamp(slots, BASE_SECONDARY_SLOTS, MAX_SECONDARY_SLOTS);
    }

    public boolean primarySwapEnabled(DungeonEquipmentLibrary equipmentLibrary) {
        if (equipmentLibrary == null) {
            return false;
        }
        for (DungeonInventoryItem item : equipped.values()) {
            boolean enabled = equipmentLibrary.find(item.itemId())
                    .map(definition -> propertyBoolean(
                            definition.defaultProperties(),
                            "enables_primary_swap",
                            false
                    ))
                    .orElse(false);
            if (enabled) {
                return true;
            }
        }
        return false;
    }

    public boolean secondaryLightEnabled(DungeonEquipmentLibrary equipmentLibrary) {
        if (equipmentLibrary == null) {
            return false;
        }
        for (DungeonInventoryItem item : equipped.values()) {
            boolean enabled = equipmentLibrary.find(item.itemId())
                    .map(definition -> propertyBoolean(
                            definition.defaultProperties(),
                            "allows_secondary_light",
                            false
                    ))
                    .orElse(false);
            if (enabled) {
                return true;
            }
        }
        return false;
    }

    private static int propertyInt(Map<String, Object> properties, String key, int fallback) {
        Object value = properties.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean propertyBoolean(Map<String, Object> properties, String key, boolean fallback) {
        Object value = properties.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

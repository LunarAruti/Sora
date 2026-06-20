package sora.simulation.items;

import sora.simulation.equipment.DungeonCarryableDefinition;
import sora.simulation.equipment.DungeonCarryableLibrary;
import sora.simulation.equipment.DungeonEquipmentState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DungeonInventory {
    public static final int DEFAULT_WIDTH = DungeonEquipmentState.BASE_INVENTORY_WIDTH;
    public static final int DEFAULT_HEIGHT = DungeonEquipmentState.BASE_INVENTORY_HEIGHT;

    private int width;
    private int height;
    private final List<DungeonInventoryItem> items = new ArrayList<>();

    public DungeonInventory() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public DungeonInventory(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Inventory dimensions must be positive.");
        }
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean resize(int width, int height) {
        if (width < 1 || height < 1) {
            return false;
        }
        for (DungeonInventoryItem item : items) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            DungeonItemSize size = definition == null ? null : definition.inventorySize();
            if (size == null || item.x() + size.width() > width || item.y() + size.height() > height) {
                return false;
            }
        }
        this.width = width;
        this.height = height;
        return true;
    }

    public List<DungeonInventoryItem> getItems() {
        return List.copyOf(items);
    }

    public boolean add(String itemId, int x, int y, int quantity) {
        return add(itemId, x, y, quantity, Map.of());
    }

    public boolean add(String itemId, int x, int y, int quantity, Map<String, Object> properties) {
        DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(itemId).orElse(null);
        if (!canStore(definition)) {
            return false;
        }
        if (quantity > stackLimit(definition)) {
            return false;
        }
        DungeonInventoryItem item = new DungeonInventoryItem(itemId, x, y, quantity, properties);
        if (!canPlace(definition, item)) {
            return false;
        }
        items.add(item);
        return true;
    }

    public boolean addNextAvailable(String itemId, int quantity) {
        return addNextAvailable(itemId, quantity, Map.of());
    }

    public boolean addNextAvailable(String itemId, int quantity, Map<String, Object> properties) {
        DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(itemId).orElse(null);
        if (!canStore(definition) || quantity < 1) {
            return false;
        }

        List<DungeonInventoryItem> proposed = new ArrayList<>(items);
        int remaining = quantity;
        int stackLimit = stackLimit(definition);
        if (stackLimit > 1) {
            remaining = fillExistingStacks(proposed, itemId, remaining, stackLimit, properties);
            if (remaining == 0) {
                replaceItems(proposed);
                return true;
            }
        }

        while (remaining > 0) {
            int stackQuantity = Math.min(remaining, stackLimit);
            DungeonInventoryItem placed = firstAvailablePlacement(proposed, definition, itemId, stackQuantity, properties);
            if (placed == null) {
                return false;
            }
            proposed.add(placed);
            remaining -= stackQuantity;
        }
        replaceItems(proposed);
        return true;
    }

    private boolean canStore(DungeonCarryableDefinition definition) {
        if (definition == null || definition.inventorySize() == null) {
            return false;
        }
        if (definition.isEquipment() || definition.itemDefinition().isInteractable()) {
            return true;
        }
        Object pickupable = definition.itemDefinition().defaultProperties().get("pickupable");
        return pickupable instanceof Boolean value && value;
    }

    private int stackLimit(DungeonCarryableDefinition definition) {
        if (definition == null || definition.isEquipment()) {
            return 1;
        }
        Object value = definition.defaultProperties().get("can_stack");
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 1;
    }

    private int fillExistingStacks(
            List<DungeonInventoryItem> proposed,
            String itemId,
            int quantity,
            int stackLimit,
            Map<String, Object> properties
    ) {
        Map<String, Object> normalizedProperties = properties == null ? Map.of() : properties;
        int remaining = quantity;
        for (int i = 0; i < proposed.size() && remaining > 0; i++) {
            DungeonInventoryItem existing = proposed.get(i);
            if (!existing.itemId().equals(itemId) ||
                    existing.quantity() >= stackLimit ||
                    !existing.properties().equals(normalizedProperties)) {
                continue;
            }
            int added = Math.min(remaining, stackLimit - existing.quantity());
            proposed.set(i, new DungeonInventoryItem(
                    existing.itemId(),
                    existing.x(),
                    existing.y(),
                    existing.quantity() + added,
                    existing.properties()
            ));
            remaining -= added;
        }
        return remaining;
    }

    private DungeonInventoryItem firstAvailablePlacement(
            List<DungeonInventoryItem> proposed,
            DungeonCarryableDefinition definition,
            String itemId,
            int quantity,
            Map<String, Object> properties
    ) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                DungeonInventoryItem candidate = new DungeonInventoryItem(itemId, x, y, quantity, properties);
                if (canPlaceIn(proposed, definition, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private void replaceItems(List<DungeonInventoryItem> replacement) {
        items.clear();
        items.addAll(replacement);
    }

    public boolean replaceAll(List<DungeonInventoryItem> replacement) {
        List<DungeonInventoryItem> validated = new ArrayList<>();
        for (DungeonInventoryItem item : replacement == null ? List.<DungeonInventoryItem>of() : replacement) {
            DungeonCarryableDefinition definition = DungeonCarryableLibrary.instance().find(item.itemId()).orElse(null);
            if (!canStore(definition) || !canPlaceIn(validated, definition, item)) {
                return false;
            }
            validated.add(item);
        }
        replaceItems(validated);
        return true;
    }

    public void clear() {
        items.clear();
    }

    public boolean canPlace(DungeonCarryableDefinition definition, DungeonInventoryItem item) {
        return canPlaceIn(items, definition, item);
    }

    private boolean canPlaceIn(
            List<DungeonInventoryItem> existingItems,
            DungeonCarryableDefinition definition,
            DungeonInventoryItem item
    ) {
        DungeonItemSize size = definition.inventorySize();
        if (size == null) {
            return false;
        }
        if (item.x() + size.width() > width || item.y() + size.height() > height) {
            return false;
        }
        for (DungeonInventoryItem existing : existingItems) {
            DungeonCarryableDefinition existingDefinition = DungeonCarryableLibrary.instance().find(existing.itemId()).orElse(null);
            if (existingDefinition != null &&
                    existingDefinition.inventorySize() != null &&
                    overlaps(item, size, existing, existingDefinition.inventorySize())) {
                return false;
            }
        }
        return true;
    }

    private boolean overlaps(
            DungeonInventoryItem a,
            DungeonItemSize aSize,
            DungeonInventoryItem b,
            DungeonItemSize bSize
    ) {
        return a.x() < b.x() + bSize.width() &&
                a.x() + aSize.width() > b.x() &&
                a.y() < b.y() + bSize.height() &&
                a.y() + aSize.height() > b.y();
    }
}

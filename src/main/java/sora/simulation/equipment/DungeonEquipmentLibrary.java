package sora.simulation.equipment;

import sora.simulation.items.DungeonItemSize;
import sora.simulation.items.DungeonItemVisual;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DungeonEquipmentLibrary {
    public static final String EQUIPMENT_TEMPLATE_ID = "equipment_template";

    private static final DungeonEquipmentLibrary INSTANCE = new DungeonEquipmentLibrary(List.of(
            equipment("satchel", "Satchel", EquipmentSlot.BACK, size(2, 2),
                    visual("satchel", "#8b5f35", "#2d1d10", "#c6985c", "S",
                            "Compact shoulderless carry bag."),
                    8.0,
                    props("inventory_rows", 8)),
            equipment("backpack", "Backpack", EquipmentSlot.BACK, size(2, 3),
                    visual("backpack", "#5f6f45", "#1e2817", "#a6bb78", "B",
                            "Standard dungeon pack with broad storage."),
                    4.0,
                    props("inventory_rows", 12)),
            equipment("heavy_backpack", "Heavy Backpack", EquipmentSlot.BACK, size(3, 3),
                    visual("heavy pack", "#4d5543", "#181d15", "#8f9f7c", "H",
                            "Large reinforced pack with maximum storage."),
                    2.0,
                    props("inventory_rows", 15, "movement_noise", 12)),

            equipment("simple_belt", "Simple Belt", EquipmentSlot.WAIST, size(2, 1),
                    visual("belt", "#6e4727", "#26160b", "#b8854a", "B",
                            "Plain belt with a small clip."),
                    8.0,
                    props("secondary_slots", 1, "secondary_allowance", EquipmentAllowance.SMALL_CONSUMABLE.name().toLowerCase())),
            equipment("food_belt", "Food Belt", EquipmentSlot.WAIST, size(2, 1),
                    visual("belt", "#7b5f2e", "#291f0e", "#d7b45b", "F",
                            "Belt loops sized for simple food packets."),
                    6.0,
                    props("secondary_slots", 2, "secondary_allowance", EquipmentAllowance.FOOD.name().toLowerCase())),
            equipment("medical_belt", "Medical Belt", EquipmentSlot.WAIST, size(2, 1),
                    visual("belt", "#795f55", "#251a18", "#f0d5ca", "+",
                            "Belt with small medical loops."),
                    5.0,
                    props("secondary_slots", 2, "secondary_allowance", EquipmentAllowance.MEDICAL.name().toLowerCase())),
            equipment("utility_belt", "Utility Belt", EquipmentSlot.WAIST, size(2, 1),
                    visual("belt", "#4f4637", "#17130f", "#d2ba7a", "U",
                            "Sturdy belt with broad utility clips."),
                    2.5,
                    props("secondary_slots", 2, "secondary_allowance", EquipmentAllowance.BROAD_SMALL.name().toLowerCase())),

            equipment("small_leg_pouch", "Small Leg Pouch", EquipmentSlot.LEG, size(1, 1),
                    visual("pouch", "#6b5a3c", "#211b12", "#b59b67", "P",
                            "Tiny leg pouch for small items."),
                    7.0,
                    props("secondary_slots", 2, "secondary_allowance", EquipmentAllowance.TINY.name().toLowerCase())),
            equipment("food_pouch", "Food Pouch", EquipmentSlot.LEG, size(1, 1),
                    visual("pouch", "#7e6739", "#2b2110", "#dfbf6e", "F",
                            "Small pouch dedicated to food."),
                    6.0,
                    props("secondary_slots", 2, "secondary_allowance", EquipmentAllowance.FOOD.name().toLowerCase())),
            equipment("medical_pouch", "Medical Pouch", EquipmentSlot.LEG, size(1, 1),
                    visual("pouch", "#765b55", "#251a18", "#f0d5ca", "+",
                            "Small pouch dedicated to medical supplies."),
                    5.0,
                    props("secondary_slots", 2, "secondary_allowance", EquipmentAllowance.MEDICAL.name().toLowerCase())),
            equipment("oil_pouch", "Oil Pouch", EquipmentSlot.LEG, size(1, 1),
                    visual("pouch", "#3d372d", "#12100d", "#d2b35a", "O",
                            "Small dark pouch for lantern oil."),
                    5.0,
                    props("secondary_slots", 2, "secondary_allowance", EquipmentAllowance.OIL.name().toLowerCase())),
            equipment("leg_pouch", "Leg Pouch", EquipmentSlot.LEG, size(1, 2),
                    visual("pouch", "#5e553f", "#1c1811", "#b9a66e", "L",
                            "Leg pouch with a quick-swap strap."),
                    3.5,
                    props("secondary_slots", 2,
                            "secondary_allowance", EquipmentAllowance.SMALL_NON_LARGE.name().toLowerCase(),
                            "enables_primary_swap", true)),
            equipment("holster", "Large Holster", EquipmentSlot.LEG, size(1, 2),
                    visual("holster", "#4c3a24", "#16100a", "#d0a75c", "H",
                            "Large holster that can quick-swap a held item."),
                    1.8,
                    props("secondary_slots", 2,
                            "secondary_allowance", EquipmentAllowance.ANY.name().toLowerCase(),
                            "enables_primary_swap", true,
                            "allows_secondary_light", true)),

            equipment("light_vest", "Light Vest", EquipmentSlot.CHEST, size(2, 2),
                    visual("vest", "#565b50", "#1c1f19", "#929b88", "V",
                            "Light protective vest."),
                    4.0,
                    props("defense", 2.0)),
            equipment("reinforced_vest", "Reinforced Vest", EquipmentSlot.CHEST, size(2, 2),
                    visual("vest", "#3f4545", "#141818", "#8a9696", "R",
                            "Heavier protective vest with reinforced plates."),
                    1.8,
                    props("defense", 5.0, "movement_noise", 8)),

            equipment("cloth_mask", "Cloth Mask", EquipmentSlot.FACE, size(1, 1),
                    visual("mask", "#918b7e", "#2d2922", "#d6cfbd", "M",
                            "Simple cloth mask."),
                    6.0,
                    props("gas_protection", 0.15, "breathing_noise", 2)),
            equipment("protective_mask", "Protective Mask", EquipmentSlot.FACE, size(1, 1),
                    visual("mask", "#606b67", "#1d2422", "#a9bbb5", "P",
                            "Protective mask with basic sealing."),
                    3.0,
                    props("gas_protection", 0.45, "breathing_noise", 4)),
            equipment("filter_mask", "Filter Mask", EquipmentSlot.FACE, size(1, 1),
                    visual("mask", "#38423f", "#101412", "#92aaa1", "F",
                            "Filtered mask for stronger gas protection."),
                    1.5,
                    props("gas_protection", 0.75, "breathing_noise", 6)),

            equipment("worn_shoes", "Worn Shoes", EquipmentSlot.FEET, size(2, 1),
                    visual("shoes", "#62513e", "#201912", "#9b8060", "S",
                            "Old shoes with limited protection."),
                    7.0,
                    props("terrain_slow_reduction", 5)),
            equipment("work_boots", "Work Boots", EquipmentSlot.FEET, size(2, 1),
                    visual("boots", "#4a3423", "#160e09", "#9e7650", "W",
                            "Heavy boots for rough dungeon floors."),
                    3.5,
                    props("terrain_slow_reduction", 20, "movement_noise", 10)),
            equipment("quiet_shoes", "Quiet Shoes", EquipmentSlot.FEET, size(2, 1),
                    visual("shoes", "#2f3035", "#0c0d10", "#747985", "Q",
                            "Soft shoes made for quieter movement."),
                    2.5,
                    props("terrain_slow_reduction", 10, "movement_noise", -8)),

            equipment("fingerless_gloves", "Fingerless Gloves", EquipmentSlot.HANDS, size(1, 1),
                    visual("gloves", "#5f5145", "#1e1915", "#b49b84", "F",
                            "Fingerless gloves for quick searching."),
                    7.0,
                    props("search_bonus", 5)),
            equipment("cloth_gloves", "Cloth Gloves", EquipmentSlot.HANDS, size(1, 1),
                    visual("gloves", "#8d8372", "#2d281f", "#d0c4ae", "G",
                            "Simple cloth gloves."),
                    6.0,
                    props("search_bonus", 10, "interaction_safety", 0.15)),
            equipment("work_gloves", "Work Gloves", EquipmentSlot.HANDS, size(1, 1),
                    visual("gloves", "#705234", "#24170d", "#c0935d", "W",
                            "Sturdy gloves for searching and prying."),
                    3.5,
                    props("search_bonus", 15, "interaction_safety", 0.35, "pry_time_bonus", 0.20)),

            equipment("key_ring", "Key Ring", EquipmentSlot.ACCESSORY, size(1, 1),
                    visual("ring", "#c6a03a", "#3c2d0a", "#ffe08a", "K",
                            "Small ring that can hold many keys."),
                    4.0,
                    props("key_capacity", 24, "secondary_allowance", EquipmentAllowance.KEYS_ONLY.name().toLowerCase())),
            equipment("charm", "Warding Charm", EquipmentSlot.ACCESSORY, size(1, 1),
                    visual("charm", "#7756a8", "#241636", "#d7b6ff", "C",
                            "Small charm reserved for sanity-related behavior."),
                    2.0,
                    props("sanity_support", 0.10)),
            equipment("watch", "Pocket Watch", EquipmentSlot.ACCESSORY, size(1, 1),
                    visual("watch", "#b49a52", "#31270f", "#f3dc8a", "T",
                            "Small watch reserved for timing UI."),
                    2.0,
                    props("time_display", true)),
            equipment("compass", "Wrist Compass", EquipmentSlot.ACCESSORY, size(1, 1),
                    visual("compass", "#49606f", "#172027", "#a6d2e8", "N",
                            "Small compass reserved for navigation UI."),
                    2.0,
                    props("navigation_display", true))
    ));

    private final List<DungeonEquipmentDefinition> definitions;
    private final Map<String, DungeonEquipmentDefinition> definitionsById;
    private final double totalEquipmentWeight;

    public DungeonEquipmentLibrary(List<DungeonEquipmentDefinition> definitions) {
        this.definitions = List.copyOf(definitions == null ? List.of() : definitions);
        this.definitionsById = indexById(this.definitions);
        this.totalEquipmentWeight = totalWeight(this.definitions);
    }

    public static DungeonEquipmentLibrary instance() {
        return INSTANCE;
    }

    public List<DungeonEquipmentDefinition> definitions() {
        return definitions;
    }

    public Optional<DungeonEquipmentDefinition> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitionsById.get(id.trim()));
    }

    public boolean contains(String id) {
        return find(id).isPresent();
    }

    public double totalEquipmentWeight() {
        return totalEquipmentWeight;
    }

    public static DungeonEquipmentDefinition equipment(
            String id,
            String name,
            EquipmentSlot apparelSlot,
            DungeonItemSize inventorySize,
            DungeonItemVisual visual,
            double equipmentWeight,
            Map<String, Object> properties
    ) {
        return new DungeonEquipmentDefinition(
                id,
                name,
                apparelSlot,
                inventorySize,
                visual,
                equipmentWeight,
                withSharedProperties(id, name, apparelSlot, equipmentWeight, properties)
        );
    }

    public static DungeonItemSize size(int width, int height) {
        return new DungeonItemSize(width, height);
    }

    public static DungeonItemVisual visual(
            String shape,
            String fill,
            String outline,
            String accent,
            String glyph,
            String description
    ) {
        return new DungeonItemVisual(shape, fill, outline, accent, glyph, description);
    }

    public static Map<String, Object> props(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Property pairs must be key/value pairs.");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object key = pairs[i];
            if (!(key instanceof String stringKey) || stringKey.isBlank()) {
                throw new IllegalArgumentException("Property key must be nonblank text.");
            }
            properties.put(stringKey.trim(), pairs[i + 1]);
        }
        return Collections.unmodifiableMap(properties);
    }

    private static Map<String, Object> withSharedProperties(
            String id,
            String name,
            EquipmentSlot apparelSlot,
            double equipmentWeight,
            Map<String, Object> properties
    ) {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("id", id);
        shared.put("name", name);
        shared.put("kind", "equipment");
        shared.put("equipment", true);
        shared.put("interactable", true);
        shared.put("apparel_slot", apparelSlot.name().toLowerCase());
        shared.put("equipment_weight", Math.max(0.0, equipmentWeight));
        shared.putAll(properties == null ? Map.of() : properties);
        return Collections.unmodifiableMap(shared);
    }

    private static Map<String, DungeonEquipmentDefinition> indexById(
            List<DungeonEquipmentDefinition> definitions
    ) {
        Map<String, DungeonEquipmentDefinition> byId = new LinkedHashMap<>();
        for (DungeonEquipmentDefinition definition : definitions) {
            DungeonEquipmentDefinition previous = byId.put(definition.id(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate dungeon equipment id: " + definition.id());
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    private static double totalWeight(List<DungeonEquipmentDefinition> definitions) {
        double total = 0.0;
        for (DungeonEquipmentDefinition definition : definitions) {
            total += Math.max(0.0, definition.equipmentWeight());
        }
        return total;
    }
}

package sora.simulation.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DungeonItemLibrary {
    public static final String WALL_LIGHT = "wall_light";

    private static final List<DungeonItemDefinition> DEFINITIONS = List.of(
            map("wall_light", "Wall-Mounted Lamp", DungeonItemCategory.LIGHT,
                    visual("edge box", "#ffd34d", "#7a5a13", "#fff2a6", "L",
                            "Small yellow box mounted against the wall-side edge of its cell."),
                    props("is_on", true, "light_radius", 15.0, "burn_time", 1000.0,
                            "fuel_remaining", 1000.0, "spawn_on_chance", 0.78,
                            "effect_id", "lit", "flicker", false, "interactable", true)),
            map("floor_lantern", "Portable Floor Lantern", DungeonItemCategory.LIGHT,
                    visual("cell box", "#f0c45a", "#6f521e", "#fff2b8", "F",
                            "Small squat yellow lantern centered on the cell."),
                    props("is_on", false, "light_radius", 16.0, "burn_time", 1000.0,
                            "fuel_remaining", 1000.0, "spawn_on_chance", 0.0,
                            "effect_id", "lit", "pickupable", true,
                            "inventory_size", size(2, 2), "interactable", true)),
            map("broken_light", "Flickering Wall Lamp", DungeonItemCategory.LIGHT,
                    visual("cracked edge box", "#9f8a4e", "#483f30", "#ffd86b", "B",
                            "Dim yellow-gray wall box with a cracked accent."),
                    props("is_on", true, "light_radius", 13.5, "burn_time", 1000.0,
                            "fuel_remaining", 1000.0, "spawn_on_chance", 0.50,
                            "effect_id", "", "flicker", true,
                            "flicker_min", 0.22, "flicker_speed", 7.5, "interactable", false)),
            map("lever", "Control Lever", DungeonItemCategory.TRIGGER,
                    visual("thin post", "#6f6a5b", "#2f2c25", "#b9b05f", "/",
                            "Thin metal base with a bright angled handle."),
                    props("is_on", false, "target_ids", List.of(), "one_time_use", false, "interactable", true)),
            map("pressure_plate", "Pressure Plate", DungeonItemCategory.TRIGGER,
                    visual("flat plate", "#777d82", "#303438", "#aab2b8", "P",
                            "Flat gray plate inset into the cell floor."),
                    props("is_pressed", false, "target_ids", List.of(),
                            "one_time_use", false, "toggle", false, "interactable", true)),
            map("gas_vent", "Toxic Gas Vent", DungeonItemCategory.HAZARD,
                    visual("grate", "#5c6463", "#232827", "#98bbb1", "G",
                            "Dark metal grate with pale green vent slots."),
                    props("is_active", true, "effect_id", "toxic_exposure", "radius", 4.0, "interactable", true)),
            map("steam_vent", "Steam Vent", DungeonItemCategory.HAZARD,
                    visual("grate", "#6f7478", "#25282b", "#d9eef2", "V",
                            "Dark metal grate with pale steam slots."),
                    props("is_active", true, "effect_id", "asphyxiation", "radius", 4.0, "interactable", false)),
            map("save_table", "Record Table", DungeonItemCategory.SAVE,
                    visual("book table", "#7b5639", "#2f1f15", "#e8d08a", "S",
                            "Small brown table with a bright book square."),
                    props("can_save", true, "interactable", true)),
            map("water_puddle", "Water Pool", DungeonItemCategory.HAZARD,
                    visual("puddle", "#285f8f", "#17364f", "#79b7e8", "W",
                            "Irregular blue floor patch."),
                    props("slow_multiplier", 0.9)),
            map("oil_puddle", "Oil Slick", DungeonItemCategory.HAZARD,
                    visual("puddle", "#24201a", "#0f0d0b", "#6d6040", "O",
                            "Irregular dark slick with a dull brown highlight."),
                    props("slow_multiplier", 0.8, "flammable", true, "burn_duration", 8.0)),
            map("chest", "Storage Chest", DungeonItemCategory.CONTAINER,
                    visual("block chest", "#8a5a2b", "#2d1b0f", "#d5a146", "C",
                            "Brown block with a golden latch."),
                    props("locked", false, "required_key_id", "", "can_be_lockpicked", true,
                            "can_be_pried_open", true, "capacity", size(10, 5), "contents", List.of(),
                            "directional", true, "prefers_wall", true, "direction_source", "wall_or_manual",
                            "random_requires_flat_wall", true, "interactable", true)),
            map("barrel", "Storage Barrel", DungeonItemCategory.CONTAINER,
                    visual("round barrel", "#79512b", "#2b1a0d", "#b68247", "B",
                            "Round brown barrel with two band lines."),
                    props("can_be_pried_open", true, "capacity", size(10, 10),
                            "contents", List.of(), "collides", true, "interactable", true)),
            map("crate", "Supply Crate", DungeonItemCategory.CONTAINER,
                    visual("slatted crate", "#8b6a3e", "#302312", "#c0995d", "R",
                            "Square wood crate with diagonal slat marks."),
                    props("can_be_pried_open", true, "capacity", size(10, 15),
                            "contents", List.of(), "collides", true, "interactable", true)),
            map("empty_crate", "Empty Supply Crate", DungeonItemCategory.CONTAINER,
                    visual("hollow crate", "#6f5635", "#2b2114", "#a98554", "E",
                            "Darker crate outline showing it has no contents."),
                    props("can_be_pried_open", true, "collides", true, "interactable", true)),
            map("table", "Utility Table", DungeonItemCategory.CONTAINER,
                    visual("table", "#735038", "#2a1b12", "#b4875a", "T",
                            "Flat brown tabletop with small leg pixels."),
                    props("capacity", size(5, 5), "contents", List.of(), "collides", true, "interactable", true)),
            map("empty_table", "Empty Utility Table", DungeonItemCategory.CONTAINER,
                    visual("empty table", "#60442f", "#23170f", "#96704d", "T",
                            "Plain brown tabletop with no stored contents."),
                    props("collides", true, "interactable", true)),
            map("bookshelf", "Archive Shelf", DungeonItemCategory.CONTAINER,
                    visual("shelf", "#6e4b2e", "#26180e", "#c29c62", "H",
                            "Tall brown block with colored book stripes."),
                    props("capacity", size(3, 4), "contents", List.of(),
                            "can_be_searched", true, "directional", true, "prefers_wall", true,
                            "direction_source", "wall_or_manual", "random_requires_flat_wall", true,
                            "collides", true, "interactable", true)),
            map("empty_bookshelf", "Empty Archive Shelf", DungeonItemCategory.CONTAINER,
                    visual("empty shelf", "#55402c", "#20150d", "#8a6b4a", "H",
                            "Tall empty brown shelf block."),
                    props("directional", true, "prefers_wall", true, "direction_source", "wall_or_manual",
                            "random_requires_flat_wall", true, "collides", true, "interactable", true)),
            map("debris", "Loose Debris", DungeonItemCategory.DETAIL,
                    visual("scattered bits", "#5e5d58", "#242421", "#8d8a81", "D",
                            "Loose gray-brown chunks scattered across the cell."),
                    props("slow_multiplier", 0.92, "collides", false)),
            map("pallet", "Wooden Pallet", DungeonItemCategory.DETAIL,
                    visual("wood slats", "#8a6b3f", "#2d2112", "#b99156", "=",
                            "Parallel tan wood slats."),
                    props("flammable", true, "slow_multiplier", 0.9, "collides", false, "interactable", true)),
            map("wood_sticks", "Broken Timber", DungeonItemCategory.DETAIL,
                    visual("sticks", "#9a6b35", "#2f1d0c", "#d39a55", "X",
                            "Crossed thin brown sticks."),
                    props("flammable", true)),
            map("bloodstain", "Blood Stain", DungeonItemCategory.DETAIL,
                    visual("stain", "#6b1010", "#250404", "#9d2323", "",
                            "Dark red irregular floor stain."),
                    props("clue_type", "")),
            map("scratch_marks", "Claw Marks", DungeonItemCategory.DETAIL,
                    visual("slashes", "#b8b0a0", "#332f28", "#eee3c8", "///",
                            "Three pale scratch slashes on the floor or wall."),
                    props("clue_type", "")),
            map("bones", "Bone Remains", DungeonItemCategory.DETAIL,
                    visual("bone pile", "#c9c2a5", "#4a4435", "#f1e9cb", "N",
                            "Small pale bone pile."),
                    props("capacity", size(5, 5), "contents", List.of(),
                            "slow_multiplier", 0.7, "interactable", true)),
            map("rubble", "Collapsed Rubble", DungeonItemCategory.DETAIL,
                    visual("rock pile", "#68655e", "#272522", "#9a9589", "U",
                            "Dense gray rock pile."),
                    props("capacity", size(5, 5), "contents", List.of(),
                            "can_be_pried_open", true, "can_be_bombed", true,
                            "slow_multiplier", 0.7, "collides", true, "interactable", true)),
            map("hanging_chains", "Hanging Chains", DungeonItemCategory.DETAIL,
                    visual("chains", "#70777a", "#25292b", "#c2c9cb", "|",
                            "Thin vertical metal chain links."),
                    props("noise_on_touch", true, "target_ids", List.of(), "interactable", true)),
            map("broken_barrel", "Broken Barrel", DungeonItemCategory.DETAIL,
                    visual("broken barrel", "#6c4a2a", "#21150b", "#a77a4a", "b",
                            "Splintered barrel remains with bent dark bands."),
                    props("breaks_into", "wood_sticks", "collides", true, "interactable", true)),
            map("broken_crate", "Broken Crate", DungeonItemCategory.DETAIL,
                    visual("broken crate", "#775b35", "#24190d", "#b78d56", "r",
                            "Collapsed crate boards and slats."),
                    props("breaks_into", "wood_sticks", "collides", true, "interactable", true)),
            map("broken_shelf", "Broken Shelf", DungeonItemCategory.DETAIL,
                    visual("broken shelf", "#5f4229", "#21140b", "#987046", "s",
                            "Broken wall shelf with missing planks."),
                    props("directional", true, "prefers_wall", true, "direction_source", "wall_or_manual",
                            "random_requires_flat_wall", true, "breaks_into", "wood_sticks",
                            "collides", true, "interactable", true)),
            map("broken_table", "Broken Table", DungeonItemCategory.DETAIL,
                    visual("broken table", "#6a4a32", "#24170d", "#a78057", "t",
                            "Cracked tabletop with snapped legs."),
                    props("breaks_into", "wood_sticks", "collides", true, "interactable", true)),
            map("cracked_pot", "Cracked Pot", DungeonItemCategory.DETAIL,
                    visual("cracked pot", "#8d6b45", "#332312", "#d0a46b", "P",
                            "Small cracked clay pot."),
                    props("can_be_broken", true, "break_tool_ids", List.of("crowbar"),
                            "drop_chance", 0.35, "drop_item_id", "rock",
                            "collides", true, "interactable", true)),
            map("flower_pot", "Flower Pot", DungeonItemCategory.DETAIL,
                    visual("flower pot", "#8a583a", "#2d1a10", "#d89b62", "F",
                            "Small clay pot with faded plant stems."),
                    props("can_be_broken", true, "break_tool_ids", List.of("crowbar"),
                            "drop_chance", 0.25, "drop_item_id", "rock",
                            "collides", true, "interactable", true)),
            map("wall_painting", "Faded Wall Painting", DungeonItemCategory.DETAIL,
                    visual("wall painting", "#635348", "#211a16", "#b49c7d", "W",
                            "Faded framed painting mounted to a wall."),
                    props("directional", true, "prefers_wall", true, "direction_source", "wall_or_manual",
                            "random_requires_flat_wall", true, "inspect_text", "", "interactable", true)),
            map("torn_banner", "Torn Banner", DungeonItemCategory.DETAIL,
                    visual("torn banner", "#6f2631", "#260b10", "#b85a64", "B",
                            "Tattered wall banner with faded cloth."),
                    props("directional", true, "prefers_wall", true, "direction_source", "wall_or_manual",
                            "random_requires_flat_wall", true, "inspect_text", "", "interactable", true)),
            map("broken_statue", "Broken Statue", DungeonItemCategory.DETAIL,
                    visual("broken statue", "#76726b", "#282522", "#aaa49a", "S",
                            "Cracked stone statue base and torso."),
                    props("can_be_bombed", true, "drop_chance", 0.45, "drop_item_id", "rock",
                            "collides", true, "interactable", true)),
            map("loose_bricks", "Loose Bricks", DungeonItemCategory.DETAIL,
                    visual("loose bricks", "#795044", "#251410", "#b56f5d", "L",
                            "Small cluster of loose brick pieces."),
                    props("directional", true, "prefers_wall", true, "direction_source", "wall_or_manual",
                            "random_requires_flat_wall", true)),
            map("ash_pile", "Ash Pile", DungeonItemCategory.DETAIL,
                    visual("ash pile", "#5d5b55", "#1d1c19", "#918e84", "A",
                            "Soft gray ash scattered across the floor."),
                    props()),
            map("discarded_cloth", "Discarded Cloth", DungeonItemCategory.DETAIL,
                    visual("cloth", "#6d6254", "#241f19", "#a99b86", "C",
                            "Dirty folded cloth scraps."),
                    props()),
            map("torn_bag", "Torn Bag", DungeonItemCategory.DETAIL,
                    visual("torn bag", "#70563b", "#261b10", "#a8885d", "G",
                            "Ripped empty cloth bag."),
                    props()),
            map("smashed_lantern", "Smashed Lantern", DungeonItemCategory.DETAIL,
                    visual("smashed lantern", "#51473a", "#17130f", "#d0a548", "L",
                            "Broken lantern frame with dull glass pieces."),
                    props("has_item", true, "drop_item_id", "lantern_oil", "drop_chance", 0.45,
                            "interactable", true)),
            map("old_toolbox", "Old Toolbox", DungeonItemCategory.DETAIL,
                    visual("toolbox", "#6a3831", "#24100c", "#b05e50", "T",
                            "Rusty little toolbox with a dark latch."),
                    props("has_item", true, "drop_pool", weightedDrops(
                                    drop("lockpick", 3.0),
                                    drop("knife", 1.0)),
                            "drop_chance", 0.5,
                            "interactable", true)),
            map("broken_chair", "Broken Chair", DungeonItemCategory.DETAIL,
                    visual("broken chair", "#735333", "#26180d", "#ad8251", "h",
                            "Collapsed wooden chair frame."),
                    props("breaks_into", "wood_sticks", "collides", true, "interactable", true)),
            map("loose_papers", "Loose Papers", DungeonItemCategory.DETAIL,
                    visual("papers", "#d9cfaa", "#574d30", "#fff4c8", "p",
                            "Scattered loose papers."),
                    props()),
            map("rusted_chain_pile", "Rusted Chain Pile", DungeonItemCategory.DETAIL,
                    visual("chain pile", "#6e7070", "#242525", "#a9adad", "R",
                            "Heap of rusted chain links."),
                    props()),
            map("scorch_mark", "Scorch Mark", DungeonItemCategory.DETAIL,
                    visual("scorch", "#28231e", "#0c0a08", "#5a4d3f", "",
                            "Dark burn mark across the floor."),
                    props()),
            map("gold_mark", "Gold Mark", DungeonItemCategory.DETAIL,
                    visual("gold mark", "#806a28", "#2f2408", "#d8bc51", "*",
                            "Small faded gold marking."),
                    props()),
            map("moss_patch", "Moss Patch", DungeonItemCategory.DETAIL,
                    visual("moss", "#355f36", "#142515", "#6ca66a", "M",
                            "Low green moss patch."),
                    props()),
            map("fungus_patch", "Fungus Patch", DungeonItemCategory.DETAIL,
                    visual("fungus", "#66406f", "#211126", "#a775b7", "f",
                            "Cluster of dull purple fungus caps."),
                    props()),

            interactable("apple", "Fresh Apple", DungeonItemCategory.FOOD, size(1, 1),
                    visual("round item", "#b83232", "#4d1111", "#64a447", "A",
                            "Small red block with a green stem pixel."),
                    props("can_stack", 5, "hunger_amount", 8.0, "heal_amount", 4, "loot_weight", 24.0)),
            interactable("bread", "Bread Loaf", DungeonItemCategory.FOOD, size(1, 1),
                    visual("loaf", "#c58b43", "#513117", "#f0c06f", "B",
                            "Tan loaf block with a lighter top stripe."),
                    props("can_stack", 4, "hunger_amount", 14.0, "heal_amount", 6, "loot_weight", 22.0)),
            interactable("moldy_bread", "Spoiled Bread", DungeonItemCategory.FOOD, size(1, 1),
                    visual("loaf", "#8b8f56", "#33351b", "#b7c779", "M",
                            "Green-tinted bread block."),
                    props("can_stack", 4, "hunger_amount", 7.0, "heal_amount", 3, "effect_id", "sick", "loot_weight", 9.0)),
            interactable("cheese", "Cheese Wedge", DungeonItemCategory.FOOD, size(1, 1),
                    visual("wedge", "#e3c54a", "#59490f", "#fff08a", "C",
                            "Yellow wedge-like block with dark holes."),
                    props("can_stack", 4, "hunger_amount", 10.0, "heal_amount", 5, "loot_weight", 18.0)),
            interactable("spoiled_cheese", "Spoiled Cheese", DungeonItemCategory.FOOD, size(1, 1),
                    visual("wedge", "#8f9a4e", "#30351b", "#c4d46b", "S",
                            "Green-yellow cheese wedge with dark spots."),
                    props("can_stack", 4, "hunger_amount", 5.0, "heal_amount", 2, "effect_id", "sick", "loot_weight", 8.0)),
            interactable("dried_rations", "Dried Rations", DungeonItemCategory.FOOD, size(1, 1),
                    visual("packet", "#8a6a45", "#302317", "#cab08a", "R",
                            "Small tied brown ration packet."),
                    props("can_stack", 6, "hunger_amount", 18.0, "heal_amount", 8, "loot_weight", 20.0)),
            interactable("cooked_meat", "Cooked Meat", DungeonItemCategory.FOOD, size(1, 2),
                    visual("meat", "#9b3f2f", "#3a140e", "#d9825f", "M",
                            "Two-cell red-brown meat strip."),
                    props("can_stack", 2, "hunger_amount", 28.0, "heal_amount", 12, "loot_weight", 10.0)),
            interactable("spoiled_meat", "Spoiled Meat", DungeonItemCategory.FOOD, size(1, 2),
                    visual("meat", "#6f6b38", "#25230e", "#9fa35a", "X",
                            "Sickly green-brown meat strip."),
                    props("can_stack", 2, "hunger_amount", 9.0, "heal_amount", 3, "effect_id", "sick", "loot_weight", 5.0)),
            interactable("cave_roach", "Cave Roach", DungeonItemCategory.FOOD, size(1, 1),
                    visual("bug", "#3b2518", "#120907", "#8f5b36", "R",
                            "Small brown cave insect."),
                    props("can_stack", 6, "hunger_amount", 3.0, "heal_amount", 1, "effect_id", "sick", "loot_weight", 6.0)),
            interactable("water_flask", "Sealed Water Flask", DungeonItemCategory.FOOD, size(1, 2),
                    visual("flask", "#3f7ca6", "#173245", "#a5d9f5", "W",
                            "Blue vertical bottle block."),
                    props("can_stack", 1, "hunger_amount", 15.0, "uses", 3, "effect_id", "refreshed", "loot_weight", 12.0)),
            interactable("bandage", "Clean Bandage", DungeonItemCategory.MEDICAL, size(1, 1),
                    visual("roll", "#d9d0b8", "#5d5443", "#ffffff", "+",
                            "Cream square with a small cross mark."),
                    props("can_stack", 5, "heal_amount", 10, "effect_id", "regeneration", "loot_weight", 14.0)),
            interactable("antidote", "Antitoxin Vial", DungeonItemCategory.MEDICAL, size(1, 1),
                    visual("vial", "#5cc48a", "#1c4a30", "#b8ffd3", "A",
                            "Small green vial."),
                    props("can_stack", 3, "effect_id", "neutralization", "loot_weight", 4.0)),
            interactable("stamina_draught", "Endurance Draught", DungeonItemCategory.MEDICAL, size(1, 1),
                    visual("vial", "#7e5ed8", "#2b1b57", "#d1c1ff", "S",
                            "Small purple vial."),
                    props("can_stack", 3, "effect_id", "endurance_boost", "loot_weight", 5.0)),
            interactable("torch", "Hand Torch", DungeonItemCategory.LIGHT, size(1, 2),
                    visual("torch", "#8a4b24", "#2f1608", "#ff9d34", "T",
                            "Brown handle with orange flame top."),
                    props("light_radius", 18.0, "burn_time", 500.0, "fuel_remaining", 500.0,
                            "is_on", false, "effect_id", "lit", "loot_weight", 6.0)),
            interactable("lantern_oil", "Lantern Oil", DungeonItemCategory.TOOL, size(1, 1),
                    visual("oil can", "#4b4740", "#181615", "#d2b25f", "O",
                            "Small dark can with a yellow cap."),
                    props("can_stack", 4, "fuel_amount", 300.0, "fuel_amount_min", 200.0,
                            "fuel_amount_max", 1000.0, "loot_weight", 9.0)),
            interactable("flare", "Signal Flare", DungeonItemCategory.LIGHT, size(1, 1),
                    visual("flare", "#d83b2f", "#54110d", "#ffd36f", "F",
                            "Red stick with bright hot tip."),
                    props("light_radius", 12.0, "burn_time", 80.0, "fuel_remaining", 80.0,
                            "effect_id", "blind", "throwable", true, "loot_weight", 5.0)),
            interactable("rock", "Throwing Stone", DungeonItemCategory.TOOL, size(1, 1),
                    visual("rock", "#74706a", "#282623", "#aaa59b", "R",
                            "Small gray chunk."),
                    props("can_stack", 6, "throwable", true, "noise_radius", 8.0, "loot_weight", 10.0)),
            interactable("bomb", "Explosive Charge", DungeonItemCategory.TOOL, size(2, 2),
                    visual("bomb", "#282828", "#050505", "#d04c36", "!",
                            "Dark square bundle with red fuse pixel."),
                    props("fuse_time", 4.0, "blast_radius", 5.0, "effect_id", "explosion", "loot_weight", 2.0)),
            interactable("small_key", "Small Key", DungeonItemCategory.KEY, size(1, 1),
                    visual("key", "#d6b656", "#4a3910", "#fff0a8", "K",
                            "Small gold key silhouette."),
                    props("key_id", "", "loot_weight", 0.0)),
            interactable("rusted_key", "Rusted Key", DungeonItemCategory.KEY, size(1, 1),
                    visual("key", "#9c6d34", "#3a2410", "#c08b4a", "K",
                            "Brown-orange key silhouette."),
                    props("key_id", "", "can_stack", 8, "loot_weight", 0.0)),
            interactable("marked_key", "Marked Key", DungeonItemCategory.KEY, size(1, 1),
                    visual("key", "#d7c372", "#4d4218", "#fff6b5", "*",
                            "Gold key with a bright mark."),
                    props("key_id", "", "loot_weight", 0.0)),
            interactable("lockpick", "Lockpick", DungeonItemCategory.TOOL, size(1, 1),
                    visual("pick", "#9da7aa", "#2d3335", "#dce6e8", "L",
                            "Thin pale metal pick."),
                    props("can_stack", 4, "break_chance", 0.18, "loot_weight", 4.0)),
            interactable("crowbar", "Pry Bar", DungeonItemCategory.TOOL, size(1, 3),
                    visual("bar", "#7d2929", "#2a0b0b", "#c84c4c", "J",
                            "Long red-brown hooked bar."),
                    props("noise_radius", 10.0, "loot_weight", 2.0)),
            interactable("hand_axe", "Hand Axe", DungeonItemCategory.COMBAT, size(2, 2),
                    visual("axe", "#7f4b2d", "#2c170c", "#c8c8bd", "A",
                            "Short axe with a pale metal head."),
                    props("damage", 11, "reach", 1.35, "attack_speed", 0.75, "loot_weight", 2.0)),
            interactable("map_scrap", "Map Fragment", DungeonItemCategory.DOCUMENT, size(1, 1),
                    visual("paper", "#d8c796", "#574b2c", "#fff0bd", "M",
                            "Small tan paper square with map line."),
                    props("reveal_radius", 16.0, "loot_weight", 4.0)),
            interactable("note", "Written Note", DungeonItemCategory.DOCUMENT, size(1, 1),
                    visual("paper", "#e1d5ab", "#5b5032", "#fff8d7", "N",
                            "Small pale paper square."),
                    props("text", "", "loot_weight", 8.0)),
            interactable("knife", "Utility Knife", DungeonItemCategory.COMBAT, size(1, 2),
                    visual("blade", "#b7c1c4", "#32383a", "#f0f6f7", "I",
                            "Short vertical silver blade."),
                    props("damage", 6, "reach", 1.25, "attack_speed", 1.4, "loot_weight", 3.0)),
            interactable("spear", "Short Spear", DungeonItemCategory.COMBAT, size(1, 3),
                    visual("spear", "#8d6734", "#2f2110", "#d6d8d0", "^",
                            "Long brown shaft with pale tip."),
                    props("damage", 9, "reach", 2.4, "attack_speed", 0.85, "loot_weight", 1.5)),
            interactable("long_spear", "Long Spear", DungeonItemCategory.COMBAT, size(1, 4),
                    visual("spear", "#7f5d31", "#281c0d", "#e1e2d7", "L",
                            "Long reach spear with a pale point."),
                    props("damage", 12, "reach", 3.1, "attack_speed", 0.65, "loot_weight", 0.9)),
            interactable("wooden_shield", "Wooden Shield", DungeonItemCategory.ARMOR, size(2, 2),
                    visual("shield", "#8d6235", "#2d1c0f", "#c59a5a", "O",
                            "Rounded brown shield block."),
                    props("block_amount", 6, "durability", 80, "loot_weight", 1.5))
    );

    private static final Map<String, DungeonItemDefinition> DEFINITIONS_BY_ID = indexById(DEFINITIONS);

    private DungeonItemLibrary() {}

    public static List<DungeonItemDefinition> all() {
        return DEFINITIONS;
    }

    public static List<DungeonItemDefinition> byKind(DungeonItemKind kind) {
        List<DungeonItemDefinition> result = new ArrayList<>();
        for (DungeonItemDefinition definition : DEFINITIONS) {
            if (definition.kind() == kind) {
                result.add(definition);
            }
        }
        return List.copyOf(result);
    }

    public static Optional<DungeonItemDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEFINITIONS_BY_ID.get(id.trim()));
    }

    public static DungeonItemDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown dungeon item id: " + id));
    }

    public static boolean contains(String id) {
        return find(id).isPresent();
    }

    private static DungeonItemDefinition map(
            String id,
            String name,
            DungeonItemCategory category,
            DungeonItemVisual visual,
            Map<String, Object> properties
    ) {
        DungeonItemSize inventorySize = properties.get("inventory_size") instanceof DungeonItemSize size ? size : null;
        return new DungeonItemDefinition(
                id,
                name,
                DungeonItemKind.MAP,
                category,
                DungeonItemPlacement.CELL,
                isWallMounted(id),
                inventorySize,
                visual,
                withSharedProperties(id, name, DungeonItemKind.MAP, category, DungeonItemPlacement.CELL, isWallMounted(id), properties)
        );
    }

    private static DungeonItemDefinition interactable(
            String id,
            String name,
            DungeonItemCategory category,
            DungeonItemSize inventorySize,
            DungeonItemVisual visual,
            Map<String, Object> properties
    ) {
        return new DungeonItemDefinition(
                id,
                name,
                DungeonItemKind.INTERACTABLE,
                category,
                DungeonItemPlacement.CELL,
                false,
                inventorySize,
                visual,
                withSharedProperties(id, name, DungeonItemKind.INTERACTABLE, category, DungeonItemPlacement.CELL, false, properties)
        );
    }

    private static boolean isWallMounted(String id) {
        return WALL_LIGHT.equals(id) ||
                "broken_light".equals(id) ||
                "wall_painting".equals(id) ||
                "torn_banner".equals(id);
    }

    private static Map<String, Object> withSharedProperties(
            String id,
            String name,
            DungeonItemKind kind,
            DungeonItemCategory category,
            DungeonItemPlacement placement,
            boolean requiresWall,
            Map<String, Object> properties
    ) {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("id", id);
        shared.put("name", name);
        shared.put("kind", kind.name().toLowerCase());
        shared.put("category", category.name().toLowerCase());
        shared.put("placement", placement.name().toLowerCase());
        shared.put("cell_based", placement == DungeonItemPlacement.CELL);
        shared.put("grid_based", placement == DungeonItemPlacement.GRID);
        shared.put("map_based", kind == DungeonItemKind.MAP);
        shared.put("interactable", kind == DungeonItemKind.INTERACTABLE);
        shared.put("detail", category == DungeonItemCategory.DETAIL);
        shared.put("requires_wall", requiresWall);
        shared.putAll(properties);
        return Collections.unmodifiableMap(shared);
    }

    private static DungeonItemSize size(int width, int height) {
        return new DungeonItemSize(width, height);
    }

    private static DungeonItemVisual visual(
            String shape,
            String fill,
            String outline,
            String accent,
            String glyph,
            String description
    ) {
        return new DungeonItemVisual(shape, fill, outline, accent, glyph, description);
    }

    private static Map<String, Object> props(Object... pairs) {
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

    @SafeVarargs
    private static List<Map<String, Object>> weightedDrops(Map<String, Object>... drops) {
        return List.of(drops);
    }

    private static Map<String, Object> drop(String itemId, double weight) {
        return Map.of("item_id", itemId, "weight", weight);
    }

    private static Map<String, DungeonItemDefinition> indexById(List<DungeonItemDefinition> definitions) {
        Map<String, DungeonItemDefinition> byId = new LinkedHashMap<>();
        for (DungeonItemDefinition definition : definitions) {
            DungeonItemDefinition previous = byId.put(definition.id(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate dungeon item id: " + definition.id());
            }
        }
        if (!byId.containsKey(WALL_LIGHT)) {
            throw new IllegalStateException("Dungeon item library must define " + WALL_LIGHT + ".");
        }
        return Collections.unmodifiableMap(byId);
    }
}

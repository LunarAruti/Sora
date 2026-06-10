package ucadmin.simulation.generation;

import java.util.List;
import java.util.Objects;

public final class DungeonArtifactTemplate {
    private final String id;
    private final String name;
    private final int category;
    private final List<DungeonLine> walls;
    private final List<DungeonOpening> openings;
    private final List<DungeonOccupiedArea> occupiedAreas;
    private final List<DungeonItem> items;

    public DungeonArtifactTemplate(
            String id,
            String name,
            int category,
            List<DungeonLine> walls,
            List<DungeonOpening> openings,
            List<DungeonOccupiedArea> occupiedAreas,
            List<DungeonItem> items
    ) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.category = Math.max(0, category);
        this.walls = List.copyOf(Objects.requireNonNull(walls, "walls"));
        this.openings = List.copyOf(Objects.requireNonNull(openings, "openings"));
        this.occupiedAreas = List.copyOf(Objects.requireNonNull(occupiedAreas, "occupiedAreas"));
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCategory() { return category; }
    public List<DungeonLine> getWalls() { return walls; }
    public List<DungeonOpening> getOpenings() { return openings; }
    public List<DungeonOccupiedArea> getOccupiedAreas() { return occupiedAreas; }
    public List<DungeonItem> getItems() { return items; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Artifact template " + field + " cannot be blank.");
        }
        return value.trim();
    }
}

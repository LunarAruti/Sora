package ucadmin.simulation.generation;

import java.util.List;
import java.util.Objects;

public final class DungeonArtifactTemplate {
    private final String id;
    private final String name;
    private final int spawnProbability;
    private final List<DungeonLine> walls;
    private final List<DungeonOpening> openings;
    private final List<DungeonOccupiedArea> occupiedAreas;

    public DungeonArtifactTemplate(
            String id,
            String name,
            int spawnProbability,
            List<DungeonLine> walls,
            List<DungeonOpening> openings,
            List<DungeonOccupiedArea> occupiedAreas
    ) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.spawnProbability = Math.max(0, Math.min(10, spawnProbability));
        this.walls = List.copyOf(Objects.requireNonNull(walls, "walls"));
        this.openings = List.copyOf(Objects.requireNonNull(openings, "openings"));
        this.occupiedAreas = List.copyOf(Objects.requireNonNull(occupiedAreas, "occupiedAreas"));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getSpawnProbability() { return spawnProbability; }
    public List<DungeonLine> getWalls() { return walls; }
    public List<DungeonOpening> getOpenings() { return openings; }
    public List<DungeonOccupiedArea> getOccupiedAreas() { return occupiedAreas; }

    public int getWeight() {
        if (spawnProbability == 0) {
            return 0;
        }
        return 11 - spawnProbability;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Artifact template " + field + " cannot be blank.");
        }
        return value.trim();
    }
}

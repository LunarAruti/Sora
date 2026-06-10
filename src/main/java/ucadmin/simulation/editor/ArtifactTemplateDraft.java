package ucadmin.simulation.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ArtifactTemplateDraft {
    private String id;
    private String name;
    private int spawnProbability = 1;
    private ArtifactPoint center;
    private final List<ArtifactWall> walls = new ArrayList<>();
    private final List<ArtifactOpening> openings = new ArrayList<>();
    private final List<ArtifactOccupiedArea> occupiedAreas = new ArrayList<>();

    public ArtifactTemplateDraft() {
        this.id = "artifact_" + UUID.randomUUID().toString().replace("-", "");
        this.name = "untitled";
    }

    private ArtifactTemplateDraft(String id, String name) {
        this.id = (id == null || id.isBlank())
                ? "artifact_" + UUID.randomUUID().toString().replace("-", "")
                : id;
        this.name = (name == null || name.isBlank()) ? "untitled" : name;
    }

    private ArtifactTemplateDraft(ArtifactTemplateDraft other) {
        this.id = other.id;
        this.name = other.name;
        this.spawnProbability = other.spawnProbability;
        this.center = other.center;
        this.walls.addAll(other.walls);
        this.openings.addAll(other.openings);
        this.occupiedAreas.addAll(other.occupiedAreas);
    }

    public ArtifactTemplateDraft copy() {
        return new ArtifactTemplateDraft(this);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getSpawnProbability() { return spawnProbability; }
    public ArtifactPoint getCenter() { return center; }
    public List<ArtifactWall> getWalls() { return List.copyOf(walls); }
    public List<ArtifactOpening> getOpenings() { return List.copyOf(openings); }
    public List<ArtifactOccupiedArea> getOccupiedAreas() { return List.copyOf(occupiedAreas); }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "untitled" : name.trim();
    }

    public void setSpawnProbability(int spawnProbability) {
        this.spawnProbability = Math.max(0, Math.min(10, spawnProbability));
    }

    public void setCenter(ArtifactPoint center) {
        this.center = center;
    }

    public void addWall(ArtifactWall wall) {
        if (wall != null) walls.add(wall);
    }

    public void addOpening(ArtifactOpening opening) {
        if (opening != null) openings.add(opening);
    }

    public void addOccupiedArea(ArtifactOccupiedArea area) {
        if (area != null) occupiedAreas.add(area);
    }

    public void eraseNear(ArtifactPoint point) {
        if (point == null) return;
        if (center != null && ArtifactEditorGeometry.distanceSquared(center, point) <= 4) {
            center = null;
            return;
        }
        walls.removeIf(wall ->
                ArtifactEditorGeometry.distanceSquared(wall.start(), point) <= 4 ||
                        ArtifactEditorGeometry.distanceSquared(wall.end(), point) <= 4);
        openings.removeIf(opening -> ArtifactEditorGeometry.distanceSquared(opening.position(), point) <= 4);
        occupiedAreas.removeIf(area ->
                area.points().stream().anyMatch(p -> ArtifactEditorGeometry.distanceSquared(p, point) <= 4));
    }

    public List<String> validateForSave() {
        List<String> warnings = new ArrayList<>();
        if (center == null) warnings.add("No center has been placed.");
        if (walls.isEmpty()) warnings.add("No walls have been drawn.");
        if (openings.isEmpty()) warnings.add("No openings have been placed.");
        if (occupiedAreas.isEmpty()) warnings.add("No occupied areas have been drawn.");
        return warnings;
    }

    public List<String> validateBlockingSaveIssues() {
        List<String> issues = new ArrayList<>();
        if ("default".equalsIgnoreCase(name == null ? "" : name.trim())) {
            issues.add("Artifact name 'default' is reserved for the generator and cannot be saved from the editor.");
        }
        return issues;
    }

    public JSONObject toArtifactJson(int canvasWidth, int canvasHeight) {
        ArtifactPoint saveCenter = center == null
                ? new ArtifactPoint(canvasWidth / 2, canvasHeight / 2)
                : center;

        JSONArray wallArray = new JSONArray();
        for (ArtifactWall wall : walls) {
            wallArray.put(wall.toJson(saveCenter));
        }

        JSONArray openingArray = new JSONArray();
        for (ArtifactOpening opening : openings) {
            openingArray.put(opening.toJson(saveCenter));
        }

        JSONArray occupiedArray = new JSONArray();
        for (ArtifactOccupiedArea area : occupiedAreas) {
            occupiedArray.put(area.toJson(saveCenter));
        }

        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("created_format", "artifact-template-v1")
                .put("spawn_probability", spawnProbability)
                .put("last_saved", System.currentTimeMillis())
                .put("editor", new JSONObject()
                        .put("canvas_width", canvasWidth)
                        .put("canvas_height", canvasHeight)
                        .put("center_canvas", saveCenter.toJson()))
                .put("walls", wallArray)
                .put("openings", openingArray)
                .put("occupied_areas", occupiedArray);
    }

    public static ArtifactTemplateDraft fromArtifactJson(JSONObject artifact, int fallbackCanvasWidth, int fallbackCanvasHeight) {
        if (artifact == null) {
            return new ArtifactTemplateDraft();
        }

        ArtifactTemplateDraft draft = new ArtifactTemplateDraft(
                artifact.optString("id", null),
                artifact.optString("name", "untitled")
        );

        JSONObject editor = artifact.optJSONObject("editor");
        ArtifactPoint center = readPoint(
                editor == null ? null : editor.optJSONObject("center_canvas"),
                new ArtifactPoint(fallbackCanvasWidth / 2, fallbackCanvasHeight / 2)
        );
        draft.setCenter(center);
        draft.setSpawnProbability(artifact.optInt("spawn_probability", 1));

        JSONArray wallArray = artifact.optJSONArray("walls");
        if (wallArray != null) {
            for (int i = 0; i < wallArray.length(); i++) {
                JSONObject wall = wallArray.optJSONObject(i);
                if (wall == null) continue;
                draft.addWall(new ArtifactWall(
                        absolutePoint(wall.optJSONObject("start"), center),
                        absolutePoint(wall.optJSONObject("end"), center)
                ));
            }
        }

        JSONArray openingArray = artifact.optJSONArray("openings");
        if (openingArray != null) {
            for (int i = 0; i < openingArray.length(); i++) {
                JSONObject opening = openingArray.optJSONObject(i);
                if (opening == null) continue;
                ArtifactDirection direction = readDirection(opening.optString("direction", "NORTH"));
                int width = Math.max(1, opening.optInt("width", 1));
                draft.addOpening(new ArtifactOpening(
                        absolutePoint(opening.optJSONObject("position"), center),
                        direction,
                        width
                ));
            }
        }

        JSONArray occupiedArray = artifact.optJSONArray("occupied_areas");
        if (occupiedArray != null) {
            for (int i = 0; i < occupiedArray.length(); i++) {
                JSONObject area = occupiedArray.optJSONObject(i);
                if (area == null) continue;
                JSONArray points = area.optJSONArray("points");
                if (points != null) {
                    List<ArtifactPoint> loadedPoints = new ArrayList<>();
                    for (int p = 0; p < points.length(); p++) {
                        JSONObject point = points.optJSONObject(p);
                        if (point != null) {
                            loadedPoints.add(absolutePoint(point, center));
                        }
                    }
                    if (loadedPoints.size() >= 3) {
                        draft.addOccupiedArea(new ArtifactOccupiedArea(loadedPoints));
                    }
                } else {
                    draft.addOccupiedArea(ArtifactOccupiedArea.rectangle(
                            absolutePoint(area.optJSONObject("start"), center),
                            absolutePoint(area.optJSONObject("end"), center)
                    ));
                }
            }
        }

        return draft;
    }

    private static ArtifactPoint absolutePoint(JSONObject relativePoint, ArtifactPoint center) {
        ArtifactPoint relative = readPoint(relativePoint, new ArtifactPoint(0, 0));
        return new ArtifactPoint(center.x() + relative.x(), center.y() + relative.y());
    }

    private static ArtifactPoint readPoint(JSONObject point, ArtifactPoint fallback) {
        if (point == null) {
            return fallback;
        }
        return new ArtifactPoint(point.optInt("x", fallback.x()), point.optInt("y", fallback.y()));
    }

    private static ArtifactDirection readDirection(String raw) {
        try {
            return ArtifactDirection.valueOf(raw == null ? "NORTH" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ArtifactDirection.NORTH;
        }
    }
}

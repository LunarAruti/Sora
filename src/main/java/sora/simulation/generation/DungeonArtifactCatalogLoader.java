package sora.simulation.generation;

import org.json.JSONArray;
import org.json.JSONObject;
import sora.database.DatabaseManager;
import sora.exceptions.DatabaseException;
import sora.util.Logger;

import java.util.ArrayList;
import java.util.List;

public final class DungeonArtifactCatalogLoader {
    private static final String ARTIFACT_FILE = "database/artifacts/artifacts.json";

    private DungeonArtifactCatalogLoader() {}

    public static DungeonArtifactCatalog loadDefaultCatalog() {
        Object raw = DatabaseManager.readJSONPath(ARTIFACT_FILE, "artifacts");
        if (!(raw instanceof JSONObject artifacts)) {
            Logger.log(Logger.TAG.ERROR, "[A0030] DungeonArtifactCatalogLoader: artifacts root is not a JSON object.");
            throw new DatabaseException("Dungeon artifact store has no artifacts object.");
        }

        List<DungeonArtifactTemplate> templates = new ArrayList<>();
        for (String artifactId : artifacts.keySet()) {
            JSONObject artifact = artifacts.optJSONObject(artifactId);
            if (artifact == null) {
                Logger.log(Logger.TAG.WARN, "[A0031] DungeonArtifactCatalogLoader: skipping non-object artifact id=" + artifactId);
                continue;
            }
            templates.add(readTemplate(artifactId, artifact));
        }

        try {
            DungeonArtifactCatalog catalog = new DungeonArtifactCatalog(templates);
            Logger.log(Logger.TAG.INFO, "DungeonArtifactCatalogLoader: loaded artifact count=" + templates.size()
                    + " fingerprint=" + catalog.getFingerprint());
            return catalog;
        } catch (IllegalArgumentException e) {
            Logger.log(Logger.TAG.ERROR, "[A0032] DungeonArtifactCatalogLoader: invalid artifact catalog: " + e.getMessage());
            throw new DatabaseException("Dungeon artifact catalog is invalid: " + e.getMessage(), e);
        }
    }

    private static DungeonArtifactTemplate readTemplate(String fallbackId, JSONObject artifact) {
        String id = artifact.optString("id", fallbackId);
        String name = artifact.optString("name", id);
        int category = artifact.optInt("category", 1);

        return new DungeonArtifactTemplate(
                id,
                name,
                category,
                readWalls(artifact.optJSONArray("walls")),
                readOpenings(artifact.optJSONArray("openings")),
                readOccupiedAreas(artifact.optJSONArray("occupied_areas")),
                readItems(artifact.optJSONArray("items"))
        );
    }

    private static List<DungeonLine> readWalls(JSONArray walls) {
        List<DungeonLine> lines = new ArrayList<>();
        if (walls == null) {
            return lines;
        }
        for (int i = 0; i < walls.length(); i++) {
            JSONObject wall = walls.optJSONObject(i);
            if (wall == null) continue;
            lines.add(new DungeonLine(
                    readPoint(wall.optJSONObject("start")),
                    readPoint(wall.optJSONObject("end"))
            ));
        }
        return lines;
    }

    private static List<DungeonOpening> readOpenings(JSONArray openings) {
        List<DungeonOpening> result = new ArrayList<>();
        if (openings == null) {
            return result;
        }
        for (int i = 0; i < openings.length(); i++) {
            JSONObject opening = openings.optJSONObject(i);
            if (opening == null) continue;
            result.add(new DungeonOpening(
                    readPoint(opening.optJSONObject("position")),
                    readDirection(opening.optString("direction", "NORTH")),
                    Math.max(1, opening.optInt("width", 6))
            ));
        }
        return result;
    }

    private static List<DungeonOccupiedArea> readOccupiedAreas(JSONArray occupiedAreas) {
        List<DungeonOccupiedArea> result = new ArrayList<>();
        if (occupiedAreas == null) {
            return result;
        }
        for (int i = 0; i < occupiedAreas.length(); i++) {
            JSONObject area = occupiedAreas.optJSONObject(i);
            if (area == null) continue;
            JSONArray points = area.optJSONArray("points");
            if (points != null) {
                result.add(readOccupiedPolygon(points));
                continue;
            }
            result.add(DungeonOccupiedArea.fromRect(DungeonRect.fromPoints(
                    readPoint(area.optJSONObject("start")),
                    readPoint(area.optJSONObject("end"))
            )));
        }
        return result;
    }

    private static DungeonOccupiedArea readOccupiedPolygon(JSONArray points) {
        List<DungeonPoint> polygon = new ArrayList<>();
        for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.optJSONObject(i);
            if (point != null) {
                polygon.add(readPoint(point));
            }
        }
        return new DungeonOccupiedArea(polygon);
    }

    private static List<DungeonItem> readItems(JSONArray items) {
        List<DungeonItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            result.add(new DungeonItem(
                    item.optString("id", "unknown"),
                    readPoint(item.optJSONObject("position")),
                    readDirection(item.optString("direction", "NORTH"))
            ));
        }
        return result;
    }

    private static DungeonPoint readPoint(JSONObject point) {
        if (point == null) {
            return new DungeonPoint(0, 0);
        }
        return new DungeonPoint(point.optInt("x", 0), point.optInt("y", 0));
    }

    private static DungeonDirection readDirection(String raw) {
        try {
            return DungeonDirection.valueOf(raw == null ? "NORTH" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DungeonDirection.NORTH;
        }
    }
}

package ucadmin.simulation.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public record ArtifactOccupiedArea(List<ArtifactPoint> points) {
    public ArtifactOccupiedArea {
        points = points == null ? List.of() : List.copyOf(points);
    }

    public static ArtifactOccupiedArea rectangle(ArtifactPoint start, ArtifactPoint end) {
        int minX = Math.min(start.x(), end.x());
        int minY = Math.min(start.y(), end.y());
        int maxX = Math.max(start.x(), end.x());
        int maxY = Math.max(start.y(), end.y());
        return new ArtifactOccupiedArea(List.of(
                new ArtifactPoint(minX, minY),
                new ArtifactPoint(maxX, minY),
                new ArtifactPoint(maxX, maxY),
                new ArtifactPoint(minX, maxY)
        ));
    }

    public JSONObject toJson(ArtifactPoint center) {
        JSONArray pointArray = new JSONArray();
        for (ArtifactPoint point : points) {
            pointArray.put(ArtifactEditorGeometry.relative(point, center).toJson());
        }

        return new JSONObject()
                .put("points", pointArray);
    }
}

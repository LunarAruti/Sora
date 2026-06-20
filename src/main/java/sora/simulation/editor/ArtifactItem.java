package sora.simulation.editor;

import org.json.JSONObject;

public record ArtifactItem(String id, ArtifactPoint position, ArtifactDirection direction) {
    public ArtifactItem {
        id = (id == null || id.isBlank()) ? "unknown" : id.trim();
        if (position == null) {
            position = new ArtifactPoint(0, 0);
        }
        if (direction == null) {
            direction = ArtifactDirection.NORTH;
        }
    }

    public JSONObject toJson(ArtifactPoint center) {
        return new JSONObject()
                .put("id", id)
                .put("position", ArtifactEditorGeometry.relative(position, center).toJson())
                .put("direction", direction.name());
    }

    public ArtifactItem translate(int dx, int dy) {
        return new ArtifactItem(
                id,
                new ArtifactPoint(position.x() + dx, position.y() + dy),
                direction
        );
    }
}

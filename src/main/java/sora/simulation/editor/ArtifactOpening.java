package sora.simulation.editor;

import org.json.JSONObject;

public record ArtifactOpening(ArtifactPoint position, ArtifactDirection direction, int width) {
    public JSONObject toJson(ArtifactPoint center) {
        return new JSONObject()
                .put("position", ArtifactEditorGeometry.relative(position, center).toJson())
                .put("direction", direction.name())
                .put("width", width);
    }
}

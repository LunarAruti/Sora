package sora.simulation.editor;

import org.json.JSONObject;

public record ArtifactWall(ArtifactPoint start, ArtifactPoint end) {
    public JSONObject toJson(ArtifactPoint center) {
        return new JSONObject()
                .put("start", ArtifactEditorGeometry.relative(start, center).toJson())
                .put("end", ArtifactEditorGeometry.relative(end, center).toJson());
    }
}

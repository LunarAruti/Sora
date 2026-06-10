package ucadmin.simulation.editor;

import org.json.JSONObject;

public record ArtifactPoint(int x, int y) {
    public JSONObject toJson() {
        return new JSONObject()
                .put("x", x)
                .put("y", y);
    }
}

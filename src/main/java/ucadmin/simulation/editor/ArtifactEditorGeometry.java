package ucadmin.simulation.editor;

final class ArtifactEditorGeometry {
    private ArtifactEditorGeometry() {}

    static ArtifactPoint relative(ArtifactPoint point, ArtifactPoint center) {
        if (point == null || center == null) {
            return new ArtifactPoint(0, 0);
        }
        return new ArtifactPoint(point.x() - center.x(), point.y() - center.y());
    }

    static int distanceSquared(ArtifactPoint a, ArtifactPoint b) {
        int dx = a.x() - b.x();
        int dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }
}

package sora.simulation.generation;

public final class DungeonArtifactTraits {
    private final DungeonArtifactTemplate template;
    private final int openingCount;
    private final int occupiedAreaCount;
    private final DungeonRect occupiedBounds;
    private final double occupiedArea;

    DungeonArtifactTraits(DungeonArtifactTemplate template) {
        this.template = template;
        this.openingCount = template.getOpenings().size();
        this.occupiedAreaCount = template.getOccupiedAreas().size();
        this.occupiedBounds = calculateBounds(template);
        this.occupiedArea = calculateOccupiedArea(template);
    }

    public DungeonArtifactTemplate getTemplate() { return template; }
    public int getCategory() { return template.getCategory(); }
    public int getOpeningCount() { return openingCount; }
    public int getOccupiedAreaCount() { return occupiedAreaCount; }
    public DungeonRect getOccupiedBounds() { return occupiedBounds; }
    public double getOccupiedArea() { return occupiedArea; }

    public boolean isForcedOnly() {
        return template.getCategory() == 0;
    }

    public boolean isDeadEndLike() {
        return openingCount == 1;
    }

    public boolean isPassageLike() {
        return openingCount == 2;
    }

    public boolean isJunctionLike() {
        return openingCount >= 3;
    }

    public double getAspectRatio() {
        int width = Math.max(1, occupiedBounds.maxX() - occupiedBounds.minX());
        int height = Math.max(1, occupiedBounds.maxY() - occupiedBounds.minY());
        return Math.max(width, height) / (double) Math.min(width, height);
    }

    private static DungeonRect calculateBounds(DungeonArtifactTemplate template) {
        if (template.getOccupiedAreas().isEmpty()) {
            return new DungeonRect(0, 0, 0, 0);
        }
        DungeonRect first = template.getOccupiedAreas().get(0).getBounds();
        int minX = first.minX();
        int minY = first.minY();
        int maxX = first.maxX();
        int maxY = first.maxY();
        for (DungeonOccupiedArea area : template.getOccupiedAreas()) {
            DungeonRect bounds = area.getBounds();
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxY());
        }
        return new DungeonRect(minX, minY, maxX, maxY);
    }

    private static double calculateOccupiedArea(DungeonArtifactTemplate template) {
        double total = 0.0;
        for (DungeonOccupiedArea area : template.getOccupiedAreas()) {
            total += polygonArea(area);
        }
        return total;
    }

    private static double polygonArea(DungeonOccupiedArea area) {
        double sum = 0.0;
        for (int i = 0; i < area.getPoints().size(); i++) {
            DungeonPoint a = area.getPoints().get(i);
            DungeonPoint b = area.getPoints().get((i + 1) % area.getPoints().size());
            sum += (double) a.x() * b.y() - (double) b.x() * a.y();
        }
        return Math.abs(sum) / 2.0;
    }
}

package sora.simulation.items;

public record DungeonItemSize(int width, int height) {
    public DungeonItemSize {
        if (width < 1) {
            throw new IllegalArgumentException("width must be positive.");
        }
        if (height < 1) {
            throw new IllegalArgumentException("height must be positive.");
        }
    }
}

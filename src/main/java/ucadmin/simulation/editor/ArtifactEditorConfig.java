package ucadmin.simulation.editor;

import java.awt.Color;

public final class ArtifactEditorConfig {
    private final String title;
    private final int width;
    private final int height;
    private final int canvasCellsWide;
    private final int canvasCellsHigh;
    private final int targetFps;
    private final long inputPollMillis;
    private final Color backgroundColor;
    private final Color gridColor;

    public ArtifactEditorConfig(
            String title,
            int width,
            int height,
            int canvasCellsWide,
            int canvasCellsHigh,
            int targetFps,
            long inputPollMillis,
            Color backgroundColor,
            Color gridColor
    ) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("ArtifactEditorConfig: title cannot be null or blank.");
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("ArtifactEditorConfig: window size must be positive.");
        }
        if (canvasCellsWide < 1 || canvasCellsHigh < 1) {
            throw new IllegalArgumentException("ArtifactEditorConfig: canvas dimensions must be positive.");
        }
        if (targetFps < 1) {
            throw new IllegalArgumentException("ArtifactEditorConfig: targetFps must be positive.");
        }
        if (inputPollMillis < 1L) {
            throw new IllegalArgumentException("ArtifactEditorConfig: inputPollMillis must be positive.");
        }

        this.title = title;
        this.width = width;
        this.height = height;
        this.canvasCellsWide = canvasCellsWide;
        this.canvasCellsHigh = canvasCellsHigh;
        this.targetFps = targetFps;
        this.inputPollMillis = inputPollMillis;
        this.backgroundColor = backgroundColor == null ? new Color(26, 28, 32) : backgroundColor;
        this.gridColor = gridColor == null ? new Color(72, 76, 84) : gridColor;
    }

    public static ArtifactEditorConfig defaultConfig() {
        return new ArtifactEditorConfig(
                "Artifact Editor",
                1100,
                800,
                200,
                200,
                60,
                25L,
                new Color(26, 28, 32),
                new Color(72, 76, 84)
        );
    }

    public String getTitle() { return title; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getCanvasCellsWide() { return canvasCellsWide; }
    public int getCanvasCellsHigh() { return canvasCellsHigh; }
    public int getTargetFps() { return targetFps; }
    public long getInputPollMillis() { return inputPollMillis; }
    public Color getBackgroundColor() { return backgroundColor; }
    public Color getGridColor() { return gridColor; }
}

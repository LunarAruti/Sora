package ucadmin.simulation;

import java.awt.Color;

/**
 * Immutable runtime configuration for a simulation instance.
 */
public final class SimulationConfig {
    private final String title;
    private final int width;
    private final int height;
    private final int targetFps;
    private final Color backgroundColor;
    private final long inputPollMillis;
    private final boolean exitOnWindowClose;

    public SimulationConfig(
            String title,
            int width,
            int height,
            int targetFps,
            Color backgroundColor,
            long inputPollMillis,
            boolean exitOnWindowClose
    ) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("SimulationConfig: title cannot be null or blank.");
        }
        if (width < 1) {
            throw new IllegalArgumentException("SimulationConfig: width must be >= 1.");
        }
        if (height < 1) {
            throw new IllegalArgumentException("SimulationConfig: height must be >= 1.");
        }
        if (targetFps < 1) {
            throw new IllegalArgumentException("SimulationConfig: targetFps must be >= 1.");
        }
        if (inputPollMillis < 1L) {
            throw new IllegalArgumentException("SimulationConfig: inputPollMillis must be >= 1.");
        }

        this.title = title;
        this.width = width;
        this.height = height;
        this.targetFps = targetFps;
        this.backgroundColor = backgroundColor == null ? Color.BLACK : backgroundColor;
        this.inputPollMillis = inputPollMillis;
        this.exitOnWindowClose = exitOnWindowClose;
    }

    public String getTitle() {
        return title;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTargetFps() {
        return targetFps;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public long getInputPollMillis() {
        return inputPollMillis;
    }

    public boolean isExitOnWindowClose() {
        return exitOnWindowClose;
    }
}

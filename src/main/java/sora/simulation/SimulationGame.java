package sora.simulation;

import java.awt.Graphics2D;

/**
 * Game hook interface owned by the simulation runtime.
 */
public interface SimulationGame {
    default void onStart(SimulationContext context) {}

    default void update(SimulationContext context, double deltaSeconds) {}

    default void render(SimulationContext context, Graphics2D graphics) {}

    default void onStop(SimulationContext context) {}

    default void onCrash(SimulationContext context, Throwable throwable) {}

    default void onKeyPressed(SimulationContext context, int keyCode) {}

    default void onKeyReleased(SimulationContext context, int keyCode) {}

    default void onKeyTyped(SimulationContext context, char keyChar) {}

    default void onMousePressed(SimulationContext context, int x, int y, int button) {}

    default void onMouseReleased(SimulationContext context, int x, int y, int button) {}

    default void onMouseMoved(SimulationContext context, int x, int y) {}

    default void onMouseDragged(SimulationContext context, int x, int y, int button) {}
}

package ucadmin.simulation;

/**
 * Read-only view of a running simulation passed into game hooks.
 */
public final class SimulationContext {
    private final SimulationEngine engine;
    private final SimulationConfig config;
    private final long createdAtMillis;

    SimulationContext(SimulationEngine engine, SimulationConfig config) {
        this.engine = engine;
        this.config = config;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public boolean isRunning() {
        return engine.isRunning();
    }

    public void exit() {
        engine.exit();
    }
}

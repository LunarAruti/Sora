package sora.simulation;

import sora.util.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone simulation runtime object.
 *
 * A simulation owns exactly two framework-managed threads:
 * one render/window thread and one input dispatch thread.
 */
public final class SimulationEngine {
    private final SimulationConfig config;
    private final SimulationGame game;
    private final SimulationContext context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private Thread renderThread;
    private Thread inputThread;
    private SimulationWindow window;

    public SimulationEngine(SimulationConfig config, SimulationGame game) {
        this.config = Objects.requireNonNull(config, "config");
        this.game = Objects.requireNonNull(game, "game");
        this.context = new SimulationContext(this, config);
    }

    /**
     * Starts the simulation if it is not already running.
     */
    public synchronized boolean run() {
        if (!running.compareAndSet(false, true)) {
            Logger.log(Logger.TAG.WARN, "[A0003] SimulationEngine: run ignored because simulation is already running.");
            return false;
        }

        stopping.set(false);
        Logger.log(Logger.TAG.SYSTEM, "SimulationEngine: starting simulation title=" + config.getTitle());

        SimulationInputController inputController =
                new SimulationInputController(running, config, game, context);

        inputThread = new Thread(withCrashBoundary(inputController, "input"), "Simulation Input");
        renderThread = new Thread(withCrashBoundary(() -> renderLoop(inputController), "render"), "Simulation Render");

        inputThread.start();
        renderThread.start();

        Logger.log(Logger.TAG.INFO, "SimulationEngine: simulation threads started.");
        return true;
    }

    /**
     * Stops the simulation if it is running.
     */
    public void exit() {
        stopInternal("external exit", null);
    }

    public boolean isRunning() {
        return running.get();
    }

    private Runnable withCrashBoundary(Runnable runnable, String threadRole) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "[A0004] SimulationEngine: " + threadRole + " thread crashed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage());
                Logger.logDump("SIMULATION_THREAD_CRASH role=" + threadRole, t);
                stopInternal(threadRole + " thread crash", t);
            }
        };
    }

    private void renderLoop(SimulationInputController inputController) {
        Logger.log(Logger.TAG.SYSTEM, "SimulationEngine: render thread started.");
        window = new SimulationWindow(config, game, context, inputController, this::exit);
        window.showWindow();
        game.onStart(context);

        long previousNanos = System.nanoTime();
        long targetFrameNanos = 1_000_000_000L / Math.max(1, config.getTargetFps());

        while (running.get()) {
            long now = System.nanoTime();
            double deltaSeconds = (now - previousNanos) / 1_000_000_000.0;
            previousNanos = now;

            game.update(context, deltaSeconds);
            if (!running.get()) {
                break;
            }
            window.renderFrame();
            sleepUntilNextFrame(targetFrameNanos);
        }

        Logger.log(Logger.TAG.SYSTEM, "SimulationEngine: render thread stopped.");
    }

    private void sleepUntilNextFrame(long targetFrameNanos) {
        long millis = Math.max(1L, targetFrameNanos / 1_000_000L);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (running.get()) {
                Logger.log(Logger.TAG.WARN, "[A0005] SimulationEngine: render sleep interrupted while running.");
            }
        }
    }

    private void stopInternal(String reason, Throwable crash) {
        if (!running.get() && !stopping.get()) {
            Logger.log(Logger.TAG.DEBUG, "SimulationEngine: stop ignored because simulation is not running.");
            return;
        }
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        Logger.log(Logger.TAG.SYSTEM, "SimulationEngine: stopping simulation reason=" + reason);
        running.set(false);

        if (crash != null) {
            try {
                game.onCrash(context, crash);
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "[A0006] SimulationEngine: game crash hook failed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }

        interruptIfOtherThread(inputThread);
        interruptIfOtherThread(renderThread);
        joinIfOtherThread(inputThread);
        joinIfOtherThread(renderThread);

        try {
            game.onStop(context);
        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR, "[A0007] SimulationEngine: game stop hook failed: "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
        }

        if (window != null) {
            try {
                window.close();
            } catch (Throwable t) {
                Logger.log(Logger.TAG.WARN, "[A0008] SimulationEngine: window close failed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }

        window = null;
        inputThread = null;
        renderThread = null;
        stopping.set(false);
        Logger.log(Logger.TAG.SYSTEM, "SimulationEngine: simulation stopped.");
    }

    private void interruptIfOtherThread(Thread thread) {
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    private void joinIfOtherThread(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(2_000L);
            if (thread.isAlive()) {
                Logger.log(Logger.TAG.WARN, "[A0009] SimulationEngine: thread did not stop within timeout: "
                        + thread.getName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.log(Logger.TAG.WARN, "[A0010] SimulationEngine: interrupted while waiting for thread: "
                    + thread.getName());
        }
    }
}

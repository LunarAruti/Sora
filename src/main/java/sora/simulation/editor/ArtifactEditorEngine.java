package sora.simulation.editor;

import sora.util.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ArtifactEditorEngine {
    private final ArtifactEditorConfig config;
    private final ArtifactEditorState state;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private Thread renderThread;
    private Thread inputThread;
    private ArtifactEditorWindow window;

    public ArtifactEditorEngine() {
        this(ArtifactEditorConfig.defaultConfig());
    }

    public ArtifactEditorEngine(ArtifactEditorConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.state = new ArtifactEditorState();
    }

    public synchronized boolean run() {
        if (!running.compareAndSet(false, true)) {
            Logger.log(Logger.TAG.WARN, "[A0015] ArtifactEditorEngine: run ignored because editor is already running.");
            return false;
        }

        stopping.set(false);
        Logger.log(Logger.TAG.SYSTEM, "ArtifactEditorEngine: starting editor.");

        ArtifactEditorCanvas canvas = new ArtifactEditorCanvas(config, state);
        ArtifactEditorInputController inputController =
                new ArtifactEditorInputController(running, config, state, canvas);
        window = new ArtifactEditorWindow(config, state, canvas, inputController, this::exit);

        inputThread = new Thread(withCrashBoundary(inputController, "input"), "Artifact Editor Input");
        renderThread = new Thread(withCrashBoundary(() -> renderLoop(window), "render"), "Artifact Editor Render");

        inputThread.start();
        renderThread.start();

        Logger.log(Logger.TAG.INFO, "ArtifactEditorEngine: editor threads started.");
        return true;
    }

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
                Logger.log(Logger.TAG.ERROR, "[A0016] ArtifactEditorEngine: " + threadRole + " thread crashed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage());
                Logger.logDump("ARTIFACT_EDITOR_THREAD_CRASH role=" + threadRole, t);
                stopInternal(threadRole + " thread crash", t);
            }
        };
    }

    private void renderLoop(ArtifactEditorWindow editorWindow) {
        Logger.log(Logger.TAG.SYSTEM, "ArtifactEditorEngine: render thread started.");
        editorWindow.showWindow();

        long frameMillis = Math.max(1L, 1000L / Math.max(1, config.getTargetFps()));
        while (running.get()) {
            editorWindow.renderFrame();
            try {
                Thread.sleep(frameMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (running.get()) {
                    Logger.log(Logger.TAG.WARN, "[A0017] ArtifactEditorEngine: render sleep interrupted while running.");
                }
                break;
            }
        }

        Logger.log(Logger.TAG.SYSTEM, "ArtifactEditorEngine: render thread stopped.");
    }

    private void stopInternal(String reason, Throwable crash) {
        if (!running.get() && !stopping.get()) {
            Logger.log(Logger.TAG.DEBUG, "ArtifactEditorEngine: stop ignored because editor is not running.");
            return;
        }
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        Logger.log(Logger.TAG.SYSTEM, "ArtifactEditorEngine: stopping editor reason=" + reason);
        running.set(false);

        if (crash != null) {
            Logger.log(Logger.TAG.ERROR, "[A0018] ArtifactEditorEngine: editor stopped after crash: "
                    + crash.getClass().getSimpleName() + " - " + crash.getMessage());
        }

        interruptIfOtherThread(inputThread);
        interruptIfOtherThread(renderThread);
        joinIfOtherThread(inputThread);
        joinIfOtherThread(renderThread);

        if (window != null) {
            try {
                window.close();
            } catch (Throwable t) {
                Logger.log(Logger.TAG.WARN, "[A0019] ArtifactEditorEngine: window close failed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }

        state.clearForShutdown();
        window = null;
        inputThread = null;
        renderThread = null;
        stopping.set(false);
        Logger.log(Logger.TAG.SYSTEM, "ArtifactEditorEngine: editor stopped.");
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
                Logger.log(Logger.TAG.WARN, "[A0020] ArtifactEditorEngine: thread did not stop within timeout: "
                        + thread.getName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.log(Logger.TAG.WARN, "[A0021] ArtifactEditorEngine: interrupted while waiting for thread: "
                    + thread.getName());
        }
    }
}

package ucadmin.simulation;

import ucadmin.util.Logger;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SimulationInputController implements Runnable {
    private static final int MAX_QUEUED_EVENTS = 512;

    private final BlockingQueue<SimulationInputEvent> events = new ArrayBlockingQueue<>(MAX_QUEUED_EVENTS);
    private final AtomicBoolean running;
    private final SimulationConfig config;
    private final SimulationGame game;
    private final SimulationContext context;

    SimulationInputController(
            AtomicBoolean running,
            SimulationConfig config,
            SimulationGame game,
            SimulationContext context
    ) {
        this.running = running;
        this.config = config;
        this.game = game;
        this.context = context;
    }

    KeyAdapter createKeyListener() {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                enqueue(SimulationInputEvent.keyPressed(event.getKeyCode()));
            }

            @Override
            public void keyReleased(KeyEvent event) {
                enqueue(SimulationInputEvent.keyReleased(event.getKeyCode()));
            }

            @Override
            public void keyTyped(KeyEvent event) {
                enqueue(SimulationInputEvent.keyTyped(event.getKeyChar()));
            }
        };
    }

    MouseAdapter createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                enqueue(SimulationInputEvent.mousePressed(event.getX(), event.getY(), event.getButton()));
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                enqueue(SimulationInputEvent.mouseReleased(event.getX(), event.getY(), event.getButton()));
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                enqueue(SimulationInputEvent.mouseMoved(event.getX(), event.getY()));
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                enqueue(SimulationInputEvent.mouseDragged(event.getX(), event.getY(), event.getButton()));
            }
        };
    }

    private void enqueue(SimulationInputEvent event) {
        if (event == null) {
            return;
        }
        if (!events.offer(event)) {
            events.poll();
            events.offer(event);
        }
    }

    @Override
    public void run() {
        Logger.log(Logger.TAG.SYSTEM, "SimulationInputController: input thread started.");
        while (running.get()) {
            try {
                SimulationInputEvent event = events.poll(config.getInputPollMillis(), TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatch(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (running.get()) {
                    Logger.log(Logger.TAG.WARN, "[A0001] SimulationInputController: input thread interrupted while running.");
                }
                break;
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "[A0002] SimulationInputController: input dispatch failed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage());
                if (t instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (t instanceof Error error) {
                    throw error;
                }
                throw new RuntimeException(t);
            }
        }
        Logger.log(Logger.TAG.SYSTEM, "SimulationInputController: input thread stopped.");
    }

    private void dispatch(SimulationInputEvent event) {
        switch (event.type) {
            case KEY_PRESSED -> game.onKeyPressed(context, event.keyCode);
            case KEY_RELEASED -> game.onKeyReleased(context, event.keyCode);
            case KEY_TYPED -> game.onKeyTyped(context, event.keyChar);
            case MOUSE_PRESSED -> game.onMousePressed(context, event.x, event.y, event.button);
            case MOUSE_RELEASED -> game.onMouseReleased(context, event.x, event.y, event.button);
            case MOUSE_MOVED -> game.onMouseMoved(context, event.x, event.y);
            case MOUSE_DRAGGED -> game.onMouseDragged(context, event.x, event.y, event.button);
        }
    }
}

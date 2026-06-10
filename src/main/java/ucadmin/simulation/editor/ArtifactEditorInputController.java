package ucadmin.simulation.editor;

import ucadmin.util.Logger;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ArtifactEditorInputController implements Runnable {
    private static final int MAX_QUEUED_EVENTS = 512;

    private final BlockingQueue<ArtifactEditorInputEvent> events = new ArrayBlockingQueue<>(MAX_QUEUED_EVENTS);
    private final AtomicBoolean running;
    private final ArtifactEditorConfig config;
    private final ArtifactEditorState state;
    private final ArtifactEditorCanvas canvas;
    private ArtifactPoint pendingStart;
    private ArtifactEditorTool pendingTool;
    private final List<ArtifactPoint> polygonPoints = new ArrayList<>();
    private boolean occupiedDragActive;
    private boolean occupiedDragged;
    private ArtifactPoint lastCursorPoint;
    private int lastPanX;
    private int lastPanY;

    ArtifactEditorInputController(
            AtomicBoolean running,
            ArtifactEditorConfig config,
            ArtifactEditorState state,
            ArtifactEditorCanvas canvas
    ) {
        this.running = running;
        this.config = config;
        this.state = state;
        this.canvas = canvas;
    }

    MouseAdapter createMouseAdapter() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                canvas.requestFocusInWindow();
                if (event.getButton() == MouseEvent.BUTTON1) {
                    enqueue(ArtifactEditorInputEvent.leftPressed(event.getX(), event.getY()));
                } else if (event.getButton() == MouseEvent.BUTTON3) {
                    enqueue(ArtifactEditorInputEvent.rightPressed(event.getX(), event.getY()));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    enqueue(ArtifactEditorInputEvent.leftReleased(event.getX(), event.getY()));
                } else if (event.getButton() == MouseEvent.BUTTON3) {
                    enqueue(ArtifactEditorInputEvent.rightReleased(event.getX(), event.getY()));
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if ((event.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0) {
                    enqueue(ArtifactEditorInputEvent.rightDragged(event.getX(), event.getY()));
                } else if ((event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
                    enqueue(ArtifactEditorInputEvent.leftDragged(event.getX(), event.getY()));
                } else {
                    enqueue(ArtifactEditorInputEvent.mouseMoved(event.getX(), event.getY()));
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                enqueue(ArtifactEditorInputEvent.mouseMoved(event.getX(), event.getY()));
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                enqueue(ArtifactEditorInputEvent.wheel(event));
            }
        };
    }

    KeyAdapter createKeyAdapter() {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                enqueueKeyPress(event.getKeyCode(), event.isControlDown());
            }
        };
    }

    void enqueueKeyPress(int keyCode, boolean controlDown) {
        enqueue(ArtifactEditorInputEvent.keyPressed(keyCode, controlDown));
    }

    private void enqueue(ArtifactEditorInputEvent event) {
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
        Logger.log(Logger.TAG.SYSTEM, "ArtifactEditorInputController: input thread started.");
        while (running.get()) {
            try {
                ArtifactEditorInputEvent event = events.poll(config.getInputPollMillis(), TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatch(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (running.get()) {
                    Logger.log(Logger.TAG.WARN, "[A0012] ArtifactEditorInputController: input thread interrupted while running.");
                }
                break;
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "[A0013] ArtifactEditorInputController: input dispatch failed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage());
                throwRuntime(t);
            }
        }
        Logger.log(Logger.TAG.SYSTEM, "ArtifactEditorInputController: input thread stopped.");
    }

    private void dispatch(ArtifactEditorInputEvent event) {
        switch (event.type) {
            case LEFT_PRESSED -> handleLeftPress(event.x, event.y);
            case LEFT_DRAGGED -> handleLeftDragged(event.x, event.y);
            case LEFT_RELEASED -> handleLeftReleased(event.x, event.y);
            case RIGHT_PRESSED -> {
                lastPanX = event.x;
                lastPanY = event.y;
            }
            case RIGHT_DRAGGED -> {
                canvas.panBy(event.x - lastPanX, event.y - lastPanY);
                lastPanX = event.x;
                lastPanY = event.y;
            }
            case RIGHT_RELEASED -> {
                lastPanX = 0;
                lastPanY = 0;
            }
            case MOUSE_MOVED -> handleMouseMoved(event.x, event.y);
            case WHEEL -> canvas.zoomAt(event.wheelRotation, event.x, event.y);
            case KEY_PRESSED -> handleKeyPressed(event.keyCode, event.controlDown);
        }
        canvas.repaint();
    }

    private void handleKeyPressed(int keyCode, boolean controlDown) {
        if (controlDown && keyCode == KeyEvent.VK_Z) {
            state.undo();
            resetPendingInput();
            return;
        }
        if (controlDown && keyCode == KeyEvent.VK_Y) {
            state.redo();
            resetPendingInput();
            return;
        }
        if (controlDown && keyCode == KeyEvent.VK_C) {
            state.copySelection();
            return;
        }
        if (controlDown && keyCode == KeyEvent.VK_V) {
            ArtifactPoint target = lastCursorPoint == null ? state.getDraft().getCenter() : lastCursorPoint;
            state.pasteClipboardAt(target);
            resetPendingInput();
            return;
        }
        if (!controlDown && keyCode == KeyEvent.VK_R) {
            state.rotatePreviewClockwise();
            Logger.log(Logger.TAG.DEBUG, "ArtifactEditorInputController: rotated preview to "
                    + (state.getRotationPreviewTurns() * 90) + " degrees.");
            return;
        }
        if (!controlDown && keyCode == KeyEvent.VK_X) {
            state.toggleEraser();
            resetPendingInput();
            Logger.log(Logger.TAG.DEBUG, "ArtifactEditorInputController: toggled tool to "
                    + state.getActiveTool() + ".");
        }
    }

    private void handleLeftPress(int screenX, int screenY) {
        ArtifactPoint point = canvas.screenToArtifactPoint(screenX, screenY);
        ArtifactPoint cell = canvas.screenToArtifactCell(screenX, screenY);
        lastCursorPoint = point;
        ArtifactEditorTool tool = state.getActiveTool();
        clearPendingIfToolChanged(tool);

        switch (tool) {
            case SELECT -> {
                pendingStart = point;
                pendingTool = ArtifactEditorTool.SELECT;
                canvas.setPreview(point, point);
            }
            case CENTER -> {
                state.snapshotForUndo();
                state.getDraft().setCenter(point);
                state.clearSelection();
                pendingStart = null;
                pendingTool = null;
                canvas.clearPreview();
            }
            case WALL -> handleTwoPointWall(point);
            case OCCUPIED -> handleOccupiedPress(point);
            case OPENING -> handleTwoPointOpening(point);
            case ITEM -> {
                state.snapshotForUndo();
                boolean placed = state.getDraft().addWallMountedItem(state.getSelectedItemId(), cell);
                if (!placed) {
                    state.undo();
                    Logger.log(Logger.TAG.DEBUG, "ArtifactEditorInputController: item placement rejected; no touching wall.");
                } else {
                    state.clearSelection();
                }
                resetPendingInput();
            }
            case ERASER -> {
                state.snapshotForUndo();
                boolean erased = state.getDraft().eraseAt(point, cell);
                if (!erased) {
                    state.undo();
                } else {
                    state.clearSelection();
                }
                resetPendingInput();
            }
        }
    }

    private void handleLeftDragged(int screenX, int screenY) {
        ArtifactEditorTool tool = state.getActiveTool();
        lastCursorPoint = canvas.screenToArtifactPoint(screenX, screenY);
        if (tool == ArtifactEditorTool.SELECT && pendingTool == ArtifactEditorTool.SELECT && pendingStart != null) {
            canvas.setPreview(pendingStart, lastCursorPoint);
            return;
        }
        if (tool != ArtifactEditorTool.OCCUPIED || !occupiedDragActive || pendingStart == null) {
            handleMouseMoved(screenX, screenY);
            return;
        }
        occupiedDragged = true;
        canvas.setPreview(pendingStart, canvas.screenToArtifactPoint(screenX, screenY));
    }

    private void handleLeftReleased(int screenX, int screenY) {
        ArtifactEditorTool tool = state.getActiveTool();
        if (tool == ArtifactEditorTool.SELECT && pendingTool == ArtifactEditorTool.SELECT && pendingStart != null) {
            ArtifactPoint releasePoint = canvas.screenToArtifactPoint(screenX, screenY);
            lastCursorPoint = releasePoint;
            state.select(ArtifactSelectionBounds.fromPoints(pendingStart, releasePoint));
            resetPendingInput();
            return;
        }
        if (tool != ArtifactEditorTool.OCCUPIED || !occupiedDragActive || pendingStart == null) {
            return;
        }

        ArtifactPoint releasePoint = canvas.screenToArtifactPoint(screenX, screenY);
        if (occupiedDragged && ArtifactEditorGeometry.distanceSquared(pendingStart, releasePoint) > 0) {
            state.snapshotForUndo();
            state.getDraft().addOccupiedArea(ArtifactOccupiedArea.rectangle(pendingStart, releasePoint));
            state.clearSelection();
            resetPendingInput();
            return;
        }

        occupiedDragActive = false;
        occupiedDragged = false;
        handleOccupiedClick(pendingStart);
        pendingStart = null;
        pendingTool = null;
    }

    private void handleTwoPointWall(ArtifactPoint point) {
        if (pendingStart == null) {
            pendingStart = point;
            pendingTool = ArtifactEditorTool.WALL;
            canvas.setPreview(pendingStart, point);
            return;
        }
        state.snapshotForUndo();
        state.getDraft().addWall(new ArtifactWall(pendingStart, point));
        state.clearSelection();
        resetPendingInput();
    }

    private void handleTwoPointOpening(ArtifactPoint point) {
        if (pendingStart == null) {
            pendingStart = point;
            pendingTool = ArtifactEditorTool.OPENING;
            canvas.setPreview(pendingStart, point);
            return;
        }
        state.snapshotForUndo();
        state.getDraft().addOpening(new ArtifactOpening(
                pendingStart,
                directionFromDelta(pendingStart, point),
                1
        ));
        state.clearSelection();
        resetPendingInput();
    }

    private void handleOccupiedPress(ArtifactPoint point) {
        pendingStart = point;
        pendingTool = ArtifactEditorTool.OCCUPIED;
        occupiedDragActive = true;
        occupiedDragged = false;
        canvas.setPreview(point, point);
    }

    private void handleOccupiedClick(ArtifactPoint point) {
        if (!polygonPoints.isEmpty() &&
                polygonPoints.size() >= 3 &&
                ArtifactEditorGeometry.distanceSquared(polygonPoints.get(0), point) <= 4) {
            state.snapshotForUndo();
            state.getDraft().addOccupiedArea(new ArtifactOccupiedArea(polygonPoints));
            state.clearSelection();
            polygonPoints.clear();
            canvas.clearPolygonPreview();
            canvas.clearPreview();
            return;
        }

        polygonPoints.add(point);
        canvas.setPolygonPreview(polygonPoints, null);
    }

    private void handleMouseMoved(int screenX, int screenY) {
        ArtifactEditorTool tool = state.getActiveTool();
        lastCursorPoint = canvas.screenToArtifactPoint(screenX, screenY);
        clearPendingIfToolChanged(tool);
        if (tool == ArtifactEditorTool.OCCUPIED && !polygonPoints.isEmpty()) {
            canvas.setPolygonPreview(polygonPoints, lastCursorPoint);
            return;
        }
        if (pendingStart == null) return;
        if (tool != ArtifactEditorTool.SELECT &&
                tool != ArtifactEditorTool.WALL &&
                tool != ArtifactEditorTool.OPENING) return;
        canvas.setPreview(pendingStart, lastCursorPoint);
    }

    private void clearPendingIfToolChanged(ArtifactEditorTool activeTool) {
        if (pendingTool != null && pendingTool != activeTool) {
            pendingStart = null;
            pendingTool = null;
            polygonPoints.clear();
            occupiedDragActive = false;
            occupiedDragged = false;
            canvas.clearPreview();
            canvas.clearPolygonPreview();
        }
    }

    private void resetPendingInput() {
        pendingStart = null;
        pendingTool = null;
        polygonPoints.clear();
        occupiedDragActive = false;
        occupiedDragged = false;
        canvas.clearPreview();
        canvas.clearPolygonPreview();
    }

    private ArtifactDirection directionFromDelta(ArtifactPoint start, ArtifactPoint end) {
        int dx = end.x() - start.x();
        int dy = end.y() - start.y();
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx >= 0 ? ArtifactDirection.EAST : ArtifactDirection.WEST;
        }
        return dy >= 0 ? ArtifactDirection.SOUTH : ArtifactDirection.NORTH;
    }

    private void throwRuntime(Throwable t) {
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(t);
    }
}

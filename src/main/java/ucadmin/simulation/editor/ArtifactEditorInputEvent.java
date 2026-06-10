package ucadmin.simulation.editor;

import java.awt.event.MouseWheelEvent;

final class ArtifactEditorInputEvent {
    enum Type {
        LEFT_PRESSED,
        LEFT_DRAGGED,
        LEFT_RELEASED,
        RIGHT_PRESSED,
        RIGHT_DRAGGED,
        RIGHT_RELEASED,
        MOUSE_MOVED,
        WHEEL,
        KEY_PRESSED
    }

    final Type type;
    final int x;
    final int y;
    final int wheelRotation;
    final int keyCode;
    final boolean controlDown;

    private ArtifactEditorInputEvent(Type type, int x, int y, int wheelRotation, int keyCode, boolean controlDown) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.wheelRotation = wheelRotation;
        this.keyCode = keyCode;
        this.controlDown = controlDown;
    }

    static ArtifactEditorInputEvent leftPressed(int x, int y) {
        return new ArtifactEditorInputEvent(Type.LEFT_PRESSED, x, y, 0, 0, false);
    }

    static ArtifactEditorInputEvent leftDragged(int x, int y) {
        return new ArtifactEditorInputEvent(Type.LEFT_DRAGGED, x, y, 0, 0, false);
    }

    static ArtifactEditorInputEvent leftReleased(int x, int y) {
        return new ArtifactEditorInputEvent(Type.LEFT_RELEASED, x, y, 0, 0, false);
    }

    static ArtifactEditorInputEvent rightPressed(int x, int y) {
        return new ArtifactEditorInputEvent(Type.RIGHT_PRESSED, x, y, 0, 0, false);
    }

    static ArtifactEditorInputEvent rightDragged(int x, int y) {
        return new ArtifactEditorInputEvent(Type.RIGHT_DRAGGED, x, y, 0, 0, false);
    }

    static ArtifactEditorInputEvent rightReleased(int x, int y) {
        return new ArtifactEditorInputEvent(Type.RIGHT_RELEASED, x, y, 0, 0, false);
    }

    static ArtifactEditorInputEvent mouseMoved(int x, int y) {
        return new ArtifactEditorInputEvent(Type.MOUSE_MOVED, x, y, 0, 0, false);
    }

    static ArtifactEditorInputEvent wheel(MouseWheelEvent event) {
        return new ArtifactEditorInputEvent(
                Type.WHEEL,
                event.getX(),
                event.getY(),
                event.getWheelRotation(),
                0,
                false
        );
    }

    static ArtifactEditorInputEvent keyPressed(int keyCode, boolean controlDown) {
        return new ArtifactEditorInputEvent(Type.KEY_PRESSED, 0, 0, 0, keyCode, controlDown);
    }
}

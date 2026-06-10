package ucadmin.simulation;

final class SimulationInputEvent {
    enum Type {
        KEY_PRESSED,
        KEY_RELEASED,
        KEY_TYPED,
        MOUSE_PRESSED,
        MOUSE_RELEASED,
        MOUSE_MOVED,
        MOUSE_DRAGGED
    }

    final Type type;
    final int keyCode;
    final char keyChar;
    final int x;
    final int y;
    final int button;

    private SimulationInputEvent(Type type, int keyCode, char keyChar, int x, int y, int button) {
        this.type = type;
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.x = x;
        this.y = y;
        this.button = button;
    }

    static SimulationInputEvent keyPressed(int keyCode) {
        return new SimulationInputEvent(Type.KEY_PRESSED, keyCode, '\0', 0, 0, 0);
    }

    static SimulationInputEvent keyReleased(int keyCode) {
        return new SimulationInputEvent(Type.KEY_RELEASED, keyCode, '\0', 0, 0, 0);
    }

    static SimulationInputEvent keyTyped(char keyChar) {
        return new SimulationInputEvent(Type.KEY_TYPED, 0, keyChar, 0, 0, 0);
    }

    static SimulationInputEvent mousePressed(int x, int y, int button) {
        return new SimulationInputEvent(Type.MOUSE_PRESSED, 0, '\0', x, y, button);
    }

    static SimulationInputEvent mouseReleased(int x, int y, int button) {
        return new SimulationInputEvent(Type.MOUSE_RELEASED, 0, '\0', x, y, button);
    }

    static SimulationInputEvent mouseMoved(int x, int y) {
        return new SimulationInputEvent(Type.MOUSE_MOVED, 0, '\0', x, y, 0);
    }

    static SimulationInputEvent mouseDragged(int x, int y, int button) {
        return new SimulationInputEvent(Type.MOUSE_DRAGGED, 0, '\0', x, y, button);
    }
}

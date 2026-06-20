package sora.simulation.editor;

import sora.simulation.items.DungeonItemLibrary;

import java.util.ArrayDeque;
import java.util.Deque;

final class ArtifactEditorState {
    private static final int MAX_UNDO_HISTORY = 5;

    private ArtifactTemplateDraft draft = new ArtifactTemplateDraft();
    private ArtifactEditorTool activeTool = ArtifactEditorTool.WALL;
    private ArtifactEditorTool previousNonEraserTool = ArtifactEditorTool.WALL;
    private String selectedItemId = DungeonItemLibrary.WALL_LIGHT;
    private ArtifactSelection selection;
    private ArtifactSelection clipboard;
    private int rotationPreviewTurns;
    private final Deque<ArtifactTemplateDraft> undo = new ArrayDeque<>();
    private final Deque<ArtifactTemplateDraft> redo = new ArrayDeque<>();

    ArtifactTemplateDraft getDraft() { return draft; }
    ArtifactEditorTool getActiveTool() { return activeTool; }
    ArtifactSelection getSelection() { return selection; }
    String getSelectedItemId() { return selectedItemId; }
    int getRotationPreviewTurns() { return rotationPreviewTurns; }

    void setActiveTool(ArtifactEditorTool activeTool) {
        ArtifactEditorTool nextTool = activeTool == null ? ArtifactEditorTool.WALL : activeTool;
        if (nextTool != ArtifactEditorTool.ERASER) {
            previousNonEraserTool = nextTool;
        }
        this.activeTool = nextTool;
    }

    void toggleEraser() {
        if (activeTool == ArtifactEditorTool.ERASER) {
            activeTool = previousNonEraserTool == null ? ArtifactEditorTool.WALL : previousNonEraserTool;
            return;
        }
        previousNonEraserTool = activeTool;
        activeTool = ArtifactEditorTool.ERASER;
    }

    void setName(String name) {
        draft.setName(name);
    }

    void setCategory(int category) {
        draft.setCategory(category);
    }

    void setSelectedItemId(String selectedItemId) {
        if (selectedItemId != null && !selectedItemId.isBlank()) {
            this.selectedItemId = selectedItemId.trim();
        }
    }

    void loadDraft(ArtifactTemplateDraft draft) {
        if (draft == null) return;
        this.draft = draft;
        rotationPreviewTurns = 0;
        selection = null;
        undo.clear();
        redo.clear();
    }

    void rotatePreviewClockwise() {
        rotationPreviewTurns = (rotationPreviewTurns + 1) % 4;
    }

    void snapshotForUndo() {
        undo.push(draft.copy());
        trimToMax(undo);
        redo.clear();
    }

    void undo() {
        if (undo.isEmpty()) return;
        redo.push(draft.copy());
        trimToMax(redo);
        draft = undo.pop();
    }

    void redo() {
        if (redo.isEmpty()) return;
        undo.push(draft.copy());
        trimToMax(undo);
        draft = redo.pop();
    }

    void select(ArtifactSelectionBounds bounds) {
        selection = draft.selectWithin(bounds);
        if (selection != null && selection.isEmpty()) {
            selection = null;
        }
    }

    void clearSelection() {
        selection = null;
    }

    void copySelection() {
        if (selection != null && !selection.isEmpty()) {
            clipboard = selection;
        }
    }

    void pasteClipboardAt(ArtifactPoint target) {
        if (clipboard == null || clipboard.isEmpty() || target == null) {
            return;
        }
        snapshotForUndo();
        ArtifactSelection pasted = clipboard.translateNear(target);
        draft.addSelection(pasted);
        selection = pasted;
    }

    void clearForShutdown() {
        draft = new ArtifactTemplateDraft();
        activeTool = ArtifactEditorTool.WALL;
        previousNonEraserTool = ArtifactEditorTool.WALL;
        selectedItemId = DungeonItemLibrary.WALL_LIGHT;
        selection = null;
        clipboard = null;
        rotationPreviewTurns = 0;
        undo.clear();
        redo.clear();
    }

    private void trimToMax(Deque<ArtifactTemplateDraft> history) {
        while (history.size() > MAX_UNDO_HISTORY) {
            history.removeLast();
        }
    }
}

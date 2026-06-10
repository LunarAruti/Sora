package ucadmin.simulation.editor;

import java.util.ArrayDeque;
import java.util.Deque;

final class ArtifactEditorState {
    private static final int MAX_UNDO_HISTORY = 5;

    private ArtifactTemplateDraft draft = new ArtifactTemplateDraft();
    private ArtifactEditorTool activeTool = ArtifactEditorTool.WALL;
    private int rotationPreviewTurns;
    private final Deque<ArtifactTemplateDraft> undo = new ArrayDeque<>();
    private final Deque<ArtifactTemplateDraft> redo = new ArrayDeque<>();

    ArtifactTemplateDraft getDraft() { return draft; }
    ArtifactEditorTool getActiveTool() { return activeTool; }
    int getRotationPreviewTurns() { return rotationPreviewTurns; }

    void setActiveTool(ArtifactEditorTool activeTool) {
        this.activeTool = activeTool == null ? ArtifactEditorTool.WALL : activeTool;
    }

    void setName(String name) {
        draft.setName(name);
    }

    void setSpawnProbability(int probability) {
        draft.setSpawnProbability(probability);
    }

    void loadDraft(ArtifactTemplateDraft draft) {
        if (draft == null) return;
        this.draft = draft;
        rotationPreviewTurns = 0;
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

    void clearForShutdown() {
        draft = new ArtifactTemplateDraft();
        activeTool = ArtifactEditorTool.WALL;
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

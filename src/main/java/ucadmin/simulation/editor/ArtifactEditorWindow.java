package ucadmin.simulation.editor;

import ucadmin.exceptions.DatabaseException;
import ucadmin.util.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

final class ArtifactEditorWindow {
    private final ArtifactEditorConfig config;
    private final ArtifactEditorState state;
    private final ArtifactEditorCanvas canvas;
    private final JFrame frame;
    private final JTextField nameField = new JTextField("untitled");
    private final JTextField probabilityField = new JTextField("1");

    ArtifactEditorWindow(
            ArtifactEditorConfig config,
            ArtifactEditorState state,
            ArtifactEditorCanvas canvas,
            ArtifactEditorInputController inputController,
            Runnable closeAction
    ) {
        this.config = config;
        this.state = state;
        this.canvas = canvas;
        this.frame = new JFrame(config.getTitle());

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (closeAction != null) {
                    closeAction.run();
                }
            }
        });
        frame.setPreferredSize(new Dimension(config.getWidth(), config.getHeight()));
        frame.setLayout(new BorderLayout());
        frame.add(buildPanel(), BorderLayout.WEST);
        frame.add(canvas, BorderLayout.CENTER);
        frame.add(buildControlsStrip(), BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);

        var mouseAdapter = inputController.createMouseAdapter();
        canvas.addMouseListener(mouseAdapter);
        canvas.addMouseMotionListener(mouseAdapter);
        canvas.addMouseWheelListener(mouseAdapter);
        canvas.addKeyListener(inputController.createKeyAdapter());
        installEditorKeyBindings();
    }

    void showWindow() {
        frame.setVisible(true);
        canvas.requestFocusInWindow();
    }

    void renderFrame() {
        canvas.repaint();
    }

    void close() {
        frame.setVisible(false);
        frame.dispose();
    }

    private JPanel buildPanel() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(210, config.getHeight()));
        panel.setMinimumSize(new Dimension(210, 0));
        panel.setMaximumSize(new Dimension(210, Integer.MAX_VALUE));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(36, 38, 43));

        configureCompactField(nameField, 120);
        configureCompactField(probabilityField, 42);
        panel.add(buildMetadataPanel());
        panel.add(Box.createVerticalStrut(8));

        panel.add(label("Tools"));
        ButtonGroup tools = new ButtonGroup();
        JPanel toolPanel = compactPanel();
        addToolButton(toolPanel, tools, "Center", ArtifactEditorTool.CENTER);
        addToolButton(toolPanel, tools, "Wall", ArtifactEditorTool.WALL);
        addToolButton(toolPanel, tools, "Opening", ArtifactEditorTool.OPENING);
        addToolButton(toolPanel, tools, "Occupied", ArtifactEditorTool.OCCUPIED);
        addToolButton(toolPanel, tools, "Eraser", ArtifactEditorTool.ERASER);
        panel.add(toolPanel);
        panel.add(Box.createVerticalStrut(8));

        JPanel actionPanel = compactPanel();
        JButton undoButton = smallButton("Undo");
        undoButton.addActionListener(e -> performUndo());
        actionPanel.add(undoButton);

        JButton redoButton = smallButton("Redo");
        redoButton.addActionListener(e -> performRedo());
        actionPanel.add(redoButton);

        JButton saveButton = smallButton("Save");
        saveButton.addActionListener(e -> saveArtifact());
        actionPanel.add(saveButton);

        JButton loadButton = smallButton("Load");
        loadButton.addActionListener(e -> loadArtifact());
        actionPanel.add(loadButton);

        JButton newButton = smallButton("New");
        newButton.addActionListener(e -> newArtifact());
        actionPanel.add(newButton);

        JButton deleteButton = smallButton("Delete");
        deleteButton.addActionListener(e -> deleteArtifact());
        actionPanel.add(deleteButton);
        panel.add(actionPanel);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JPanel buildMetadataPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(label("Name"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(label("Prob"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(probabilityField, gbc);

        return panel;
    }

    private JLabel buildControlsStrip() {
        JLabel label = new JLabel(
                "LMB draw/place   LMB drag occupied rect   RMB pan   Wheel zoom   R rotate   Ctrl+Z/Y undo/redo"
        );
        label.setForeground(new Color(190, 194, 202));
        label.setBackground(new Color(28, 30, 34));
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        return label;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setForeground(new Color(210, 214, 220));
        return label;
    }

    private JPanel compactPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        panel.setOpaque(false);
        return panel;
    }

    private JButton smallButton(String text) {
        JButton button = new JButton(text);
        button.setMargin(new java.awt.Insets(2, 6, 2, 6));
        return button;
    }

    private void configureCompactField(JTextField field, int width) {
        Dimension size = new Dimension(width, 24);
        field.setPreferredSize(size);
        field.setMinimumSize(size);
        field.setMaximumSize(size);
    }

    private void installEditorKeyBindings() {
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
                "artifactEditorUndo"
        );
        frame.getRootPane().getActionMap().put("artifactEditorUndo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (canvasHasFocus()) {
                    return;
                }
                performUndo();
            }
        });

        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
                "artifactEditorRedo"
        );
        frame.getRootPane().getActionMap().put("artifactEditorRedo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (canvasHasFocus()) {
                    return;
                }
                performRedo();
            }
        });
    }

    private boolean canvasHasFocus() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner == canvas;
    }

    private void performUndo() {
        state.undo();
        refreshFieldsFromDraft();
        canvas.clearPreview();
        canvas.clearPolygonPreview();
        canvas.repaint();
    }

    private void performRedo() {
        state.redo();
        refreshFieldsFromDraft();
        canvas.clearPreview();
        canvas.clearPolygonPreview();
        canvas.repaint();
    }

    private void addToolButton(
            JPanel panel,
            ButtonGroup group,
            String label,
            ArtifactEditorTool tool
    ) {
        JRadioButton button = new JRadioButton(label);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(36, 38, 43));
        button.setSelected(state.getActiveTool() == tool);
        button.addActionListener(e -> {
            state.setActiveTool(tool);
            canvas.clearPreview();
            canvas.clearPolygonPreview();
            canvas.repaint();
        });
        group.add(button);
        panel.add(button);
    }

    private void saveArtifact() {
        state.setName(nameField.getText());
        state.setSpawnProbability(readProbability());
        List<String> blockingIssues = state.getDraft().validateBlockingSaveIssues();
        if (!blockingIssues.isEmpty()) {
            JOptionPane.showMessageDialog(
                    frame,
                    String.join(System.lineSeparator(), blockingIssues),
                    "Cannot Save Artifact",
                    JOptionPane.ERROR_MESSAGE
            );
            Logger.log(Logger.TAG.WARN, "[A0033] ArtifactEditorWindow: save blocked: "
                    + String.join(" ", blockingIssues));
            return;
        }
        if (!confirmOverwriteIfNeeded()) {
            return;
        }

        List<String> warnings = state.getDraft().validateForSave();
        if (!warnings.isEmpty()) {
            int result = JOptionPane.showConfirmDialog(
                    frame,
                    String.join(System.lineSeparator(), warnings)
                            + System.lineSeparator()
                            + System.lineSeparator()
                            + "Save anyway?",
                    "Artifact Warnings",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (result != JOptionPane.YES_OPTION) {
                Logger.log(Logger.TAG.INFO, "ArtifactEditorWindow: save cancelled after validation warning.");
                return;
            }
        }

        try {
            ArtifactStore.save(
                    state.getDraft(),
                    config.getCanvasCellsWide(),
                    config.getCanvasCellsHigh()
            );
            JOptionPane.showMessageDialog(
                    frame,
                    "Saved artifact to " + ArtifactStore.getArtifactFilePath(),
                    "Saved",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (DatabaseException ex) {
            Logger.log(Logger.TAG.ERROR, "[A0014] ArtifactEditorWindow: save failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                    frame,
                    ex.getMessage(),
                    "Save Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private boolean confirmOverwriteIfNeeded() {
        try {
            String existingId = ArtifactStore.findArtifactIdByName(state.getDraft().getName());
            if (existingId == null || existingId.equals(state.getDraft().getId())) {
                return true;
            }

            int result = JOptionPane.showConfirmDialog(
                    frame,
                    "An artifact named '" + state.getDraft().getName() + "' already exists."
                            + System.lineSeparator()
                            + "Overwrite it?",
                    "Overwrite Artifact",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.YES_OPTION) {
                ArtifactStore.deleteById(existingId, state.getDraft().getName());
                return true;
            }
            Logger.log(Logger.TAG.INFO, "ArtifactEditorWindow: save cancelled before overwrite.");
            return false;
        } catch (DatabaseException ex) {
            Logger.log(Logger.TAG.ERROR, "[A0026] ArtifactEditorWindow: overwrite check failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                    frame,
                    ex.getMessage(),
                    "Overwrite Check Failed",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
    }

    private int readProbability() {
        try {
            return Integer.parseInt(probabilityField.getText().trim());
        } catch (NumberFormatException e) {
            Logger.log(Logger.TAG.WARN, "[A0027] ArtifactEditorWindow: invalid probability, defaulting to 1.");
            probabilityField.setText("1");
            return 1;
        }
    }

    private void loadArtifact() {
        String idOrName = JOptionPane.showInputDialog(
                frame,
                "Artifact id or name:",
                "Load Artifact",
                JOptionPane.QUESTION_MESSAGE
        );
        if (idOrName == null || idOrName.isBlank()) {
            Logger.log(Logger.TAG.INFO, "ArtifactEditorWindow: load cancelled.");
            return;
        }

        try {
            ArtifactTemplateDraft draft = ArtifactStore.load(
                    idOrName,
                    config.getCanvasCellsWide(),
                    config.getCanvasCellsHigh()
            );
            state.loadDraft(draft);
            refreshFieldsFromDraft();
            canvas.clearPreview();
            canvas.clearPolygonPreview();
            canvas.repaint();
            Logger.log(Logger.TAG.INFO, "ArtifactEditorWindow: loaded artifact id=" + draft.getId()
                    + " name=" + draft.getName());
        } catch (DatabaseException ex) {
            Logger.log(Logger.TAG.ERROR, "[A0025] ArtifactEditorWindow: load failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                    frame,
                    ex.getMessage(),
                    "Load Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void deleteArtifact() {
        ArtifactTemplateDraft draft = state.getDraft();
        int result = JOptionPane.showConfirmDialog(
                frame,
                "Delete artifact '" + draft.getName() + "'?",
                "Delete Artifact",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            Logger.log(Logger.TAG.INFO, "ArtifactEditorWindow: delete cancelled.");
            return;
        }

        try {
            ArtifactStore.delete(draft);
            state.loadDraft(new ArtifactTemplateDraft());
            refreshFieldsFromDraft();
            canvas.clearPreview();
            canvas.clearPolygonPreview();
            canvas.repaint();
            Logger.log(Logger.TAG.INFO, "ArtifactEditorWindow: deleted artifact id=" + draft.getId());
        } catch (DatabaseException ex) {
            Logger.log(Logger.TAG.ERROR, "[A0029] ArtifactEditorWindow: delete failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                    frame,
                    ex.getMessage(),
                    "Delete Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void newArtifact() {
        state.loadDraft(new ArtifactTemplateDraft());
        refreshFieldsFromDraft();
        canvas.clearPreview();
        canvas.clearPolygonPreview();
        canvas.repaint();
        Logger.log(Logger.TAG.INFO, "ArtifactEditorWindow: new artifact draft created.");
    }

    private void refreshFieldsFromDraft() {
        nameField.setText(state.getDraft().getName());
        probabilityField.setText(String.valueOf(state.getDraft().getSpawnProbability()));
    }
}

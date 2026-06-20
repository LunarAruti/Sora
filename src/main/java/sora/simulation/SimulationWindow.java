package sora.simulation;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

final class SimulationWindow {
    private final SimulationConfig config;
    private final SimulationGame game;
    private final SimulationContext context;
    private final JFrame frame;
    private final JPanel panel;

    SimulationWindow(
            SimulationConfig config,
            SimulationGame game,
            SimulationContext context,
            SimulationInputController inputController,
            Runnable closeAction
    ) {
        this.config = config;
        this.game = game;
        this.context = context;
        this.frame = new JFrame(config.getTitle());
        this.panel = new SimulationPanel();

        panel.setPreferredSize(new Dimension(config.getWidth(), config.getHeight()));
        panel.setFocusable(true);
        panel.setFocusTraversalKeysEnabled(false);
        panel.setBackground(config.getBackgroundColor());
        panel.addKeyListener(inputController.createKeyListener());
        panel.addMouseListener(inputController.createMouseListener());
        panel.addMouseMotionListener(inputController.createMouseListener());

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (config.isExitOnWindowClose() && closeAction != null) {
                    closeAction.run();
                }
            }
        });
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    void showWindow() {
        frame.setVisible(true);
        panel.requestFocusInWindow();
    }

    void renderFrame() {
        panel.repaint();
    }

    void close() {
        frame.setVisible(false);
        frame.dispose();
    }

    private final class SimulationPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!(graphics instanceof Graphics2D g)) {
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color oldColor = g.getColor();
            g.setColor(config.getBackgroundColor());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(oldColor);
            game.render(context, g);
        }
    }
}

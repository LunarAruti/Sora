package ucadmin.main;

import ucadmin.simulation.MazeSimulationGame;
import ucadmin.simulation.editor.ArtifactEditorEngine;
import ucadmin.util.Logger;
import ucadmin.util.ShutdownManager;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TestingGrounds {
    private static final int TEST_MODE = 1;

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== TESTING GROUNDS ===");

        try {
            switch (TEST_MODE) {
                case 1 -> {
                    Logger.log(Logger.TAG.SYSTEM, "TestingGrounds: starting maze simulation functionality test.");

                    MazeSimulationGame game = new MazeSimulationGame(false);

                    Logger.log(Logger.TAG.INFO, "TestingGrounds: launching maze simulation seed=" + game.getSeed());
                    boolean started = game.run();
                    if (!started) {
                        throw new IllegalStateException("TestingGrounds: maze simulation failed to start.");
                    }

                    while (game.isRunning()) {
                        Thread.sleep(250L);
                    }

                    Logger.log(Logger.TAG.INFO, "TestingGrounds: maze simulation ended; continuing to shutdown.");
                }
                case 2 -> {
                    Logger.log(Logger.TAG.SYSTEM, "TestingGrounds: starting artifact editor functionality test.");

                    ArtifactEditorEngine editor = new ArtifactEditorEngine();

                    Logger.log(Logger.TAG.INFO, "TestingGrounds: launching artifact editor.");
                    boolean started = editor.run();
                    if (!started) {
                        throw new IllegalStateException("TestingGrounds: artifact editor failed to start.");
                    }

                    while (editor.isRunning()) {
                        Thread.sleep(250L);
                    }

                    Logger.log(Logger.TAG.INFO, "TestingGrounds: artifact editor ended; continuing to shutdown.");
                }
                default -> throw new IllegalArgumentException("TestingGrounds: unsupported TEST_MODE=" + TEST_MODE);
            }

        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR, "[0011] TestingGrounds crash: " + t);
            logThrowableTrace("[0011] TestingGrounds stack trace", t);
        }
        ShutdownManager.shutdown(null);

    }

    /**
     * Logs the full throwable, its cause chain, and its stack trace so test
     * failures are diagnosable from the log output alone.
     *
     * @param heading top-level heading for the logged trace
     * @param throwable thrown failure
     */
    private static void logThrowableTrace(String heading, Throwable throwable) {
        if (throwable == null) {
            Logger.log(Logger.TAG.ERROR, heading + ": <null throwable>");
            return;
        }

        Throwable current = throwable;
        int depth = 0;
        while (current != null) {
            Logger.log(
                    Logger.TAG.ERROR,
                    heading + " cause[" + depth + "]: " +
                            current.getClass().getName() +
                            " | message=" + current.getMessage()
            );
            current = current.getCause();
            depth++;
        }

        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        Logger.log(Logger.TAG.ERROR, heading + System.lineSeparator() + writer);
        Logger.logDump(heading, throwable);
    }
}

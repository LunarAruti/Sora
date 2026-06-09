package ucadmin.main;

import ucadmin.tools.dpcode.DPCode;
import ucadmin.tools.dpcode.DPCodeReader;
import ucadmin.tools.dpcode.DPDensityMode;
import ucadmin.tools.pixelgenerator.PixelArt;
import ucadmin.tools.pixelgenerator.PixelGenerator;
import ucadmin.tools.pixelgenerator.PixelGridBuilder;
import ucadmin.tools.pixelgenerator.PixelImageQuantizer;
import ucadmin.tools.qrgenerator.QrEncoder;
import ucadmin.util.Logger;
import ucadmin.util.ShutdownManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ucadmin.tools.pixelgenerator.PixelImageQuantizer.quantize;

public class TestingGrounds {

    private static final Path TEST_OUTPUT_DIR = Paths.get(
            "C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database"
    );

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== TESTING GROUNDS ===");

        try {
            Logger.log(Logger.TAG.SYSTEM, "TestingGrounds: starting maze simulation functionality test.");

            ucadmin.simulation.SimulationConfig config = new ucadmin.simulation.SimulationConfig(
                    "UC Maze Simulation Test",
                    1000,
                    1000,
                    60,
                    new java.awt.Color(55, 55, 55),
                    50L,
                    false
            );

            ucadmin.simulation.SimulationEngine engine = new ucadmin.simulation.SimulationEngine(
                    config,
                    new ucadmin.simulation.MazeSimulationGame()
            );

            Logger.log(Logger.TAG.INFO, "TestingGrounds: launching simulation via blocking run().");
            engine.run();
            Logger.log(Logger.TAG.INFO, "TestingGrounds: simulation ended; continuing to shutdown.");

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

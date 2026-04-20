package ucadmin.main;

import ucadmin.tools.pixelgenerator.PixelArt;
import ucadmin.tools.pixelgenerator.PixelGenerator;
import ucadmin.tools.pixelgenerator.PixelImageQuantizer;
import ucadmin.tools.qrgenerator.QrEncoder;
import ucadmin.util.Logger;
import ucadmin.util.ShutdownManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ucadmin.tools.pixelgenerator.PixelImageQuantizer.quantize;

public class TestingGrounds {

    private static final Path TEST_OUTPUT_DIR = Paths.get(
            "C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database"
    );

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== QR GENERATOR TESTING GROUNDS ===");

        try {

            QrEncoder.encode("https://www.youtube.com/watch?v=oHg5SJYRHA0", QrEncoder.QrMode.BYTE, QrEncoder.ErrorCorrectionLevel.L, QrEncoder.TextEncoding.UTF_8,"C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database\\qr.png");

        } catch (Throwable t) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0011] QR TestingGrounds crash: " + t
            );
            logThrowableTrace("[0011] QR TestingGrounds stack trace", t);
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

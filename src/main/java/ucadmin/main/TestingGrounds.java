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

            DPCode code = new DPCode("C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database\\codeString.txt",true);
            code.setBootstrapProfile(DPCode.BootstrapProfile.STANDARD_V1);
            code.setOutputPath("C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database\\code.png");
            code.setImageSizePx(1200);
            code.setPayloadMode(DPCode.PayloadMode.RAW_BYTES);
            code.setDensityMode(DPDensityMode.D256);
            code.setPreprocessMode(DPCode.PreprocessMode.NONE);
            code.setEccProfile(DPCode.EccProfile.MEDIUM);
            code.setPayloadCharset(StandardCharsets.UTF_8);
            code.encode();

            DPCodeReader.ReadResult result = DPCodeReader.read("C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database\\code.png");
            String textAfter = result.getPayloadAsString(StandardCharsets.UTF_8);
            int errors = result.getErrors();
            boolean valid = result.isPayloadVerified();
            int size = result.getPayloadLength();
            int grid = result.getLogicalSize();

            System.out.println("logical " + grid);
            System.out.println("size " + size);
            System.out.println("valid " + valid);
            System.out.println("errors " + errors);
            System.out.println(textAfter);

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

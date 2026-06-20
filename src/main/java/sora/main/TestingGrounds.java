package sora.main;

import sora.tools.dpcode.DPDensityMode;
import sora.tools.pixelgenerator.PixelImageQuantizer;
import sora.util.Logger;
import sora.util.ShutdownManager;
import sora.tools.dpcode.DPCode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class TestingGrounds {
    private static final int TEST_MODE = 1;

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== TESTING GROUNDS ===");

        try {
            sora.tools.pixelgenerator.PixelGridBuilder builder =
                    new sora.tools.pixelgenerator.PixelGridBuilder(
                            100,
                            150,
                            1200,
                            1800,
                            "C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database\\IMG_2484_manual_100w_retry.png",
                            0,
                            null
                    );

            builder.fillBackground("white");

            builder.fillRect(41, 2, 44, 5, "powderpink");
            builder.fillRect(66, 10, 69, 13, "powderpink");
            builder.fillRect(74, 8, 78, 14, "blushpink");
            builder.fillRect(84, 10, 87, 15, "lightpink");
            builder.fillRect(88, 18, 93, 28, "lightpink");
            builder.fillRect(11, 20, 18, 27, "mistyrose");
            builder.fillRect(24, 8, 29, 13, "mistyrose");

            builder.fillRect(0, 28, 34, 60, "seashell");
            builder.fillRect(0, 32, 9, 46, "white");
            builder.fillRect(0, 50, 10, 60, "white");
            builder.fillRect(6, 29, 23, 40, "mistyrose");
            builder.fillRect(7, 47, 23, 56, "blushpink");
            builder.fillRect(3, 49, 14, 60, "mulberryred");
            builder.fillRect(15, 53, 33, 60, "maroon");
            builder.drawLine(0, 50, 10, 44, "palevioletred");
            builder.drawLine(3, 59, 17, 60, "wineberry");
            builder.drawLine(15, 60, 33, 52, "wineberry");
            builder.drawLine(12, 27, 32, 20, "blushpink");
            builder.drawLine(26, 20, 34, 29, "rosybrown");
            builder.drawLine(24, 29, 31, 40, "white");
            builder.drawLine(22, 41, 27, 47, "beige");

            builder.fillRect(26, 12, 73, 61, "maroon");
            builder.fillRect(37, 24, 68, 72, "currant");
            builder.fillRect(39, 17, 50, 73, "palevioletred");
            builder.fillRect(43, 20, 49, 63, "indianred");
            builder.fillRect(29, 18, 34, 44, "wineberry");
            builder.fillRect(60, 21, 72, 50, "wineberry");
            builder.fillRect(67, 37, 77, 55, "dustypink");
            builder.drawLine(30, 13, 38, 29, "wineberry");
            builder.drawLine(41, 9, 36, 28, "wineberry");
            builder.drawLine(50, 10, 62, 8, "wineberry");
            builder.drawLine(63, 8, 81, 18, "wineberry");
            builder.drawLine(79, 19, 90, 33, "wineberry");
            builder.drawLine(89, 34, 76, 50, "wineberry");
            builder.drawLine(24, 63, 36, 48, "wineberry");
            builder.drawLine(31, 73, 40, 58, "wineberry");
            builder.drawLine(50, 73, 59, 56, "wineberry");
            builder.drawLine(63, 72, 74, 56, "wineberry");
            builder.drawLine(27, 26, 34, 34, "white");
            builder.drawLine(61, 54, 70, 61, "lightpink");
            builder.drawLine(46, 15, 51, 30, "lightpink");
            builder.fillRect(30, 35, 35, 41, "dustypink");
            builder.fillRect(54, 29, 59, 34, "dustypink");
            builder.fillRect(64, 26, 69, 32, "dustypink");

            builder.fillRect(55, 34, 77, 79, "floralwhite");
            builder.fillRect(52, 45, 74, 61, "lightpink");
            builder.fillRect(55, 37, 75, 43, "white");
            builder.fillRect(77, 40, 81, 59, "seashell");
            builder.fillRect(66, 59, 78, 72, "mistyrose");
            builder.drawLine(54, 34, 77, 34, "wineberry");
            builder.drawLine(72, 34, 84, 31, "wineberry");
            builder.drawLine(80, 32, 82, 58, "wineberry");
            builder.drawLine(77, 79, 68, 78, "wineberry");
            builder.drawLine(58, 47, 76, 47, "blushpink");
            builder.drawLine(56, 53, 74, 59, "blushpink");
            builder.drawLine(58, 60, 70, 61, "floralwhite");
            builder.drawLine(64, 38, 73, 38, "wineberry");
            builder.fillRect(64, 39, 67, 41, "white");
            builder.fillRect(68, 39, 69, 40, "teal");
            builder.fillRect(70, 39, 73, 41, "white");
            builder.drawLine(65, 42, 72, 41, "black");
            builder.drawLine(63, 44, 72, 42, "wineberry");
            builder.drawLine(60, 58, 71, 57, "oxblood");
            builder.drawLine(61, 63, 74, 66, "mistyrose");
            builder.drawLine(59, 70, 73, 78, "white");
            builder.drawLine(58, 72, 63, 79, "white");

            builder.fillRect(75, 35, 99, 82, "seashell");
            builder.fillRect(74, 35, 99, 42, "beige");
            builder.fillRect(73, 40, 99, 46, "blushpink");
            builder.fillRect(76, 43, 99, 78, "floralwhite");
            builder.fillRect(77, 43, 98, 46, "lightcream");
            builder.drawRect(80, 50, 98, 82, "rosybrown");
            builder.drawLine(74, 35, 89, 33, "rosybrown");
            builder.drawLine(89, 33, 99, 37, "rosybrown");
            builder.drawLine(74, 46, 99, 46, "lightpink");
            builder.drawLine(76, 82, 99, 82, "rosybrown");
            builder.drawLine(78, 52, 82, 68, "white");
            builder.drawLine(81, 52, 84, 88, "white");
            builder.drawLine(84, 56, 88, 96, "white");
            builder.drawLine(91, 48, 92, 83, "white");
            builder.drawLine(96, 46, 97, 80, "white");
            builder.drawLine(76, 36, 99, 41, "cream");
            builder.drawLine(84, 82, 88, 82, "rosybrown");
            builder.drawLine(88, 82, 90, 95, "white");
            builder.drawLine(98, 72, 99, 81, "white");
            builder.fillRect(77, 47, 81, 49, "white");
            builder.fillRect(87, 47, 89, 48, "white");

            builder.fillRect(37, 57, 53, 87, "currant");
            builder.fillRect(33, 72, 45, 93, "palevioletred");
            builder.drawLine(35, 88, 31, 93, "wineberry");
            builder.drawLine(51, 84, 55, 90, "wineberry");
            builder.drawLine(37, 58, 34, 72, "wineberry");

            builder.fillRect(38, 70, 79, 149, "floralwhite");
            builder.fillRect(38, 70, 56, 149, "seashell");
            builder.fillRect(46, 80, 57, 149, "beige");
            builder.fillRect(57, 73, 70, 149, "white");
            builder.fillRect(70, 78, 81, 140, "floralwhite");
            builder.fillRect(60, 97, 70, 126, "white");
            builder.fillRect(59, 128, 71, 149, "floralwhite");
            builder.drawLine(38, 70, 38, 149, "wineberry");
            builder.drawLine(57, 73, 57, 149, "rosybrown");
            builder.drawLine(70, 79, 70, 149, "rosybrown");
            builder.drawLine(81, 78, 79, 149, "rosybrown");
            builder.drawLine(38, 149, 59, 149, "wineberry");
            builder.drawLine(62, 149, 79, 149, "wineberry");
            builder.drawLine(57, 73, 79, 78, "wineberry");
            builder.drawLine(46, 80, 57, 79, "wineberry");
            builder.drawLine(63, 71, 72, 74, "white");

            builder.fillRect(43, 78, 55, 97, "lightgray");
            builder.fillRect(47, 91, 52, 102, "slategray");
            builder.fillRect(44, 116, 58, 144, "lightgray");
            builder.fillRect(42, 125, 54, 149, "gainsboro");
            builder.fillRect(68, 82, 75, 87, "slategray");
            builder.fillRect(43, 78, 49, 82, "slategray");
            builder.fillRect(45, 84, 49, 89, "gray");
            builder.fillRect(46, 120, 56, 128, "slategray");
            builder.fillRect(42, 130, 50, 139, "gray");
            builder.fillRect(53, 135, 58, 145, "warmpink");
            builder.fillRect(47, 140, 53, 149, "pastelpink");
            builder.drawLine(49, 102, 63, 102, "slategray");
            builder.drawLine(69, 82, 76, 83, "slategray");

            builder.fillRect(0, 92, 32, 149, "white");
            builder.fillRect(6, 96, 20, 132, "pastelpink");
            builder.fillRect(10, 97, 14, 130, "white");
            builder.fillRect(22, 103, 28, 134, "pastelpink");
            builder.fillRect(24, 104, 26, 130, "white");
            builder.fillRect(0, 122, 12, 149, "white");
            builder.fillRect(0, 132, 7, 149, "white");
            builder.drawLine(0, 119, 8, 102, "wineberry");
            builder.drawLine(8, 101, 19, 90, "wineberry");
            builder.drawLine(19, 90, 31, 94, "wineberry");
            builder.drawLine(31, 94, 28, 118, "wineberry");
            builder.drawLine(28, 118, 32, 135, "wineberry");
            builder.drawLine(32, 135, 25, 148, "wineberry");
            builder.drawLine(0, 149, 25, 149, "wineberry");
            builder.drawLine(8, 122, 14, 133, "white");
            builder.drawLine(22, 111, 26, 127, "white");

            builder.fillRect(82, 58, 87, 132, "white");
            builder.fillRect(88, 74, 91, 149, "white");
            builder.fillRect(75, 90, 81, 138, "white");
            builder.drawLine(79, 60, 82, 74, "white");
            builder.drawLine(81, 82, 86, 97, "white");
            builder.drawLine(84, 103, 88, 122, "white");
            builder.drawLine(78, 124, 86, 133, "white");
            builder.drawLine(74, 138, 83, 144, "white");
            builder.drawLine(90, 128, 93, 149, "white");
            builder.drawLine(82, 58, 85, 132, "rosybrown");
            builder.drawLine(91, 74, 94, 149, "rosybrown");

            builder.fillRect(8, 62, 17, 66, "warmpink");
            builder.fillRect(73, 95, 85, 99, "powderpink");
            builder.fillRect(76, 102, 85, 106, "powderpink");
            builder.fillRect(84, 144, 99, 149, "powderpink");

            for (int y = 55; y <= 74; y += 7) {
                builder.drawLine(53, y, 54, y + 2, "white");
                builder.drawLine(60, y + 1, 61, y + 4, "white");
                builder.drawLine(68, y, 69, y + 3, "white");
            }

            for (int y = 88; y <= 138; y += 13) {
                builder.drawLine(62, y, 64, y + 5, "ghostwhite");
                builder.drawLine(66, y + 3, 69, y + 9, "ghostwhite");
            }

            builder.drawLine(94, 61, 98, 63, "powderpink");
            builder.drawLine(96, 68, 99, 69, "powderpink");
            builder.drawLine(92, 28, 97, 30, "powderpink");
            builder.drawLine(90, 21, 95, 23, "powderpink");
            builder.drawLine(69, 16, 74, 18, "powderpink");

            java.nio.file.Path imagePath = builder.render();
            Logger.log(Logger.TAG.SYSTEM, "TestingGrounds manual image saved to: " + imagePath);

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

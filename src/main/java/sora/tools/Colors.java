package sora.tools;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple color utility that keeps HEX and RGB in sync.
 */
public final class Colors {

    private Colors() {}

    /**
     * Color object storing HEX and RGB values.
     * Updating either form updates the other.
     */
    public static class Color {

        private String hex;
        private int r;
        private int g;
        private int b;

        /**
         * Creates a color from a hex string.
         *
         * Behavior:
         * - Accepts formats "RRGGBB" or "#RRGGBB".
         * - Normalizes and stores as "#RRGGBB".
         * - Updates RGB values to match.
         *
         * @param hex hex string for the color.
         * @throws IllegalArgumentException if hex is null or invalid.
         */
        public Color(String hex) {
            setHex(hex);
        }

        /**
         * Creates a color from RGB components.
         *
         * Behavior:
         * - Each component must be in [0, 255].
         * - Updates HEX to match.
         *
         * @param r red component (0-255).
         * @param g green component (0-255).
         * @param b blue component (0-255).
         * @throws IllegalArgumentException if any component is out of range.
         */
        public Color(int r, int g, int b) {
            setRgb(r, g, b);
        }

        /**
         * Creates a color from a preset.
         *
         * Behavior:
         * - Uses the preset's HEX value.
         * - Updates RGB values to match.
         *
         * @param preset predefined color preset.
         * @throws IllegalArgumentException if preset is null.
         */
        public Color(Preset preset) {
            if (preset == null) {
                throw new IllegalArgumentException("preset cannot be null");
            }
            setHex(preset.hex);
        }

        /**
         * Returns the current HEX value in "#RRGGBB" format.
         *
         * @return HEX string.
         */
        public String getHex() {
            return hex;
        }

        /**
         * Returns the red component (0-255).
         *
         * @return red value.
         */
        public int getR() {
            return r;
        }

        /**
         * Returns the green component (0-255).
         *
         * @return green value.
         */
        public int getG() {
            return g;
        }

        /**
         * Returns the blue component (0-255).
         *
         * @return blue value.
         */
        public int getB() {
            return b;
        }

        /**
         * Returns the packed 24-bit RGB integer for this color.
         *
         * <p>The returned value uses the layout {@code 0xRRGGBB} and does not
         * include an alpha channel.</p>
         *
         * @return packed RGB integer
         */
        public int toRgbInt() {
            return (r << 16) | (g << 8) | b;
        }

        /**
         * Returns the packed 32-bit ARGB integer for this color.
         *
         * <p>The alpha channel is always fully opaque, so the returned value
         * uses the layout {@code 0xFFRRGGBB}.</p>
         *
         * @return packed ARGB integer
         */
        public int toArgbInt() {
            return 0xFF000000 | toRgbInt();
        }

        /**
         * Updates the color using a HEX string.
         *
         * Behavior:
         * - Accepts formats "RRGGBB" or "#RRGGBB".
         * - Normalizes and stores as "#RRGGBB".
         * - Updates RGB values to match.
         *
         * @param hex hex string for the color.
         * @return this color for chaining.
         * @throws IllegalArgumentException if hex is null or invalid.
         */
        public Color setHex(String hex) {
            String normalized = normalizeHex(hex);
            this.hex = normalized;
            syncFromHex(normalized);
            return this;
        }

        /**
         * Updates the color using RGB components.
         *
         * Behavior:
         * - Each component must be in [0, 255].
         * - Updates HEX to match.
         *
         * @param r red component (0-255).
         * @param g green component (0-255).
         * @param b blue component (0-255).
         * @return this color for chaining.
         * @throws IllegalArgumentException if any component is out of range.
         */
        public Color setRgb(int r, int g, int b) {
            validateRgb(r, g, b);
            this.r = r;
            this.g = g;
            this.b = b;
            this.hex = toHex(r, g, b);
            return this;
        }

        /**
         * Inverts the current color in-place.
         *
         * Behavior:
         * - Each RGB component is replaced with 255 - component.
         * - Updates HEX to match.
         *
         * @return this color for chaining.
         */
        public Color invert() {
            this.r = 255 - r;
            this.g = 255 - g;
            this.b = 255 - b;
            this.hex = toHex(r, g, b);
            return this;
        }

        /**
         * Creates a new random color.
         *
         * Behavior:
         * - Uses ThreadLocalRandom for RGB values.
         *
         * @return a new random Color instance.
         */
        public static Color random() {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            return new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
        }

        private static void validateRgb(int r, int g, int b) {
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
                throw new IllegalArgumentException("RGB values must be in range 0-255");
            }
        }

        private static String normalizeHex(String hex) {
            if (hex == null) {
                throw new IllegalArgumentException("hex cannot be null");
            }
            String trimmed = hex.trim();
            if (trimmed.startsWith("#")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.length() != 6) {
                throw new IllegalArgumentException("hex must be 6 characters (RRGGBB)");
            }
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                boolean isDigit = (c >= '0' && c <= '9');
                boolean isLower = (c >= 'a' && c <= 'f');
                boolean isUpper = (c >= 'A' && c <= 'F');
                if (!isDigit && !isLower && !isUpper) {
                    throw new IllegalArgumentException("hex contains invalid characters");
                }
            }
            return "#" + trimmed.toUpperCase();
        }

        private void syncFromHex(String normalizedHex) {
            int value = Integer.parseInt(normalizedHex.substring(1), 16);
            this.r = (value >> 16) & 0xFF;
            this.g = (value >> 8) & 0xFF;
            this.b = value & 0xFF;
        }

        private static String toHex(int r, int g, int b) {
            return String.format("#%02X%02X%02X", r, g, b);
        }
    }

    /**
     * Common color presets for quick use.
     */
    public enum Preset {
        BLACK("#000000"),
        WHITE("#FFFFFF"),
        GRAY("#808080"),
        SILVER("#C0C0C0"),
        RED("#FF0000"),
        MAROON("#800000"),
        ORANGE("#FFA500"),
        YELLOW("#FFFF00"),
        OLIVE("#808000"),
        GREEN("#00FF00"),
        LIME("#32CD32"),
        TEAL("#008080"),
        CYAN("#00FFFF"),
        BLUE("#0000FF"),
        NAVY("#000080"),
        PURPLE("#800080"),
        MAGENTA("#FF00FF"),
        PINK("#FFC0CB"),
        BROWN("#8B4513"),
        GOLD("#FFD700");

        private final String hex;

        Preset(String hex) {
            this.hex = hex;
        }
    }
}

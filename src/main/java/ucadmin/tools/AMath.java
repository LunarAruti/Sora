package ucadmin.tools;

/**
 * Small math helpers and constants for common operations.
 */
public final class AMath {

    public static final double PI = java.lang.Math.PI;
    public static final double TAU = java.lang.Math.PI * 2.0;
    public static final double E = java.lang.Math.E;
    public static final double DEG_TO_RAD = java.lang.Math.PI / 180.0;
    public static final double RAD_TO_DEG = 180.0 / java.lang.Math.PI;
    public static final double EPSILON = 1e-9;
    public static final double GOLDEN_RATIO = (1.0 + java.lang.Math.sqrt(5.0)) / 2.0;

    private AMath() {}

    /**
     * Clamps a comparable value to the inclusive range [min, max].
     *
     * Behavior:
     * - Uses compareTo for ordering.
     * - If min > max, the bounds are swapped.
     * - Returns min if value is below range, max if above.
     *
     * @param value the value to clamp.
     * @param min lower bound.
     * @param max upper bound.
     * @param <T> comparable type.
     * @return clamped value within the bounds.
     * @throws IllegalArgumentException if any argument is null.
     */
    public static <T extends Comparable<T>> T clamp(T value, T min, T max) {
        if (value == null || min == null || max == null) {
            throw new IllegalArgumentException("value/min/max cannot be null");
        }
        if (min.compareTo(max) > 0) {
            T tmp = min;
            min = max;
            max = tmp;
        }
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    /**
     * Checks whether a comparable value is within a range.
     *
     * Behavior:
     * - Uses compareTo for ordering.
     * - If min > max, the bounds are swapped.
     * - If inclusive is true, endpoints are considered inside the range.
     *
     * @param value the value to test.
     * @param min lower bound.
     * @param max upper bound.
     * @param inclusive true for inclusive range, false for exclusive.
     * @param <T> comparable type.
     * @return true if within the range, false otherwise.
     * @throws IllegalArgumentException if any argument is null.
     */
    public static <T extends Comparable<T>> boolean between(T value, T min, T max, boolean inclusive) {
        if (value == null || min == null || max == null) {
            throw new IllegalArgumentException("value/min/max cannot be null");
        }
        if (min.compareTo(max) > 0) {
            T tmp = min;
            min = max;
            max = tmp;
        }
        if (inclusive) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        }
        return value.compareTo(min) > 0 && value.compareTo(max) < 0;
    }

    /**
     * Returns the minimum of two comparable values.
     *
     * Behavior:
     * - Uses compareTo for ordering.
     *
     * @param a first value.
     * @param b second value.
     * @param <T> comparable type.
     * @return the smaller value (or a if equal).
     * @throws IllegalArgumentException if any argument is null.
     */
    public static <T extends Comparable<T>> T min(T a, T b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("a/b cannot be null");
        }
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * Returns the maximum of two comparable values.
     *
     * Behavior:
     * - Uses compareTo for ordering.
     *
     * @param a first value.
     * @param b second value.
     * @param <T> comparable type.
     * @return the larger value (or a if equal).
     * @throws IllegalArgumentException if any argument is null.
     */
    public static <T extends Comparable<T>> T max(T a, T b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("a/b cannot be null");
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Raises a base to a power.
     *
     * Behavior:
     * - Delegates to Math.pow.
     *
     * @param base the base value.
     * @param exp the exponent.
     * @return base raised to exp.
     */
    public static double pow(double base, double exp) {
        return java.lang.Math.pow(base, exp);
    }

    /**
     * Returns the square root of a value.
     *
     * Behavior:
     * - Delegates to Math.sqrt.
     *
     * @param value input value.
     * @return square root.
     */
    public static double sqrt(double value) {
        return java.lang.Math.sqrt(value);
    }

    /**
     * Returns the cube root of a value.
     *
     * Behavior:
     * - Delegates to Math.cbrt.
     *
     * @param value input value.
     * @return cube root.
     */
    public static double cbrt(double value) {
        return java.lang.Math.cbrt(value);
    }

    /**
     * Computes the n-th root of a value.
     *
     * Behavior:
     * - Uses Math.pow(value, 1.0 / n).
     * - If n is 0, returns NaN.
     *
     * @param value input value.
     * @param n root degree.
     * @return n-th root of value.
     */
    public static double nthRoot(double value, double n) {
        if (n == 0.0) {
            return Double.NaN;
        }
        return java.lang.Math.pow(value, 1.0 / n);
    }

    /**
     * Returns the absolute value of an int.
     *
     * Behavior:
     * - Delegates to Math.abs.
     *
     * @param value input value.
     * @return absolute value.
     */
    public static int absInt(int value) {
        return java.lang.Math.abs(value);
    }

    /**
     * Returns the absolute value of a long.
     *
     * Behavior:
     * - Delegates to Math.abs.
     *
     * @param value input value.
     * @return absolute value.
     */
    public static long absLong(long value) {
        return java.lang.Math.abs(value);
    }

    /**
     * Returns the absolute value of a double.
     *
     * Behavior:
     * - Delegates to Math.abs.
     *
     * @param value input value.
     * @return absolute value.
     */
    public static double absDouble(double value) {
        return java.lang.Math.abs(value);
    }

    /**
     * Returns the sign of a value as -1, 0, or 1.
     *
     * Behavior:
     * - Returns 0 for NaN.
     *
     * @param value input value.
     * @return sign (-1, 0, 1).
     */
    public static int sign(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        if (value > 0) {
            return 1;
        }
        if (value < 0) {
            return -1;
        }
        return 0;
    }

    /**
     * Safely divides two doubles with a fallback on divide-by-zero.
     *
     * Behavior:
     * - If denominator is 0 or NaN, returns fallback.
     *
     * @param numerator numerator value.
     * @param denominator denominator value.
     * @param fallback value to return when denominator is 0 or NaN.
     * @return division result or fallback.
     */
    public static double safeDivide(double numerator, double denominator, double fallback) {
        if (denominator == 0.0 || Double.isNaN(denominator)) {
            return fallback;
        }
        return numerator / denominator;
    }

    /**
     * Linearly interpolates between a and b by t.
     *
     * Behavior:
     * - t=0 returns a, t=1 returns b.
     *
     * @param a start value.
     * @param b end value.
     * @param t interpolation factor.
     * @return interpolated value.
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Maps a value from one range into another.
     *
     * Behavior:
     * - If inMin == inMax, returns outMin.
     *
     * @param value input value.
     * @param inMin input range min.
     * @param inMax input range max.
     * @param outMin output range min.
     * @param outMax output range max.
     * @return mapped value in output range.
     */
    public static double mapRange(double value, double inMin, double inMax, double outMin, double outMax) {
        if (inMin == inMax) {
            return outMin;
        }
        double t = (value - inMin) / (inMax - inMin);
        return outMin + (outMax - outMin) * t;
    }
}

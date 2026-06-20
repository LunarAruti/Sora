package sora.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * String helpers focused on regex matching and extraction.
 */
public final class AString {

    private AString() {}

    /**
     * Checks whether the entire input matches the provided regex pattern.
     *
     * Behavior:
     * - Uses standard Java regex syntax.
     * - Returns false if input or pattern is null.
     * - Performs a full-string match (equivalent to {@code matches()}).
     *
     * @param input the input string to test.
     * @param pattern the regex pattern to match against.
     * @return true if the full input matches the pattern, false otherwise.
     * @throws PatternSyntaxException if the pattern is an invalid regex.
     */
    public static boolean matches(java.lang.String input, java.lang.String pattern) {
        if (input == null || pattern == null) {
            return false;
        }
        return Pattern.compile(pattern).matcher(input).matches();
    }

    /**
     * Checks whether any substring of the input matches the provided regex pattern.
     *
     * Behavior:
     * - Uses standard Java regex syntax.
     * - Returns false if input or pattern is null.
     * - Returns true if any match is found within the input.
     *
     * @param input the input string to test.
     * @param pattern the regex pattern to search for.
     * @return true if any match exists, false otherwise.
     * @throws PatternSyntaxException if the pattern is an invalid regex.
     */
    public static boolean containsMatch(java.lang.String input, java.lang.String pattern) {
        if (input == null || pattern == null) {
            return false;
        }
        return Pattern.compile(pattern).matcher(input).find();
    }

    /**
     * Returns the first matched substring for the given regex pattern.
     *
     * Behavior:
     * - Uses standard Java regex syntax.
     * - Returns null if input or pattern is null.
     * - Returns null if no match is found.
     * - Returns the exact substring that matched (group 0).
     *
     * @param input the input string to search.
     * @param pattern the regex pattern to search for.
     * @return the first matched substring, or null if none.
     * @throws PatternSyntaxException if the pattern is an invalid regex.
     */
    public static java.lang.String firstMatch(java.lang.String input, java.lang.String pattern) {
        if (input == null || pattern == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group();
    }

    /**
     * Finds all matched substrings for the given regex pattern.
     *
     * Behavior:
     * - Uses standard Java regex syntax.
     * - Returns an empty list if input or pattern is null.
     * - Returns each full match (group 0) in encounter order.
     *
     * @param input the input string to search.
     * @param pattern the regex pattern to search for.
     * @return a list of all matched substrings (possibly empty).
     * @throws PatternSyntaxException if the pattern is an invalid regex.
     */
    public static Lists.ResizingArray<java.lang.String> findAll(java.lang.String input, java.lang.String pattern) {
        Lists.ResizingArray<java.lang.String> results = new Lists.ResizingArray<>();
        if (input == null || pattern == null) {
            return results;
        }
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            results.add(results.size(), matcher.group());
        }
        return results;
    }

    /**
     * Removes all whitespace characters from the input.
     *
     * Behavior:
     * - Removes spaces, tabs, newlines, and all Unicode whitespace.
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @return the input with all whitespace removed, or null if input is null.
     */
    public static java.lang.String removeWhitespace(java.lang.String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s+", "");
    }

    /**
     * Collapses runs of whitespace into a single space and trims edges.
     *
     * Behavior:
     * - Converts any consecutive whitespace into a single space.
     * - Trims leading and trailing whitespace.
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @return normalized whitespace string, or null if input is null.
     */
    public static java.lang.String normalizeWhitespace(java.lang.String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s+", " ").trim();
    }

    /**
     * Returns a lowercase version of the input.
     *
     * Behavior:
     * - Uses the default locale rules.
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @return lowercase string, or null if input is null.
     */
    public static java.lang.String toLower(java.lang.String input) {
        return input == null ? null : input.toLowerCase();
    }

    /**
     * Returns an uppercase version of the input.
     *
     * Behavior:
     * - Uses the default locale rules.
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @return uppercase string, or null if input is null.
     */
    public static java.lang.String toUpper(java.lang.String input) {
        return input == null ? null : input.toUpperCase();
    }

    /**
     * Trims leading and trailing whitespace.
     *
     * Behavior:
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @return trimmed string, or null if input is null.
     */
    public static java.lang.String trim(java.lang.String input) {
        return input == null ? null : input.trim();
    }

    /**
     * Removes ASCII control characters from the input.
     *
     * Behavior:
     * - Strips characters in ranges U+0000–U+001F and U+007F.
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @return input without control characters, or null if input is null.
     */
    public static java.lang.String stripNonPrintable(java.lang.String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[\\x00-\\x1F\\x7F]", "");
    }

    /**
     * Returns a substring with bounds safely clamped to the input length.
     *
     * Behavior:
     * - Negative indices clamp to 0.
     * - End index clamps to input length.
     * - If start exceeds end after clamping, returns an empty string.
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @param start starting index (inclusive).
     * @param end ending index (exclusive).
     * @return safe substring, empty string if invalid range, or null if input is null.
     */
    public static java.lang.String safeSubstring(java.lang.String input, int start, int end) {
        if (input == null) {
            return null;
        }
        int length = input.length();
        int safeStart = AMath.max(0, start);
        int safeEnd = AMath.min(length, end);
        if (safeStart >= safeEnd) {
            return "";
        }
        return input.substring(safeStart, safeEnd);
    }

    /**
     * Truncates a string to a maximum length and optionally appends a suffix.
     *
     * Behavior:
     * - If input length is <= max, returns input unchanged.
     * - If suffix is null, uses an empty suffix.
     * - Ensures final length does not exceed max.
     * - Returns null if input is null.
     *
     * @param input the input string.
     * @param max maximum length of the returned string (>= 0).
     * @param suffix suffix to append when truncating.
     * @return truncated string, or null if input is null.
     * @throws IllegalArgumentException if max is negative.
     */
    public static java.lang.String truncate(java.lang.String input, int max, java.lang.String suffix) {
        if (input == null) {
            return null;
        }
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0");
        }
        if (input.length() <= max) {
            return input;
        }
        java.lang.String safeSuffix = (suffix == null) ? "" : suffix;
        int suffixLen = safeSuffix.length();
        if (suffixLen > max) {
            return safeSuffix.substring(0, max);
        }
        int end = max - suffixLen;
        return input.substring(0, end) + safeSuffix;
    }

    /**
     * Checks whether the input contains the target string.
     *
     * Behavior:
     * - Returns false if input or target is null.
     * - If ignoreCase is true, compares using case-insensitive match.
     *
     * @param input the input string to search.
     * @param target the substring to look for.
     * @param ignoreCase whether to ignore case when comparing.
     * @return true if input contains target, false otherwise.
     */
    public static boolean contains(java.lang.String input, java.lang.String target, boolean ignoreCase) {
        if (input == null || target == null) {
            return false;
        }
        if (!ignoreCase) {
            return input.contains(target);
        }
        return input.toLowerCase().contains(target.toLowerCase());
    }
}

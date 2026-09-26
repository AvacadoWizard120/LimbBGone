package io.github.avacadowizard120.mobamputation.config;

/** Shared network-safe views of developer-editable rule strings. */
public final class RuleTextLimits {
    public static final int MAX_CHARS = 32767;

    /** Keeps only complete newline-delimited tool rules within the limit. */
    public static String toolRules(String configured) {
        String rules = configured == null ? "" : configured;
        if (rules.length() <= MAX_CHARS) {
            return rules;
        }
        int boundary = lastLineBoundary(rules);
        return boundary < 0 ? "" : rules.substring(0, boundary);
    }

    /** Keeps only complete comma-delimited projectile rules within the limit. */
    public static String projectileRules(String configured) {
        String rules = configured == null ? "" : configured;
        if (rules.length() <= MAX_CHARS) {
            return rules;
        }
        int boundary = rules.lastIndexOf(',', MAX_CHARS);
        return boundary < 0 ? "" : rules.substring(0, boundary);
    }

    private static int lastLineBoundary(String rules) {
        for (int index = MAX_CHARS; index >= 0; index--) {
            char character = rules.charAt(index);
            if (character == '\n' || character == '\r') {
                if (character == '\n' && index > 0 && rules.charAt(index - 1) == '\r') {
                    return index - 1;
                }
                return index;
            }
        }
        return -1;
    }

    private RuleTextLimits() {
    }
}

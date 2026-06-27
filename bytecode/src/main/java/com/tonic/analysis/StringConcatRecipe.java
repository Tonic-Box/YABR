package com.tonic.analysis;

/**
 * Analysis-layer formatter for {@code StringConcatFactory.makeConcatWithConstants} recipe strings.
 * The recipe encodes a dynamic argument as {@code } and a bootstrap constant as {@code };
 * {@link #toReadable(String)} renders these as {@code {arg}}/{@code {const}} and escapes any other ISO
 * control character so the raw markers never leak into disassembly or query output.
 *
 * <p>Intentionally decoupled from the execution-layer
 * {@code com.tonic.analysis.execution.invoke.StringConcatHandler}: this is the static-analysis view.
 */
final class StringConcatRecipe {

    private static final char TAG_ARG = '';
    private static final char TAG_CONST = '';

    private StringConcatRecipe() {
    }

    /**
     * Renders a raw recipe to its readable form, with {@code {arg}}/{@code {const}} markers and
     * {@code \\uXXXX} escapes for other control characters.
     *
     * @param raw the raw recipe string
     * @return the readable recipe, or {@code null} when {@code raw} is {@code null}
     */
    static String toReadable(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == TAG_ARG) {
                sb.append("{arg}");
            } else if (c == TAG_CONST) {
                sb.append("{const}");
            } else {
                appendEscaped(sb, c);
            }
        }
        return sb.toString();
    }

    /**
     * Appends a string with its ISO control characters escaped as {@code \\uXXXX}; ordinary characters
     * are copied verbatim. The recipe markers are not treated specially here, so this is also a safe
     * escaper for arbitrary string constants in disassembly output.
     *
     * @param sb    the buffer to append to
     * @param value the string to escape
     */
    static void appendEscaped(StringBuilder sb, String value) {
        for (int i = 0; i < value.length(); i++) {
            appendEscaped(sb, value.charAt(i));
        }
    }

    private static void appendEscaped(StringBuilder sb, char c) {
        if (Character.isISOControl(c)) {
            sb.append(String.format("\\u%04X", (int) c));
        } else {
            sb.append(c);
        }
    }
}

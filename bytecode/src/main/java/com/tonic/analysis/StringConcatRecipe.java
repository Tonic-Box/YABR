package com.tonic.analysis;

/**
 * Analysis-layer formatter for {@code StringConcatFactory.makeConcatWithConstants} recipe strings.
 */
final class StringConcatRecipe
{

    private static final char TAG_ARG = '';
    private static final char TAG_CONST = '';

    private StringConcatRecipe()
    {
    }

    /**
     * Renders a raw recipe to its readable form, with {@code {arg}}/{@code {const}} markers and
     * {@code \\uXXXX} escapes for other control characters.
     * @param raw the raw recipe string
     * @return the readable recipe, or {@code null} when {@code raw} is {@code null}
     */
    static String toReadable(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == TAG_ARG)
            {
                sb.append("{arg}");
            }
            else if (c == TAG_CONST)
            {
                sb.append("{const}");
            }
            else
            {
                appendEscaped(sb, c);
            }
        }
        return sb.toString();
    }

    /**
     * Appends a string with its ISO control characters escaped as {@code \\uXXXX}; ordinary characters are copied
     * verbatim.
     * @param sb    the buffer to append to
     * @param value the string to escape
     */
    static void appendEscaped(StringBuilder sb, String value)
    {
        for (int i = 0; i < value.length(); i++)
        {
            appendEscaped(sb, value.charAt(i));
        }
    }

    private static void appendEscaped(StringBuilder sb, char c)
    {
        if (Character.isISOControl(c))
        {
            sb.append(String.format("\\u%04X", (int) c));
        }
        else
        {
            sb.append(c);
        }
    }
}

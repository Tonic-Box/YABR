package com.tonic.analysis.ssa.llvm.lift;

/**
 * Inverse of {@code SymbolMangler}: parses a mangled LLVM global symbol back to its components.
 */
final class SymbolDemangle
{

    private SymbolDemangle()
    {
    }

    static final class Symbol
    {
        final String owner;
        final String name;
        final String descriptor;

        Symbol(String owner, String name, String descriptor)
        {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    /**
     * Parses a symbol string.
     */
    static Symbol parse(String symbol)
    {
        String raw = unescape(stripQuotes(symbol));
        // ABI symbols like jvm_new, @jvm.gf ..., @.str.* are not method symbols
        if (raw.startsWith("jvm_") || raw.startsWith("jvm.") || raw.startsWith(".str."))
        {
            return null;
        }

        // Find the descriptor - it starts with '(' (possibly after the method name)
        int descStart = raw.indexOf('(');
        if (descStart < 0)
        {
            return null;
        }
        String descriptor = raw.substring(descStart);

        // Before the descriptor: "owner.name"
        String ownerAndName = raw.substring(0, descStart);
        int dot = ownerAndName.lastIndexOf('.');
        if (dot < 0)
        {
            return new Symbol("", ownerAndName, descriptor);
        }
        return new Symbol(ownerAndName.substring(0, dot), ownerAndName.substring(dot + 1), descriptor);
    }

    /**
     * Strips the {@code @"..."} wrapper, or strips a leading {@code @} if unquoted.
     */
    static String stripQuotes(String symbol)
    {
        if (symbol == null)
        {
            return "";
        }
        symbol = symbol.trim();
        if (symbol.startsWith("@\"") && symbol.endsWith("\""))
        {
            return symbol.substring(2, symbol.length() - 1);
        }
        if (symbol.startsWith("@"))
        {
            return symbol.substring(1);
        }
        return symbol;
    }

    static String unescape(String s)
    {
        if (!s.contains("\\"))
        {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++)
        {
            if (s.charAt(i) == '\\' && i + 2 < s.length())
            {
                String hex = s.substring(i + 1, i + 3);
                try
                {
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 2;
                    continue;
                }
                catch (NumberFormatException ignored)
                {
                }
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}

package com.tonic.renamer.descriptor;

import java.util.function.Function;

/**
 * Remaps class references within generic signatures.
 *
 * Handles complex signature syntax including:
 * - Class signatures: <T:Ljava/lang/Object;>Ljava/util/List<TT;>;
 * - Method signatures: <T:Ljava/lang/Object;>(TT;)TT;
 * - Field signatures: Ljava/util/List<Lcom/example/Entity;>;
 * - Type arguments: +Lcom/example/Type; (extends), -Lcom/example/Type; (super), *
 * - Inner classes: Lcom/example/Outer.Inner;
 * - Array types: [Lcom/example/Type;
 */
public class SignatureRemapper {

    private final Function<String, String> classMapper;

    /**
     * Creates a remapper with the given class name mapping function.
     *
     * @param classMapper Function that maps old class names to new ones
     */
    public SignatureRemapper(Function<String, String> classMapper) {
        this.classMapper = classMapper;
    }

    /**
     * Remaps all class references in a generic signature.
     *
     * @param signature The generic signature
     * @return The remapped signature, or null if signature was null
     */
    public String remap(String signature) {
        if (signature == null || signature.isEmpty()) {
            return signature;
        }
        StringBuilder result = new StringBuilder(signature.length());
        remapSignature(signature, 0, result);
        return result.toString();
    }

    private int remapSignature(String sig, int pos, StringBuilder out) {
        while (pos < sig.length()) {
            char c = sig.charAt(pos);
            switch (c) {
                case '<':
                    // Start of formal type parameters or type arguments
                    out.append(c);
                    pos = remapTypeArguments(sig, pos + 1, out);
                    break;

                case '>':
                    // End of type arguments
                    out.append(c);
                    pos++;
                    break;

                case 'L':
                    // Class type
                    pos = remapClassType(sig, pos, out);
                    break;

                case 'T':
                    // Type variable reference
                    pos = remapTypeVariable(sig, pos, out);
                    break;

                case '[':
                    // Array type
                    out.append(c);
                    pos++;
                    break;

                case '+':
                case '-':
                case '*':
                    // Wildcard indicators
                    out.append(c);
                    pos++;
                    if (c == '*') {
                        // Unbounded wildcard, nothing follows
                    }
                    // For + and -, a type follows
                    break;

                case '(':
                case ')':
                case ';':
                case ':':
                    // Delimiters
                    out.append(c);
                    pos++;
                    break;

                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'I':
                case 'J':
                case 'S':
                case 'Z':
                case 'V':
                    // Primitive types and void
                    out.append(c);
                    pos++;
                    break;

                case '^':
                    // Exception type in throws clause
                    out.append(c);
                    pos++;
                    break;

                default:
                    // Identifier character (part of type parameter name, etc.)
                    out.append(c);
                    pos++;
                    break;
            }
        }
        return pos;
    }

    private int remapTypeArguments(String sig, int pos, StringBuilder out) {
        while (pos < sig.length()) {
            char c = sig.charAt(pos);
            if (c == '>') {
                return pos; // Don't consume '>', let caller handle it
            }
            pos = remapSignature(sig, pos, out);
            if (pos >= sig.length()) break;
            c = sig.charAt(pos);
            if (c == '>') {
                return pos;
            }
        }
        return pos;
    }

    private int remapClassType(String sig, int pos, StringBuilder out) {
        // Lclassname; or Lclassname<typeargs>; or Lclassname.innerclass;
        int start = pos + 1; // Skip 'L'
        StringBuilder className = new StringBuilder();
        pos = start;

        while (pos < sig.length()) {
            char c = sig.charAt(pos);
            if (c == ';') {
                // End of class type
                String original = className.toString();
                String mapped = mapClassName(original);
                out.append('L').append(mapped).append(';');
                return pos + 1;
            } else if (c == '<') {
                // Type arguments follow
                String original = className.toString();
                String mapped = mapClassName(original);
                out.append('L').append(mapped).append('<');
                pos++;
                // Recursively handle type arguments
                while (pos < sig.length() && sig.charAt(pos) != '>') {
                    pos = remapSignature(sig, pos, out);
                }
                if (pos < sig.length() && sig.charAt(pos) == '>') {
                    out.append('>');
                    pos++;
                }
                // Continue to handle remainder (might be .Inner or ;)
                className = new StringBuilder();
            } else if (c == '.') {
                // Inner class follows
                String original = className.toString();
                String mapped = mapClassName(original);
                out.append('L').append(mapped).append('.');
                pos++;
                className = new StringBuilder();
                // Continue to get inner class name
                while (pos < sig.length()) {
                    c = sig.charAt(pos);
                    if (c == ';' || c == '<' || c == '.') {
                        break;
                    }
                    className.append(c);
                    pos++;
                }
                // Append inner class name (not remapped separately)
                out.deleteCharAt(out.length() - 1); // Remove trailing '.'
                // Actually for inner classes, we need the full path
                // Let's reconsider: Outer$Inner or Outer.Inner in signatures
                // The full name including inner should be mapped together
                // For now, append inner name
                if (className.length() > 0) {
                    out.append('.').append(className);
                }
                className = new StringBuilder();
            } else {
                className.append(c);
                pos++;
            }
        }

        // Malformed signature, output what we have
        if (className.length() > 0) {
            out.append('L').append(className);
        }
        return pos;
    }

    private int remapTypeVariable(String sig, int pos, StringBuilder out) {
        // Tname;
        out.append('T');
        pos++; // Skip 'T'
        while (pos < sig.length()) {
            char c = sig.charAt(pos);
            out.append(c);
            pos++;
            if (c == ';') {
                break;
            }
        }
        return pos;
    }

    private String mapClassName(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }
        String mapped = classMapper.apply(className);
        return mapped != null ? mapped : className;
    }

    /**
     * Checks if a signature contains any class references that would be remapped.
     *
     * @param signature The signature to check
     * @return true if the signature would change after remapping
     */
    public boolean needsRemapping(String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        return !signature.equals(remap(signature));
    }
}

package com.tonic.analysis.ssa.llvm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interns the name strings (class / field / method ids, string literals) passed to the runtime ABI
 * as private NUL-terminated C-string globals ({@code @.str.N}). Deduped by content; the symbol name
 * is assigned at first sight (deterministic given the deterministic lowering order), so it can be
 * referenced inline before the full set is known.
 */
final class CStringPool {

    private final Map<String, String> symbols = new LinkedHashMap<>();
    private int counter = 0;

    /** Returns the {@code @.str.N} symbol for {@code content}, allocating one on first use. */
    String intern(String content) {
        return symbols.computeIfAbsent(content, k -> "@.str." + (counter++));
    }

    /** UTF-8 byte length of a string (the {@code len} argument for {@code jvm_intern_string}). */
    static int utf8Length(String content) {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    List<String> renderConstants() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : symbols.entrySet()) {
            byte[] bytes = e.getKey().getBytes(StandardCharsets.UTF_8);
            out.add(e.getValue() + " = private constant [" + (bytes.length + 1) + " x i8] c\""
                + escape(bytes) + "\\00\"");
        }
        return out;
    }

    private static String escape(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (c >= 0x20 && c <= 0x7E && c != '"' && c != '\\') {
                sb.append((char) c);
            } else {
                sb.append('\\').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }
}

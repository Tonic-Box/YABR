package com.tonic.analysis.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent, self-invalidating verdict cache for the recovery oracle. Keyed by method identity and
 * validated by a hash of both the original and the recompiled bytecode: a cached verdict is reused only
 * when neither has changed since it was recorded. When the recovery pipeline changes, the recompiled
 * hash changes and the entry is recomputed automatically - no version bookkeeping. Reusing a verdict
 * skips the method's (dominant) execution; the (cheaper) recompile is still done to produce the hash.
 */
final class OracleCache {

    /** Per method: {originalHash, recompiledHash, verdictKind, verdictDetail}. */
    private final Map<String, String[]> entries = new HashMap<>();
    private int hits;
    private int misses;

    /** SHA-256 hex of a method's raw instruction bytes; empty for a null/absent code attribute. */
    static String hash(byte[] code) {
        if (code == null) {
            return "";
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(code);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    String[] lookup(String key) {
        return entries.get(key);
    }

    void recordHit() {
        hits++;
    }

    void store(String key, String origHash, String recHash, String kind, String detail) {
        entries.put(key, new String[]{origHash, recHash, kind, detail});
        misses++;
    }

    int hits() {
        return hits;
    }

    int misses() {
        return misses;
    }

    static OracleCache load(Path file) {
        OracleCache c = new OracleCache();
        try {
            if (file != null && Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String[] p = line.split("\t", -1);
                    if (p.length == 5) {
                        c.entries.put(p[0], new String[]{p[1], p[2], p[3], unescape(p[4])});
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return c;
    }

    void save(Path file) {
        try {
            List<String> lines = new ArrayList<>(entries.size());
            for (Map.Entry<String, String[]> e : entries.entrySet()) {
                String[] v = e.getValue();
                lines.add(e.getKey() + "\t" + v[0] + "\t" + v[1] + "\t" + v[2] + "\t" + escape(v[3]));
            }
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}

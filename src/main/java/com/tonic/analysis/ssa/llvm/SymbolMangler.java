package com.tonic.analysis.ssa.llvm;

/**
 * Maps a JVM method (owner + name + descriptor) to a deterministic, collision-free LLVM global
 * symbol. Uses LLVM's quoted-identifier form ({@code @"..."}), which permits arbitrary characters,
 * so the full JVM signature is the key — distinct overloads and packages never collide, and the
 * identical string is used at the {@code define}, every {@code call}, and the {@code declare}.
 */
final class SymbolMangler {

    private SymbolMangler() {
    }

    static String mangle(String owner, String name, String descriptor) {
        String raw = (owner == null ? "" : owner) + "." + name + descriptor;
        return "@\"" + raw.replace("\\", "\\5C").replace("\"", "\\22") + "\"";
    }
}

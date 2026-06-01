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
        return quote((owner == null ? "" : owner) + "." + name + descriptor);
    }

    /** Static-field global symbol {@code @"owner.name"} (no descriptor; field names are unique per class). */
    static String mangleField(String owner, String name) {
        return quote((owner == null ? "" : owner) + "." + name);
    }

    /**
     * Per-field runtime-ABI accessor symbol, e.g. {@code @"jvm.gf owner.name desc"}. The {@code tag}
     * ({@code gf}/{@code pf}) plus the descriptor keep it disjoint from method and static-field symbols.
     */
    static String mangleFieldAccessor(String tag, String owner, String name, String descriptor) {
        return quote("jvm." + tag + " " + (owner == null ? "" : owner) + "." + name + " " + descriptor);
    }

    /** invokedynamic call-site symbol, e.g. {@code @"jvm.indy owner.name desc #bsm"}. */
    static String mangleIndy(String owner, String name, String descriptor, int bootstrapIndex) {
        return quote("jvm.indy " + (owner == null ? "" : owner) + "." + name + " " + descriptor
            + " #" + bootstrapIndex);
    }

    /** ConstantDynamic bootstrap symbol, e.g. {@code @"jvm.condy name desc #bsm"}. */
    static String mangleCondy(String name, String descriptor, int bootstrapIndex) {
        return quote("jvm.condy " + (name == null ? "" : name) + " " + descriptor + " #" + bootstrapIndex);
    }

    private static String quote(String raw) {
        return "@\"" + raw.replace("\\", "\\5C").replace("\"", "\\22") + "\"";
    }
}

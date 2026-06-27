package com.tonic.analysis;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

/**
 * Immutable configuration for {@link CodePrinter}'s method-level disassembly. Each flag enables one
 * enrichment over the bare instruction listing. {@link #terse()} (all off) reproduces the legacy
 * per-instruction output; {@link #verbose()} (all on) is the rich view JStudio consumes.
 *
 * <p>Instances are created with {@link #terse()}/{@link #verbose()} and refined with the generated
 * {@code with*} methods, e.g. {@code DisassemblyOptions.terse().withExceptionTable(true)}.
 */
@Getter
@With
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class DisassemblyOptions {

    /** Emit a {@code max_stack}/{@code max_locals} header line. */
    private final boolean header;
    /** Interleave {@code // line N} comments from the LineNumberTable. */
    private final boolean lineNumbers;
    /** Annotate local-slot operands with {@code // name: descriptor} from the LocalVariableTable. */
    private final boolean localVariables;
    /** Emit the method's exception table. */
    private final boolean exceptionTable;
    /** Interleave stack-map frame markers from the StackMapTable. */
    private final boolean stackMapFrames;
    /** Resolve each invokedynamic's bootstrap method handle and static arguments. */
    private final boolean resolveBootstraps;

    /**
     * @return options with every enrichment disabled (the legacy terse listing).
     */
    public static DisassemblyOptions terse() {
        return new DisassemblyOptions(false, false, false, false, false, false);
    }

    /**
     * @return options with every enrichment enabled.
     */
    public static DisassemblyOptions verbose() {
        return new DisassemblyOptions(true, true, true, true, true, true);
    }
}

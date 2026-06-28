package com.tonic.analysis;

/**
 * Immutable configuration for {@link CodePrinter}'s method-level disassembly. Each flag enables one
 * enrichment over the bare instruction listing. {@link #terse()} (all off) reproduces the legacy
 * per-instruction output; {@link #verbose()} (all on) is the rich view JStudio consumes.
 *
 * <p>Instances are created with {@link #terse()}/{@link #verbose()} and refined with the
 * {@code with*} methods, e.g. {@code DisassemblyOptions.terse().withExceptionTable(true)}.
 */
public final class DisassemblyOptions {

    private final boolean header;
    private final boolean lineNumbers;
    private final boolean localVariables;
    private final boolean exceptionTable;
    private final boolean stackMapFrames;
    private final boolean resolveBootstraps;

    private DisassemblyOptions(boolean header, boolean lineNumbers, boolean localVariables,
                               boolean exceptionTable, boolean stackMapFrames, boolean resolveBootstraps) {
        this.header = header;
        this.lineNumbers = lineNumbers;
        this.localVariables = localVariables;
        this.exceptionTable = exceptionTable;
        this.stackMapFrames = stackMapFrames;
        this.resolveBootstraps = resolveBootstraps;
    }

    /** Returns whether a {@code max_stack}/{@code max_locals} header line is emitted. */
    public boolean isHeader() {
        return header;
    }

    /** Returns whether {@code // line N} comments from the LineNumberTable are interleaved. */
    public boolean isLineNumbers() {
        return lineNumbers;
    }

    /** Returns whether local-slot operands are annotated with {@code // name: descriptor} from the LocalVariableTable. */
    public boolean isLocalVariables() {
        return localVariables;
    }

    /** Returns whether the method's exception table is emitted. */
    public boolean isExceptionTable() {
        return exceptionTable;
    }

    /** Returns whether stack-map frame markers from the StackMapTable are interleaved. */
    public boolean isStackMapFrames() {
        return stackMapFrames;
    }

    /** Returns whether each invokedynamic's bootstrap method handle and static arguments are resolved. */
    public boolean isResolveBootstraps() {
        return resolveBootstraps;
    }

    public DisassemblyOptions withHeader(boolean header) {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    public DisassemblyOptions withLineNumbers(boolean lineNumbers) {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    public DisassemblyOptions withLocalVariables(boolean localVariables) {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    public DisassemblyOptions withExceptionTable(boolean exceptionTable) {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    public DisassemblyOptions withStackMapFrames(boolean stackMapFrames) {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    public DisassemblyOptions withResolveBootstraps(boolean resolveBootstraps) {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

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

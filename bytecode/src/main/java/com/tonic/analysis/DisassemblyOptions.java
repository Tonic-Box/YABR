package com.tonic.analysis;

/**
 * Immutable flag set selecting which enrichments {@link CodePrinter} adds to a method disassembly,
 * built from {@link #terse()} or {@link #verbose()} and refined with the {@code with*} copies.
 */
public final class DisassemblyOptions
{

    private final boolean header;
    private final boolean lineNumbers;
    private final boolean localVariables;
    private final boolean exceptionTable;
    private final boolean stackMapFrames;
    private final boolean resolveBootstraps;

    private DisassemblyOptions(boolean header, boolean lineNumbers, boolean localVariables, boolean exceptionTable, boolean stackMapFrames, boolean resolveBootstraps)
    {
        this.header = header;
        this.lineNumbers = lineNumbers;
        this.localVariables = localVariables;
        this.exceptionTable = exceptionTable;
        this.stackMapFrames = stackMapFrames;
        this.resolveBootstraps = resolveBootstraps;
    }

    /**
     * @return whether a {@code max_stack}/{@code max_locals} header line is emitted
     */
    public boolean isHeader()
    {
        return header;
    }

    /**
     * @return whether {@code // line N} comments from the LineNumberTable are interleaved
     */
    public boolean isLineNumbers()
    {
        return lineNumbers;
    }

    /**
     * @return whether local-slot operands are annotated with {@code // name: descriptor} from the LocalVariableTable
     */
    public boolean isLocalVariables()
    {
        return localVariables;
    }

    /**
     * @return whether the method's exception table is emitted
     */
    public boolean isExceptionTable()
    {
        return exceptionTable;
    }

    /**
     * @return whether stack-map frame markers from the StackMapTable are interleaved
     */
    public boolean isStackMapFrames()
    {
        return stackMapFrames;
    }

    /**
     * @return whether each invokedynamic's bootstrap method handle and static arguments are resolved
     */
    public boolean isResolveBootstraps()
    {
        return resolveBootstraps;
    }

    /**
     * Derives a copy with the header flag replaced.
     * @param header whether to emit the {@code max_stack}/{@code max_locals} header line
     * @return the derived options
     */
    public DisassemblyOptions withHeader(boolean header)
    {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    /**
     * Derives a copy with the line-number flag replaced.
     * @param lineNumbers whether to interleave {@code // line N} comments
     * @return the derived options
     */
    public DisassemblyOptions withLineNumbers(boolean lineNumbers)
    {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    /**
     * Derives a copy with the local-variable flag replaced.
     * @param localVariables whether to annotate local-slot operands from the LocalVariableTable
     * @return the derived options
     */
    public DisassemblyOptions withLocalVariables(boolean localVariables)
    {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    /**
     * Derives a copy with the exception-table flag replaced.
     * @param exceptionTable whether to emit the method's exception table
     * @return the derived options
     */
    public DisassemblyOptions withExceptionTable(boolean exceptionTable)
    {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    /**
     * Derives a copy with the stack-map-frame flag replaced.
     * @param stackMapFrames whether to interleave stack-map frame markers
     * @return the derived options
     */
    public DisassemblyOptions withStackMapFrames(boolean stackMapFrames)
    {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    /**
     * Derives a copy with the bootstrap-resolution flag replaced.
     * @param resolveBootstraps whether to resolve each invokedynamic's bootstrap handle and static arguments
     * @return the derived options
     */
    public DisassemblyOptions withResolveBootstraps(boolean resolveBootstraps)
    {
        return new DisassemblyOptions(header, lineNumbers, localVariables, exceptionTable, stackMapFrames, resolveBootstraps);
    }

    /**
     * Creates options with every enrichment disabled.
     * @return the bare instruction-listing options
     */
    public static DisassemblyOptions terse()
    {
        return new DisassemblyOptions(false, false, false, false, false, false);
    }

    /**
     * Creates options with every enrichment enabled.
     * @return the fully enriched options
     */
    public static DisassemblyOptions verbose()
    {
        return new DisassemblyOptions(true, true, true, true, true, true);
    }
}

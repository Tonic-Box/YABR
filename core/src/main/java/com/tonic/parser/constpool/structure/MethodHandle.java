package com.tonic.parser.constpool.structure;

/**
 * The reference kind and pool index pair that makes up a CONSTANT_MethodHandle entry.
 */
public class MethodHandle
{
    private final int referenceKind;
    private final int referenceIndex;

    /**
     * Creates a method handle descriptor.
     * @param referenceKind the JVMS reference kind, 1 through 9
     * @param referenceIndex pool index of the field, method or interface method referred to
     */
    public MethodHandle(int referenceKind, int referenceIndex)
    {
        this.referenceKind = referenceKind;
        this.referenceIndex = referenceIndex;
    }

    /**
     * @return the reference kind
     */
    public int getReferenceKind()
    {
        return referenceKind;
    }

    /**
     * @return the reference index
     */
    public int getReferenceIndex()
    {
        return referenceIndex;
    }

    @Override
    public String toString()
    {
        return "MethodHandle{" +
                "referenceKind=" + referenceKind +
                ", referenceIndex=" + referenceIndex +
                '}';
    }
}

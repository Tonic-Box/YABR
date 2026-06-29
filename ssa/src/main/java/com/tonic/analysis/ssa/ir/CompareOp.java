package com.tonic.analysis.ssa.ir;

/**
 * Comparison operations for branches.
 */
public enum CompareOp {
    /** Equal. */
    EQ,
    /** Not equal. */
    NE,
    /** Less than. */
    LT,
    /** Greater or equal. */
    GE,
    /** Greater than. */
    GT,
    /** Less or equal. */
    LE,
    /** If equal to zero. */
    IFEQ,
    /** If not equal to zero. */
    IFNE,
    /** If less than zero. */
    IFLT,
    /** If greater or equal to zero. */
    IFGE,
    /** If greater than zero. */
    IFGT,
    /** If less or equal to zero. */
    IFLE,
    /** If reference is null. */
    IFNULL,
    /** If reference is not null. */
    IFNONNULL,
    /** If references are equal. */
    ACMPEQ,
    /** If references are not equal. */
    ACMPNE;

    /** The logically-opposite comparison (the branch that fires exactly when this one would not). */
    public CompareOp invert() {
        switch (this) {
            case EQ: return NE;
            case NE: return EQ;
            case LT: return GE;
            case GE: return LT;
            case GT: return LE;
            case LE: return GT;
            case IFEQ: return IFNE;
            case IFNE: return IFEQ;
            case IFLT: return IFGE;
            case IFGE: return IFLT;
            case IFGT: return IFLE;
            case IFLE: return IFGT;
            case IFNULL: return IFNONNULL;
            case IFNONNULL: return IFNULL;
            case ACMPEQ: return ACMPNE;
            case ACMPNE: return ACMPEQ;
            default: throw new IllegalStateException("No inverse for " + this);
        }
    }
}

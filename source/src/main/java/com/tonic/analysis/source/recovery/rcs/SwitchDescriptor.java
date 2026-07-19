package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.List;
import java.util.Set;

/**
 * A decoded, structuring-ready view of a native int/enum {@code switch}: everything the switch decoders own
 * (selector expression, enum-constant labels, merge block, case ordering) resolved up front, so the
 * reaching-condition engine can structure the case bodies itself rather than delegating them to the opaque
 * legacy switch recovery. Produced by {@link RegionRecoveryBridge#decodeSwitch(IRBlock)}, which returns null
 * for shapes the engine does not own (string, pattern, comparison-chain synthesized switches).
 */
public final class SwitchDescriptor {

    private final IRBlock header;
    private final Expression selector;
    private final IRBlock merge;
    private final List<CaseSpec> cases;
    private final Set<IRBlock> caseHeaders;

    public SwitchDescriptor(IRBlock header, Expression selector, IRBlock merge, List<CaseSpec> cases,
                            Set<IRBlock> caseHeaders) {
        this.header = header;
        this.selector = selector;
        this.merge = merge;
        this.cases = cases;
        this.caseHeaders = caseHeaders;
    }

    /** The switch block itself. */
    public IRBlock header() {
        return header;
    }

    /** The recovered selector expression (enum variable, {@code e.ordinal()}, or the raw key). */
    public Expression selector() {
        return selector;
    }

    /** The block where control resumes after the switch, or null when every case exits. */
    public IRBlock merge() {
        return merge;
    }

    /** The cases in source order; the default, if any, is last. */
    public List<CaseSpec> cases() {
        return cases;
    }

    /** Every distinct case-body entry block (the sibling stop-set), excluding the merge. */
    public Set<IRBlock> caseHeaders() {
        return caseHeaders;
    }

    /**
     * One case of a decoded switch. A non-default case carries either integer labels or (for an enum switch whose
     * constants resolved) expression labels, and the entry block of its body. The default case has {@code isDefault}
     * set and a null {@code header} when it is empty (its target is the merge).
     */
    public static final class CaseSpec {
        private final List<Integer> intLabels;
        private final List<Expression> exprLabels;
        private final boolean isDefault;
        private final IRBlock header;

        public CaseSpec(List<Integer> intLabels, List<Expression> exprLabels, boolean isDefault, IRBlock header) {
            this.intLabels = intLabels;
            this.exprLabels = exprLabels;
            this.isDefault = isDefault;
            this.header = header;
        }

        public List<Integer> intLabels() {
            return intLabels;
        }

        public List<Expression> exprLabels() {
            return exprLabels;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public IRBlock header() {
            return header;
        }
    }
}

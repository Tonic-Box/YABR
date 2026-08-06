package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.List;
import java.util.Set;

/**
 * A decoded, structuring-ready view of a native int/enum {@code switch}.
 */
public final class SwitchDescriptor
{

    private final IRBlock header;
    private final Expression selector;
    private final IRBlock merge;
    private final List<CaseSpec> cases;
    private final Set<IRBlock> caseHeaders;
    private final boolean desugaredSelector;

    /**
     * Creates a descriptor whose header successors are the case bodies.
     *
     * @param header the switch block
     * @param selector the recovered selector expression
     * @param merge where control resumes after the switch, or null if every case exits
     * @param cases the cases in source order, default last
     * @param caseHeaders every distinct case-body entry block
     */
    public SwitchDescriptor(IRBlock header, Expression selector, IRBlock merge, List<CaseSpec> cases, Set<IRBlock> caseHeaders)
    {
        this(header, selector, merge, cases, caseHeaders, false);
    }

    /**
     * Creates a descriptor, stating whether the header's successors are dispatch
     * scaffolding rather than case bodies.
     *
     * @param header the switch block
     * @param selector the recovered selector expression
     * @param merge where control resumes after the switch, or null if every case exits
     * @param cases the cases in source order, default last
     * @param caseHeaders every distinct case-body entry block
     * @param desugaredSelector true when the raw successors are a desugared dispatch chain
     */
    public SwitchDescriptor(IRBlock header, Expression selector, IRBlock merge, List<CaseSpec> cases, Set<IRBlock> caseHeaders, boolean desugaredSelector)
    {
        this.header = header;
        this.selector = selector;
        this.merge = merge;
        this.cases = cases;
        this.caseHeaders = caseHeaders;
        this.desugaredSelector = desugaredSelector;
    }

    /**
     * @return true when the raw CFG edges out of the header are a desugared dispatch scaffold - a
     *         string switch's hashCode/equals chains - rather than the case bodies, so the model
     *         must follow this descriptor's case headers and merge instead of the raw successors
     */
    public boolean desugaredSelector()
    {
        return desugaredSelector;
    }

    /**
     * @return the switch block itself
     */
    public IRBlock header()
    {
        return header;
    }

    /**
     * @return the recovered selector expression - an enum variable, {@code e.ordinal()}, or the raw key
     */
    public Expression selector()
    {
        return selector;
    }

    /**
     * @return the block where control resumes after the switch, or null when every case exits
     */
    public IRBlock merge()
    {
        return merge;
    }

    /**
     * @return the cases in source order, with the default, if any, last
     */
    public List<CaseSpec> cases()
    {
        return cases;
    }

    /**
     * @return every distinct case-body entry block, the sibling stop-set, excluding the merge
     */
    public Set<IRBlock> caseHeaders()
    {
        return caseHeaders;
    }

    /**
     * One case of a decoded switch.
     */
    public static final class CaseSpec
    {
        private final List<Integer> intLabels;
        private final List<Expression> exprLabels;
        private final boolean isDefault;
        private final IRBlock header;

        public CaseSpec(List<Integer> intLabels, List<Expression> exprLabels, boolean isDefault, IRBlock header)
        {
            this.intLabels = intLabels;
            this.exprLabels = exprLabels;
            this.isDefault = isDefault;
            this.header = header;
        }

        /**
         * @return the integer labels of this case, empty when it carries expression labels
         */
        public List<Integer> intLabels()
        {
            return intLabels;
        }

        /**
         * @return the enum-constant labels of this case, empty when it carries integer labels
         */
        public List<Expression> exprLabels()
        {
            return exprLabels;
        }

        /**
         * @return true if this is the default case
         */
        public boolean isDefault()
        {
            return isDefault;
        }

        /**
         * @return the entry block of the case body, or null for an empty default
         */
        public IRBlock header()
        {
            return header;
        }
    }
}

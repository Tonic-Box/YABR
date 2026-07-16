package com.tonic.analysis.oracle;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.listener.CapableListener;
import com.tonic.analysis.execution.listener.ListenerCapability;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Records the set of conditional-branch outcomes taken during one execution, each as an encoded
 * {@code (branchPC, taken)} edge. Coverage-guided input generation uses this to tell whether a new
 * input reaches a branch direction not seen before.
 */
final class BranchCoverageListener implements CapableListener {

    private final Set<Long> edges = new HashSet<>();

    public Set<ListenerCapability> getCapabilities() {
        return EnumSet.of(ListenerCapability.BRANCH_OPERATIONS);
    }

    public void onBranch(StackFrame frame, int fromPC, int toPC, boolean taken) {
        edges.add(((long) fromPC << 1) | (taken ? 1L : 0L));
    }

    Set<Long> edges() {
        return edges;
    }
}

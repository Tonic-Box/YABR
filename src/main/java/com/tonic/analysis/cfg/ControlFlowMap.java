package com.tonic.analysis.cfg;

import java.util.List;
import java.util.Map;

/**
 * Represents the control flow map between basic blocks.
 */
public class ControlFlowMap {
    private final Map<BasicBlock, List<BasicBlock>> map;

    public ControlFlowMap(Map<BasicBlock, List<BasicBlock>> map) {
        this.map = map;
    }

    public Map<BasicBlock, List<BasicBlock>> getMap() {
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<BasicBlock, List<BasicBlock>> entry : map.entrySet()) {
            sb.append("Block ").append(entry.getKey().getId()).append(" -> ");
            List<BasicBlock> successors = entry.getValue();
            if (successors.isEmpty()) {
                sb.append("[]\n");
            } else {
                sb.append("[");
                for (int i = 0; i < successors.size(); i++) {
                    sb.append(successors.get(i).getId());
                    if (i < successors.size() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append("]\n");
            }
        }
        return sb.toString();
    }
}
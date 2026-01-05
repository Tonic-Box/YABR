package com.tonic.analysis.pdg.node;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class PDGRegionNode extends PDGNode {

    private final String regionName;
    private final Set<IRBlock> coveredBlocks;

    public PDGRegionNode(int id, PDGNodeType type, String regionName, IRBlock primaryBlock) {
        super(id, type, primaryBlock);
        this.regionName = regionName;
        this.coveredBlocks = new HashSet<>();
        if (primaryBlock != null) {
            coveredBlocks.add(primaryBlock);
        }
    }

    public static PDGRegionNode createEntry(int id, String methodName, IRBlock entryBlock) {
        return new PDGRegionNode(id, PDGNodeType.ENTRY, "ENTRY:" + methodName, entryBlock);
    }

    public static PDGRegionNode createExit(int id, String methodName, IRBlock exitBlock) {
        return new PDGRegionNode(id, PDGNodeType.EXIT, "EXIT:" + methodName, exitBlock);
    }

    public static PDGRegionNode createRegion(int id, String name, IRBlock block) {
        return new PDGRegionNode(id, PDGNodeType.REGION, name, block);
    }

    public void addCoveredBlock(IRBlock block) {
        coveredBlocks.add(block);
    }

    public boolean coversBlock(IRBlock block) {
        return coveredBlocks.contains(block);
    }

    @Override
    public String getLabel() {
        return regionName;
    }

    @Override
    public List<Value> getUsedValues() {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue() {
        return null;
    }

    public boolean isEntry() {
        return getType() == PDGNodeType.ENTRY;
    }

    public boolean isExit() {
        return getType() == PDGNodeType.EXIT;
    }

    @Override
    public String toString() {
        return String.format("PDGRegion[%d: %s]", getId(), regionName);
    }
}

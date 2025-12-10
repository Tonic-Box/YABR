package com.tonic.analysis.ssa.util;

import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Utility class for deep-cloning IR methods.
 * Creates fresh SSAValues and IRBlocks with proper mappings.
 * Used by method inlining to clone callee IR before splicing into caller.
 */
public class IRMethodCloner {

    private final Map<SSAValue, SSAValue> valueMapping;
    private final Map<IRBlock, IRBlock> blockMapping;
    private final String prefix;

    /**
     * Creates a new cloner with a prefix for renamed blocks.
     *
     * @param prefix prefix for cloned block names (e.g., "inline_")
     */
    public IRMethodCloner(String prefix) {
        this.valueMapping = new HashMap<>();
        this.blockMapping = new HashMap<>();
        this.prefix = prefix;
    }

    /**
     * Creates a new cloner with default prefix.
     */
    public IRMethodCloner() {
        this("cloned_");
    }

    /**
     * Clones an IRMethod, creating fresh copies of all blocks, instructions, and values.
     *
     * @param source the method to clone
     * @return a new IRMethod with cloned contents
     */
    public IRMethod clone(IRMethod source) {
        IRMethod cloned = new IRMethod(
                source.getOwnerClass(),
                source.getName(),
                source.getDescriptor(),
                source.isStatic()
        );

        // Clone parameters
        for (SSAValue param : source.getParameters()) {
            SSAValue clonedParam = cloneValue(param);
            cloned.addParameter(clonedParam);
        }

        // First pass: create all blocks (needed for edge references)
        for (IRBlock block : source.getBlocks()) {
            IRBlock clonedBlock = new IRBlock(prefix + block.getName());
            blockMapping.put(block, clonedBlock);
            cloned.addBlock(clonedBlock);
        }

        // Set entry block
        if (source.getEntryBlock() != null) {
            cloned.setEntryBlock(blockMapping.get(source.getEntryBlock()));
        }

        // Second pass: clone instructions and set up edges
        for (IRBlock block : source.getBlocks()) {
            IRBlock clonedBlock = blockMapping.get(block);

            // Clone phi instructions
            for (PhiInstruction phi : block.getPhiInstructions()) {
                PhiInstruction clonedPhi = clonePhi(phi);
                clonedBlock.addPhi(clonedPhi);
            }

            // Clone regular instructions
            for (IRInstruction instr : block.getInstructions()) {
                IRInstruction clonedInstr = cloneInstruction(instr);
                clonedBlock.addInstruction(clonedInstr);
            }

            // Set up successor edges
            for (IRBlock succ : block.getSuccessors()) {
                IRBlock clonedSucc = blockMapping.get(succ);
                EdgeType edgeType = block.getEdgeType(succ);
                clonedBlock.addSuccessor(clonedSucc, edgeType);
            }
        }

        // Third pass: fix phi incoming block references
        for (IRBlock block : source.getBlocks()) {
            IRBlock clonedBlock = blockMapping.get(block);
            for (int i = 0; i < block.getPhiInstructions().size(); i++) {
                PhiInstruction originalPhi = block.getPhiInstructions().get(i);
                PhiInstruction clonedPhi = clonedBlock.getPhiInstructions().get(i);

                // Re-add incoming values with correct block references
                for (IRBlock incomingBlock : originalPhi.getIncomingBlocks()) {
                    Value originalValue = originalPhi.getIncoming(incomingBlock);
                    Value clonedValue = mapValue(originalValue);
                    IRBlock clonedIncomingBlock = blockMapping.get(incomingBlock);
                    clonedPhi.addIncoming(clonedValue, clonedIncomingBlock);
                }
            }
        }

        cloned.setReturnType(source.getReturnType());
        cloned.setMaxLocals(source.getMaxLocals());
        cloned.setMaxStack(source.getMaxStack());

        return cloned;
    }

    /**
     * Gets the value mapping from original to cloned values.
     *
     * @return unmodifiable view of the value mapping
     */
    public Map<SSAValue, SSAValue> getValueMapping() {
        return Collections.unmodifiableMap(valueMapping);
    }

    /**
     * Gets the block mapping from original to cloned blocks.
     *
     * @return unmodifiable view of the block mapping
     */
    public Map<IRBlock, IRBlock> getBlockMapping() {
        return Collections.unmodifiableMap(blockMapping);
    }

    /**
     * Clones an SSAValue, creating a fresh value with the same type.
     */
    private SSAValue cloneValue(SSAValue original) {
        if (valueMapping.containsKey(original)) {
            return valueMapping.get(original);
        }
        SSAValue cloned = new SSAValue(original.getType());
        valueMapping.put(original, cloned);
        return cloned;
    }

    /**
     * Maps a value (SSAValue or Constant) to its cloned counterpart.
     * Constants are returned as-is since they're immutable.
     */
    private Value mapValue(Value value) {
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            if (valueMapping.containsKey(ssa)) {
                return valueMapping.get(ssa);
            }
            // Create new mapping if not exists
            return cloneValue(ssa);
        }
        // Constants are immutable, return as-is
        return value;
    }

    /**
     * Maps a list of values.
     */
    private List<Value> mapValues(List<Value> values) {
        List<Value> mapped = new ArrayList<>(values.size());
        for (Value v : values) {
            mapped.add(mapValue(v));
        }
        return mapped;
    }

    /**
     * Clones a phi instruction (without incoming values - added in third pass).
     */
    private PhiInstruction clonePhi(PhiInstruction phi) {
        SSAValue clonedResult = cloneValue(phi.getResult());
        return new PhiInstruction(clonedResult);
    }

    /**
     * Clones an instruction, creating a fresh copy with mapped values.
     */
    private IRInstruction cloneInstruction(IRInstruction instr) {
        // Handle each instruction type
        if (instr instanceof ConstantInstruction) {
            ConstantInstruction ci = (ConstantInstruction) instr;
            return new ConstantInstruction(
                    cloneValue(ci.getResult()),
                    ci.getConstant()
            );
        }

        if (instr instanceof CopyInstruction) {
            CopyInstruction ci = (CopyInstruction) instr;
            return new CopyInstruction(
                    cloneValue(ci.getResult()),
                    mapValue(ci.getSource())
            );
        }

        if (instr instanceof BinaryOpInstruction) {
            BinaryOpInstruction bi = (BinaryOpInstruction) instr;
            return new BinaryOpInstruction(
                    cloneValue(bi.getResult()),
                    bi.getOp(),
                    mapValue(bi.getLeft()),
                    mapValue(bi.getRight())
            );
        }

        if (instr instanceof UnaryOpInstruction) {
            UnaryOpInstruction ui = (UnaryOpInstruction) instr;
            return new UnaryOpInstruction(
                    cloneValue(ui.getResult()),
                    ui.getOp(),
                    mapValue(ui.getOperand())
            );
        }

        if (instr instanceof ReturnInstruction) {
            ReturnInstruction ri = (ReturnInstruction) instr;
            if (ri.isVoidReturn()) {
                return new ReturnInstruction();
            }
            return new ReturnInstruction(mapValue(ri.getReturnValue()));
        }

        if (instr instanceof GotoInstruction) {
            GotoInstruction gi = (GotoInstruction) instr;
            IRBlock target = blockMapping.get(gi.getTarget());
            return new GotoInstruction(target);
        }

        if (instr instanceof BranchInstruction) {
            BranchInstruction bi = (BranchInstruction) instr;
            return new BranchInstruction(
                    bi.getCondition(),
                    mapValue(bi.getLeft()),
                    mapValue(bi.getRight()),
                    blockMapping.get(bi.getTrueTarget()),
                    blockMapping.get(bi.getFalseTarget())
            );
        }

        if (instr instanceof SwitchInstruction) {
            SwitchInstruction si = (SwitchInstruction) instr;
            SwitchInstruction cloned = new SwitchInstruction(
                    mapValue(si.getKey()),
                    blockMapping.get(si.getDefaultTarget())
            );
            for (Map.Entry<Integer, IRBlock> entry : si.getCases().entrySet()) {
                cloned.addCase(entry.getKey(), blockMapping.get(entry.getValue()));
            }
            return cloned;
        }

        if (instr instanceof InvokeInstruction) {
            InvokeInstruction ii = (InvokeInstruction) instr;
            List<Value> args = mapValues(ii.getArguments());
            if (ii.getResult() != null) {
                return new InvokeInstruction(
                        cloneValue(ii.getResult()),
                        ii.getInvokeType(),
                        ii.getOwner(),
                        ii.getName(),
                        ii.getDescriptor(),
                        args,
                        ii.getOriginalCpIndex()
                );
            } else {
                return new InvokeInstruction(
                        ii.getInvokeType(),
                        ii.getOwner(),
                        ii.getName(),
                        ii.getDescriptor(),
                        args,
                        ii.getOriginalCpIndex()
                );
            }
        }

        if (instr instanceof NewInstruction) {
            NewInstruction ni = (NewInstruction) instr;
            return new NewInstruction(
                    cloneValue(ni.getResult()),
                    ni.getClassName()
            );
        }

        if (instr instanceof NewArrayInstruction) {
            NewArrayInstruction nai = (NewArrayInstruction) instr;
            List<Value> dims = mapValues(nai.getDimensions());
            if (dims.size() == 1) {
                return new NewArrayInstruction(
                        cloneValue(nai.getResult()),
                        nai.getElementType(),
                        dims.get(0)
                );
            } else {
                return new NewArrayInstruction(
                        cloneValue(nai.getResult()),
                        nai.getElementType(),
                        dims
                );
            }
        }

        if (instr instanceof ArrayLoadInstruction) {
            ArrayLoadInstruction ali = (ArrayLoadInstruction) instr;
            return new ArrayLoadInstruction(
                    cloneValue(ali.getResult()),
                    mapValue(ali.getArray()),
                    mapValue(ali.getIndex())
            );
        }

        if (instr instanceof ArrayStoreInstruction) {
            ArrayStoreInstruction asi = (ArrayStoreInstruction) instr;
            return new ArrayStoreInstruction(
                    mapValue(asi.getArray()),
                    mapValue(asi.getIndex()),
                    mapValue(asi.getValue())
            );
        }

        if (instr instanceof ArrayLengthInstruction) {
            ArrayLengthInstruction ali = (ArrayLengthInstruction) instr;
            return new ArrayLengthInstruction(
                    cloneValue(ali.getResult()),
                    mapValue(ali.getArray())
            );
        }

        if (instr instanceof GetFieldInstruction) {
            GetFieldInstruction gfi = (GetFieldInstruction) instr;
            if (gfi.isStatic()) {
                return new GetFieldInstruction(
                        cloneValue(gfi.getResult()),
                        gfi.getOwner(),
                        gfi.getName(),
                        gfi.getDescriptor()
                );
            } else {
                return new GetFieldInstruction(
                        cloneValue(gfi.getResult()),
                        gfi.getOwner(),
                        gfi.getName(),
                        gfi.getDescriptor(),
                        mapValue(gfi.getObjectRef())
                );
            }
        }

        if (instr instanceof PutFieldInstruction) {
            PutFieldInstruction pfi = (PutFieldInstruction) instr;
            if (pfi.isStatic()) {
                return new PutFieldInstruction(
                        pfi.getOwner(),
                        pfi.getName(),
                        pfi.getDescriptor(),
                        mapValue(pfi.getValue())
                );
            } else {
                return new PutFieldInstruction(
                        pfi.getOwner(),
                        pfi.getName(),
                        pfi.getDescriptor(),
                        mapValue(pfi.getObjectRef()),
                        mapValue(pfi.getValue())
                );
            }
        }

        if (instr instanceof CastInstruction) {
            CastInstruction ci = (CastInstruction) instr;
            return new CastInstruction(
                    cloneValue(ci.getResult()),
                    mapValue(ci.getObjectRef()),
                    ci.getTargetType()
            );
        }

        if (instr instanceof InstanceOfInstruction) {
            InstanceOfInstruction ioi = (InstanceOfInstruction) instr;
            return new InstanceOfInstruction(
                    cloneValue(ioi.getResult()),
                    mapValue(ioi.getObjectRef()),
                    ioi.getCheckType()
            );
        }

        if (instr instanceof ThrowInstruction) {
            ThrowInstruction ti = (ThrowInstruction) instr;
            return new ThrowInstruction(mapValue(ti.getException()));
        }

        if (instr instanceof MonitorEnterInstruction) {
            MonitorEnterInstruction mei = (MonitorEnterInstruction) instr;
            return new MonitorEnterInstruction(mapValue(mei.getObjectRef()));
        }

        if (instr instanceof MonitorExitInstruction) {
            MonitorExitInstruction mxi = (MonitorExitInstruction) instr;
            return new MonitorExitInstruction(mapValue(mxi.getObjectRef()));
        }

        if (instr instanceof LoadLocalInstruction) {
            LoadLocalInstruction lli = (LoadLocalInstruction) instr;
            return new LoadLocalInstruction(
                    cloneValue(lli.getResult()),
                    lli.getLocalIndex()
            );
        }

        if (instr instanceof StoreLocalInstruction) {
            StoreLocalInstruction sli = (StoreLocalInstruction) instr;
            return new StoreLocalInstruction(
                    sli.getLocalIndex(),
                    mapValue(sli.getValue())
            );
        }

        throw new IllegalArgumentException("Unknown instruction type: " + instr.getClass().getName());
    }
}

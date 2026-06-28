package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.lower.CopyInfo;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * Represents a method in SSA form with control flow graph and instructions.
 */
public class IRMethod {

    private final String ownerClass;
    private final String name;
    private final String descriptor;
    private final boolean isStatic;

    private MethodEntry sourceMethod;

    private final List<SSAValue> parameters;
    private final List<SourceLocal> sourceLocals = new ArrayList<>();
    private final List<IRBlock> blocks;
    private IRBlock entryBlock;

    private final List<ExceptionHandler> exceptionHandlers;
    private final Map<IRBlock, SSAValue> handlerExceptionValues = new HashMap<>();

    private IRType returnType;
    private int maxLocals;
    private int maxStack;

    private Map<SSAValue, List<CopyInfo>> phiCopyMapping;

    /**
     * Creates a new IR method.
     *
     * @param ownerClass the class containing this method
     * @param name the method name
     * @param descriptor the method descriptor
     * @param isStatic whether the method is static
     */
    public IRMethod(String ownerClass, String name, String descriptor, boolean isStatic) {
        this.ownerClass = ownerClass;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.parameters = new ArrayList<>();
        this.blocks = new ArrayList<>();
        this.exceptionHandlers = new ArrayList<>();
    }

    public String getOwnerClass() {
        return ownerClass;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public MethodEntry getSourceMethod() {
        return sourceMethod;
    }

    public void setSourceMethod(MethodEntry sourceMethod) {
        this.sourceMethod = sourceMethod;
    }

    public List<SSAValue> getParameters() {
        return parameters;
    }

    /**
     * Source-level locals (receiver, parameters, declared body variables) recorded during AST lowering,
     * used to emit a LocalVariableTable. Empty for IR not produced from source (e.g. the bytecode-lift path).
     */
    public List<SourceLocal> getSourceLocals() {
        return sourceLocals;
    }

    public List<IRBlock> getBlocks() {
        return blocks;
    }

    public IRBlock getEntryBlock() {
        return entryBlock;
    }

    public void setEntryBlock(IRBlock entryBlock) {
        this.entryBlock = entryBlock;
    }

    public List<ExceptionHandler> getExceptionHandlers() {
        return exceptionHandlers;
    }

    public Map<IRBlock, SSAValue> getHandlerExceptionValues() {
        return handlerExceptionValues;
    }

    public IRType getReturnType() {
        return returnType;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public void setMaxLocals(int maxLocals) {
        this.maxLocals = maxLocals;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public void setMaxStack(int maxStack) {
        this.maxStack = maxStack;
    }

    /**
     * Mapping from phi results to their copy instructions for register coalescing.
     * Used by RegisterAllocator to coalesce phi copies into the same register.
     */
    public Map<SSAValue, List<CopyInfo>> getPhiCopyMapping() {
        return phiCopyMapping;
    }

    public void setPhiCopyMapping(Map<SSAValue, List<CopyInfo>> phiCopyMapping) {
        this.phiCopyMapping = phiCopyMapping;
    }

    /**
     * Adds a parameter to this method.
     *
     * @param param the parameter SSA value
     */
    public void addParameter(SSAValue param) {
        parameters.add(param);
    }

    /** Records a source-level local (or appends an SSA value to an existing one); see {@link SourceLocal}. */
    public void addSourceLocal(SourceLocal local) {
        sourceLocals.add(local);
    }

    /**
     * A source-declared variable (the receiver, a parameter, or a body local) and the SSA value(s) it lowered
     * to. Carries the real source name + declared type so the lowerer can emit a LocalVariableTable; the slot
     * and scope are resolved later from register allocation and the final bytecode layout.
     */
    public static final class SourceLocal {
        private final String name;
        private final IRType type;
        private final List<SSAValue> values = new ArrayList<>();
        private final boolean parameter;

        public SourceLocal(String name, IRType type, boolean parameter) {
            this.name = name;
            this.type = type;
            this.parameter = parameter;
        }

        public String getName() {
            return name;
        }

        public IRType getType() {
            return type;
        }

        public List<SSAValue> getValues() {
            return values;
        }

        public boolean isParameter() {
            return parameter;
        }

        /** Adds another SSA value bound to this source variable (SSA splits one source var across defs). */
        public void addValue(SSAValue value) {
            values.add(value);
        }
    }

    /**
     * Adds a basic block to this method.
     *
     * @param block the block to add
     */
    public void addBlock(IRBlock block) {
        block.setMethod(this);
        blocks.add(block);
    }

    /**
     * Removes a basic block from this method and updates CFG edges.
     *
     * @param block the block to remove
     */
    public void removeBlock(IRBlock block) {
        blocks.remove(block);
        for (IRBlock pred : new ArrayList<>(block.getPredecessors())) {
            pred.removeSuccessor(block);
        }
        for (IRBlock succ : new ArrayList<>(block.getSuccessors())) {
            block.removeSuccessor(succ);
        }

        // Keep exception handlers consistent with the CFG: a removed block LEAVES the protected region
        // (shrink tryBlocks) rather than leaving a stale reference that the lowerer would later drop,
        // collapsing the try range. tryStart/tryEnd are nulled but the handler survives as long as its
        // region (tryBlocks) is non-empty — losing tryStart must not delete a still-protected region.
        for (ExceptionHandler h : exceptionHandlers) {
            if (h.getTryBlocks() != null) {
                h.getTryBlocks().remove(block);
            }
            if (h.getTryStart() == block) {
                h.setTryStart(null);
            }
            if (h.getTryEnd() == block) {
                h.setTryEnd(null);
            }
        }
        // Drop a handler only when its catch target is gone or its protected region is now empty.
        exceptionHandlers.removeIf(h -> h.getHandlerBlock() == block
                || (h.getTryBlocks() != null ? h.getTryBlocks().isEmpty() : h.getTryStart() == null));
    }

    /**
     * Adds an exception handler to this method.
     *
     * @param handler the exception handler
     */
    public void addExceptionHandler(ExceptionHandler handler) {
        exceptionHandlers.add(handler);
    }

    /**
     * Records the caught-exception value for each handler block, as recovered by the bytecode lifter.
     *
     * @param values map from handler block to the SSA value holding the caught exception
     */
    public void setHandlerExceptionValues(Map<IRBlock, SSAValue> values) {
        handlerExceptionValues.clear();
        handlerExceptionValues.putAll(values);
    }

    /**
     * Returns the caught-exception value for a handler block, or null if none is known.
     *
     * @param handlerBlock the handler block
     * @return the SSA value holding the caught exception, or null
     */
    public SSAValue getHandlerExceptionValue(IRBlock handlerBlock) {
        return handlerExceptionValues.get(handlerBlock);
    }

    /**
     * Gets blocks in breadth-first order starting from entry block.
     *
     * @return ordered list of blocks
     */
    public List<IRBlock> getBlocksInOrder() {
        if (entryBlock == null) return new ArrayList<>(blocks);

        List<IRBlock> ordered = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(entryBlock);

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (visited.contains(block)) continue;
            visited.add(block);
            ordered.add(block);
            worklist.addAll(block.getSuccessors());
        }

        for (IRBlock block : blocks) {
            if (!visited.contains(block)) {
                ordered.add(block);
            }
        }

        return ordered;
    }

    /**
     * Gets blocks in post-order traversal.
     *
     * @return blocks in post-order
     */
    public List<IRBlock> getPostOrder() {
        List<IRBlock> postOrder = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();
        if (entryBlock != null) {
            postOrderDFS(entryBlock, visited, postOrder);
        }
        return postOrder;
    }

    private void postOrderDFS(IRBlock startBlock, Set<IRBlock> visited, List<IRBlock> result) {
        Deque<PostOrderWorkItem> stack = new ArrayDeque<>();
        stack.push(new PostOrderWorkItem(startBlock, false));

        while (!stack.isEmpty()) {
            PostOrderWorkItem item = stack.pop();
            IRBlock block = item.block;

            if (item.childrenProcessed) {
                result.add(block);
                continue;
            }

            if (visited.contains(block)) {
                continue;
            }
            visited.add(block);

            stack.push(new PostOrderWorkItem(block, true));

            for (IRBlock succ : block.getSuccessors()) {
                if (!visited.contains(succ)) {
                    stack.push(new PostOrderWorkItem(succ, false));
                }
            }
        }
    }

    private static class PostOrderWorkItem {
        final IRBlock block;
        final boolean childrenProcessed;

        PostOrderWorkItem(IRBlock block, boolean childrenProcessed) {
            this.block = block;
            this.childrenProcessed = childrenProcessed;
        }
    }

    /**
     * Gets blocks in reverse post-order traversal.
     *
     * @return blocks in reverse post-order
     */
    public List<IRBlock> getReversePostOrder() {
        List<IRBlock> rpo = getPostOrder();
        Collections.reverse(rpo);
        return rpo;
    }

    /**
     * Gets the number of basic blocks in this method.
     *
     * @return block count
     */
    public int getBlockCount() {
        return blocks.size();
    }

    /**
     * Gets the total number of instructions in this method.
     *
     * @return instruction count
     */
    public int getInstructionCount() {
        int count = 0;
        for (IRBlock block : blocks) {
            count += block.getPhiInstructions().size();
            count += block.getInstructions().size();
        }
        return count;
    }

    /**
     * Sets the return type of this method.
     *
     * @param returnType the IR return type
     */
    public void setReturnType(IRType returnType) {
        this.returnType = returnType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(isStatic ? "static " : "").append(ownerClass).append(".").append(name).append(descriptor).append(" {\n");

        for (IRBlock block : getBlocksInOrder()) {
            sb.append(block);
        }

        sb.append("}\n");
        return sb.toString();
    }
}

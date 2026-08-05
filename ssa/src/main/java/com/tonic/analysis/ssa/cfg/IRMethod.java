package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.lower.CopyInfo;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * A method in SSA form: its CFG blocks plus parameters, exception handlers, and lowering metadata.
 */
public class IRMethod
{

    private final String ownerClass;
    private final String name;
    private final String descriptor;
    private final boolean isStatic;

    private MethodEntry sourceMethod;

    private final List<SSAValue> parameters;
    private final List<SourceLocal> sourceLocals = new ArrayList<>();
    /** Pairs of SSA values the register allocator should place in ONE slot: a variable state a finally
     * handler reads together with the state live on normal flow. See addSlotAffinity. */
    private final List<SSAValue[]> slotAffinities = new ArrayList<>();
    private final List<IRBlock> blocks;
    private IRBlock entryBlock;

    private final List<ExceptionHandler> exceptionHandlers;
    private final Map<IRBlock, SSAValue> handlerExceptionValues = new HashMap<>();

    private IRType returnType;
    private int maxLocals;
    private int maxStack;

    private Map<SSAValue, List<CopyInfo>> phiCopyMapping;

    /**
     * Phi results kept stack-resident (not materialized to a local); their value lives on the operand stack.
     */
    private final Set<SSAValue> stackResidentPhiResults = new HashSet<>();
    /**
     * Incoming values of stack-resident phis; each is left on the operand stack by its predecessor block.
     */
    private final Set<SSAValue> stackResidentPhiIncomings = new HashSet<>();

    /**
     * Creates a new IR method.
     * @param ownerClass the class containing this method
     * @param name the method name
     * @param descriptor the method descriptor
     * @param isStatic whether the method is static
     */
    public IRMethod(String ownerClass, String name, String descriptor, boolean isStatic)
    {
        this.ownerClass = ownerClass;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.parameters = new ArrayList<>();
        this.blocks = new ArrayList<>();
        this.exceptionHandlers = new ArrayList<>();
    }

    /**
     * @return the owner class
     */
    public String getOwnerClass()
    {
        return ownerClass;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return whether static
     */
    public boolean isStatic()
    {
        return isStatic;
    }

    /**
     * @return the source method
     */
    public MethodEntry getSourceMethod()
    {
        return sourceMethod;
    }

    /**
     * @param sourceMethod the class-file method this IR was lifted from
     */
    public void setSourceMethod(MethodEntry sourceMethod)
    {
        this.sourceMethod = sourceMethod;
    }

    /**
     * @return the parameters
     */
    public List<SSAValue> getParameters()
    {
        return parameters;
    }

    /**
     * Requests that two values share one local slot. Used by the try/finally lowering: the
     * synthetic finally handler evaluates the finally against a variable's pre-try value while normal flow
     * carries its in-try reassignment; unless both live in the variable's single slot, the handler reads a
     * stale value (a guarded close reading null on the exception path, leaking the resource).
     *
     * @param a one value of the pair; ignored if null
     * @param b the other value of the pair; ignored if null or identical to a
     */
    public void addSlotAffinity(SSAValue a, SSAValue b)
    {
        if (a != null && b != null && a != b)
        {
            slotAffinities.add(new SSAValue[]{a, b});
        }
    }

    /**
     * @return the slot affinities
     */
    public List<SSAValue[]> getSlotAffinities()
    {
        return slotAffinities;
    }

    /**
     * @return the source locals
     */
    public List<SourceLocal> getSourceLocals()
    {
        return sourceLocals;
    }

    /**
     * @return the blocks
     */
    public List<IRBlock> getBlocks()
    {
        return blocks;
    }

    /**
     * @return the entry block
     */
    public IRBlock getEntryBlock()
    {
        return entryBlock;
    }

    /**
     * @param entryBlock the entry block of the CFG
     */
    public void setEntryBlock(IRBlock entryBlock)
    {
        this.entryBlock = entryBlock;
    }

    /**
     * @return the exception handlers
     */
    public List<ExceptionHandler> getExceptionHandlers()
    {
        return exceptionHandlers;
    }

    /**
     * @return the handler exception values
     */
    public Map<IRBlock, SSAValue> getHandlerExceptionValues()
    {
        return handlerExceptionValues;
    }

    /**
     * @return the return type
     */
    public IRType getReturnType()
    {
        return returnType;
    }

    /**
     * @return the max locals
     */
    public int getMaxLocals()
    {
        return maxLocals;
    }

    /**
     * @param maxLocals the number of local variable slots
     */
    public void setMaxLocals(int maxLocals)
    {
        this.maxLocals = maxLocals;
    }

    /**
     * @return the max stack
     */
    public int getMaxStack()
    {
        return maxStack;
    }

    /**
     * @param maxStack the maximum operand stack depth
     */
    public void setMaxStack(int maxStack)
    {
        this.maxStack = maxStack;
    }

    /**
     * @return the phi results mapped to their copy instructions, which the register allocator coalesces
     *         into one register
     */
    public Map<SSAValue, List<CopyInfo>> getPhiCopyMapping()
    {
        return phiCopyMapping;
    }

    /**
     * @param phiCopyMapping mapping from phi results to their copy instructions
     */
    public void setPhiCopyMapping(Map<SSAValue, List<CopyInfo>> phiCopyMapping)
    {
        this.phiCopyMapping = phiCopyMapping;
    }

    /**
     * @return the phi results kept on the operand stack across the merge instead of spilled to a local
     */
    public Set<SSAValue> getStackResidentPhiResults()
    {
        return stackResidentPhiResults;
    }

    /**
     * @return the incoming values of stack-resident phis, each left on the operand stack by its
     *         predecessor block
     */
    public Set<SSAValue> getStackResidentPhiIncomings()
    {
        return stackResidentPhiIncomings;
    }

    /**
     * Adds a parameter to this method.
     * @param param the parameter SSA value
     */
    public void addParameter(SSAValue param)
    {
        parameters.add(param);
    }

    /**
     * Records a source-level local (or appends an SSA value to an existing one); see {@link SourceLocal}.
     *
     * @param local the source local to record
     */
    public void addSourceLocal(SourceLocal local)
    {
        sourceLocals.add(local);
    }

    /**
     * Finds the source local a value was lowered from.
     *
     * @param value the SSA value to look up
     * @return the owning source local, or null if no local claims it
     */
    public SourceLocal sourceLocalOf(SSAValue value)
    {
        for (SourceLocal local : sourceLocals)
        {
            if (local.getValues().contains(value))
            {
                return local;
            }
        }
        return null;
    }

    /**
     * A source-declared variable (the receiver, a parameter, or a body local) and the SSA value(s) it lowered
     * to. Carries the real source name + declared type so the lowerer can emit a LocalVariableTable; the slot
     * and scope are resolved later from register allocation and the final bytecode layout.
     */
    public static final class SourceLocal
    {
        private final String name;
        private final IRType type;
        private final List<SSAValue> values = new ArrayList<>();
        private final boolean parameter;
        private String signature;

        public SourceLocal(String name, IRType type, boolean parameter)
        {
            this.name = name;
            this.type = type;
            this.parameter = parameter;
        }

        /**
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        /**
         * @return the generic signature of the declared type, or null when the declaration is not generic
         */
        public String getSignature()
        {
            return signature;
        }

        /**
         * @param signature the generic signature of the declared type, or null when not generic
         */
        public void setSignature(String signature)
        {
            this.signature = signature;
        }

        /**
         * @return the type
         */
        public IRType getType()
        {
            return type;
        }

        /**
         * @return the values
         */
        public List<SSAValue> getValues()
        {
            return values;
        }

        /**
         * @return whether parameter
         */
        public boolean isParameter()
        {
            return parameter;
        }

        /**
         * Adds another SSA value bound to this source variable (SSA splits one source var across defs).
         *
         * @param value the SSA value to bind
         */
        public void addValue(SSAValue value)
        {
            values.add(value);
        }
    }

    /**
     * Adds a basic block to this method.
     * @param block the block to add
     */
    public void addBlock(IRBlock block)
    {
        block.setMethod(this);
        blocks.add(block);
    }

    /**
     * Removes a basic block from this method and updates CFG edges.
     * @param block the block to remove
     */
    public void removeBlock(IRBlock block)
    {
        blocks.remove(block);
        for (IRBlock pred : new ArrayList<>(block.getPredecessors()))
        {
            pred.removeSuccessor(block);
        }
        for (IRBlock succ : new ArrayList<>(block.getSuccessors()))
        {
            block.removeSuccessor(succ);
        }

        // Keep exception handlers consistent with the CFG: a removed block LEAVES the protected region
        // (shrink tryBlocks) rather than leaving a stale reference that the lowerer would later drop,
        // collapsing the try range. tryStart/tryEnd are nulled but the handler survives as long as its
        // region (tryBlocks) is non-empty - losing tryStart must not delete a still-protected region.
        for (ExceptionHandler h : exceptionHandlers)
        {
            if (h.getTryBlocks() != null)
            {
                h.getTryBlocks().remove(block);
            }
            if (h.getTryStart() == block)
            {
                h.setTryStart(null);
            }
            if (h.getTryEnd() == block)
            {
                h.setTryEnd(null);
            }
        }
        // Drop a handler only when its catch target is gone or its protected region is now empty.
        exceptionHandlers.removeIf(h -> h.getHandlerBlock() == block
                || (h.getTryBlocks() != null ? h.getTryBlocks().isEmpty() : h.getTryStart() == null));
    }

    /**
     * Adds an exception handler to this method.
     * @param handler the exception handler
     */
    public void addExceptionHandler(ExceptionHandler handler)
    {
        exceptionHandlers.add(handler);
    }

    /**
     * Records the caught-exception value for each handler block, as recovered by the bytecode lifter.
     * @param values map from handler block to the SSA value holding the caught exception
     */
    public void setHandlerExceptionValues(Map<IRBlock, SSAValue> values)
    {
        handlerExceptionValues.clear();
        handlerExceptionValues.putAll(values);
    }

    /**
     * Returns the caught-exception value for a handler block, or null if none is known.
     * @param handlerBlock the handler block
     * @return the SSA value holding the caught exception, or null
     */
    public SSAValue getHandlerExceptionValue(IRBlock handlerBlock)
    {
        return handlerExceptionValues.get(handlerBlock);
    }

    /**
     * Gets blocks in breadth-first order starting from entry block.
     * @return ordered list of blocks
     */
    public List<IRBlock> getBlocksInOrder()
    {
        if (entryBlock == null) return new ArrayList<>(blocks);

        List<IRBlock> ordered = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(entryBlock);

        while (!worklist.isEmpty())
        {
            IRBlock block = worklist.poll();
            if (visited.contains(block)) continue;
            visited.add(block);
            ordered.add(block);
            worklist.addAll(block.getSuccessors());
        }

        for (IRBlock block : blocks)
        {
            if (!visited.contains(block))
            {
                ordered.add(block);
            }
        }

        return ordered;
    }

    /**
     * Gets blocks in post-order traversal.
     * @return blocks in post-order
     */
    public List<IRBlock> getPostOrder()
    {
        List<IRBlock> postOrder = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();
        if (entryBlock != null)
        {
            postOrderDFS(entryBlock, visited, postOrder);
        }
        return postOrder;
    }

    private void postOrderDFS(IRBlock startBlock, Set<IRBlock> visited, List<IRBlock> result)
    {
        Deque<PostOrderWorkItem> stack = new ArrayDeque<>();
        stack.push(new PostOrderWorkItem(startBlock, false));

        while (!stack.isEmpty())
        {
            PostOrderWorkItem item = stack.pop();
            IRBlock block = item.block;

            if (item.childrenProcessed)
            {
                result.add(block);
                continue;
            }

            if (visited.contains(block))
            {
                continue;
            }
            visited.add(block);

            stack.push(new PostOrderWorkItem(block, true));

            for (IRBlock succ : block.getSuccessors())
            {
                if (!visited.contains(succ))
                {
                    stack.push(new PostOrderWorkItem(succ, false));
                }
            }
        }
    }

    private static class PostOrderWorkItem
    {
        final IRBlock block;
        final boolean childrenProcessed;

        PostOrderWorkItem(IRBlock block, boolean childrenProcessed)
        {
            this.block = block;
            this.childrenProcessed = childrenProcessed;
        }
    }

    /**
     * Gets blocks in reverse post-order traversal.
     * @return blocks in reverse post-order
     */
    public List<IRBlock> getReversePostOrder()
    {
        List<IRBlock> rpo = getPostOrder();
        Collections.reverse(rpo);
        return rpo;
    }

    /**
     * Gets the number of basic blocks in this method.
     * @return block count
     */
    public int getBlockCount()
    {
        return blocks.size();
    }

    /**
     * Gets the total number of instructions in this method.
     * @return instruction count
     */
    public int getInstructionCount()
    {
        int count = 0;
        for (IRBlock block : blocks)
        {
            count += block.getPhiInstructions().size();
            count += block.getInstructions().size();
        }
        return count;
    }

    /**
     * Sets the return type of this method.
     * @param returnType the IR return type
     */
    public void setReturnType(IRType returnType)
    {
        this.returnType = returnType;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(isStatic ? "static " : "").append(ownerClass).append(".").append(name).append(descriptor).append(" {\n");

        for (IRBlock block : getBlocksInOrder())
        {
            sb.append(block);
        }

        sb.append("}\n");
        return sb.toString();
    }
}

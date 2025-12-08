package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.lower.CopyInfo;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Represents a method in SSA form with control flow graph and instructions.
 */
@Getter
public class IRMethod {

    private final String ownerClass;
    private final String name;
    private final String descriptor;
    private final boolean isStatic;

    @Setter
    private MethodEntry sourceMethod;

    private final List<SSAValue> parameters;
    private final List<IRBlock> blocks;
    @Setter
    private IRBlock entryBlock;

    private final List<ExceptionHandler> exceptionHandlers;

    private IRType returnType;
    @Setter
    private int maxLocals;
    @Setter
    private int maxStack;

    /**
     * Mapping from phi results to their copy instructions for register coalescing.
     * Used by RegisterAllocator to coalesce phi copies into the same register.
     */
    @Setter
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

    /**
     * Adds a parameter to this method.
     *
     * @param param the parameter SSA value
     */
    public void addParameter(SSAValue param) {
        parameters.add(param);
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

    private void postOrderDFS(IRBlock block, Set<IRBlock> visited, List<IRBlock> result) {
        if (visited.contains(block)) return;
        visited.add(block);
        for (IRBlock succ : block.getSuccessors()) {
            postOrderDFS(succ, visited, result);
        }
        result.add(block);
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

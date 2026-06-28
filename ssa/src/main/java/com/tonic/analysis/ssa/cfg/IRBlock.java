package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;

import java.util.*;

/**
 * Represents a basic block in SSA form containing phi instructions and regular instructions.
 */
public class IRBlock {

    private static final ThreadLocal<int[]> NEXT_ID = ThreadLocal.withInitial(() -> new int[1]);

    private final int id;
    private String name;
    private IRMethod method;

    private final List<PhiInstruction> phiInstructions;
    private final List<IRInstruction> instructions;

    private final Set<IRBlock> predecessors;
    private final Set<IRBlock> successors;
    private final Map<IRBlock, EdgeType> successorEdgeTypes;

    private final List<ExceptionHandler> exceptionHandlers;

    private int bytecodeOffset;

    /**
     * Creates a new basic block with an auto-generated name.
     */
    public IRBlock() {
        this.id = NEXT_ID.get()[0]++;
        this.name = "B" + id;
        this.phiInstructions = new ArrayList<>();
        this.instructions = new ArrayList<>();
        this.predecessors = new LinkedHashSet<>();
        this.successors = new LinkedHashSet<>();
        this.successorEdgeTypes = new HashMap<>();
        this.exceptionHandlers = new ArrayList<>();
        this.bytecodeOffset = -1;
    }

    /**
     * Creates a new basic block with the given name.
     *
     * @param name the block name
     */
    public IRBlock(String name) {
        this();
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IRMethod getMethod() {
        return method;
    }

    public void setMethod(IRMethod method) {
        this.method = method;
    }

    public List<PhiInstruction> getPhiInstructions() {
        return phiInstructions;
    }

    public List<IRInstruction> getInstructions() {
        return instructions;
    }

    public Set<IRBlock> getPredecessors() {
        return predecessors;
    }

    public Set<IRBlock> getSuccessors() {
        return successors;
    }

    public Map<IRBlock, EdgeType> getSuccessorEdgeTypes() {
        return successorEdgeTypes;
    }

    public List<ExceptionHandler> getExceptionHandlers() {
        return exceptionHandlers;
    }

    public int getBytecodeOffset() {
        return bytecodeOffset;
    }

    public void setBytecodeOffset(int bytecodeOffset) {
        this.bytecodeOffset = bytecodeOffset;
    }

    /**
     * Adds a phi instruction to this block.
     *
     * @param phi the phi instruction to add
     */
    public void addPhi(PhiInstruction phi) {
        phi.setBlock(this);
        phiInstructions.add(phi);
    }

    /**
     * Alias for addPhi to match common naming convention.
     *
     * @param phi the phi instruction to add
     */
    public void addPhiInstruction(PhiInstruction phi) {
        addPhi(phi);
    }

    /**
     * Removes a phi instruction from this block.
     *
     * @param phi the phi instruction to remove
     */
    public void removePhi(PhiInstruction phi) {
        phiInstructions.remove(phi);
    }

    /**
     * Adds an instruction to the end of this block.
     *
     * @param instruction the instruction to add
     */
    public void addInstruction(IRInstruction instruction) {
        instruction.setBlock(this);
        instructions.add(instruction);
    }

    /**
     * Inserts an instruction at the specified index.
     *
     * @param index the position to insert at
     * @param instruction the instruction to insert
     */
    public void insertInstruction(int index, IRInstruction instruction) {
        instruction.setBlock(this);
        instructions.add(index, instruction);
    }

    /**
     * Removes an instruction from this block.
     *
     * @param instruction the instruction to remove
     */
    public void removeInstruction(IRInstruction instruction) {
        instructions.remove(instruction);
    }

    /**
     * Adds a successor block with a normal edge.
     *
     * @param successor the successor block
     */
    public void addSuccessor(IRBlock successor) {
        addSuccessor(successor, EdgeType.NORMAL);
    }

    /**
     * Adds a successor block with the specified edge type.
     *
     * @param successor the successor block
     * @param edgeType the type of edge
     */
    public void addSuccessor(IRBlock successor, EdgeType edgeType) {
        if (successors.add(successor)) {
            successorEdgeTypes.put(successor, edgeType);
            successor.predecessors.add(this);
        }
    }

    /**
     * Removes a successor block and updates predecessor relationships.
     *
     * @param successor the successor block to remove
     */
    public void removeSuccessor(IRBlock successor) {
        successors.remove(successor);
        successorEdgeTypes.remove(successor);
        successor.predecessors.remove(this);
    }

    /**
     * Gets the edge type to a successor block.
     *
     * @param successor the successor block
     * @return the edge type
     */
    public EdgeType getEdgeType(IRBlock successor) {
        return successorEdgeTypes.getOrDefault(successor, EdgeType.NORMAL);
    }

    /**
     * Adds an exception handler to this block.
     *
     * @param handler the exception handler
     */
    public void addExceptionHandler(ExceptionHandler handler) {
        exceptionHandlers.add(handler);
    }

    /**
     * Gets the terminator instruction of this block.
     *
     * @return the terminator instruction, or null if none
     */
    public IRInstruction getTerminator() {
        if (instructions.isEmpty()) return null;
        IRInstruction last = instructions.get(instructions.size() - 1);
        return last.isTerminator() ? last : null;
    }

    /**
     * Sets the terminator instruction for this block.
     * If a terminator already exists, it will be replaced.
     *
     * @param terminator the terminator instruction to set
     */
    public void setTerminator(IRInstruction terminator) {
        if (!instructions.isEmpty()) {
            IRInstruction last = instructions.get(instructions.size() - 1);
            if (last.isTerminator()) {
                instructions.remove(instructions.size() - 1);
            }
        }
        addInstruction(terminator);
    }

    /**
     * Adds a predecessor block directly (for use in block duplication).
     *
     * @param pred the predecessor block
     */
    public void addPredecessor(IRBlock pred) {
        predecessors.add(pred);
    }

    /**
     * Removes a predecessor block directly.
     *
     * @param pred the predecessor block to remove
     */
    public void removePredecessor(IRBlock pred) {
        predecessors.remove(pred);
    }

    /**
     * Checks if this block has a terminator instruction.
     *
     * @return true if block has a terminator, false otherwise
     */
    public boolean hasTerminator() {
        return getTerminator() != null;
    }

    /**
     * Gets all instructions including phi instructions.
     *
     * @return combined list of phi and regular instructions
     */
    public List<IRInstruction> getAllInstructions() {
        List<IRInstruction> all = new ArrayList<>();
        all.addAll(phiInstructions);
        all.addAll(instructions);
        return all;
    }

    /**
     * Checks if this block is empty.
     *
     * @return true if no instructions, false otherwise
     */
    public boolean isEmpty() {
        return phiInstructions.isEmpty() && instructions.isEmpty();
    }

    /**
     * Checks if this is the entry block.
     *
     * @return true if this is the method's entry block, false otherwise
     */
    public boolean isEntry() {
        return predecessors.isEmpty() && method != null && method.getEntryBlock() == this;
    }

    /**
     * Checks if this is an exit block.
     *
     * @return true if block has no successors, false otherwise
     */
    public boolean isExit() {
        return successors.isEmpty();
    }

    /**
     * Resets the ID counter for basic blocks.
     */
    public static void resetIdCounter() {
        NEXT_ID.get()[0] = 0;
    }

    /**
     * Identity by {@code id}. Ids are unique within a method (the counter is reset
     * per lift in {@link com.tonic.analysis.ssa.lift.BytecodeLifter}), so this is
     * equivalent to object identity in every collection scope, while giving
     * deterministic hashing/iteration order across runs.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof IRBlock && ((IRBlock) o).id == id);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(":\n");

        for (PhiInstruction phi : phiInstructions) {
            sb.append("  ").append(phi).append("\n");
        }

        for (IRInstruction instr : instructions) {
            sb.append("  ").append(instr).append("\n");
        }

        return sb.toString();
    }

}

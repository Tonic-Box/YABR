package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.analysis.ssa.cfg.*;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.util.Modifiers;

import java.util.*;

import static com.tonic.util.Opcode.*;

/**
 * Lifts bytecode to SSA-form IR.
 */
public class BytecodeLifter {

    private final ConstPool constPool;
    private Map<IRBlock, Map<Integer, PhiInstruction>> stackPhiIndex;
    /** The exception value pushed onto each handler block's entry stack (the actual caught exception). */
    private Map<IRBlock, SSAValue> handlerExceptionValues;

    /**
     * Creates a new bytecode lifter.
     *
     * @param constPool the constant pool
     */
    public BytecodeLifter(ConstPool constPool) {
        this.constPool = constPool;
    }

    /**
     * Lifts a method from bytecode to SSA-form IR.
     *
     * @param method the method to lift
     * @return the SSA-form IR representation
     */
    public IRMethod lift(MethodEntry method) {
        SSAValue.resetIdCounter();
        IRBlock.resetIdCounter();
        IRInstruction.resetIdCounter();
        stackPhiIndex = new HashMap<>();
        handlerExceptionValues = new HashMap<>();

        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null) {
            return createEmptyMethod(method);
        }

        IRMethod irMethod = createIRMethod(method);
        CodeWriter codeWriter = new CodeWriter(method);

        List<Instruction> instructions = new ArrayList<>();
        for (Instruction instr : codeWriter.getInstructions()) {
            instructions.add(instr);
        }

        Set<Integer> blockStarts = findBlockBoundaries(instructions, codeAttr);
        Map<Integer, IRBlock> offsetToBlock = createBlocks(irMethod, blockStarts);

        ClassFile classFile = method.getClassFile();
        BootstrapMethodsAttribute bsmAttr = findBootstrapMethodsAttribute(classFile);
        InstructionTranslator translator = new InstructionTranslator(constPool, bsmAttr);
        for (Map.Entry<Integer, IRBlock> entry : offsetToBlock.entrySet()) {
            translator.registerBlock(entry.getKey(), entry.getValue());
        }

        initializeParameters(irMethod, method);
        translateInstructions(irMethod, instructions, offsetToBlock, translator, codeAttr);
        connectBlocks(irMethod);
        handleExceptionHandlers(irMethod, codeAttr, offsetToBlock);

        // Fix up PHI uses - ensure instructions use PHI values instead of raw incoming values
        fixupPhiUses(irMethod);

        irMethod.setHandlerExceptionValues(handlerExceptionValues);

        return irMethod;
    }

    /**
     * Re-types phi results to the common type of their incoming values when every non-null
     * incoming is the same primitive type. Iterates to a fixpoint so corrections propagate
     * through chained phis. Conservative: leaves reference phis and mixed-type (genuine pun)
     * phis untouched. Run after SSA renaming, once local-slot phis and their operands exist:
     * a slot reused for an Iterator then an int loop counter otherwise leaves the counter phi
     * typed by the slot's stale reference type, propagating an Object type-pun into the source.
     */
    public static void refinePhiTypes(IRMethod method) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 16) {
            changed = false;
            for (IRBlock block : method.getBlocks()) {
                for (PhiInstruction phi : new ArrayList<>(block.getPhiInstructions())) {
                    SSAValue result = phi.getResult();
                    if (result == null) {
                        continue;
                    }
                    IRType unified = uniformPrimitiveIncomingType(phi);
                    if (unified == null) {
                        unified = uniformReferenceIncomingType(phi);
                    }
                    if (unified != null && !unified.equals(result.getType())) {
                        SSAValue retyped = new SSAValue(unified, result.getName());
                        result.replaceAllUsesWith(retyped);
                        phi.setResult(retyped);
                        retyped.setDefinition(phi);
                        changed = true;
                    }
                }
            }
        }
    }

    /**
     * Returns the shared primitive type of the phi's incomings, or null if any concrete incoming is
     * non-primitive or the incomings disagree.
     *
     * <p>An incoming that is itself a phi of the same (still-unrefined) type as this phi is skipped: it is a
     * back-edge in a phi cycle, and counting it would deadlock a cycle whose only concrete seed is a
     * different primitive — e.g. a long loop variable seeded by a long constant but carried through phis that
     * defaulted to int. Across the refinement fixpoint the concrete seed's type then propagates around the
     * cycle. Concrete (non-phi) incomings always count, so a genuine primitive mix still blocks refinement.
     */
    private static IRType uniformPrimitiveIncomingType(PhiInstruction phi) {
        IRType resultType = phi.getResult() != null ? phi.getResult().getType() : null;
        PrimitiveType common = null;
        for (Value v : phi.getOperands()) {
            if (v == null) {
                continue;
            }
            IRType t = v.getType();
            if (t == resultType && v instanceof SSAValue
                    && ((SSAValue) v).getDefinition() instanceof PhiInstruction) {
                continue;
            }
            if (!(t instanceof PrimitiveType)) {
                return null;
            }
            if (common == null) {
                common = (PrimitiveType) t;
            } else if (common != t) {
                return null;
            }
        }
        return common;
    }

    /**
     * Unifies a reference phi's type without hierarchy knowledge: null-bottom incomings are
     * ignored, self-referential loop operands are skipped, and if every remaining incoming
     * carries one identical reference or array type, the phi adopts it. When the concrete
     * reference incomings disagree (e.g. a String constant merged with an Object value, as a
     * ternary produces), the phi widens to java/lang/Object rather than keeping a stale narrow
     * type that would pun an unrelated reference into a downcast.
     */
    private static IRType uniformReferenceIncomingType(PhiInstruction phi) {
        IRType resultType = phi.getResult() != null ? phi.getResult().getType() : null;
        IRType common = null;
        boolean disagree = false;
        for (Value v : phi.getOperands()) {
            if (v == null || isNullBottom(v)) {
                continue;
            }
            IRType t = v.getType();
            if (t == resultType && v instanceof SSAValue
                    && ((SSAValue) v).getDefinition() instanceof PhiInstruction) {
                continue;
            }
            if (t == null || !t.isReference()) {
                return null;
            }
            if (common == null) {
                common = t;
            } else if (!common.equals(t)) {
                disagree = true;
            }
        }
        if (common == null) {
            return null;
        }
        return disagree ? new ReferenceType("java/lang/Object") : common;
    }

    /**
     * Whether a phi operand is a null bottom, assignable to any reference and so ignorable when
     * unifying a reference phi's type. This is the bare {@link NullConstant} or an SSAValue defined
     * by a {@link ConstantInstruction} of it: {@code aconst_null} lifts to such a ConstantInstruction
     * with an Object-typed result, so a null reaching a phi through a local store arrives as that
     * Object-typed value rather than a bare NullConstant. Both must count as bottom, else a null
     * merged with a concrete reference or array type would spuriously widen the phi to Object.
     */
    private static boolean isNullBottom(Value v) {
        if (v instanceof NullConstant) {
            return true;
        }
        return v instanceof SSAValue
                && ((SSAValue) v).getDefinition() instanceof ConstantInstruction
                && ((ConstantInstruction) ((SSAValue) v).getDefinition()).getConstant() instanceof NullConstant;
    }

    private IRMethod createEmptyMethod(MethodEntry method) {
        return new IRMethod(
                method.getOwnerName(),
                method.getName(),
                method.getDesc(),
                Modifiers.isStatic(method.getAccess())
        );
    }

    private IRMethod createIRMethod(MethodEntry method) {
        IRMethod irMethod = new IRMethod(
                method.getOwnerName(),
                method.getName(),
                method.getDesc(),
                Modifiers.isStatic(method.getAccess())
        );
        irMethod.setSourceMethod(method);

        String desc = method.getDesc();
        String returnDesc = desc.substring(desc.indexOf(')') + 1);
        if (!returnDesc.equals("V")) {
            irMethod.setReturnType(IRType.fromDescriptor(returnDesc));
        } else {
            irMethod.setReturnType(VoidType.INSTANCE);
        }

        return irMethod;
    }

    private Set<Integer> findBlockBoundaries(List<Instruction> instructions, CodeAttribute codeAttr) {
        Set<Integer> blockStarts = new TreeSet<>();
        blockStarts.add(0);

        for (Instruction instr : instructions) {
            int offset = instr.getOffset();
            int opcode = instr.getOpcode();

            if (instr instanceof ConditionalBranchInstruction) {
                ConditionalBranchInstruction branch = (ConditionalBranchInstruction) instr;
                int target = offset + branch.getBranchOffset();
                int fallthrough = offset + branch.getLength();
                blockStarts.add(target);
                blockStarts.add(fallthrough);
            } else if (instr instanceof GotoInstruction) {
                GotoInstruction gotoInstr = (GotoInstruction) instr;
                int target = offset + gotoInstr.getBranchOffset();
                blockStarts.add(target);
                int fallthrough = offset + gotoInstr.getLength();
                if (fallthrough < getTotalCodeLength(instructions)) {
                    blockStarts.add(fallthrough);
                }
            } else if (instr instanceof JsrInstruction) {
                JsrInstruction jsrInstr = (JsrInstruction) instr;
                // JSR jumps to subroutine and eventually returns to continuation
                int subroutineTarget = offset + jsrInstr.getBranchOffset();
                int continuationOffset = offset + jsrInstr.getLength();
                blockStarts.add(subroutineTarget);
                blockStarts.add(continuationOffset);
            } else if (instr instanceof RetInstruction) {
                // RET is a terminator - block ends here
                int fallthrough = offset + instr.getLength();
                if (fallthrough < getTotalCodeLength(instructions)) {
                    blockStarts.add(fallthrough);
                }
            } else if (instr instanceof TableSwitchInstruction) {
                TableSwitchInstruction tableSwitch = (TableSwitchInstruction) instr;
                blockStarts.add(offset + tableSwitch.getDefaultOffset());
                for (int jumpOffset : tableSwitch.getJumpOffsets().values()) {
                    blockStarts.add(offset + jumpOffset);
                }
                int fallthrough = offset + tableSwitch.getLength();
                if (fallthrough < getTotalCodeLength(instructions)) {
                    blockStarts.add(fallthrough);
                }
            } else if (instr instanceof LookupSwitchInstruction) {
                LookupSwitchInstruction lookupSwitch = (LookupSwitchInstruction) instr;
                blockStarts.add(offset + lookupSwitch.getDefaultOffset());
                for (int jumpOffset : lookupSwitch.getMatchOffsets().values()) {
                    blockStarts.add(offset + jumpOffset);
                }
                int fallthrough = offset + lookupSwitch.getLength();
                if (fallthrough < getTotalCodeLength(instructions)) {
                    blockStarts.add(fallthrough);
                }
            } else if (isTerminator(opcode)) {
                int fallthrough = offset + instr.getLength();
                if (fallthrough < getTotalCodeLength(instructions)) {
                    blockStarts.add(fallthrough);
                }
            }
        }

        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            blockStarts.add(entry.getStartPc());
            blockStarts.add(entry.getEndPc());
            blockStarts.add(entry.getHandlerPc());
        }

        return blockStarts;
    }

    private int getTotalCodeLength(List<Instruction> instructions) {
        if (instructions.isEmpty()) return 0;
        Instruction last = instructions.get(instructions.size() - 1);
        return last.getOffset() + last.getLength();
    }

    private Map<Integer, IRBlock> createBlocks(IRMethod irMethod, Set<Integer> blockStarts) {
        Map<Integer, IRBlock> offsetToBlock = new TreeMap<>();

        for (int offset : blockStarts) {
            IRBlock block = new IRBlock("B" + offset);
            block.setBytecodeOffset(offset);
            irMethod.addBlock(block);
            offsetToBlock.put(offset, block);
        }

        if (!offsetToBlock.isEmpty()) {
            irMethod.setEntryBlock(offsetToBlock.get(offsetToBlock.keySet().iterator().next()));
        }

        return offsetToBlock;
    }

    private void initializeParameters(IRMethod irMethod, MethodEntry method) {
        boolean isStatic = Modifiers.isStatic(method.getAccess());
        String desc = method.getDesc();
        int localIndex = 0;

        if (!isStatic) {
            SSAValue thisParam = new SSAValue(new ReferenceType(method.getOwnerName()), "this");
            irMethod.addParameter(thisParam);
            localIndex++;
        }

        int i = 1;
        int paramNum = 0;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            IRType paramType;
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                String className = desc.substring(i + 1, end);
                paramType = new ReferenceType(className);
                i = end + 1;
            } else if (c == '[') {
                int start = i;
                while (desc.charAt(i) == '[') i++;
                if (desc.charAt(i) == 'L') {
                    int end = desc.indexOf(';', i);
                    i = end + 1;
                } else {
                    i++;
                }
                paramType = IRType.fromDescriptor(desc.substring(start, i));
            } else {
                paramType = IRType.fromDescriptor(String.valueOf(c));
                i++;
            }

            SSAValue param = new SSAValue(paramType, "p" + paramNum);
            irMethod.addParameter(param);
            paramNum++;

            localIndex++;
            if (paramType.isTwoSlot()) {
                localIndex++;
            }
        }

        irMethod.setMaxLocals(localIndex);
    }

    private void translateInstructions(IRMethod irMethod, List<Instruction> instructions,
                                       Map<Integer, IRBlock> offsetToBlock, InstructionTranslator translator,
                                       CodeAttribute codeAttr) {
        if (instructions.isEmpty()) return;

        AbstractState state = new AbstractState();
        initializeState(state, irMethod);

        Map<IRBlock, AbstractState> blockStates = new HashMap<>();
        Set<IRBlock> processedBlocks = new HashSet<>();

        IRBlock currentBlock = irMethod.getEntryBlock();
        blockStates.put(currentBlock, state.copy());

        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(currentBlock);

        // Also add exception handler blocks to the worklist
        // They're not reachable via normal control flow but need to be translated
        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            IRBlock handlerBlock = offsetToBlock.get(entry.getHandlerPc());
            if (handlerBlock != null && !worklist.contains(handlerBlock)) {
                // Initialize state for handler block - the exception is on the stack
                AbstractState handlerState = new AbstractState();
                initializeState(handlerState, irMethod);
                // Push the caught exception onto the stack
                ReferenceType catchType = null;
                if (entry.getCatchType() != 0) {
                    String className = constPool.getClassName(entry.getCatchType());
                    catchType = new ReferenceType(className);
                }
                SSAValue exceptionValue = new SSAValue(
                        catchType != null ? catchType : ReferenceType.THROWABLE,
                        "exc_" + handlerBlock.getName()
                );
                handlerState.push(exceptionValue);
                handlerExceptionValues.put(handlerBlock, exceptionValue);
                blockStates.put(handlerBlock, handlerState);
                worklist.add(handlerBlock);
            }
        }

        Map<Integer, Integer> offsetToInstrIndex = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            offsetToInstrIndex.put(instructions.get(i).getOffset(), i);
        }

        // Track source block for each successor's initial state (for PHI creation)
        Map<IRBlock, IRBlock> stateSourceBlocks = new HashMap<>();

        while (!worklist.isEmpty()) {
            currentBlock = worklist.poll();
            if (processedBlocks.contains(currentBlock)) continue;
            processedBlocks.add(currentBlock);

            state = blockStates.getOrDefault(currentBlock, new AbstractState()).copy();

            int startOffset = currentBlock.getBytecodeOffset();
            Integer startIndex = offsetToInstrIndex.get(startOffset);
            if (startIndex == null) continue;

            for (int i = startIndex; i < instructions.size(); i++) {
                Instruction instr = instructions.get(i);
                int offset = instr.getOffset();

                if (i > startIndex && offsetToBlock.containsKey(offset)) {
                    IRBlock nextBlock = offsetToBlock.get(offset);
                    if (!currentBlock.hasTerminator()) {
                        currentBlock.addInstruction(SimpleInstruction.createGoto(nextBlock));
                    }
                    if (!blockStates.containsKey(nextBlock)) {
                        blockStates.put(nextBlock, state.copy());
                        stateSourceBlocks.put(nextBlock, currentBlock);
                        worklist.add(nextBlock);
                    } else {
                        // Merge states when block already has state from another path
                        AbstractState existingState = blockStates.get(nextBlock);
                        IRBlock firstSourceBlock = stateSourceBlocks.get(nextBlock);
                        mergeStackWithPhis(state, existingState, nextBlock, currentBlock, firstSourceBlock);
                    }
                    break;
                }

                // Set debug context for error messages
                AbstractState.setDebugContext(currentBlock.getName(), offset);

                translator.translate(instr, state, currentBlock);

                if (currentBlock.hasTerminator()) {
                    propagateState(state, currentBlock, blockStates, worklist, stateSourceBlocks);
                    break;
                }
            }
        }
    }

    /**
     * Adds an exception edge from every protected (try) block to its handler block, returning the edges added
     * (caller → handler pairs) so they can be removed again with {@link #removeExceptionEdges}.
     *
     * <p>These edges are a transient scaffold for SSA construction only. Without them a handler is unreachable
     * from the entry, so it is absent from the dominator tree: {@code PhiInserter} never places phis there and
     * {@code VariableRenamer} never visits it, leaving the handler's {@code LoadLocal} placeholders for locals
     * live across the exception edge (method params like {@code this}, and try-body definitions) unrenamed —
     * the lowerer then allocates them stale registers and the handler reads slots that are never written.
     * Modelling the edge (an exception may transfer control from any protected block to the handler) lets
     * standard SSA construction merge those locals into the handler's entry via phis.
     *
     * <p>The edges are added only around phi-insertion + renaming and then removed: callers that walk the CFG
     * for normal control flow (e.g. the source-recovery decompiler's statement reconstruction) must not see
     * them, and the lowered exception table is rebuilt from {@code ExceptionHandler.tryBlocks}, not these
     * edges. The operand stack is untouched — the handler entry already holds just the caught exception and
     * renaming only resolves locals.
     *
     * @param irMethod the method whose handler blocks to connect
     * @return the list of (fromBlock, handlerBlock) edges that were actually added
     */
    public static List<IRBlock[]> addExceptionEdges(IRMethod irMethod) {
        List<IRBlock[]> added = new ArrayList<>();
        for (ExceptionHandler handler : irMethod.getExceptionHandlers()) {
            IRBlock handlerBlock = handler.getHandlerBlock();
            if (handlerBlock == null) {
                continue;
            }
            Set<IRBlock> tryBlocks = handler.getTryBlocks();
            if (tryBlocks == null || tryBlocks.isEmpty()) {
                continue;
            }
            for (IRBlock tryBlock : tryBlocks) {
                if (tryBlock != null && tryBlock != handlerBlock
                        && !tryBlock.getSuccessors().contains(handlerBlock)) {
                    tryBlock.addSuccessor(handlerBlock, EdgeType.EXCEPTION);
                    added.add(new IRBlock[]{tryBlock, handlerBlock});
                }
            }
        }
        return added;
    }

    /**
     * Removes the transient exception edges added by {@link #addExceptionEdges} once SSA local renaming is
     * complete, so the final CFG carries only real control-flow edges. Each removed edge is the exact
     * (fromBlock, handlerBlock) pair that was added, so a handler that legitimately is also a normal successor
     * of a block keeps that normal edge.
     *
     * @param addedEdges the edges returned by {@link #addExceptionEdges}
     */
    public static void removeExceptionEdges(List<IRBlock[]> addedEdges) {
        for (IRBlock[] edge : addedEdges) {
            edge[0].removeSuccessor(edge[1]);
        }
    }

    private void initializeState(AbstractState state, IRMethod irMethod) {
        int localIndex = 0;
        for (SSAValue param : irMethod.getParameters()) {
            state.setLocal(localIndex, param);
            localIndex++;
            if (param.getType().isTwoSlot()) {
                localIndex++;
            }
        }
    }

    private void propagateState(AbstractState state, IRBlock block, Map<IRBlock, AbstractState> blockStates,
                                Queue<IRBlock> worklist,
                                Map<IRBlock, IRBlock> stateSourceBlocks) {
        IRInstruction terminator = block.getTerminator();
        if (terminator == null) return;

        List<IRBlock> successors = new ArrayList<>();
        if (terminator instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) terminator;
            if (simple.getOp() == SimpleOp.GOTO) {
                successors.add(simple.getTarget());
            }
        } else if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
            successors.add(branch.getTrueTarget());
            successors.add(branch.getFalseTarget());
        } else if (terminator instanceof SwitchInstruction) {
            SwitchInstruction switchInstr = (SwitchInstruction) terminator;
            successors.add(switchInstr.getDefaultTarget());
            successors.addAll(switchInstr.getCases().values());
        }

        for (IRBlock succ : successors) {
            if (succ == null) continue;

            if (!blockStates.containsKey(succ)) {
                // First time visiting this block - just copy state and record source
                blockStates.put(succ, state.copy());
                stateSourceBlocks.put(succ, block);
                worklist.add(succ);
            } else {
                // Block already has a state from another path - need to merge
                // Insert PHI nodes for stack values that differ
                AbstractState existingState = blockStates.get(succ);
                IRBlock firstSourceBlock = stateSourceBlocks.get(succ);
                mergeStackWithPhis(state, existingState, succ, block, firstSourceBlock);
            }
        }
    }


    /**
     * Merges stack values from incoming state with existing state using PHI nodes.
     * When two control flow paths merge at a block and have different stack values,
     * we need to insert PHI nodes to properly represent the merged values.
     */
    private void mergeStackWithPhis(AbstractState incomingState, AbstractState existingState,
                                     IRBlock targetBlock, IRBlock incomingBlock, IRBlock firstSourceBlock) {
        List<Value> incomingStack = incomingState.getStackValues();
        List<Value> existingStack = existingState.getStackValues();

        // Stack sizes should match at merge points
        if (incomingStack.size() != existingStack.size()) {
            return; // Mismatch - can't merge safely
        }

        // Check each stack slot and create PHI if values differ
        for (int i = 0; i < incomingStack.size(); i++) {
            Value incomingVal = incomingStack.get(i);
            Value existingVal = existingStack.get(i);

            // If values are identical, no PHI needed
            if (incomingVal.equals(existingVal)) {
                continue;
            }

            // Check if there's already a PHI for this stack slot
            PhiInstruction existingPhi = findStackPhi(targetBlock, i);
            if (existingPhi != null) {
                existingPhi.addIncoming(incomingVal, incomingBlock);
            } else {
                IRType type;
                if (incomingVal instanceof SSAValue) {
                    SSAValue ssaVal = (SSAValue) incomingVal;
                    type = ssaVal.getType();
                } else if (existingVal instanceof SSAValue) {
                    SSAValue ssaVal2 = (SSAValue) existingVal;
                    type = ssaVal2.getType();
                } else {
                    type = PrimitiveType.INT;
                }
                SSAValue phiResult = new SSAValue(type, "stack_phi_" + i);
                PhiInstruction phi = new PhiInstruction(phiResult);
                // Use tracked first source block for the existing value
                phi.addIncoming(existingVal, firstSourceBlock);
                phi.addIncoming(incomingVal, incomingBlock);
                targetBlock.addPhi(phi);
                registerStackPhi(targetBlock, i, phi);

                // Update the existing state to use the PHI value
                existingState.setStackValue(i, phiResult);

                // Also update any instructions in the block that use the old value
                replaceValueInBlock(targetBlock, existingVal, phiResult);
            }
        }
    }

    /**
     * Replaces all uses of oldValue with newValue in the given block's instructions.
     */
    private void replaceValueInBlock(IRBlock block, Value oldValue, Value newValue) {
        for (IRInstruction instr : block.getInstructions()) {
            instr.replaceOperand(oldValue, newValue);
        }
    }

    /**
     * Post-processing pass to fix up PHI uses.
     * For each PHI instruction, ensures all instructions in the block AND all
     * reachable successor blocks use the PHI result instead of any incoming values.
     * This is necessary because successor blocks may have been processed before
     * the phi was created (when only one predecessor had been seen).
     */
    private void fixupPhiUses(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                SSAValue phiResult = phi.getResult();
                for (Value incomingValue : phi.getOperands()) {
                    replaceValueInBlockAndSuccessors(block, incomingValue, phiResult, phi);
                }
            }
        }
    }

    /**
     * Replaces all uses of oldValue with newValue in the given block and all
     * reachable successor blocks. Uses a worklist to avoid infinite loops
     * in cyclic control flow. Also updates phi incoming values in successor blocks
     * where the edge comes from a visited block, except for the phi that triggered
     * the replacement: in cyclic control flow the walk reaches that phi's own
     * predecessors, and rewriting its incoming edges would make it reference itself.
     */
    private void replaceValueInBlockAndSuccessors(IRBlock startBlock, Value oldValue, Value newValue,
                                                  PhiInstruction currentPhi) {
        Set<IRBlock> visited = new HashSet<>();
        Deque<IRBlock> worklist = new ArrayDeque<>();
        worklist.add(startBlock);

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (visited.contains(block)) {
                continue;
            }
            visited.add(block);

            for (IRInstruction instr : block.getInstructions()) {
                if (!instr.isPhi()) {
                    instr.replaceOperand(oldValue, newValue);
                }
            }

            for (IRBlock succ : block.getSuccessors()) {
                for (PhiInstruction phi : succ.getPhiInstructions()) {
                    if (phi == currentPhi) {
                        continue;
                    }
                    Value incoming = phi.getIncoming(block);
                    if (incoming != null && incoming.equals(oldValue)) {
                        phi.removeIncoming(block);
                        phi.addIncoming(newValue, block);
                    }
                }

                if (!visited.contains(succ)) {
                    worklist.add(succ);
                }
            }
        }
    }

    /**
     * Finds an existing PHI instruction for a specific stack slot.
     */
    private PhiInstruction findStackPhi(IRBlock block, int stackSlot) {
        Map<Integer, PhiInstruction> blockPhis = stackPhiIndex.get(block);
        return blockPhis != null ? blockPhis.get(stackSlot) : null;
    }

    /**
     * Registers a stack PHI in the index for fast lookup.
     */
    private void registerStackPhi(IRBlock block, int stackSlot, PhiInstruction phi) {
        stackPhiIndex.computeIfAbsent(block, k -> new HashMap<>()).put(stackSlot, phi);
    }

    private void connectBlocks(IRMethod irMethod) {
        for (IRBlock block : irMethod.getBlocks()) {
            IRInstruction terminator = block.getTerminator();
            if (terminator == null) continue;

            if (terminator instanceof SimpleInstruction) {
                SimpleInstruction simple = (SimpleInstruction) terminator;
                if (simple.getOp() == SimpleOp.GOTO) {
                    IRBlock target = simple.getTarget();
                    if (target != null) {
                        block.addSuccessor(target);
                    }
                }
            } else if (terminator instanceof BranchInstruction) {
                BranchInstruction branch = (BranchInstruction) terminator;
                if (branch.getTrueTarget() != null) {
                    block.addSuccessor(branch.getTrueTarget());
                }
                if (branch.getFalseTarget() != null) {
                    block.addSuccessor(branch.getFalseTarget());
                }
            } else if (terminator instanceof SwitchInstruction) {
                SwitchInstruction switchInstr = (SwitchInstruction) terminator;
                if (switchInstr.getDefaultTarget() != null) {
                    block.addSuccessor(switchInstr.getDefaultTarget());
                }
                for (IRBlock target : switchInstr.getCases().values()) {
                    if (target != null) {
                        block.addSuccessor(target);
                    }
                }
            }
        }
    }

    private void handleExceptionHandlers(IRMethod irMethod, CodeAttribute codeAttr, Map<Integer, IRBlock> offsetToBlock) {
        // Track which handler blocks already have exception marker inserted
        Set<IRBlock> processedHandlerBlocks = new HashSet<>();

        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            IRBlock handlerBlock = offsetToBlock.get(entry.getHandlerPc());
            if (handlerBlock == null) continue;

            ReferenceType catchType = null;
            if (entry.getCatchType() != 0) {
                String className = constPool.getClassName(entry.getCatchType());
                catchType = new ReferenceType(className);
            }

            IRBlock tryStart = findBlockContaining(offsetToBlock, entry.getStartPc());
            IRBlock tryEnd = findBlockContaining(offsetToBlock, entry.getEndPc());

            if (tryStart != null) {
                ExceptionHandler handler = new ExceptionHandler(tryStart, tryEnd, handlerBlock, catchType);
                // Record every block of the protected region so the exception table is regenerated per
                // contiguous PC run after lowering reorders blocks. Without this, the tryStart/tryEnd
                // fallback yields a single range that can wrongly span the (interleaved or trailing) handler.
                Set<IRBlock> tryBlocks = new HashSet<>();
                for (Map.Entry<Integer, IRBlock> e : offsetToBlock.entrySet()) {
                    if (e.getKey() >= entry.getStartPc() && e.getKey() < entry.getEndPc()) {
                        tryBlocks.add(e.getValue());
                    }
                }
                handler.setTryBlocks(tryBlocks);
                irMethod.addExceptionHandler(handler);

                // Mark the handler entry with a self-copy of the ACTUAL caught-exception value (the one
                // pushed onto the handler's entry stack and used by the handler body), not a fresh dummy.
                // The bytecode emitter recognizes this marker and stores the JVM-pushed exception off the
                // stack into the exception value's local; the old dummy-valued marker emitted nothing, so a
                // handler that did work before re-using the exception lost the capturing astore.
                if (!processedHandlerBlocks.contains(handlerBlock)) {
                    processedHandlerBlocks.add(handlerBlock);

                    // Skip if block already starts with a load (bytecode handles exception)
                    if (handlerBlock.getInstructions().isEmpty() ||
                            !(handlerBlock.getInstructions().get(0) instanceof LoadLocalInstruction)) {
                        SSAValue exceptionValue = handlerExceptionValues.get(handlerBlock);
                        if (exceptionValue == null) {
                            exceptionValue = new SSAValue(
                                    catchType != null ? catchType : ReferenceType.THROWABLE, "exc");
                        }
                        handlerBlock.insertInstruction(0, new CopyInstruction(exceptionValue, exceptionValue));
                    }
                }
            }
        }
    }

    private IRBlock findBlockContaining(Map<Integer, IRBlock> offsetToBlock, int offset) {
        if (offsetToBlock instanceof TreeMap) {
            TreeMap<Integer, IRBlock> treeMap = (TreeMap<Integer, IRBlock>) offsetToBlock;
            Map.Entry<Integer, IRBlock> entry = treeMap.floorEntry(offset);
            return entry != null ? entry.getValue() : null;
        }
        IRBlock result = null;
        for (Map.Entry<Integer, IRBlock> entry : offsetToBlock.entrySet()) {
            if (entry.getKey() <= offset) {
                result = entry.getValue();
            } else {
                break;
            }
        }
        return result;
    }

    private boolean isTerminator(int opcode) {
        return (opcode >= IRETURN.getCode() && opcode <= RETURN_.getCode())
                || opcode == ATHROW.getCode()
                || opcode == JSR.getCode()
                || opcode == RET.getCode()
                || opcode == JSR_W.getCode();
    }

    private static BootstrapMethodsAttribute findBootstrapMethodsAttribute(ClassFile classFile) {
        if (classFile == null) {
            return null;
        }
        for (Attribute attr : classFile.getClassAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                return (BootstrapMethodsAttribute) attr;
            }
        }
        return null;
    }
}

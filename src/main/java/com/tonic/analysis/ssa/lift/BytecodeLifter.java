package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.analysis.ssa.cfg.*;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.utill.Modifiers;

import java.util.*;

/**
 * Lifts bytecode to SSA-form IR.
 */
public class BytecodeLifter {

    private final ConstPool constPool;

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

        InstructionTranslator translator = new InstructionTranslator(constPool);
        for (Map.Entry<Integer, IRBlock> entry : offsetToBlock.entrySet()) {
            translator.registerBlock(entry.getKey(), entry.getValue());
        }

        initializeParameters(irMethod, method);
        translateInstructions(irMethod, instructions, offsetToBlock, translator, codeAttr);
        connectBlocks(irMethod, offsetToBlock, instructions);
        handleExceptionHandlers(irMethod, codeAttr, offsetToBlock);

        // Fix up PHI uses - ensure instructions use PHI values instead of raw incoming values
        fixupPhiUses(irMethod);

        return irMethod;
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

            if (instr instanceof ConditionalBranchInstruction branch) {
                int target = offset + branch.getBranchOffset();
                int fallthrough = offset + branch.getLength();
                blockStarts.add(target);
                blockStarts.add(fallthrough);
            } else if (instr instanceof com.tonic.analysis.instruction.GotoInstruction gotoInstr) {
                int target = offset + gotoInstr.getBranchOffset();
                blockStarts.add(target);
                int fallthrough = offset + gotoInstr.getLength();
                if (fallthrough < getTotalCodeLength(instructions)) {
                    blockStarts.add(fallthrough);
                }
            } else if (instr instanceof JsrInstruction jsrInstr) {
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
            } else if (instr instanceof TableSwitchInstruction tableSwitch) {
                blockStarts.add(offset + tableSwitch.getDefaultOffset());
                for (int jumpOffset : tableSwitch.getJumpOffsets().values()) {
                    blockStarts.add(offset + jumpOffset);
                }
                int fallthrough = offset + tableSwitch.getLength();
                if (fallthrough < getTotalCodeLength(instructions)) {
                    blockStarts.add(fallthrough);
                }
            } else if (instr instanceof LookupSwitchInstruction lookupSwitch) {
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
            blockStarts.add(entry.getHandlerPc());
            blockStarts.add(entry.getStartPc());
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
                        currentBlock.addInstruction(new com.tonic.analysis.ssa.ir.GotoInstruction(nextBlock));
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
                    propagateState(state, currentBlock, blockStates, worklist, offsetToBlock, stateSourceBlocks);
                    break;
                }
            }
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
                                Queue<IRBlock> worklist, Map<Integer, IRBlock> offsetToBlock,
                                Map<IRBlock, IRBlock> stateSourceBlocks) {
        IRInstruction terminator = block.getTerminator();
        if (terminator == null) return;

        List<IRBlock> successors = new ArrayList<>();
        if (terminator instanceof com.tonic.analysis.ssa.ir.GotoInstruction gotoInstr) {
            successors.add(gotoInstr.getTarget());
        } else if (terminator instanceof BranchInstruction branch) {
            successors.add(branch.getTrueTarget());
            successors.add(branch.getFalseTarget());
        } else if (terminator instanceof SwitchInstruction switchInstr) {
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
                // Add the incoming value to the existing PHI
                existingPhi.addIncoming(incomingVal, incomingBlock);
            } else {
                // Create a new PHI for this stack slot
                IRType type = incomingVal instanceof SSAValue ssaVal ? ssaVal.getType() :
                              existingVal instanceof SSAValue ssaVal2 ? ssaVal2.getType() :
                              PrimitiveType.INT;
                SSAValue phiResult = new SSAValue(type, "stack_phi_" + i);
                PhiInstruction phi = new PhiInstruction(phiResult);
                // Use tracked first source block for the existing value
                phi.addIncoming(existingVal, firstSourceBlock);
                phi.addIncoming(incomingVal, incomingBlock);
                targetBlock.addPhi(phi);

                // Update the existing state to use the PHI value
                existingState.getStackValues().set(i, phiResult);

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
     * For each PHI instruction, ensures all instructions in the block
     * use the PHI result instead of any of the incoming values.
     */
    private void fixupPhiUses(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                SSAValue phiResult = phi.getResult();
                // Replace uses of any incoming value with the PHI result
                for (Value incomingValue : phi.getOperands()) {
                    for (IRInstruction instr : block.getInstructions()) {
                        instr.replaceOperand(incomingValue, phiResult);
                    }
                }
            }
        }
    }

    /**
     * Finds an existing PHI instruction for a specific stack slot.
     */
    private PhiInstruction findStackPhi(IRBlock block, int stackSlot) {
        for (PhiInstruction phi : block.getPhiInstructions()) {
            if (phi.getResult().getName().equals("stack_phi_" + stackSlot)) {
                return phi;
            }
        }
        return null;
    }

    private void connectBlocks(IRMethod irMethod, Map<Integer, IRBlock> offsetToBlock, List<Instruction> instructions) {
        for (IRBlock block : irMethod.getBlocks()) {
            IRInstruction terminator = block.getTerminator();
            if (terminator == null) continue;

            if (terminator instanceof com.tonic.analysis.ssa.ir.GotoInstruction gotoInstr) {
                IRBlock target = gotoInstr.getTarget();
                if (target != null) {
                    block.addSuccessor(target);
                }
            } else if (terminator instanceof BranchInstruction branch) {
                if (branch.getTrueTarget() != null) {
                    block.addSuccessor(branch.getTrueTarget());
                }
                if (branch.getFalseTarget() != null) {
                    block.addSuccessor(branch.getFalseTarget());
                }
            } else if (terminator instanceof SwitchInstruction switchInstr) {
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
                irMethod.addExceptionHandler(handler);

                SSAValue exceptionValue = new SSAValue(
                        catchType != null ? catchType : ReferenceType.THROWABLE,
                        "exc"
                );
                if (handlerBlock.getInstructions().isEmpty() ||
                        !(handlerBlock.getInstructions().get(0) instanceof LoadLocalInstruction)) {
                    handlerBlock.insertInstruction(0, new CopyInstruction(exceptionValue, exceptionValue));
                }
            }
        }
    }

    private IRBlock findBlockContaining(Map<Integer, IRBlock> offsetToBlock, int offset) {
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
        return opcode == 0xAC   // ireturn
                || opcode == 0xAD   // lreturn
                || opcode == 0xAE   // freturn
                || opcode == 0xAF   // dreturn
                || opcode == 0xB0   // areturn
                || opcode == 0xB1   // return
                || opcode == 0xBF   // athrow
                || opcode == 0xA8   // jsr
                || opcode == 0xA9   // ret
                || opcode == 0xC9;  // jsr_w
    }
}

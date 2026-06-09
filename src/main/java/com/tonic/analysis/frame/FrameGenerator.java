package com.tonic.analysis.frame;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.StackMapTableAttribute;
import com.tonic.parser.attribute.stack.FullFrame;
import com.tonic.parser.attribute.stack.StackMapFrame;
import com.tonic.parser.attribute.stack.VerificationTypeInfo;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.utill.Logger;

import java.util.*;

import static com.tonic.utill.Opcode.*;

/**
 * Generates StackMapTable frames for a method.
 * Uses FULL_FRAME for all entries (simple and always valid).
 * <p>
 * Usage:
 * <pre>
 * FrameGenerator gen = new FrameGenerator(constPool);
 * List<StackMapFrame> frames = gen.computeFrames(methodEntry);
 * </pre>
 */
public class FrameGenerator {
    private final ConstPool constPool;
    private final TypeInference typeInference;
    private int maxStackSlots;

    /**
     * Constructs a FrameGenerator for the given constant pool.
     *
     * @param constPool the constant pool
     */
    public FrameGenerator(ConstPool constPool) {
        this.constPool = constPool;
        this.typeInference = new TypeInference(constPool);
    }

    /**
     * Computes the StackMapTable frames for a method.
     *
     * @param method The method to compute frames for
     * @return List of StackMapFrame entries (all FullFrame type)
     */
    public List<StackMapFrame> computeFrames(MethodEntry method) {
        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null) {
            return Collections.emptyList();
        }

        Set<Integer> frameTargets = findFrameTargets(method);
        if (frameTargets.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, TypeState> states = computeTypeStates(method, frameTargets);

        List<StackMapFrame> frames = new ArrayList<>();
        List<Integer> sortedTargets = new ArrayList<>(frameTargets);
        Collections.sort(sortedTargets);

        int previousOffset = -1;
        for (int target : sortedTargets) {
            TypeState state = states.get(target);
            if (state == null) {
                Logger.error("No type state computed for offset " + target);
                continue;
            }

            int offsetDelta;
            if (previousOffset == -1) {
                offsetDelta = target;
            } else {
                offsetDelta = target - previousOffset - 1;
            }

            FullFrame frame = createFullFrame(offsetDelta, state);
            frames.add(frame);
            previousOffset = target;
        }

        return frames;
    }

    /**
     * Computes the true {@code max_stack} over the method's control-flow graph: the peak operand-stack
     * depth (in slots) across every reachable program point — including loop back-edges, join points,
     * and exception-handler entry states. Unlike a linear textual scan this never under-reports.
     *
     * @param method the method to analyze
     * @return the CFG-correct max_stack in slots (0 if the method has no code)
     */
    public int computeMaxStack(MethodEntry method) {
        if (method.getCodeAttribute() == null) {
            return 0;
        }
        computeTypeStates(method, findFrameTargets(method));
        return maxStackSlots;
    }

    /**
     * The max operand-stack depth (slots) observed by the most recent {@link #computeFrames} /
     * {@link #computeMaxStack} run. Valid only after the worklist has run (i.e. when the method had
     * frame targets, or after {@link #computeMaxStack}).
     */
    public int getMaxStack() {
        return maxStackSlots;
    }

    /**
     * Finds all bytecode offsets that require a frame entry.
     *
     * @param method the method to analyze
     * @return set of offsets requiring frames
     */
    private Set<Integer> findFrameTargets(MethodEntry method) {
        Set<Integer> targets = new TreeSet<>();
        CodeAttribute codeAttr = method.getCodeAttribute();

        CodeWriter codeWriter = new CodeWriter(method);
        for (Instruction instr : codeWriter.getInstructions()) {
            int offset = instr.getOffset();

            if (instr instanceof ConditionalBranchInstruction) {
                ConditionalBranchInstruction branch = (ConditionalBranchInstruction) instr;
                int target = offset + branch.getBranchOffset();
                if (target > 0) {
                    targets.add(target);
                }
            }

            if (instr instanceof GotoInstruction) {
                GotoInstruction gotoInstr =
                    (GotoInstruction) instr;
                int target = offset + gotoBranchOffset(gotoInstr);
                if (target > 0) {
                    targets.add(target);
                }
            }

            if (instr instanceof TableSwitchInstruction) {
                TableSwitchInstruction tableSwitch = (TableSwitchInstruction) instr;
                int defaultTarget = offset + tableSwitch.getDefaultOffset();
                if (defaultTarget > 0) targets.add(defaultTarget);

                for (int jumpOffset : tableSwitch.getJumpOffsets().values()) {
                    int target = offset + jumpOffset;
                    if (target > 0) targets.add(target);
                }
            }

            if (instr instanceof LookupSwitchInstruction) {
                LookupSwitchInstruction lookupSwitch = (LookupSwitchInstruction) instr;
                int defaultTarget = offset + lookupSwitch.getDefaultOffset();
                if (defaultTarget > 0) targets.add(defaultTarget);

                for (int jumpOffset : lookupSwitch.getMatchOffsets().values()) {
                    int target = offset + jumpOffset;
                    if (target > 0) targets.add(target);
                }
            }

            if (instr instanceof JsrInstruction) {
                JsrInstruction jsr = (JsrInstruction) instr;
                int target = offset + jsr.getBranchOffset();
                if (target > 0) targets.add(target);
            }
        }

        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            int handlerPc = entry.getHandlerPc();
            if (handlerPc > 0) {
                targets.add(handlerPc);
            }
        }

        return targets;
    }

    /**
     * Computes type states at all required offsets by simulating bytecode execution.
     *
     * @param method the method to analyze
     * @param frameTargets offsets requiring frames
     * @return map of offset to type state
     */
    private Map<Integer, TypeState> computeTypeStates(MethodEntry method, Set<Integer> frameTargets) {
        Map<Integer, TypeState> states = new HashMap<>();
        CodeAttribute codeAttr = method.getCodeAttribute();

        maxStackSlots = 0;
        TypeState initialState = TypeState.fromMethodEntry(method, constPool);

        CodeWriter codeWriter = new CodeWriter(method);
        List<Instruction> instructionList = new ArrayList<>();
        for (Instruction instr : codeWriter.getInstructions()) {
            instructionList.add(instr);
        }

        Map<Integer, Integer> offsetToIndex = new HashMap<>();
        for (int i = 0; i < instructionList.size(); i++) {
            offsetToIndex.put(instructionList.get(i).getOffset(), i);
        }

        Map<Integer, TypeState> visitedStates = new HashMap<>();
        Queue<WorkItem> worklist = new LinkedList<>();
        worklist.add(new WorkItem(0, initialState));

        Set<Integer> handlerPcSet = new HashSet<>();
        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            handlerPcSet.add(entry.getHandlerPc());
        }

        processWorklist(worklist, visitedStates, states, frameTargets, handlerPcSet,
                instructionList, offsetToIndex, constPool);

        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            int handlerPc = entry.getHandlerPc();

            TypeState baseLocals;
            if (states.containsKey(handlerPc)) {
                baseLocals = states.get(handlerPc);
            } else {
                baseLocals = initialState;
                for (Map.Entry<Integer, TypeState> vs : visitedStates.entrySet()) {
                    int visitedOffset = vs.getKey();
                    if (visitedOffset >= entry.getStartPc() && visitedOffset < entry.getEndPc()) {
                        baseLocals = baseLocals.merge(vs.getValue(), constPool);
                    }
                }
            }

            TypeState handlerState = createExceptionHandlerState(baseLocals, entry.getCatchType());
            if (states.containsKey(handlerPc)) {
                states.put(handlerPc, states.get(handlerPc).merge(handlerState, constPool));
            } else {
                states.put(handlerPc, handlerState);
            }
            worklist.add(new WorkItem(handlerPc, handlerState));
        }

        processWorklist(worklist, visitedStates, states, frameTargets, handlerPcSet,
                instructionList, offsetToIndex, constPool);

        return states;
    }

    /**
     * Creates a TypeState for an exception handler entry point.
     *
     * @param baseState the base state to derive from
     * @param catchType the constant pool index of the exception type
     * @return new state with exception on stack
     */
    private TypeState createExceptionHandlerState(TypeState baseState, int catchType) {
        VerificationType exceptionType;
        if (catchType == 0) {
            exceptionType = VerificationType.object(constPool.findOrAddClass("java/lang/Throwable").getIndex(constPool));
        } else {
            exceptionType = VerificationType.object(catchType);
        }

        return baseState.clearStack().push(exceptionType);
    }

    /**
     * Creates a FullFrame from the given offset delta and type state.
     *
     * @param offsetDelta the offset delta for the frame
     * @param state the type state
     * @return FullFrame instance
     */
    private FullFrame createFullFrame(int offsetDelta, TypeState state) {
        List<VerificationTypeInfo> locals = state.localsToVerificationTypeInfo();
        List<VerificationTypeInfo> stack = state.stackToVerificationTypeInfo();

        return new FullFrame(offsetDelta, locals, stack);
    }

    /**
     * Checks if an opcode represents an unconditional jump.
     *
     * @param opcode the instruction opcode
     * @return true if unconditional jump
     */
    private boolean isUnconditionalJump(int opcode) {
        return opcode == GOTO.getCode()
            || opcode == GOTO_W.getCode()
            || opcode == JSR.getCode()
            || opcode == JSR_W.getCode();
    }

    /**
     * Checks if an opcode terminates the current execution path.
     *
     * @param opcode the instruction opcode
     * @return true if terminator
     */
    private boolean isTerminator(int opcode) {
        return (opcode >= IRETURN.getCode() && opcode <= RETURN_.getCode())
            || opcode == ATHROW.getCode()
            || opcode == RET.getCode();
    }

    /**
     * Gets the target offset of a jump instruction.
     *
     * @param gotoInstr the jump instruction
     * @return target offset or -1 if not a jump
     */
    private static int gotoBranchOffset(GotoInstruction gotoInstr) {
        return gotoInstr.getType() == GotoInstruction.GotoType.GOTO_WIDE
                ? gotoInstr.getBranchOffsetWide() : gotoInstr.getBranchOffset();
    }

    private int getJumpTarget(Instruction instr) {
        if (instr instanceof GotoInstruction) {
            GotoInstruction gotoInstr = (GotoInstruction) instr;
            return instr.getOffset() + gotoBranchOffset(gotoInstr);
        }
        if (instr instanceof JsrInstruction) {
            JsrInstruction jsrInstr = (JsrInstruction) instr;
            return instr.getOffset() + jsrInstr.getBranchOffset();
        }
        return -1;
    }

    private void processWorklist(
            Queue<WorkItem> worklist,
            Map<Integer, TypeState> visitedStates,
            Map<Integer, TypeState> states,
            Set<Integer> frameTargets,
            Set<Integer> handlerPcSet,
            List<Instruction> instructionList,
            Map<Integer, Integer> offsetToIndex,
            ConstPool constPool) {

        while (!worklist.isEmpty()) {
            WorkItem item = worklist.poll();
            int offset = item.offset;
            TypeState currentState = item.state;

            if (visitedStates.containsKey(offset)) {
                TypeState existing = visitedStates.get(offset);
                TypeState merged = existing.merge(currentState, constPool);
                if (merged.equals(existing)) {
                    continue;
                }
                visitedStates.put(offset, merged);
                currentState = merged;
                if (frameTargets.contains(offset)) {
                    if (states.containsKey(offset)) {
                        states.put(offset, states.get(offset).merge(merged, constPool));
                    } else {
                        states.put(offset, merged);
                    }
                }
            } else {
                visitedStates.put(offset, currentState);
                if (frameTargets.contains(offset) && states.containsKey(offset)) {
                    states.put(offset, states.get(offset).merge(currentState, constPool));
                }
            }

            maxStackSlots = Math.max(maxStackSlots, currentState.stackSlots());

            Integer index = offsetToIndex.get(offset);
            if (index == null) {
                continue;
            }

            for (int i = index; i < instructionList.size(); i++) {
                Instruction instr = instructionList.get(i);
                int instrOffset = instr.getOffset();

                if (frameTargets.contains(instrOffset)) {
                    TypeState stateToRecord = currentState;
                    if (states.containsKey(instrOffset)) {
                        stateToRecord = states.get(instrOffset).merge(stateToRecord, constPool);
                    }
                    states.put(instrOffset, stateToRecord);
                }

                if (i > index && handlerPcSet.contains(instrOffset)) {
                    break;
                }

                try {
                    currentState = typeInference.apply(currentState, instr);
                } catch (IllegalStateException e) {
                    throw new IllegalStateException(
                            "Frame error at offset " + instrOffset + " for instruction "
                                    + instr + ": " + e.getMessage(), e);
                }
                maxStackSlots = Math.max(maxStackSlots, currentState.stackSlots());

                int opcode = instr.getOpcode();

                if (isUnconditionalJump(opcode)) {
                    int target = getJumpTarget(instr);
                    if (target >= 0) {
                        worklist.add(new WorkItem(target, currentState));
                    }
                    break;
                }

                if (isTerminator(opcode)) {
                    break;
                }

                if (instr instanceof ConditionalBranchInstruction) {
                    ConditionalBranchInstruction branch = (ConditionalBranchInstruction) instr;
                    int target = instrOffset + branch.getBranchOffset();
                    worklist.add(new WorkItem(target, currentState));
                }

                if (instr instanceof TableSwitchInstruction) {
                    TableSwitchInstruction tableSwitch = (TableSwitchInstruction) instr;
                    for (int jumpOffset : tableSwitch.getJumpOffsets().values()) {
                        int target = instrOffset + jumpOffset;
                        worklist.add(new WorkItem(target, currentState));
                    }
                    int defaultTarget = instrOffset + tableSwitch.getDefaultOffset();
                    worklist.add(new WorkItem(defaultTarget, currentState));
                    break;
                }

                if (instr instanceof LookupSwitchInstruction) {
                    LookupSwitchInstruction lookupSwitch = (LookupSwitchInstruction) instr;
                    for (int jumpOffset : lookupSwitch.getMatchOffsets().values()) {
                        int target = instrOffset + jumpOffset;
                        worklist.add(new WorkItem(target, currentState));
                    }
                    int defaultTarget = instrOffset + lookupSwitch.getDefaultOffset();
                    worklist.add(new WorkItem(defaultTarget, currentState));
                    break;
                }
            }
        }
    }

    /**
     * Work item for the worklist algorithm.
     */
    private static class WorkItem {
        final int offset;
        final TypeState state;

        WorkItem(int offset, TypeState state) {
            this.offset = offset;
            this.state = state;
        }
    }

    /**
     * Convenience method to compute and update the StackMapTable for a method.
     *
     * @param method The method to update
     */
    public void updateStackMapTable(MethodEntry method) {
        List<StackMapFrame> frames = computeFrames(method);
        CodeAttribute codeAttr = method.getCodeAttribute();

        if (codeAttr == null) {
            return;
        }

        StackMapTableAttribute stackMapTable = null;
        for (Attribute attr : codeAttr.getAttributes()) {
            if (attr instanceof StackMapTableAttribute) {
                stackMapTable = (StackMapTableAttribute) attr;
                break;
            }
        }

        if (frames.isEmpty()) {
            if (stackMapTable != null) {
                codeAttr.getAttributes().remove(stackMapTable);
            }
            return;
        }

        if (stackMapTable == null) {
            int nameIndex = constPool.findOrAddUtf8("StackMapTable").getIndex(constPool);
            stackMapTable = new StackMapTableAttribute("StackMapTable", method, nameIndex, 0);
            codeAttr.getAttributes().add(stackMapTable);
        }

        stackMapTable.setFrames(frames);
        stackMapTable.updateLength();

        codeAttr.updateLength();
    }
}

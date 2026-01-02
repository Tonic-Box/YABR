package com.tonic.analysis.verifier.controlflow;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;

import java.util.*;

public class ControlFlowVerifier {
    private final ClassFile classFile;
    private final VerifierConfig config;

    public ControlFlowVerifier(ClassFile classFile, VerifierConfig config) {
        this.classFile = classFile;
        this.config = config;
    }

    public void verify(MethodEntry method, ErrorCollector collector) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0) {
            return;
        }

        CodeWriter codeWriter;
        try {
            codeWriter = new CodeWriter(method);
        } catch (Exception e) {
            return;
        }

        Map<Integer, Instruction> instructionMap = getInstructionsMap(codeWriter);
        Set<Integer> reachable = new HashSet<>();
        Set<Integer> terminating = new HashSet<>();

        findReachableInstructions(instructionMap, bytecode, reachable, terminating, code);

        for (Integer offset : instructionMap.keySet()) {
            if (!reachable.contains(offset)) {
                collector.addWarning(new VerificationError(
                        VerificationErrorType.UNREACHABLE_CODE,
                        offset,
                        "Unreachable code at offset " + offset,
                        VerificationError.Severity.WARNING
                ));
            }
        }

        verifyAllPathsTerminate(instructionMap, bytecode, reachable, terminating, code, collector);
    }

    private void findReachableInstructions(Map<Integer, Instruction> instructions, byte[] bytecode,
                                           Set<Integer> reachable, Set<Integer> terminating,
                                           CodeAttribute code) {
        Deque<Integer> worklist = new ArrayDeque<>();
        worklist.add(0);

        for (var entry : code.getExceptionTable()) {
            worklist.add(entry.getHandlerPc());
        }

        while (!worklist.isEmpty()) {
            int offset = worklist.poll();
            if (reachable.contains(offset)) {
                continue;
            }
            reachable.add(offset);

            Instruction instr = instructions.get(offset);
            if (instr == null) {
                continue;
            }

            int opcode = instr.getOpcode();

            if (isTerminatingInstruction(opcode)) {
                terminating.add(offset);
                continue;
            }

            List<Integer> successors = getSuccessors(instr, offset, bytecode);
            for (int succ : successors) {
                if (!reachable.contains(succ) && instructions.containsKey(succ)) {
                    worklist.add(succ);
                }
            }
        }
    }

    private void verifyAllPathsTerminate(Map<Integer, Instruction> instructions, byte[] bytecode,
                                         Set<Integer> reachable, Set<Integer> terminating,
                                         CodeAttribute code, ErrorCollector collector) {
        Map<Integer, Set<Integer>> predecessors = buildPredecessorMap(instructions, bytecode, code);

        for (Integer offset : reachable) {
            Instruction instr = instructions.get(offset);
            if (instr == null) continue;

            int opcode = instr.getOpcode();
            if (isTerminatingInstruction(opcode)) {
                continue;
            }

            List<Integer> successors = getSuccessors(instr, offset, bytecode);

            boolean hasValidSuccessor = false;
            for (int succ : successors) {
                if (instructions.containsKey(succ)) {
                    hasValidSuccessor = true;
                    break;
                }
            }

            if (!hasValidSuccessor && successors.isEmpty()) {
                int nextOffset = offset + instr.getLength();
                if (nextOffset >= bytecode.length) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.INSTRUCTION_FALLS_OFF_END,
                            offset,
                            "Execution falls off end of code after offset " + offset
                    ));
                }
            }
        }
    }

    private Map<Integer, Set<Integer>> buildPredecessorMap(Map<Integer, Instruction> instructions,
                                                           byte[] bytecode, CodeAttribute code) {
        Map<Integer, Set<Integer>> predecessors = new HashMap<>();

        for (Integer offset : instructions.keySet()) {
            predecessors.put(offset, new HashSet<>());
        }

        for (Map.Entry<Integer, Instruction> entry : instructions.entrySet()) {
            int offset = entry.getKey();
            Instruction instr = entry.getValue();

            List<Integer> successors = getSuccessors(instr, offset, bytecode);
            for (int succ : successors) {
                if (predecessors.containsKey(succ)) {
                    predecessors.get(succ).add(offset);
                }
            }
        }

        for (var exEntry : code.getExceptionTable()) {
            int handlerPc = exEntry.getHandlerPc();
            if (predecessors.containsKey(handlerPc)) {
                for (int pc = exEntry.getStartPc(); pc < exEntry.getEndPc(); ) {
                    if (instructions.containsKey(pc)) {
                        predecessors.get(handlerPc).add(pc);
                        Instruction instr = instructions.get(pc);
                        pc += instr.getLength();
                    } else {
                        pc++;
                    }
                }
            }
        }

        return predecessors;
    }

    private boolean isTerminatingInstruction(int opcode) {
        return (opcode >= 0xAC && opcode <= 0xB1) ||
               opcode == 0xBF ||
               opcode == 0xA7 ||
               opcode == 0xC8;
    }

    private List<Integer> getSuccessors(Instruction instr, int offset, byte[] bytecode) {
        List<Integer> successors = new ArrayList<>();
        int opcode = instr.getOpcode();

        if (opcode >= 0xAC && opcode <= 0xB1) {
            return successors;
        }
        if (opcode == 0xBF) {
            return successors;
        }

        int nextOffset = offset + instr.getLength();

        if (opcode == 0xA7 || opcode == 0xC8) {
            if (instr instanceof com.tonic.analysis.instruction.GotoInstruction) {
                int target = offset + ((com.tonic.analysis.instruction.GotoInstruction) instr).getBranchOffset();
                successors.add(target);
            }
            return successors;
        }

        if (instr instanceof ConditionalBranchInstruction) {
            ConditionalBranchInstruction branch = (ConditionalBranchInstruction) instr;
            successors.add(offset + branch.getBranchOffset());
            if (nextOffset < bytecode.length) {
                successors.add(nextOffset);
            }
            return successors;
        }

        if (instr instanceof TableSwitchInstruction) {
            TableSwitchInstruction ts = (TableSwitchInstruction) instr;
            successors.add(offset + ts.getDefaultOffset());
            for (int jumpOffset : ts.getJumpOffsets().values()) {
                successors.add(offset + jumpOffset);
            }
            return successors;
        }

        if (instr instanceof LookupSwitchInstruction) {
            LookupSwitchInstruction ls = (LookupSwitchInstruction) instr;
            successors.add(offset + ls.getDefaultOffset());
            for (int jumpOffset : ls.getMatchOffsets().values()) {
                successors.add(offset + jumpOffset);
            }
            return successors;
        }

        if (opcode == 0xA8 || opcode == 0xC9) {
            if (instr instanceof JsrInstruction) {
                JsrInstruction jsr = (JsrInstruction) instr;
                successors.add(offset + jsr.getBranchOffset());
            }
            if (nextOffset < bytecode.length) {
                successors.add(nextOffset);
            }
            return successors;
        }

        if (nextOffset < bytecode.length) {
            successors.add(nextOffset);
        }

        return successors;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Instruction> getInstructionsMap(CodeWriter codeWriter) {
        try {
            java.lang.reflect.Field f = CodeWriter.class.getDeclaredField("instructions");
            f.setAccessible(true);
            return (Map<Integer, Instruction>) f.get(codeWriter);
        } catch (Exception e) {
            return new TreeMap<>();
        }
    }
}

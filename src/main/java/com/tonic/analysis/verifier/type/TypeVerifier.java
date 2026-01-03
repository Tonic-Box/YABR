package com.tonic.analysis.verifier.type;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.frame.TypeInference;
import com.tonic.analysis.frame.TypeState;
import com.tonic.analysis.frame.VerificationType;
import com.tonic.analysis.instruction.*;
import com.tonic.analysis.verifier.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;

import java.util.*;

import static com.tonic.utill.Opcode.*;

public class TypeVerifier {
    private final ClassFile classFile;
    private final ClassPool classPool;
    private final VerifierConfig config;
    private final TypeConstraint typeConstraint;

    public TypeVerifier(ClassFile classFile, ClassPool classPool, VerifierConfig config) {
        this.classFile = classFile;
        this.classPool = classPool;
        this.config = config;
        this.typeConstraint = new TypeConstraint(classPool);
    }

    public void verify(MethodEntry method, ErrorCollector collector) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return;
        }

        ConstPool constPool = classFile != null ? classFile.getConstPool() : null;
        if (constPool == null) {
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0) {
            return;
        }

        int maxStack = code.getMaxStack();
        int maxLocals = code.getMaxLocals();

        CodeWriter codeWriter;
        try {
            codeWriter = new CodeWriter(method);
        } catch (Exception e) {
            return;
        }

        TypeInference inference = new TypeInference(constPool);
        TypeState initialState = createInitialState(method, constPool);

        Map<Integer, TypeState> stateAtOffset = new HashMap<>();
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> worklist = new ArrayDeque<>();

        stateAtOffset.put(0, initialState);
        worklist.add(0);

        while (!worklist.isEmpty()) {
            if (collector.shouldStop()) return;

            int offset = worklist.poll();
            if (visited.contains(offset)) {
                continue;
            }
            visited.add(offset);

            TypeState state = stateAtOffset.get(offset);
            if (state == null) {
                continue;
            }

            Instruction instr = codeWriter.getInstructions().iterator().next();
            for (Instruction i : codeWriter.getInstructions()) {
                if (i.getOffset() == offset) {
                    instr = i;
                    break;
                }
            }

            if (instr.getOffset() != offset) {
                for (Map.Entry<Integer, Instruction> entry : ((TreeMap<Integer, Instruction>)
                        getInstructionsMap(codeWriter)).entrySet()) {
                    if (entry.getKey() == offset) {
                        instr = entry.getValue();
                        break;
                    }
                }
            }

            if (state.getStackSize() > maxStack) {
                collector.addError(new VerificationError(
                        VerificationErrorType.STACK_OVERFLOW,
                        offset,
                        "Stack size " + state.getStackSize() + " exceeds max_stack " + maxStack
                ));
                if (collector.shouldStop()) return;
            }

            TypeState nextState;
            try {
                nextState = inference.apply(state, instr);
            } catch (IllegalStateException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("underflow")) {
                    collector.addError(new VerificationError(
                            VerificationErrorType.STACK_UNDERFLOW,
                            offset,
                            "Stack underflow at instruction " + instr.getClass().getSimpleName()
                    ));
                } else {
                    collector.addError(new VerificationError(
                            VerificationErrorType.TYPE_MISMATCH,
                            offset,
                            "Type error at instruction: " + msg
                    ));
                }
                if (collector.shouldStop()) return;
                continue;
            }

            verifyLocalAccess(instr, state, maxLocals, offset, collector);
            if (collector.shouldStop()) return;

            verifyReturnType(instr, state, method, offset, constPool, collector);
            if (collector.shouldStop()) return;

            int opcode = instr.getOpcode();
            List<Integer> successors = getSuccessors(instr, offset, bytecode);

            for (int succ : successors) {
                TypeState existing = stateAtOffset.get(succ);
                if (existing == null) {
                    stateAtOffset.put(succ, nextState);
                    worklist.add(succ);
                } else {
                    TypeState merged = existing.merge(nextState);
                    if (!merged.equals(existing)) {
                        stateAtOffset.put(succ, merged);
                        visited.remove(succ);
                        worklist.add(succ);
                    }
                }
            }
        }
    }

    private TypeState createInitialState(MethodEntry method, ConstPool constPool) {
        if (method instanceof MethodEntry) {
            return TypeState.fromMethodEntry((MethodEntry) method, constPool);
        }
        return TypeState.empty();
    }

    private void verifyLocalAccess(Instruction instr, TypeState state, int maxLocals,
                                   int offset, ErrorCollector collector) {
        int localIndex = -1;
        boolean isStore = false;
        boolean isTwoSlot = false;

        if (instr instanceof ILoadInstruction) {
            localIndex = ((ILoadInstruction) instr).getVarIndex();
        } else if (instr instanceof LLoadInstruction) {
            localIndex = ((LLoadInstruction) instr).getVarIndex();
            isTwoSlot = true;
        } else if (instr instanceof FLoadInstruction) {
            localIndex = ((FLoadInstruction) instr).getVarIndex();
        } else if (instr instanceof DLoadInstruction) {
            localIndex = ((DLoadInstruction) instr).getVarIndex();
            isTwoSlot = true;
        } else if (instr instanceof ALoadInstruction) {
            localIndex = ((ALoadInstruction) instr).getVarIndex();
        } else if (instr instanceof IStoreInstruction) {
            localIndex = ((IStoreInstruction) instr).getVarIndex();
            isStore = true;
        } else if (instr instanceof LStoreInstruction) {
            localIndex = ((LStoreInstruction) instr).getVarIndex();
            isStore = true;
            isTwoSlot = true;
        } else if (instr instanceof FStoreInstruction) {
            localIndex = ((FStoreInstruction) instr).getVarIndex();
            isStore = true;
        } else if (instr instanceof DStoreInstruction) {
            localIndex = ((DStoreInstruction) instr).getVarIndex();
            isStore = true;
            isTwoSlot = true;
        } else if (instr instanceof AStoreInstruction) {
            localIndex = ((AStoreInstruction) instr).getVarIndex();
            isStore = true;
        }

        if (localIndex >= 0) {
            int maxIndex = isTwoSlot ? localIndex + 1 : localIndex;
            if (maxIndex >= maxLocals) {
                collector.addError(new VerificationError(
                        VerificationErrorType.LOCALS_OVERFLOW,
                        offset,
                        "Local variable index " + localIndex +
                        (isTwoSlot ? " (2 slots)" : "") +
                        " exceeds max_locals " + maxLocals
                ));
            }

            if (!isStore && localIndex < state.getLocalsCount()) {
                VerificationType localType = state.getLocal(localIndex);
                if (localType.equals(VerificationType.TOP)) {
                    collector.addWarning(new VerificationError(
                            VerificationErrorType.UNINITIALIZED_LOCAL,
                            offset,
                            "Possible access to uninitialized local variable at index " + localIndex,
                            VerificationError.Severity.WARNING
                    ));
                }
            }
        }
    }

    private void verifyReturnType(Instruction instr, TypeState state, MethodEntry method,
                                  int offset, ConstPool constPool, ErrorCollector collector) {
        int opcode = instr.getOpcode();
        if (opcode < IRETURN.getCode() || opcode > RETURN_.getCode()) {
            return;
        }

        String desc = method.getDesc();
        VerificationType expectedReturn = TypeState.getReturnType(desc, constPool);

        if (opcode == RETURN_.getCode()) {
            if (expectedReturn != null) {
                collector.addError(new VerificationError(
                        VerificationErrorType.INCOMPATIBLE_RETURN_TYPE,
                        offset,
                        "RETURN used in method with non-void return type"
                ));
            }
            return;
        }

        if (expectedReturn == null) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INCOMPATIBLE_RETURN_TYPE,
                    offset,
                    "Non-RETURN instruction used in void method"
            ));
            return;
        }

        if (state.isStackEmpty()) {
            collector.addError(new VerificationError(
                    VerificationErrorType.STACK_UNDERFLOW,
                    offset,
                    "Stack empty at return instruction"
            ));
            return;
        }

        VerificationType actualReturn = state.peek();
        if (!typeConstraint.isAssignableTo(actualReturn, expectedReturn)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INCOMPATIBLE_RETURN_TYPE,
                    offset,
                    "Return type mismatch: expected " + expectedReturn + ", got " + actualReturn
            ));
        }
    }

    private List<Integer> getSuccessors(Instruction instr, int offset, byte[] bytecode) {
        List<Integer> successors = new ArrayList<>();
        int opcode = instr.getOpcode();

        if (opcode >= IRETURN.getCode() && opcode <= RETURN_.getCode()) {
            return successors;
        }
        if (opcode == ATHROW.getCode()) {
            return successors;
        }

        int nextOffset = offset + instr.getLength();

        if (opcode == GOTO.getCode() || opcode == GOTO_W.getCode()) {
            if (instr instanceof GotoInstruction) {
                int target = offset + ((GotoInstruction) instr).getBranchOffset();
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

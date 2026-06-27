package com.tonic.analysis;

import com.tonic.analysis.instruction.*;
import com.tonic.util.Opcode;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.tonic.util.Opcode.*;

/**
 * Pure layout and classification facts about individual instructions: branch/switch tests,
 * encoded lengths, switch alignment padding, local-variable slot indices and sizes, and conditional
 * opcode inversion. Stateless; used while laying out and rewriting a method's bytecode.
 */
final class InstructionLayout {

    private InstructionLayout() {
    }

    static boolean isBranch(Instruction i) {
        return i instanceof GotoInstruction || i instanceof ConditionalBranchInstruction
                || i instanceof JsrInstruction;
    }

    static boolean isSwitch(Instruction i) {
        return i instanceof TableSwitchInstruction || i instanceof LookupSwitchInstruction;
    }

    /** Assigns sequential offsets to {@code order}, recomputing switch padding from each offset. */
    static Map<Instruction, Integer> layout(List<Instruction> order) {
        Map<Instruction, Integer> off = new IdentityHashMap<>();
        int run = 0;
        for (Instruction i : order) {
            off.put(i, run);
            run += instructionLength(i, run);
        }
        return off;
    }

    static int instructionLength(Instruction i, int offset) {
        return isSwitch(i) ? switchBaseLength(i) + paddingAfterOpcode(offset) : i.getLength();
    }

    /** Padding bytes after a switch opcode so its operands align to a 4-byte boundary from method start. */
    static int paddingAfterOpcode(int offset) {
        return (4 - ((offset + 1) % 4)) % 4;
    }

    /** A switch instruction's length excluding alignment padding. */
    static int switchBaseLength(Instruction i) {
        if (i instanceof TableSwitchInstruction) {
            TableSwitchInstruction t = (TableSwitchInstruction) i;
            return 13 + (t.getHigh() - t.getLow() + 1) * 4;
        }
        LookupSwitchInstruction l = (LookupSwitchInstruction) i;
        return 9 + l.getNpairs() * 8;
    }

    /** The local-variable slot a load/store/iinc/ret (compact, general, or wide) references, or -1. */
    static int localVarIndex(Instruction i) {
        return i instanceof LocalVarInstruction ? ((LocalVarInstruction) i).getVarIndex() : -1;
    }

    static int localSlotSize(Instruction i) {
        if (i instanceof LLoadInstruction || i instanceof LStoreInstruction
                || i instanceof DLoadInstruction || i instanceof DStoreInstruction) {
            return 2;
        }
        if (i instanceof WideInstruction) {
            Opcode m = ((WideInstruction) i).getModifiedOpcode();
            if (m == LLOAD || m == LSTORE || m == DLOAD || m == DSTORE) {
                return 2;
            }
        }
        return 1;
    }

    static int invertConditionalOpcode(ConditionalBranchInstruction.BranchType t) {
        ConditionalBranchInstruction.BranchType inverse;
        switch (t) {
            case IFEQ: inverse = ConditionalBranchInstruction.BranchType.IFNE; break;
            case IFNE: inverse = ConditionalBranchInstruction.BranchType.IFEQ; break;
            case IFLT: inverse = ConditionalBranchInstruction.BranchType.IFGE; break;
            case IFGE: inverse = ConditionalBranchInstruction.BranchType.IFLT; break;
            case IFGT: inverse = ConditionalBranchInstruction.BranchType.IFLE; break;
            case IFLE: inverse = ConditionalBranchInstruction.BranchType.IFGT; break;
            case IF_ICMPEQ: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPNE; break;
            case IF_ICMPNE: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPEQ; break;
            case IF_ICMPLT: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPGE; break;
            case IF_ICMPGE: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPLT; break;
            case IF_ICMPGT: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPLE; break;
            case IF_ICMPLE: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPGT; break;
            case IF_ACMPEQ: inverse = ConditionalBranchInstruction.BranchType.IF_ACMPNE; break;
            case IF_ACMPNE: inverse = ConditionalBranchInstruction.BranchType.IF_ACMPEQ; break;
            case IFNULL: inverse = ConditionalBranchInstruction.BranchType.IFNONNULL; break;
            case IFNONNULL: inverse = ConditionalBranchInstruction.BranchType.IFNULL; break;
            default: throw new IllegalStateException("cannot invert conditional " + t);
        }
        return inverse.getOpcode();
    }
}

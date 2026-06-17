package com.tonic.analysis;

import com.tonic.analysis.instruction.*;
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ConstPool;
import com.tonic.utill.Opcode;

import java.util.Map;

/**
 * Renders the operand text of a single {@link Instruction} into a target buffer.
 *
 * <p>The line prefix ({@code offset: mnemonic}) is emitted by the caller; each {@code visit} method
 * appends only the operand suffix for its instruction, so a no-operand opcode contributes nothing
 * (the inherited no-op visits). Operands are read from the parsed instruction's accessors rather
 * than re-decoding bytes, keeping {@link InstructionFactory} the single bytecode decoder.
 *
 * <p>When a {@link DisassemblyContext} is supplied the renderer also appends verbose annotations:
 * {@code // name: descriptor} after local-slot operands and the resolved bootstrap after
 * invokedynamic. With a {@code null} context the output is the terse legacy listing.
 */
final class InstructionRenderer extends AbstractBytecodeVisitor {

    private final StringBuilder sb;
    private final ConstPool constPool;
    private final DisassemblyContext context;

    InstructionRenderer(StringBuilder sb, ConstPool constPool) {
        this(sb, constPool, null);
    }

    InstructionRenderer(StringBuilder sb, ConstPool constPool, DisassemblyContext context) {
        this.sb = sb;
        this.constPool = constPool;
        this.context = context;
    }

    /**
     * Appends the operand text for the given instruction. The caller is responsible for the line
     * prefix and the trailing newline.
     *
     * @param instr the instruction to render
     */
    void render(Instruction instr) {
        instr.accept(this);
    }

    @Override
    public void visit(BipushInstruction instr) {
        sb.append(instr.getValue());
    }

    @Override
    public void visit(SipushInstruction instr) {
        sb.append(instr.getValue());
    }

    @Override
    public void visit(LdcInstruction instr) {
        reference(instr.getCpIndex());
        condy(instr.getCpIndex());
    }

    @Override
    public void visit(LdcWInstruction instr) {
        reference(instr.getCpIndex());
        condy(instr.getCpIndex());
    }

    @Override
    public void visit(Ldc2WInstruction instr) {
        reference(instr.getCpIndex());
        condy(instr.getCpIndex());
    }

    @Override
    public void visit(ILoadInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(LLoadInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(FLoadInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(DLoadInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(ALoadInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(IStoreInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(LStoreInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(FStoreInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(DStoreInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(AStoreInstruction instr) {
        localIndex(instr, instr.getVarIndex());
    }

    @Override
    public void visit(RetInstruction instr) {
        sb.append(instr.getVarIndex());
        local(instr, instr.getVarIndex());
    }

    @Override
    public void visit(IIncInstruction instr) {
        sb.append(instr.getVarIndex()).append(", ").append(instr.getConstValue());
        local(instr, instr.getVarIndex());
    }

    @Override
    public void visit(ConditionalBranchInstruction instr) {
        appendTarget(instr.getOffset() + instr.getBranchOffset());
    }

    @Override
    public void visit(GotoInstruction instr) {
        int relative = instr.getType() == GotoInstruction.GotoType.GOTO_WIDE
                ? instr.getBranchOffsetWide()
                : instr.getBranchOffset();
        appendTarget(instr.getOffset() + relative);
    }

    @Override
    public void visit(JsrInstruction instr) {
        appendTarget(instr.getOffset() + instr.getBranchOffset());
    }

    @Override
    public void visit(GetFieldInstruction instr) {
        reference(instr.getFieldIndex());
    }

    @Override
    public void visit(PutFieldInstruction instr) {
        reference(instr.getFieldIndex());
    }

    @Override
    public void visit(InvokeVirtualInstruction instr) {
        reference(instr.getMethodIndex());
    }

    @Override
    public void visit(InvokeSpecialInstruction instr) {
        reference(instr.getMethodIndex());
    }

    @Override
    public void visit(InvokeStaticInstruction instr) {
        reference(instr.getMethodIndex());
    }

    @Override
    public void visit(InvokeInterfaceInstruction instr) {
        reference(instr.getMethodIndex());
        sb.append(", count=").append(instr.getCount()).append(", zero=0");
    }

    @Override
    public void visit(InvokeDynamicInstruction instr) {
        sb.append('#').append(instr.getCpIndex())
                .append(" (").append(instr.resolveMethod()).append(')');
        if (context != null) {
            sb.append(context.bootstrap(instr));
        }
    }

    @Override
    public void visit(NewInstruction instr) {
        reference(instr.getClassIndex());
    }

    @Override
    public void visit(ANewArrayInstruction instr) {
        reference(instr.getClassIndex());
    }

    @Override
    public void visit(CheckCastInstruction instr) {
        reference(instr.getClassIndex());
    }

    @Override
    public void visit(InstanceOfInstruction instr) {
        reference(instr.getClassIndex());
    }

    @Override
    public void visit(MultiANewArrayInstruction instr) {
        reference(instr.getClassIndex());
    }

    @Override
    public void visit(NewArrayInstruction instr) {
        sb.append(atypeDescription(instr.getTypeCode()));
    }

    @Override
    public void visit(WideInstruction instr) {
        sb.append(instr.getModifiedOpcode().getMnemonic()).append(' ').append(instr.getVarIndex());
        if (instr.getModifiedOpcode() == Opcode.IINC) {
            sb.append(", ").append(instr.getConstValue());
        }
        local(instr, instr.getVarIndex());
    }

    @Override
    public void visit(WideIIncInstruction instr) {
        sb.append("iinc ").append(instr.getVarIndex()).append(", ").append(instr.getConstValue());
        local(instr, instr.getVarIndex());
    }

    @Override
    public void visit(TableSwitchInstruction instr) {
        int base = instr.getOffset();
        int count = instr.getHigh() - instr.getLow() + 1;
        sb.append(" default=").append(formatTarget(base + instr.getDefaultOffset()))
                .append(", low=").append(instr.getLow())
                .append(", high=").append(instr.getHigh())
                .append(", count=").append(count);
        for (Map.Entry<Integer, Integer> entry : instr.getJumpOffsets().entrySet()) {
            sb.append("\n        case[").append(entry.getKey())
                    .append("] => offset ").append(formatTarget(base + entry.getValue()));
        }
    }

    @Override
    public void visit(LookupSwitchInstruction instr) {
        int base = instr.getOffset();
        sb.append(" default=").append(formatTarget(base + instr.getDefaultOffset()))
                .append(", npairs=").append(instr.getNpairs());
        for (Map.Entry<Integer, Integer> entry : instr.getMatchOffsets().entrySet()) {
            sb.append("\n        match=").append(entry.getKey())
                    .append(" => offset ").append(formatTarget(base + entry.getValue()));
        }
    }

    @Override
    public void visit(UnknownInstruction instr) {
        Opcode op = Opcode.fromCode(instr.getOpcode());
        if (op == Opcode.UNKNOWN) {
            sb.append(String.format("<unknown opcode 0x%02X>", instr.getOpcode()));
            return;
        }
        if (op == Opcode.WIDE) {
            renderInvalidWide(instr.getOperands());
            return;
        }
        if (op.getOperandCount() > 0) {
            sb.append(" <invalid>");
        }
    }

    /**
     * Renders the operand of a load/store instruction: the index for the general (2-byte) form, and
     * nothing for the compact {@code iload_0}-style forms which encode the slot in the opcode. A
     * local-variable annotation is appended in verbose mode.
     */
    private void localIndex(Instruction instr, int varIndex) {
        if (instr.getLength() == 2) {
            sb.append(varIndex);
        }
        local(instr, varIndex);
    }

    /**
     * Appends a {@code // name: descriptor} annotation for the local slot, when a verbose context is
     * present and the slot is named at this instruction's offset.
     */
    private void local(Instruction instr, int slot) {
        if (context != null) {
            sb.append(context.localAnnotation(instr.getOffset(), slot));
        }
    }

    /**
     * Appends a branch target as an absolute bytecode offset, zero-padded to match the offset column.
     */
    private void appendTarget(int absoluteOffset) {
        sb.append(formatTarget(absoluteOffset));
    }

    private static String formatTarget(int absoluteOffset) {
        return String.format("%04d", absoluteOffset);
    }

    /**
     * Appends a constant-pool reference operand as {@code #index (resolved)}.
     */
    private void reference(int index) {
        sb.append('#').append(index).append(" (").append(ConstPoolFormat.reference(constPool, index)).append(')');
    }

    /**
     * Appends the resolved condy bootstrap annotation for a ldc-family operand, when a verbose context
     * is present and the loaded constant is a {@code CONSTANT_Dynamic}.
     */
    private void condy(int index) {
        if (context != null) {
            sb.append(context.condy(index));
        }
    }

    /**
     * Renders the suffix for a malformed WIDE instruction. A recognized modified opcode is named
     * before {@code <invalid>}; an unrecognized one is reported as {@code <unsupported>}; a WIDE with
     * no following byte is simply {@code <invalid>}.
     */
    private void renderInvalidWide(byte[] operands) {
        if (operands.length == 0) {
            sb.append(" <invalid>");
            return;
        }
        int sub = operands[0] & 0xFF;
        if (isWideModifier(sub)) {
            sb.append(Opcode.fromCode(sub).getMnemonic()).append(" <invalid>");
        } else {
            sb.append(" <unsupported>");
        }
    }

    private static boolean isWideModifier(int sub) {
        return (sub >= 0x15 && sub <= 0x19)
                || (sub >= 0x36 && sub <= 0x3A)
                || sub == 0x84
                || sub == 0xA9;
    }

    /**
     * Provides a human-readable element type for a NEWARRAY atype code.
     *
     * @param atype the array type code
     * @return the element type name, or {@code unknown_atype_N} for an unrecognized code
     */
    private static String atypeDescription(int atype) {
        switch (atype) {
            case 4:  return "boolean";
            case 5:  return "char";
            case 6:  return "float";
            case 7:  return "double";
            case 8:  return "byte";
            case 9:  return "short";
            case 10: return "int";
            case 11: return "long";
            default: return "unknown_atype_" + atype;
        }
    }
}

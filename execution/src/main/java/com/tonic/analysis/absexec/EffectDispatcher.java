package com.tonic.analysis.absexec;

import com.tonic.analysis.instruction.ALoadInstruction;
import com.tonic.analysis.instruction.AStoreInstruction;
import com.tonic.analysis.instruction.ConditionalBranchInstruction;
import com.tonic.analysis.instruction.DLoadInstruction;
import com.tonic.analysis.instruction.DStoreInstruction;
import com.tonic.analysis.instruction.FLoadInstruction;
import com.tonic.analysis.instruction.FStoreInstruction;
import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.instruction.IIncInstruction;
import com.tonic.analysis.instruction.ILoadInstruction;
import com.tonic.analysis.instruction.IStoreInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeInterfaceInstruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.InvokeStaticInstruction;
import com.tonic.analysis.instruction.InvokeVirtualInstruction;
import com.tonic.analysis.instruction.LLoadInstruction;
import com.tonic.analysis.instruction.LStoreInstruction;
import com.tonic.analysis.instruction.LookupSwitchInstruction;
import com.tonic.analysis.instruction.TableSwitchInstruction;
import com.tonic.util.DescriptorUtil;
import com.tonic.util.Opcode;

import static com.tonic.util.Opcode.*;

/**
 * Computes the operand-stack / local-variable effect of one bytecode instruction during abstract execution,
 * building the def-use links on the {@link InsnContext} and driving branch/jump/stop on the {@link Frame}.
 */
final class EffectDispatcher
{

    private EffectDispatcher()
    {
    }

    static void execute(Instruction insn, Frame frame, InsnContext ictx)
    {
        int op = insn.getOpcode();
        Opcode opcode = Opcode.fromCode(op);
        Stack stack = frame.stack();

        switch (opcode)
        {
            case NOP: // nop
                return;

            // constants: push one (wide for long/double)
            case ACONST_NULL: // aconst_null
            case ICONST_M1: case ICONST_0: case ICONST_1: case ICONST_2: case ICONST_3: case ICONST_4: case ICONST_5: // iconst
            case FCONST_0: case FCONST_1: case FCONST_2: // fconst
            case BIPUSH: case SIPUSH: // bipush/sipush
            case LDC: case LDC_W: // ldc/ldc_w
                push(ictx, stack, false);
                return;
            case LCONST_0: case LCONST_1: // lconst
            case DCONST_0: case DCONST_1: // dconst
            case LDC2_W: // ldc2_w
                push(ictx, stack, true);
                return;

            // loads: read local, push
            case ILOAD: load(ictx, frame, ((ILoadInstruction) insn).getVarIndex(), false); return;
            case FLOAD: load(ictx, frame, ((FLoadInstruction) insn).getVarIndex(), false); return;
            case ALOAD: load(ictx, frame, ((ALoadInstruction) insn).getVarIndex(), false); return;
            case LLOAD: load(ictx, frame, ((LLoadInstruction) insn).getVarIndex(), true); return;
            case DLOAD: load(ictx, frame, ((DLoadInstruction) insn).getVarIndex(), true); return;
            case ILOAD_0: case ILOAD_1: case ILOAD_2: case ILOAD_3: load(ictx, frame, op - ILOAD_0.getCode(), false); return; // iload_n
            case FLOAD_0: case FLOAD_1: case FLOAD_2: case FLOAD_3: load(ictx, frame, op - FLOAD_0.getCode(), false); return; // fload_n
            case ALOAD_0: case ALOAD_1: case ALOAD_2: case ALOAD_3: load(ictx, frame, op - ALOAD_0.getCode(), false); return; // aload_n
            case LLOAD_0: case LLOAD_1: case LLOAD_2: case LLOAD_3: load(ictx, frame, op - LLOAD_0.getCode(), true); return; // lload_n
            case DLOAD_0: case DLOAD_1: case DLOAD_2: case DLOAD_3: load(ictx, frame, op - DLOAD_0.getCode(), true); return; // dload_n

            // array loads: pop arrayref+index, push element
            case IALOAD: case FALOAD: case AALOAD: case BALOAD: case CALOAD: case SALOAD: // iaload/faload/aaload/baload/caload/saload
                pop(ictx, stack, 2); push(ictx, stack, false); return;
            case LALOAD: case DALOAD: // laload/daload
                pop(ictx, stack, 2); push(ictx, stack, true); return;

            // stores: pop value, store local
            case ISTORE: store(ictx, frame, ((IStoreInstruction) insn).getVarIndex(), false); return;
            case FSTORE: store(ictx, frame, ((FStoreInstruction) insn).getVarIndex(), false); return;
            case ASTORE: store(ictx, frame, ((AStoreInstruction) insn).getVarIndex(), false); return;
            case LSTORE: store(ictx, frame, ((LStoreInstruction) insn).getVarIndex(), true); return;
            case DSTORE: store(ictx, frame, ((DStoreInstruction) insn).getVarIndex(), true); return;
            case ISTORE_0: case ISTORE_1: case ISTORE_2: case ISTORE_3: store(ictx, frame, op - ISTORE_0.getCode(), false); return; // istore_n
            case FSTORE_0: case FSTORE_1: case FSTORE_2: case FSTORE_3: store(ictx, frame, op - FSTORE_0.getCode(), false); return; // fstore_n
            case ASTORE_0: case ASTORE_1: case ASTORE_2: case ASTORE_3: store(ictx, frame, op - ASTORE_0.getCode(), false); return; // astore_n
            case LSTORE_0: case LSTORE_1: case LSTORE_2: case LSTORE_3: store(ictx, frame, op - LSTORE_0.getCode(), true); return; // lstore_n
            case DSTORE_0: case DSTORE_1: case DSTORE_2: case DSTORE_3: store(ictx, frame, op - DSTORE_0.getCode(), true); return; // dstore_n

            // array stores: pop arrayref+index+value
            case IASTORE: case FASTORE: case AASTORE: case BASTORE: case CASTORE: case SASTORE: pop(ictx, stack, 3); return;
            case LASTORE: case DASTORE: pop(ictx, stack, 3); return; // lastore/dastore (value is one wide entry)

            // stack manipulation
            case POP: pop(ictx, stack, 1); return; // pop
            case POP2: pop2(ictx, stack); return; // pop2
            case DUP: dup(ictx, stack); return; // dup
            case DUP_X1: dupX1(ictx, stack); return; // dup_x1
            case DUP_X2: dupX2(ictx, stack); return; // dup_x2
            case DUP2: dup2(ictx, stack); return; // dup2
            case DUP2_X1: case DUP2_X2: dup2Generic(ictx, stack, opcode); return; // dup2_x1 / dup2_x2 (conservative)
            case SWAP: swap(stack); return; // swap

            // binary arithmetic / logic: pop 2, push 1 (wide for long/double)
            case IADD: case ISUB: case IMUL: case IDIV: case IREM: // i: add/sub/mul/div/rem
            case ISHL: case ISHR: case IUSHR: case IAND: case IOR: case IXOR: // i: shl/shr/ushr/and/or/xor
                pop(ictx, stack, 2); push(ictx, stack, false); return;
            case LADD: case LSUB: case LMUL: case LDIV: case LREM: // l: add/sub/mul/div/rem
            case LAND: case LOR: case LXOR: // l: and/or/xor
                pop(ictx, stack, 2); push(ictx, stack, true); return;
            case LSHL: case LSHR: case LUSHR: // lshl/lshr/lushr: pop long + int, push long
                pop(ictx, stack, 2); push(ictx, stack, true); return;
            case FADD: case FSUB: case FMUL: case FDIV: case FREM: // f arith
                pop(ictx, stack, 2); push(ictx, stack, false); return;
            case DADD: case DSUB: case DMUL: case DDIV: case DREM: // d arith
                pop(ictx, stack, 2); push(ictx, stack, true); return;

            // negation: pop 1, push 1 (same width)
            case INEG: case FNEG: pop(ictx, stack, 1); push(ictx, stack, false); return; // ineg/fneg
            case LNEG: case DNEG: pop(ictx, stack, 1); push(ictx, stack, true); return; // lneg/dneg

            case IINC: iinc(ictx, frame, (IIncInstruction) insn); return;

            // conversions: pop 1, push 1 sized by the TARGET type, not the source
            case I2L: case I2D: case L2D: case F2L: case F2D: case D2L:
                convert(ictx, stack, true); return;
            case I2F: case L2I: case L2F: case F2I: case D2I: case D2F:
            case I2B: case I2C: case I2S:
                convert(ictx, stack, false); return;

            // comparisons: pop 2, push int
            case LCMP: case FCMPL: case FCMPG: case DCMPL: case DCMPG: // lcmp/fcmpl/fcmpg/dcmpl/dcmpg
                pop(ictx, stack, 2); push(ictx, stack, false); return;

            // conditional branches
            case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE: // if<cond> (ifeq..ifle)
            case IFNULL: case IFNONNULL: // ifnull/ifnonnull
                pop(ictx, stack, 1);
                branch(ictx, frame, (ConditionalBranchInstruction) insn);
                return;
            case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE: // if_icmp<cond>
            case IF_ACMPEQ: case IF_ACMPNE: // if_acmp<cond>
                pop(ictx, stack, 2);
                branch(ictx, frame, (ConditionalBranchInstruction) insn);
                return;

            case GOTO: case GOTO_W: { // goto / goto_w
                int target = insn.getOffset() + ((GotoInstruction) insn).getBranchOffset();
                frame.jumpTo(ictx, frame.instructionAtOffset(target));
                return;
            }

            case TABLESWITCH: tableSwitch(ictx, frame, (TableSwitchInstruction) insn); return;
            case LOOKUPSWITCH: lookupSwitch(ictx, frame, (LookupSwitchInstruction) insn); return;

            // returns / throw
            case IRETURN: case LRETURN: case FRETURN: case DRETURN: case ARETURN: pop(ictx, stack, 1); frame.stop(); return;
            case RETURN_: frame.stop(); return; // return (void)
            case ATHROW: pop(ictx, stack, 1); frame.stop(); return; // athrow

            // field access
            case GETSTATIC: getField(ictx, stack, (GetFieldInstruction) insn, false); return; // getstatic
            case GETFIELD: getField(ictx, stack, (GetFieldInstruction) insn, true); return; // getfield
            case PUTSTATIC: putField(ictx, stack, false); return; // putstatic
            case PUTFIELD: putField(ictx, stack, true); return; // putfield

            // invokes
            case INVOKEVIRTUAL: case INVOKESPECIAL: case INVOKEINTERFACE: invoke(ictx, stack, insn, true); return; // virtual/special/interface
            case INVOKESTATIC: invoke(ictx, stack, insn, false); return; // static
            case INVOKEDYNAMIC: generic(ictx, stack, insn); return; // dynamic (no receiver; effect via net stack change)

            // object/array creation & misc with simple effects
            case NEW: push(ictx, stack, false); return; // new
            case NEWARRAY: case ANEWARRAY: pop(ictx, stack, 1); push(ictx, stack, false); return; // newarray/anewarray
            case ARRAYLENGTH: pop(ictx, stack, 1); push(ictx, stack, false); return; // arraylength
            case CHECKCAST: return; // checkcast: pop1 push1 same ref -> net 0, leave stack as-is (top stays)
            case INSTANCEOF: pop(ictx, stack, 1); push(ictx, stack, false); return; // instanceof
            case MONITORENTER: case MONITOREXIT: pop(ictx, stack, 1); return; // monitorenter/exit

            default:
                // Generic fallback by net stack change (e.g. multianewarray, wide-prefixed, jsr/ret).
                generic(ictx, stack, insn);
        }
    }

    // helpers

    private static void push(InsnContext ictx, Stack stack, boolean wide)
    {
        StackCtx s = new StackCtx(ictx, wide);
        ictx.push(s);
        stack.push(s);
    }

    private static void pop(InsnContext ictx, Stack stack, int n)
    {
        StackCtx[] popped = new StackCtx[n];
        for (int i = 0; i < n; i++)
        {
            popped[i] = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
        }
        ictx.pop(popped);
    }

    private static void load(InsnContext ictx, Frame frame, int idx, boolean wide)
    {
        VarCtx v = frame.variables().get(idx);
        if (v != null)
        {
            ictx.read(v);
        }
        push(ictx, frame.stack(), wide);
    }

    private static void store(InsnContext ictx, Frame frame, int idx, boolean wide)
    {
        pop(ictx, frame.stack(), 1);
        frame.variables().set(idx, new VarCtx(ictx, wide));
    }

    private static void iinc(InsnContext ictx, Frame frame, IIncInstruction insn)
    {
        VarCtx v = frame.variables().get(insn.getVarIndex());
        if (v != null)
        {
            ictx.read(v);
        }
        frame.variables().set(insn.getVarIndex(), new VarCtx(ictx, false));
    }

    private static void convert(InsnContext ictx, Stack stack, boolean targetWide)
    {
        pop(ictx, stack, 1);
        push(ictx, stack, targetWide);
    }

    private static void getField(InsnContext ictx, Stack stack, GetFieldInstruction insn, boolean instance)
    {
        if (instance)
        {
            pop(ictx, stack, 1); // objref
        }
        push(ictx, stack, isWideDesc(insn.getFieldDescriptor()));
    }

    private static void putField(InsnContext ictx, Stack stack, boolean instance)
    {
        pop(ictx, stack, instance ? 2 : 1); // value (+ objref for putfield)
    }

    private static void invoke(InsnContext ictx, Stack stack, Instruction insn, boolean hasReceiver)
    {
        String desc = methodDescriptor(insn);
        int argEntries = desc == null ? 0 : DescriptorUtil.parseParameterDescriptors(desc).size();
        pop(ictx, stack, argEntries + (hasReceiver ? 1 : 0));
        String ret = returnDescriptor(desc);
        if (ret != null && !ret.equals("V"))
        {
            push(ictx, stack, ret.equals("J") || ret.equals("D"));
        }
    }

    private static void branch(InsnContext ictx, Frame frame, ConditionalBranchInstruction insn)
    {
        int target = insn.getOffset() + insn.getBranchOffset();
        frame.fork(ictx, frame.instructionAtOffset(target)); // taken path; fall-through continues this frame
    }

    private static void tableSwitch(InsnContext ictx, Frame frame, TableSwitchInstruction insn)
    {
        pop(ictx, frame.stack(), 1);
        frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + insn.getDefaultOffset()));
        for (Integer off : insn.getJumpOffsets().values())
        {
            frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + off));
        }
        frame.stop(); // all successors are explicit forks
    }

    private static void lookupSwitch(InsnContext ictx, Frame frame, LookupSwitchInstruction insn)
    {
        pop(ictx, frame.stack(), 1);
        frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + insn.getDefaultOffset()));
        for (Integer off : insn.getMatchOffsets().values())
        {
            frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + off));
        }
        frame.stop();
    }

    private static void generic(InsnContext ictx, Stack stack, Instruction insn)
    {
        int change = insn.getStackChange();
        if (change < 0)
        {
            pop(ictx, stack, -change);
        }
        else
        {
            for (int i = 0; i < change; i++)
            {
                push(ictx, stack, false);
            }
        }
    }

    // dup/swap (rearrange existing StackCtx so def-use of duplicated values is preserved)

    private static void dup(InsnContext ictx, Stack stack)
    {
        StackCtx top = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
        stack.push(top);
        stack.push(top);
        ictx.push(top);
    }

    private static void dupX1(InsnContext ictx, Stack stack)
    {
        StackCtx a = stack.pop(); // top
        StackCtx b = stack.pop();
        stack.push(a);
        stack.push(b);
        stack.push(a);
        ictx.push(a);
    }

    private static void dupX2(InsnContext ictx, Stack stack)
    {
        StackCtx a = stack.pop();
        StackCtx b = stack.pop();
        if (b.isWide())
        {
            stack.push(a);
            stack.push(b);
            stack.push(a);
        }
        else
        {
            StackCtx c = stack.pop();
            stack.push(a);
            stack.push(c);
            stack.push(b);
            stack.push(a);
        }
        ictx.push(a);
    }

    private static void dup2(InsnContext ictx, Stack stack)
    {
        StackCtx a = stack.pop();
        if (a.isWide())
        {
            stack.push(a);
            stack.push(a);
            ictx.push(a);
        }
        else
        {
            StackCtx b = stack.pop();
            stack.push(b);
            stack.push(a);
            stack.push(b);
            stack.push(a);
            ictx.push(b, a);
        }
    }

    private static void dup2Generic(InsnContext ictx, Stack stack, Opcode opcode)
    {
        // dup2_x1 / dup2_x2: conservative - duplicate the top one/two values past the insertion point.
        StackCtx a = stack.pop();
        StackCtx b = stack.pop();
        if (a.isWide())
        {
            if (opcode == DUP2_X2 && !b.isWide())
            {
                StackCtx c = stack.pop();
                stack.push(a); stack.push(c);
            }
            else
            {
                stack.push(a);
            }
            stack.push(b);
            stack.push(a);
            ictx.push(a);
        }
        else
        {
            StackCtx c = stack.pop();
            stack.push(b); stack.push(a); stack.push(c); stack.push(b); stack.push(a);
            ictx.push(b, a);
        }
    }

    private static void pop2(InsnContext ictx, Stack stack)
    {
        StackCtx a = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
        if (a.isWide())
        {
            ictx.pop(a);
        }
        else
        {
            StackCtx b = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
            ictx.pop(a, b);
        }
    }

    private static void swap(Stack stack)
    {
        StackCtx a = stack.pop();
        StackCtx b = stack.pop();
        stack.push(a);
        stack.push(b);
    }

    private static boolean isWideDesc(String desc)
    {
        return desc != null && (desc.equals("J") || desc.equals("D"));
    }

    private static String methodDescriptor(Instruction insn)
    {
        if (insn instanceof InvokeVirtualInstruction)
        {
            return ((InvokeVirtualInstruction) insn).getMethodDescriptor();
        }
        if (insn instanceof InvokeSpecialInstruction)
        {
            return ((InvokeSpecialInstruction) insn).getMethodDescriptor();
        }
        if (insn instanceof InvokeStaticInstruction)
        {
            return ((InvokeStaticInstruction) insn).getMethodDescriptor();
        }
        if (insn instanceof InvokeInterfaceInstruction)
        {
            return ((InvokeInterfaceInstruction) insn).getMethodDescriptor();
        }
        return null;
    }

    private static String returnDescriptor(String methodDesc)
    {
        if (methodDesc == null)
        {
            return null;
        }
        int close = methodDesc.lastIndexOf(')');
        return close < 0 ? null : methodDesc.substring(close + 1);
    }
}

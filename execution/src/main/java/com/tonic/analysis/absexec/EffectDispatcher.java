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
import com.tonic.analysis.instruction.LLoadInstruction;
import com.tonic.analysis.instruction.LStoreInstruction;
import com.tonic.analysis.instruction.LookupSwitchInstruction;
import com.tonic.analysis.instruction.PutFieldInstruction;
import com.tonic.analysis.instruction.TableSwitchInstruction;
import com.tonic.util.DescriptorUtil;

/**
 * Computes the operand-stack / local-variable effect of one bytecode instruction during abstract execution,
 * building the def-use links on the {@link InsnContext} and driving branch/jump/stop on the {@link Frame}.
 *
 * <p>Mirrors the opcode dispatch of the SSA lifter's {@code InstructionTranslator} but produces def-use
 * instead of SSA. Each value (including long/double) is one logical {@link StackCtx}, so pop/push counts are
 * simple. Instructions the deobfuscators inspect (fields, constants, multiplies, dup/swap, loads/stores,
 * branches) are modelled precisely; the rest fall back to {@link Instruction#getStackChange()}.
 */
final class EffectDispatcher {

    private EffectDispatcher() {
    }

    static void execute(Instruction insn, Frame frame, InsnContext ictx) {
        int op = insn.getOpcode();
        Stack stack = frame.stack();
        Variables vars = frame.variables();

        switch (op) {
            case 0x00: // nop
                return;

            // --- constants: push one (wide for long/double) ---
            case 0x01: // aconst_null
            case 0x02: case 0x03: case 0x04: case 0x05: case 0x06: case 0x07: case 0x08: // iconst
            case 0x0B: case 0x0C: case 0x0D: // fconst
            case 0x10: case 0x11: // bipush/sipush
            case 0x12: case 0x13: // ldc/ldc_w
                push(ictx, stack, false);
                return;
            case 0x09: case 0x0A: // lconst
            case 0x0E: case 0x0F: // dconst
            case 0x14: // ldc2_w
                push(ictx, stack, true);
                return;

            // --- loads: read local, push ---
            case 0x15: load(ictx, frame, ((ILoadInstruction) insn).getVarIndex(), false); return;
            case 0x17: load(ictx, frame, ((FLoadInstruction) insn).getVarIndex(), false); return;
            case 0x19: load(ictx, frame, ((ALoadInstruction) insn).getVarIndex(), false); return;
            case 0x16: load(ictx, frame, ((LLoadInstruction) insn).getVarIndex(), true); return;
            case 0x18: load(ictx, frame, ((DLoadInstruction) insn).getVarIndex(), true); return;
            case 0x1A: case 0x1B: case 0x1C: case 0x1D: load(ictx, frame, op - 0x1A, false); return; // iload_n
            case 0x22: case 0x23: case 0x24: case 0x25: load(ictx, frame, op - 0x22, false); return; // fload_n
            case 0x2A: case 0x2B: case 0x2C: case 0x2D: load(ictx, frame, op - 0x2A, false); return; // aload_n
            case 0x1E: case 0x1F: case 0x20: case 0x21: load(ictx, frame, op - 0x1E, true); return; // lload_n
            case 0x26: case 0x27: case 0x28: case 0x29: load(ictx, frame, op - 0x26, true); return; // dload_n

            // --- array loads: pop arrayref+index, push element ---
            case 0x2E: case 0x30: case 0x32: case 0x33: case 0x34: case 0x35: // iaload/faload/aaload/baload/caload/saload
                pop(ictx, stack, 2); push(ictx, stack, false); return;
            case 0x2F: case 0x31: // laload/daload
                pop(ictx, stack, 2); push(ictx, stack, true); return;

            // --- stores: pop value, store local ---
            case 0x36: store(ictx, frame, ((IStoreInstruction) insn).getVarIndex(), false); return;
            case 0x38: store(ictx, frame, ((FStoreInstruction) insn).getVarIndex(), false); return;
            case 0x3A: store(ictx, frame, ((AStoreInstruction) insn).getVarIndex(), false); return;
            case 0x37: store(ictx, frame, ((LStoreInstruction) insn).getVarIndex(), true); return;
            case 0x39: store(ictx, frame, ((DStoreInstruction) insn).getVarIndex(), true); return;
            case 0x3B: case 0x3C: case 0x3D: case 0x3E: store(ictx, frame, op - 0x3B, false); return; // istore_n
            case 0x43: case 0x44: case 0x45: case 0x46: store(ictx, frame, op - 0x43, false); return; // fstore_n
            case 0x4B: case 0x4C: case 0x4D: case 0x4E: store(ictx, frame, op - 0x4B, false); return; // astore_n
            case 0x3F: case 0x40: case 0x41: case 0x42: store(ictx, frame, op - 0x3F, true); return; // lstore_n
            case 0x47: case 0x48: case 0x49: case 0x4A: store(ictx, frame, op - 0x47, true); return; // dstore_n

            // --- array stores: pop arrayref+index+value ---
            case 0x4F: case 0x51: case 0x53: case 0x54: case 0x55: case 0x56: pop(ictx, stack, 3); return;
            case 0x50: case 0x52: pop(ictx, stack, 3); return; // lastore/dastore (value is one wide entry)

            // --- stack manipulation ---
            case 0x57: pop(ictx, stack, 1); return; // pop
            case 0x58: pop2(ictx, stack); return; // pop2
            case 0x59: dup(ictx, stack); return; // dup
            case 0x5A: dupX1(ictx, stack); return; // dup_x1
            case 0x5B: dupX2(ictx, stack); return; // dup_x2
            case 0x5C: dup2(ictx, stack); return; // dup2
            case 0x5D: case 0x5E: dup2Generic(ictx, stack, op); return; // dup2_x1 / dup2_x2 (conservative)
            case 0x5F: swap(ictx, stack); return; // swap

            // --- binary arithmetic / logic: pop 2, push 1 (wide for long/double) ---
            case 0x60: case 0x64: case 0x68: case 0x6C: case 0x70: // i: add/sub/mul/div/rem
            case 0x78: case 0x7A: case 0x7C: case 0x7E: case 0x80: case 0x82: // i: shl/shr/ushr/and/or/xor
                pop(ictx, stack, 2); push(ictx, stack, false); return;
            case 0x61: case 0x65: case 0x69: case 0x6D: case 0x71: // l: add/sub/mul/div/rem
            case 0x7F: case 0x81: case 0x83: // l: and/or/xor
                pop(ictx, stack, 2); push(ictx, stack, true); return;
            case 0x79: case 0x7B: case 0x7D: // lshl/lshr/lushr: pop long + int, push long
                pop(ictx, stack, 2); push(ictx, stack, true); return;
            case 0x62: case 0x66: case 0x6A: case 0x6E: case 0x72: // f arith
                pop(ictx, stack, 2); push(ictx, stack, false); return;
            case 0x63: case 0x67: case 0x6B: case 0x6F: case 0x73: // d arith
                pop(ictx, stack, 2); push(ictx, stack, true); return;

            // --- negation: pop 1, push 1 (same width) ---
            case 0x74: case 0x76: pop(ictx, stack, 1); push(ictx, stack, false); return; // ineg/fneg
            case 0x75: case 0x77: pop(ictx, stack, 1); push(ictx, stack, true); return; // lneg/dneg

            case 0x84: iinc(ictx, frame, (IIncInstruction) insn); return;

            // --- conversions: pop 1, push 1 (target width) ---
            case 0x85: case 0x8A: convert(ictx, stack, true); return; // i2l, f2l
            case 0x87: case 0x89: convert(ictx, stack, true); return; // i2d, l2d... handled below precisely
            case 0x86: case 0x88: case 0x8B: case 0x8E: convert(ictx, stack, false); return; // i2f,l2i,f2i,d2i
            case 0x8C: convert(ictx, stack, true); return; // f2l
            case 0x8D: convert(ictx, stack, true); return; // f2d
            case 0x8F: case 0x90: convert(ictx, stack, op == 0x8F); return; // d2l(wide), d2f(narrow)
            case 0x91: case 0x92: case 0x93: convert(ictx, stack, false); return; // i2b,i2c,i2s

            // --- comparisons: pop 2, push int ---
            case 0x94: case 0x95: case 0x96: case 0x97: case 0x98: // lcmp/fcmpl/fcmpg/dcmpl/dcmpg
                pop(ictx, stack, 2); push(ictx, stack, false); return;

            // --- conditional branches ---
            case 0x99: case 0x9A: case 0x9B: case 0x9C: case 0x9D: case 0x9E: // if<cond> (ifeq..ifle)
            case 0xC6: case 0xC7: // ifnull/ifnonnull
                pop(ictx, stack, 1);
                branch(ictx, frame, (ConditionalBranchInstruction) insn);
                return;
            case 0x9F: case 0xA0: case 0xA1: case 0xA2: case 0xA3: case 0xA4: // if_icmp<cond>
            case 0xA5: case 0xA6: // if_acmp<cond>
                pop(ictx, stack, 2);
                branch(ictx, frame, (ConditionalBranchInstruction) insn);
                return;

            case 0xA7: case 0xC8: { // goto / goto_w
                int target = insn.getOffset() + ((GotoInstruction) insn).getBranchOffset();
                frame.jumpTo(ictx, frame.instructionAtOffset(target));
                return;
            }

            case 0xAA: tableSwitch(ictx, frame, (TableSwitchInstruction) insn); return;
            case 0xAB: lookupSwitch(ictx, frame, (LookupSwitchInstruction) insn); return;

            // --- returns / throw ---
            case 0xAC: case 0xAD: case 0xAE: case 0xAF: case 0xB0: pop(ictx, stack, 1); frame.stop(); return;
            case 0xB1: frame.stop(); return; // return (void)
            case 0xBF: pop(ictx, stack, 1); frame.stop(); return; // athrow

            // --- field access ---
            case 0xB2: getField(ictx, stack, (GetFieldInstruction) insn, false); return; // getstatic
            case 0xB4: getField(ictx, stack, (GetFieldInstruction) insn, true); return; // getfield
            case 0xB3: putField(ictx, stack, (PutFieldInstruction) insn, false); return; // putstatic
            case 0xB5: putField(ictx, stack, (PutFieldInstruction) insn, true); return; // putfield

            // --- invokes ---
            case 0xB6: case 0xB7: case 0xB9: invoke(ictx, stack, insn, true); return; // virtual/special/interface
            case 0xB8: invoke(ictx, stack, insn, false); return; // static
            case 0xBA: generic(ictx, stack, insn); return; // dynamic (no receiver; effect via net stack change)

            // --- object/array creation & misc with simple effects ---
            case 0xBB: push(ictx, stack, false); return; // new
            case 0xBC: case 0xBD: pop(ictx, stack, 1); push(ictx, stack, false); return; // newarray/anewarray
            case 0xBE: pop(ictx, stack, 1); push(ictx, stack, false); return; // arraylength
            case 0xC0: return; // checkcast: pop1 push1 same ref -> net 0, leave stack as-is (top stays)
            case 0xC1: pop(ictx, stack, 1); push(ictx, stack, false); return; // instanceof
            case 0xC2: case 0xC3: pop(ictx, stack, 1); return; // monitorenter/exit

            default:
                // Generic fallback by net stack change (e.g. multianewarray, wide-prefixed, jsr/ret).
                generic(ictx, stack, insn);
                return;
        }
    }

    // --- helpers ---

    private static void push(InsnContext ictx, Stack stack, boolean wide) {
        StackCtx s = new StackCtx(ictx, wide);
        ictx.push(s);
        stack.push(s);
    }

    private static void pop(InsnContext ictx, Stack stack, int n) {
        StackCtx[] popped = new StackCtx[n];
        for (int i = 0; i < n; i++) {
            popped[i] = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
        }
        ictx.pop(popped);
    }

    private static void load(InsnContext ictx, Frame frame, int idx, boolean wide) {
        VarCtx v = frame.variables().get(idx);
        if (v != null) {
            ictx.read(v);
        }
        push(ictx, frame.stack(), wide);
    }

    private static void store(InsnContext ictx, Frame frame, int idx, boolean wide) {
        pop(ictx, frame.stack(), 1);
        frame.variables().set(idx, new VarCtx(ictx, wide));
    }

    private static void iinc(InsnContext ictx, Frame frame, IIncInstruction insn) {
        VarCtx v = frame.variables().get(insn.getVarIndex());
        if (v != null) {
            ictx.read(v);
        }
        frame.variables().set(insn.getVarIndex(), new VarCtx(ictx, false));
    }

    private static void convert(InsnContext ictx, Stack stack, boolean targetWide) {
        pop(ictx, stack, 1);
        push(ictx, stack, targetWide);
    }

    private static void getField(InsnContext ictx, Stack stack, GetFieldInstruction insn, boolean instance) {
        if (instance) {
            pop(ictx, stack, 1); // objref
        }
        push(ictx, stack, isWideDesc(insn.getFieldDescriptor()));
    }

    private static void putField(InsnContext ictx, Stack stack, PutFieldInstruction insn, boolean instance) {
        pop(ictx, stack, instance ? 2 : 1); // value (+ objref for putfield)
    }

    private static void invoke(InsnContext ictx, Stack stack, Instruction insn, boolean hasReceiver) {
        String desc = methodDescriptor(insn);
        int argEntries = desc == null ? 0 : DescriptorUtil.parseParameterDescriptors(desc).size();
        pop(ictx, stack, argEntries + (hasReceiver ? 1 : 0));
        String ret = returnDescriptor(desc);
        if (ret != null && !ret.equals("V")) {
            push(ictx, stack, ret.equals("J") || ret.equals("D"));
        }
    }

    private static void branch(InsnContext ictx, Frame frame, ConditionalBranchInstruction insn) {
        int target = insn.getOffset() + insn.getBranchOffset();
        frame.fork(ictx, frame.instructionAtOffset(target)); // taken path; fall-through continues this frame
    }

    private static void tableSwitch(InsnContext ictx, Frame frame, TableSwitchInstruction insn) {
        pop(ictx, frame.stack(), 1);
        frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + insn.getDefaultOffset()));
        for (Integer off : insn.getJumpOffsets().values()) {
            frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + off));
        }
        frame.stop(); // all successors are explicit forks
    }

    private static void lookupSwitch(InsnContext ictx, Frame frame, LookupSwitchInstruction insn) {
        pop(ictx, frame.stack(), 1);
        frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + insn.getDefaultOffset()));
        for (Integer off : insn.getMatchOffsets().values()) {
            frame.fork(ictx, frame.instructionAtOffset(insn.getOffset() + off));
        }
        frame.stop();
    }

    private static void generic(InsnContext ictx, Stack stack, Instruction insn) {
        int change = insn.getStackChange();
        if (change < 0) {
            pop(ictx, stack, -change);
        } else {
            for (int i = 0; i < change; i++) {
                push(ictx, stack, false);
            }
        }
    }

    // --- dup/swap (rearrange existing StackCtx so def-use of duplicated values is preserved) ---

    private static void dup(InsnContext ictx, Stack stack) {
        StackCtx top = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
        stack.push(top);
        stack.push(top);
        ictx.push(top);
    }

    private static void dupX1(InsnContext ictx, Stack stack) {
        StackCtx a = stack.pop(); // top
        StackCtx b = stack.pop();
        stack.push(a);
        stack.push(b);
        stack.push(a);
        ictx.push(a);
    }

    private static void dupX2(InsnContext ictx, Stack stack) {
        StackCtx a = stack.pop();
        StackCtx b = stack.pop();
        if (b.isWide()) {
            stack.push(a);
            stack.push(b);
            stack.push(a);
        } else {
            StackCtx c = stack.pop();
            stack.push(a);
            stack.push(c);
            stack.push(b);
            stack.push(a);
        }
        ictx.push(a);
    }

    private static void dup2(InsnContext ictx, Stack stack) {
        StackCtx a = stack.pop();
        if (a.isWide()) {
            stack.push(a);
            stack.push(a);
            ictx.push(a);
        } else {
            StackCtx b = stack.pop();
            stack.push(b);
            stack.push(a);
            stack.push(b);
            stack.push(a);
            ictx.push(b, a);
        }
    }

    private static void dup2Generic(InsnContext ictx, Stack stack, int op) {
        // dup2_x1 / dup2_x2: conservative — duplicate the top one/two values past the insertion point.
        StackCtx a = stack.pop();
        if (a.isWide()) {
            StackCtx b = stack.pop();
            if (op == 0x5E && !b.isWide()) {
                StackCtx c = stack.pop();
                stack.push(a); stack.push(c); stack.push(b); stack.push(a);
            } else {
                stack.push(a); stack.push(b); stack.push(a);
            }
            ictx.push(a);
        } else {
            StackCtx b = stack.pop();
            StackCtx c = stack.pop();
            stack.push(b); stack.push(a); stack.push(c); stack.push(b); stack.push(a);
            ictx.push(b, a);
        }
    }

    private static void pop2(InsnContext ictx, Stack stack) {
        StackCtx a = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
        if (a.isWide()) {
            ictx.pop(a);
        } else {
            StackCtx b = stack.getSize() > 0 ? stack.pop() : new StackCtx(ictx, false);
            ictx.pop(a, b);
        }
    }

    private static void swap(InsnContext ictx, Stack stack) {
        StackCtx a = stack.pop();
        StackCtx b = stack.pop();
        stack.push(a);
        stack.push(b);
    }

    private static boolean isWideDesc(String desc) {
        return desc != null && (desc.equals("J") || desc.equals("D"));
    }

    private static String methodDescriptor(Instruction insn) {
        if (insn instanceof com.tonic.analysis.instruction.InvokeVirtualInstruction) {
            return ((com.tonic.analysis.instruction.InvokeVirtualInstruction) insn).getMethodDescriptor();
        }
        if (insn instanceof com.tonic.analysis.instruction.InvokeSpecialInstruction) {
            return ((com.tonic.analysis.instruction.InvokeSpecialInstruction) insn).getMethodDescriptor();
        }
        if (insn instanceof com.tonic.analysis.instruction.InvokeStaticInstruction) {
            return ((com.tonic.analysis.instruction.InvokeStaticInstruction) insn).getMethodDescriptor();
        }
        if (insn instanceof com.tonic.analysis.instruction.InvokeInterfaceInstruction) {
            return ((com.tonic.analysis.instruction.InvokeInterfaceInstruction) insn).getMethodDescriptor();
        }
        return null;
    }

    private static String returnDescriptor(String methodDesc) {
        if (methodDesc == null) {
            return null;
        }
        int close = methodDesc.lastIndexOf(')');
        return close < 0 ? null : methodDesc.substring(close + 1);
    }
}

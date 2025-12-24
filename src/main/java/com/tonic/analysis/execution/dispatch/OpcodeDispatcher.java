package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.utill.Opcode;

public final class OpcodeDispatcher {

    public enum DispatchResult {
        CONTINUE,
        BRANCH,
        INVOKE,
        INVOKE_DYNAMIC,
        CONSTANT_DYNAMIC,
        METHOD_HANDLE,
        METHOD_TYPE,
        RETURN,
        THROW,
        ATHROW,
        FIELD_GET,
        FIELD_PUT,
        NEW_OBJECT,
        NEW_ARRAY,
        CHECKCAST,
        INSTANCEOF
    }

    public DispatchResult dispatch(StackFrame frame, DispatchContext context) {
        Instruction instruction = frame.getCurrentInstruction();
        if (instruction == null) {
            throw new IllegalStateException("No instruction at PC " + frame.getPC());
        }

        int opcode = instruction.getOpcode();
        ConcreteStack stack = frame.getStack();
        ConcreteLocals locals = frame.getLocals();

        switch (opcode) {
            case 0x00:
                return dispatchNop(frame, instruction);

            case 0x01:
                return dispatchAConstNull(frame, stack, instruction);

            case 0x02: case 0x03: case 0x04: case 0x05:
            case 0x06: case 0x07: case 0x08:
                return dispatchIConst(frame, stack, instruction, opcode);

            case 0x09: case 0x0A:
                return dispatchLConst(frame, stack, instruction, opcode);

            case 0x0B: case 0x0C: case 0x0D:
                return dispatchFConst(frame, stack, instruction, opcode);

            case 0x0E: case 0x0F:
                return dispatchDConst(frame, stack, instruction, opcode);

            case 0x10:
                return dispatchBipush(frame, stack, (BipushInstruction) instruction);

            case 0x11:
                return dispatchSipush(frame, stack, (SipushInstruction) instruction);

            case 0x12:
                return dispatchLdc(frame, stack, context, (LdcInstruction) instruction);

            case 0x13:
                return dispatchLdcW(frame, stack, context, (LdcWInstruction) instruction);

            case 0x14:
                return dispatchLdc2W(frame, stack, context, (Ldc2WInstruction) instruction);

            case 0x15:
                return dispatchILoad(frame, stack, locals, (ILoadInstruction) instruction);

            case 0x16:
                return dispatchLLoad(frame, stack, locals, (LLoadInstruction) instruction);

            case 0x17:
                return dispatchFLoad(frame, stack, locals, (FLoadInstruction) instruction);

            case 0x18:
                return dispatchDLoad(frame, stack, locals, (DLoadInstruction) instruction);

            case 0x19:
                return dispatchALoad(frame, stack, locals, (ALoadInstruction) instruction);

            case 0x1A: case 0x1B: case 0x1C: case 0x1D:
                return dispatchILoadN(frame, stack, locals, instruction, opcode);

            case 0x1E: case 0x1F: case 0x20: case 0x21:
                return dispatchLLoadN(frame, stack, locals, instruction, opcode);

            case 0x22: case 0x23: case 0x24: case 0x25:
                return dispatchFLoadN(frame, stack, locals, instruction, opcode);

            case 0x26: case 0x27: case 0x28: case 0x29:
                return dispatchDLoadN(frame, stack, locals, instruction, opcode);

            case 0x2A: case 0x2B: case 0x2C: case 0x2D:
                return dispatchALoadN(frame, stack, locals, instruction, opcode);

            case 0x2E:
                return dispatchIALoad(frame, stack, context, instruction);

            case 0x2F:
                return dispatchLALoad(frame, stack, context, instruction);

            case 0x30:
                return dispatchFALoad(frame, stack, context, instruction);

            case 0x31:
                return dispatchDALoad(frame, stack, context, instruction);

            case 0x32:
                return dispatchAALoad(frame, stack, context, instruction);

            case 0x33:
                return dispatchBALoad(frame, stack, context, instruction);

            case 0x34:
                return dispatchCALoad(frame, stack, context, instruction);

            case 0x35:
                return dispatchSALoad(frame, stack, context, instruction);

            case 0x36:
                return dispatchIStore(frame, stack, locals, (IStoreInstruction) instruction);

            case 0x37:
                return dispatchLStore(frame, stack, locals, (LStoreInstruction) instruction);

            case 0x38:
                return dispatchFStore(frame, stack, locals, (FStoreInstruction) instruction);

            case 0x39:
                return dispatchDStore(frame, stack, locals, (DStoreInstruction) instruction);

            case 0x3A:
                return dispatchAStore(frame, stack, locals, (AStoreInstruction) instruction);

            case 0x3B: case 0x3C: case 0x3D: case 0x3E:
                return dispatchIStoreN(frame, stack, locals, instruction, opcode);

            case 0x3F: case 0x40: case 0x41: case 0x42:
                return dispatchLStoreN(frame, stack, locals, instruction, opcode);

            case 0x43: case 0x44: case 0x45: case 0x46:
                return dispatchFStoreN(frame, stack, locals, instruction, opcode);

            case 0x47: case 0x48: case 0x49: case 0x4A:
                return dispatchDStoreN(frame, stack, locals, instruction, opcode);

            case 0x4B: case 0x4C: case 0x4D: case 0x4E:
                return dispatchAStoreN(frame, stack, locals, instruction, opcode);

            case 0x4F:
                return dispatchIAStore(frame, stack, context, instruction);

            case 0x50:
                return dispatchLAStore(frame, stack, context, instruction);

            case 0x51:
                return dispatchFAStore(frame, stack, context, instruction);

            case 0x52:
                return dispatchDAStore(frame, stack, context, instruction);

            case 0x53:
                return dispatchAAStore(frame, stack, context, instruction);

            case 0x54:
                return dispatchBAStore(frame, stack, context, instruction);

            case 0x55:
                return dispatchCAStore(frame, stack, context, instruction);

            case 0x56:
                return dispatchSAStore(frame, stack, context, instruction);

            case 0x57:
                return dispatchPop(frame, stack, instruction);

            case 0x58:
                return dispatchPop2(frame, stack, instruction);

            case 0x59:
                return dispatchDup(frame, stack, instruction);

            case 0x5A:
                return dispatchDupX1(frame, stack, instruction);

            case 0x5B:
                return dispatchDupX2(frame, stack, instruction);

            case 0x5C:
                return dispatchDup2(frame, stack, instruction);

            case 0x5D:
                return dispatchDup2X1(frame, stack, instruction);

            case 0x5E:
                return dispatchDup2X2(frame, stack, instruction);

            case 0x5F:
                return dispatchSwap(frame, stack, instruction);

            case 0x60: case 0x61: case 0x62: case 0x63:
            case 0x64: case 0x65: case 0x66: case 0x67:
            case 0x68: case 0x69: case 0x6A: case 0x6B:
            case 0x6C: case 0x6D: case 0x6E: case 0x6F:
            case 0x70: case 0x71: case 0x72: case 0x73:
                return dispatchArithmetic(frame, stack, (ArithmeticInstruction) instruction);

            case 0x74:
                return dispatchINeg(frame, stack, instruction);

            case 0x75:
                return dispatchLNeg(frame, stack, instruction);

            case 0x76:
                return dispatchFNeg(frame, stack, instruction);

            case 0x77:
                return dispatchDNeg(frame, stack, instruction);

            case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D:
                return dispatchShift(frame, stack, (ArithmeticShiftInstruction) instruction);

            case 0x7E:
                return dispatchIAnd(frame, stack, instruction);

            case 0x7F:
                return dispatchLAnd(frame, stack, instruction);

            case 0x80:
                return dispatchIOr(frame, stack, instruction);

            case 0x81:
                return dispatchLOr(frame, stack, instruction);

            case 0x82:
                return dispatchIXor(frame, stack, instruction);

            case 0x83:
                return dispatchLXor(frame, stack, instruction);

            case 0x84:
                return dispatchIInc(frame, locals, (IIncInstruction) instruction);

            case 0x85:
                return dispatchI2L(frame, stack, instruction);

            case 0x86: case 0x87:
            case 0x88: case 0x89: case 0x8A:
            case 0x8B: case 0x8C: case 0x8D:
            case 0x8E: case 0x8F: case 0x90:
                return dispatchConversion(frame, stack, (ConversionInstruction) instruction);

            case 0x91: case 0x92: case 0x93:
                return dispatchNarrowingConversion(frame, stack, (NarrowingConversionInstruction) instruction);

            case 0x94: case 0x95: case 0x96: case 0x97: case 0x98:
                return dispatchCompare(frame, stack, (CompareInstruction) instruction);

            case 0x99: case 0x9A: case 0x9B: case 0x9C: case 0x9D: case 0x9E:
            case 0x9F: case 0xA0: case 0xA1: case 0xA2: case 0xA3: case 0xA4:
            case 0xA5: case 0xA6:
            case 0xC6: case 0xC7:
                return dispatchConditionalBranch(frame, stack, context, (ConditionalBranchInstruction) instruction);

            case 0xA7:
                return dispatchGoto(frame, context, (GotoInstruction) instruction);

            case 0xA8:
                throw new UnsupportedOperationException("jsr is not supported (legacy)");

            case 0xA9:
                throw new UnsupportedOperationException("ret is not supported (legacy)");

            case 0xAA:
                return dispatchTableSwitch(frame, stack, context, (TableSwitchInstruction) instruction);

            case 0xAB:
                return dispatchLookupSwitch(frame, stack, context, (LookupSwitchInstruction) instruction);

            case 0xAC: case 0xAD: case 0xAE: case 0xAF: case 0xB0: case 0xB1:
                return dispatchReturn(frame, stack, (ReturnInstruction) instruction);

            case 0xB2: case 0xB4:
                return dispatchGetField(frame, stack, context, (GetFieldInstruction) instruction);

            case 0xB3: case 0xB5:
                return dispatchPutField(frame, stack, context, (PutFieldInstruction) instruction);

            case 0xB6:
                return dispatchInvokeVirtual(frame, stack, context, (InvokeVirtualInstruction) instruction);

            case 0xB7:
                return dispatchInvokeSpecial(frame, stack, context, (InvokeSpecialInstruction) instruction);

            case 0xB8:
                return dispatchInvokeStatic(frame, stack, context, (InvokeStaticInstruction) instruction);

            case 0xB9:
                return dispatchInvokeInterface(frame, stack, context, (InvokeInterfaceInstruction) instruction);

            case 0xBA:
                return dispatchInvokeDynamic(frame, stack, context, (InvokeDynamicInstruction) instruction);

            case 0xBB:
                return dispatchNew(frame, stack, context, (NewInstruction) instruction);

            case 0xBC:
                return dispatchNewArray(frame, stack, context, (NewArrayInstruction) instruction);

            case 0xBD:
                return dispatchANewArray(frame, stack, context, (ANewArrayInstruction) instruction);

            case 0xBE:
                return dispatchArrayLength(frame, stack, instruction);

            case 0xBF:
                return dispatchAThrow(frame, stack, instruction);

            case 0xC0:
                return dispatchCheckCast(frame, stack, context, (CheckCastInstruction) instruction);

            case 0xC1:
                return dispatchInstanceOf(frame, stack, context, (InstanceOfInstruction) instruction);

            case 0xC2:
                return dispatchMonitorEnter(frame, stack, instruction);

            case 0xC3:
                return dispatchMonitorExit(frame, stack, instruction);

            case 0xC4:
                return dispatchWide(frame, stack, locals, context, (WideInstruction) instruction);

            case 0xC5:
                return dispatchMultiANewArray(frame, stack, context, (MultiANewArrayInstruction) instruction);

            case 0xC8:
                return dispatchGotoW(frame, context, (GotoInstruction) instruction);

            case 0xC9:
                throw new UnsupportedOperationException("jsr_w is not supported (legacy)");

            default:
                throw new UnsupportedOperationException("Opcode 0x" + Integer.toHexString(opcode) + " not implemented");
        }
    }

    private DispatchResult dispatchNop(StackFrame frame, Instruction instruction) {
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAConstNull(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.pushNull();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode) {
        int value = opcode - 0x03;
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode) {
        long value = opcode - 0x09;
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode) {
        float value = opcode - 0x0B;
        stack.pushFloat(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode) {
        double value = opcode - 0x0E;
        stack.pushDouble(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchBipush(StackFrame frame, ConcreteStack stack, BipushInstruction instruction) {
        stack.pushInt(instruction.getValue());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchSipush(StackFrame frame, ConcreteStack stack, SipushInstruction instruction) {
        stack.pushInt(instruction.getValue());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLdc(StackFrame frame, ConcreteStack stack, DispatchContext context, LdcInstruction instruction) {
        int index = instruction.getCpIndex();
        LdcInstruction.ConstantType type = instruction.getConstantType();

        switch (type) {
            case INTEGER:
                stack.pushInt(context.resolveIntConstant(index));
                break;
            case FLOAT:
                stack.pushFloat(context.resolveFloatConstant(index));
                break;
            case STRING:
                context.resolveStringConstant(index);
                stack.pushReference(context.resolveClassConstant(index));
                break;
            case CLASS:
                stack.pushReference(context.resolveClassConstant(index));
                break;
            case METHOD_HANDLE:
                MethodHandleInfo mhInfo = resolveMethodHandle(instruction.getConstPool(), index);
                context.setPendingMethodHandle(mhInfo);
                return DispatchResult.METHOD_HANDLE;
            case METHOD_TYPE:
                MethodTypeInfo mtInfo = resolveMethodType(instruction.getConstPool(), index);
                context.setPendingMethodType(mtInfo);
                return DispatchResult.METHOD_TYPE;
            case DYNAMIC:
                ConstantDynamicInfo cdInfo = resolveConstantDynamic(instruction.getConstPool(), index);
                context.setPendingConstantDynamic(cdInfo);
                return DispatchResult.CONSTANT_DYNAMIC;
            default:
                stack.pushInt(0);
                break;
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLdcW(StackFrame frame, ConcreteStack stack, DispatchContext context, LdcWInstruction instruction) {
        int index = instruction.getCpIndex();
        LdcInstruction.ConstantType type = instruction.getConstantType();

        switch (type) {
            case INTEGER:
                stack.pushInt(context.resolveIntConstant(index));
                break;
            case FLOAT:
                stack.pushFloat(context.resolveFloatConstant(index));
                break;
            case LONG:
                stack.pushLong(context.resolveLongConstant(index));
                break;
            case DOUBLE:
                stack.pushDouble(context.resolveDoubleConstant(index));
                break;
            case STRING:
                context.resolveStringConstant(index);
                stack.pushReference(context.resolveClassConstant(index));
                break;
            case CLASS:
                stack.pushReference(context.resolveClassConstant(index));
                break;
            case METHOD_HANDLE:
                MethodHandleInfo mhInfo = resolveMethodHandle(instruction.getConstPool(), index);
                context.setPendingMethodHandle(mhInfo);
                return DispatchResult.METHOD_HANDLE;
            case METHOD_TYPE:
                MethodTypeInfo mtInfo = resolveMethodType(instruction.getConstPool(), index);
                context.setPendingMethodType(mtInfo);
                return DispatchResult.METHOD_TYPE;
            case DYNAMIC:
                ConstantDynamicInfo cdInfo = resolveConstantDynamic(instruction.getConstPool(), index);
                context.setPendingConstantDynamic(cdInfo);
                return DispatchResult.CONSTANT_DYNAMIC;
            default:
                stack.pushInt(0);
                break;
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLdc2W(StackFrame frame, ConcreteStack stack, DispatchContext context, Ldc2WInstruction instruction) {
        int index = instruction.getCpIndex();
        LdcInstruction.ConstantType type = instruction.getConstantType();

        switch (type) {
            case LONG:
                stack.pushLong(context.resolveLongConstant(index));
                break;
            case DOUBLE:
                stack.pushDouble(context.resolveDoubleConstant(index));
                break;
            case DYNAMIC:
                ConstantDynamicInfo cdInfo = resolveConstantDynamic(instruction.getConstPool(), index);
                context.setPendingConstantDynamic(cdInfo);
                return DispatchResult.CONSTANT_DYNAMIC;
            default:
                stack.pushLong(0L);
                break;
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchILoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, ILoadInstruction instruction) {
        int value = locals.getInt(instruction.getVarIndex());
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLLoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, LLoadInstruction instruction) {
        long value = locals.getLong(instruction.getVarIndex());
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFLoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, FLoadInstruction instruction) {
        float value = locals.getFloat(instruction.getVarIndex());
        stack.pushFloat(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDLoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, DLoadInstruction instruction) {
        double value = locals.getDouble(instruction.getVarIndex());
        stack.pushDouble(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchALoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, ALoadInstruction instruction) {
        ObjectInstance value = locals.getReference(instruction.getVarIndex());
        if (value == null) {
            stack.pushNull();
        } else {
            stack.pushReference(value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchILoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x1A;
        int value = locals.getInt(index);
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLLoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x1E;
        long value = locals.getLong(index);
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFLoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x22;
        float value = locals.getFloat(index);
        stack.pushFloat(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDLoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x26;
        double value = locals.getDouble(index);
        stack.pushDouble(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchALoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x2A;
        ObjectInstance value = locals.getReference(index);
        if (value == null) {
            stack.pushNull();
        } else {
            stack.pushReference(value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "iaload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        int value = array.getInt(index);
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "laload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        long value = array.getLong(index);
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "faload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        float value = array.getFloat(index);
        stack.pushFloat(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "daload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        double value = array.getDouble(index);
        stack.pushDouble(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "aaload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        Object value = array.get(index);
        if (value == null) {
            stack.pushNull();
        } else {
            stack.pushReference((ObjectInstance) value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchBALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "baload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        byte value = array.getByte(index);
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchCALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "caload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        char value = array.getChar(index);
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchSALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "saload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        short value = array.getShort(index);
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, IStoreInstruction instruction) {
        int value = stack.popInt();
        locals.setInt(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, LStoreInstruction instruction) {
        long value = stack.popLong();
        locals.setLong(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, FStoreInstruction instruction) {
        float value = stack.popFloat();
        locals.setFloat(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, DStoreInstruction instruction) {
        double value = stack.popDouble();
        locals.setDouble(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, AStoreInstruction instruction) {
        ObjectInstance value = stack.popReference();
        if (value == null) {
            locals.setNull(instruction.getVarIndex());
        } else {
            locals.setReference(instruction.getVarIndex(), value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x3B;
        int value = stack.popInt();
        locals.setInt(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x3F;
        long value = stack.popLong();
        locals.setLong(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x43;
        float value = stack.popFloat();
        locals.setFloat(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x47;
        double value = stack.popDouble();
        locals.setDouble(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode) {
        int index = opcode - 0x4B;
        ObjectInstance value = stack.popReference();
        if (value == null) {
            locals.setNull(index);
        } else {
            locals.setReference(index, value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int value = stack.popInt();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "iastore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.setInt(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        long value = stack.popLong();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "lastore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.setLong(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        float value = stack.popFloat();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "fastore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.setFloat(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        double value = stack.popDouble();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "dastore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.setDouble(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        ObjectInstance value = stack.popReference();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "aastore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.set(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchBAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int value = stack.popInt();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "bastore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.setByte(index, (byte) value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchCAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int value = stack.popInt();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "castore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.setChar(index, (char) value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchSAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction) {
        int value = stack.popInt();
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "sastore");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        array.setShort(index, (short) value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchPop(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.pop();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchPop2(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        ConcreteValue value1 = stack.peek();
        if (value1.isWide()) {
            stack.pop();
        } else {
            stack.pop();
            stack.pop();
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.dup();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDupX1(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.dupX1();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDupX2(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.dupX2();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup2(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.dup2();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup2X1(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.dup2X1();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup2X2(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.dup2X2();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchSwap(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.swap();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchArithmetic(StackFrame frame, ConcreteStack stack, ArithmeticInstruction instruction) {
        ArithmeticInstruction.ArithmeticType type = instruction.getType();

        switch (type) {
            case IADD: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 + v2);
                break;
            }
            case LADD: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 + v2);
                break;
            }
            case FADD: {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 + v2);
                break;
            }
            case DADD: {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 + v2);
                break;
            }
            case ISUB: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 - v2);
                break;
            }
            case LSUB: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 - v2);
                break;
            }
            case FSUB: {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 - v2);
                break;
            }
            case DSUB: {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 - v2);
                break;
            }
            case IMUL: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 * v2);
                break;
            }
            case LMUL: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 * v2);
                break;
            }
            case FMUL: {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 * v2);
                break;
            }
            case DMUL: {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 * v2);
                break;
            }
            case IDIV: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 / v2);
                break;
            }
            case LDIV: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 / v2);
                break;
            }
            case FDIV: {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 / v2);
                break;
            }
            case DDIV: {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 / v2);
                break;
            }
            case IREM: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 % v2);
                break;
            }
            case LREM: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 % v2);
                break;
            }
            case FREM: {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 % v2);
                break;
            }
            case DREM: {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 % v2);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchINeg(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        int value = stack.popInt();
        stack.pushInt(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLNeg(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        long value = stack.popLong();
        stack.pushLong(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFNeg(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        float value = stack.popFloat();
        stack.pushFloat(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDNeg(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        double value = stack.popDouble();
        stack.pushDouble(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchShift(StackFrame frame, ConcreteStack stack, ArithmeticShiftInstruction instruction) {
        ArithmeticShiftInstruction.ShiftType type = instruction.getType();

        switch (type) {
            case ISHL: {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value << shiftAmount);
                break;
            }
            case LSHL: {
                int shiftAmount = stack.popInt();
                long value = stack.popLong();
                stack.pushLong(value << shiftAmount);
                break;
            }
            case ISHR: {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value >> shiftAmount);
                break;
            }
            case LSHR: {
                int shiftAmount = stack.popInt();
                long value = stack.popLong();
                stack.pushLong(value >> shiftAmount);
                break;
            }
            case IUSHR: {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value >>> shiftAmount);
                break;
            }
            case LUSHR: {
                int shiftAmount = stack.popInt();
                long value = stack.popLong();
                stack.pushLong(value >>> shiftAmount);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIAnd(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        int v2 = stack.popInt();
        int v1 = stack.popInt();
        stack.pushInt(v1 & v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLAnd(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        long v2 = stack.popLong();
        long v1 = stack.popLong();
        stack.pushLong(v1 & v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIOr(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        int v2 = stack.popInt();
        int v1 = stack.popInt();
        stack.pushInt(v1 | v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLOr(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        long v2 = stack.popLong();
        long v1 = stack.popLong();
        stack.pushLong(v1 | v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIXor(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        int v2 = stack.popInt();
        int v1 = stack.popInt();
        stack.pushInt(v1 ^ v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLXor(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        long v2 = stack.popLong();
        long v1 = stack.popLong();
        stack.pushLong(v1 ^ v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIInc(StackFrame frame, ConcreteLocals locals, IIncInstruction instruction) {
        int currentValue = locals.getInt(instruction.getVarIndex());
        locals.setInt(instruction.getVarIndex(), currentValue + instruction.getConstValue());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchI2L(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        int value = stack.popInt();
        stack.pushLong((long) value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchConversion(StackFrame frame, ConcreteStack stack, ConversionInstruction instruction) {
        ConversionInstruction.ConversionType type = instruction.getType();

        switch (type) {
            case I2F: {
                int value = stack.popInt();
                stack.pushFloat((float) value);
                break;
            }
            case I2D: {
                int value = stack.popInt();
                stack.pushDouble((double) value);
                break;
            }
            case L2I: {
                long value = stack.popLong();
                stack.pushInt((int) value);
                break;
            }
            case L2F: {
                long value = stack.popLong();
                stack.pushFloat((float) value);
                break;
            }
            case L2D: {
                long value = stack.popLong();
                stack.pushDouble((double) value);
                break;
            }
            case F2I: {
                float value = stack.popFloat();
                stack.pushInt((int) value);
                break;
            }
            case F2L: {
                float value = stack.popFloat();
                stack.pushLong((long) value);
                break;
            }
            case F2D: {
                float value = stack.popFloat();
                stack.pushDouble((double) value);
                break;
            }
            case D2I: {
                double value = stack.popDouble();
                stack.pushInt((int) value);
                break;
            }
            case D2L: {
                double value = stack.popDouble();
                stack.pushLong((long) value);
                break;
            }
            case D2F: {
                double value = stack.popDouble();
                stack.pushFloat((float) value);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchNarrowingConversion(StackFrame frame, ConcreteStack stack, NarrowingConversionInstruction instruction) {
        NarrowingConversionInstruction.NarrowingType type = instruction.getType();

        switch (type) {
            case I2B: {
                int value = stack.popInt();
                stack.pushInt((byte) value);
                break;
            }
            case I2C: {
                int value = stack.popInt();
                stack.pushInt((char) value);
                break;
            }
            case I2S: {
                int value = stack.popInt();
                stack.pushInt((short) value);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchCompare(StackFrame frame, ConcreteStack stack, CompareInstruction instruction) {
        CompareInstruction.CompareType type = instruction.getType();

        switch (type) {
            case LCMP: {
                long value2 = stack.popLong();
                long value1 = stack.popLong();
                stack.pushInt(Long.compare(value1, value2));
                break;
            }
            case FCMPL: {
                float value2 = stack.popFloat();
                float value1 = stack.popFloat();
                if (Float.isNaN(value1) || Float.isNaN(value2)) {
                    stack.pushInt(-1);
                } else {
                    stack.pushInt(Float.compare(value1, value2));
                }
                break;
            }
            case FCMPG: {
                float value2 = stack.popFloat();
                float value1 = stack.popFloat();
                if (Float.isNaN(value1) || Float.isNaN(value2)) {
                    stack.pushInt(1);
                } else {
                    stack.pushInt(Float.compare(value1, value2));
                }
                break;
            }
            case DCMPL: {
                double value2 = stack.popDouble();
                double value1 = stack.popDouble();
                if (Double.isNaN(value1) || Double.isNaN(value2)) {
                    stack.pushInt(-1);
                } else {
                    stack.pushInt(Double.compare(value1, value2));
                }
                break;
            }
            case DCMPG: {
                double value2 = stack.popDouble();
                double value1 = stack.popDouble();
                if (Double.isNaN(value1) || Double.isNaN(value2)) {
                    stack.pushInt(1);
                } else {
                    stack.pushInt(Double.compare(value1, value2));
                }
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchConditionalBranch(StackFrame frame, ConcreteStack stack, DispatchContext context, ConditionalBranchInstruction instruction) {
        ConditionalBranchInstruction.BranchType type = instruction.getType();
        boolean takeBranch = false;

        switch (type) {
            case IFEQ: {
                int value = stack.popInt();
                takeBranch = (value == 0);
                break;
            }
            case IFNE: {
                int value = stack.popInt();
                takeBranch = (value != 0);
                break;
            }
            case IFLT: {
                int value = stack.popInt();
                takeBranch = (value < 0);
                break;
            }
            case IFGE: {
                int value = stack.popInt();
                takeBranch = (value >= 0);
                break;
            }
            case IFGT: {
                int value = stack.popInt();
                takeBranch = (value > 0);
                break;
            }
            case IFLE: {
                int value = stack.popInt();
                takeBranch = (value <= 0);
                break;
            }
            case IF_ICMPEQ: {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 == value2);
                break;
            }
            case IF_ICMPNE: {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 != value2);
                break;
            }
            case IF_ICMPLT: {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 < value2);
                break;
            }
            case IF_ICMPGE: {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 >= value2);
                break;
            }
            case IF_ICMPGT: {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 > value2);
                break;
            }
            case IF_ICMPLE: {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 <= value2);
                break;
            }
            case IF_ACMPEQ: {
                ObjectInstance value2 = stack.popReference();
                ObjectInstance value1 = stack.popReference();
                takeBranch = (value1 == value2);
                break;
            }
            case IF_ACMPNE: {
                ObjectInstance value2 = stack.popReference();
                ObjectInstance value1 = stack.popReference();
                takeBranch = (value1 != value2);
                break;
            }
            case IFNULL: {
                ConcreteValue value = stack.pop();
                takeBranch = value.isNull();
                break;
            }
            case IFNONNULL: {
                ConcreteValue value = stack.pop();
                takeBranch = !value.isNull();
                break;
            }
        }

        if (takeBranch) {
            int target = instruction.getOffset() + instruction.getBranchOffset();
            context.setBranchTarget(target);
            return DispatchResult.BRANCH;
        } else {
            frame.advancePC(instruction.getLength());
            return DispatchResult.CONTINUE;
        }
    }

    private DispatchResult dispatchGoto(StackFrame frame, DispatchContext context, GotoInstruction instruction) {
        int target = instruction.getOffset() + instruction.getBranchOffset();
        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchGotoW(StackFrame frame, DispatchContext context, GotoInstruction instruction) {
        int target = instruction.getOffset() + instruction.getBranchOffsetWide();
        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchTableSwitch(StackFrame frame, ConcreteStack stack, DispatchContext context, TableSwitchInstruction instruction) {
        int index = stack.popInt();
        int target;

        if (index >= instruction.getLow() && index <= instruction.getHigh()) {
            Integer offset = instruction.getJumpOffsets().get(index);
            if (offset != null) {
                target = instruction.getOffset() + offset;
            } else {
                target = instruction.getOffset() + instruction.getDefaultOffset();
            }
        } else {
            target = instruction.getOffset() + instruction.getDefaultOffset();
        }

        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchLookupSwitch(StackFrame frame, ConcreteStack stack, DispatchContext context, LookupSwitchInstruction instruction) {
        int key = stack.popInt();
        int target;

        Integer offset = instruction.getMatchOffsets().get(key);
        if (offset != null) {
            target = instruction.getOffset() + offset;
        } else {
            target = instruction.getOffset() + instruction.getDefaultOffset();
        }

        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchReturn(StackFrame frame, ConcreteStack stack, ReturnInstruction instruction) {
        return DispatchResult.RETURN;
    }

    private DispatchResult dispatchGetField(StackFrame frame, ConcreteStack stack, DispatchContext context, GetFieldInstruction instruction) {
        FieldInfo fieldInfo = new FieldInfo(
            instruction.getOwnerClass(),
            instruction.getFieldName(),
            instruction.getFieldDescriptor(),
            instruction.isStatic()
        );
        context.setPendingFieldAccess(fieldInfo);
        return DispatchResult.FIELD_GET;
    }

    private DispatchResult dispatchPutField(StackFrame frame, ConcreteStack stack, DispatchContext context, PutFieldInstruction instruction) {
        FieldInfo fieldInfo = new FieldInfo(
            instruction.getOwnerClass(),
            instruction.getFieldName(),
            instruction.getFieldDescriptor(),
            instruction.isStatic()
        );
        context.setPendingFieldAccess(fieldInfo);
        return DispatchResult.FIELD_PUT;
    }

    private DispatchResult dispatchInvokeVirtual(StackFrame frame, ConcreteStack stack, DispatchContext context, InvokeVirtualInstruction instruction) {
        MethodInfo methodInfo = new MethodInfo(
            instruction.getOwnerClass(),
            instruction.getMethodName(),
            instruction.getMethodDescriptor(),
            false,
            false
        );
        context.setPendingInvoke(methodInfo);
        return DispatchResult.INVOKE;
    }

    private DispatchResult dispatchInvokeSpecial(StackFrame frame, ConcreteStack stack, DispatchContext context, InvokeSpecialInstruction instruction) {
        MethodInfo methodInfo = new MethodInfo(
            instruction.getOwnerClass(),
            instruction.getMethodName(),
            instruction.getMethodDescriptor(),
            false,
            false
        );
        context.setPendingInvoke(methodInfo);
        return DispatchResult.INVOKE;
    }

    private DispatchResult dispatchInvokeStatic(StackFrame frame, ConcreteStack stack, DispatchContext context, InvokeStaticInstruction instruction) {
        MethodInfo methodInfo = new MethodInfo(
            instruction.getOwnerClass(),
            instruction.getMethodName(),
            instruction.getMethodDescriptor(),
            true,
            false
        );
        context.setPendingInvoke(methodInfo);
        return DispatchResult.INVOKE;
    }

    private DispatchResult dispatchInvokeInterface(StackFrame frame, ConcreteStack stack, DispatchContext context, InvokeInterfaceInstruction instruction) {
        MethodInfo methodInfo = new MethodInfo(
            instruction.getOwnerClass(),
            instruction.getMethodName(),
            instruction.getMethodDescriptor(),
            false,
            true
        );
        context.setPendingInvoke(methodInfo);
        return DispatchResult.INVOKE;
    }

    private DispatchResult dispatchNew(StackFrame frame, ConcreteStack stack, DispatchContext context, NewInstruction instruction) {
        String className = instruction.resolveClass();
        context.setPendingNewClass(className);
        return DispatchResult.NEW_OBJECT;
    }

    private DispatchResult dispatchNewArray(StackFrame frame, ConcreteStack stack, DispatchContext context, NewArrayInstruction instruction) {
        int count = stack.popInt();
        context.setPendingArrayDimensions(new int[]{count});

        String componentType;
        int typeCode = instruction.getArrayType().getCode();
        switch (typeCode) {
            case 4: componentType = "Z"; break;
            case 5: componentType = "C"; break;
            case 6: componentType = "F"; break;
            case 7: componentType = "D"; break;
            case 8: componentType = "B"; break;
            case 9: componentType = "S"; break;
            case 10: componentType = "I"; break;
            case 11: componentType = "J"; break;
            default: throw new IllegalStateException("Invalid array type: " + typeCode);
        }
        context.setPendingNewClass(componentType);
        return DispatchResult.NEW_ARRAY;
    }

    private DispatchResult dispatchANewArray(StackFrame frame, ConcreteStack stack, DispatchContext context, ANewArrayInstruction instruction) {
        int count = stack.popInt();
        context.setPendingArrayDimensions(new int[]{count});
        String className = instruction.resolveClass();
        context.setPendingNewClass("L" + className + ";");
        return DispatchResult.NEW_ARRAY;
    }

    private DispatchResult dispatchArrayLength(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        ConcreteValue arrayRef = stack.pop();
        if (arrayRef.isNull()) {
            throw new NullPointerException("Cannot get length of null array");
        }
        ObjectInstance obj = arrayRef.asReference();
        if (!(obj instanceof ArrayInstance)) {
            throw new IllegalStateException("Object is not an array");
        }
        ArrayInstance array = (ArrayInstance) obj;
        stack.pushInt(array.getLength());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAThrow(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        ConcreteValue exceptionRef = stack.pop();
        if (exceptionRef.isNull()) {
            throw new NullPointerException("Cannot throw null");
        }
        return DispatchResult.ATHROW;
    }

    private DispatchResult dispatchCheckCast(StackFrame frame, ConcreteStack stack, DispatchContext context, CheckCastInstruction instruction) {
        ConcreteValue ref = stack.peek();
        if (!ref.isNull()) {
            ObjectInstance obj = ref.asReference();
            context.checkCast(obj, instruction.resolveClass());
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CHECKCAST;
    }

    private DispatchResult dispatchInstanceOf(StackFrame frame, ConcreteStack stack, DispatchContext context, InstanceOfInstruction instruction) {
        ConcreteValue ref = stack.pop();

        int result = 0;
        if (!ref.isNull()) {
            ObjectInstance obj = ref.asReference();
            if (context.isInstanceOf(obj, instruction.resolveClass())) {
                result = 1;
            }
        }
        stack.pushInt(result);
        frame.advancePC(instruction.getLength());
        return DispatchResult.INSTANCEOF;
    }

    private DispatchResult dispatchMonitorEnter(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.pop();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchMonitorExit(StackFrame frame, ConcreteStack stack, Instruction instruction) {
        stack.pop();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchWide(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, DispatchContext context, WideInstruction instruction) {
        Opcode op = instruction.getModifiedOpcode();
        int varIndex = instruction.getVarIndex();

        switch (op) {
            case ILOAD:
                stack.pushInt(locals.getInt(varIndex));
                break;
            case LLOAD:
                stack.pushLong(locals.getLong(varIndex));
                break;
            case FLOAD:
                stack.pushFloat(locals.getFloat(varIndex));
                break;
            case DLOAD:
                stack.pushDouble(locals.getDouble(varIndex));
                break;
            case ALOAD:
                ObjectInstance ref = locals.getReference(varIndex);
                if (ref == null) {
                    stack.pushNull();
                } else {
                    stack.pushReference(ref);
                }
                break;
            case ISTORE:
                locals.setInt(varIndex, stack.popInt());
                break;
            case LSTORE:
                locals.setLong(varIndex, stack.popLong());
                break;
            case FSTORE:
                locals.setFloat(varIndex, stack.popFloat());
                break;
            case DSTORE:
                locals.setDouble(varIndex, stack.popDouble());
                break;
            case ASTORE:
                ObjectInstance val = stack.popReference();
                if (val == null) {
                    locals.setNull(varIndex);
                } else {
                    locals.setReference(varIndex, val);
                }
                break;
            case IINC:
                int current = locals.getInt(varIndex);
                locals.setInt(varIndex, current + instruction.getConstValue());
                break;
            case RET:
                throw new UnsupportedOperationException("RET instruction is not supported (legacy)");
            default:
                throw new IllegalStateException("Invalid wide-modified opcode: " + op);
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchMultiANewArray(StackFrame frame, ConcreteStack stack, DispatchContext context, MultiANewArrayInstruction instruction) {
        int dimensions = instruction.getDimensions();
        int[] counts = new int[dimensions];

        for (int i = dimensions - 1; i >= 0; i--) {
            counts[i] = stack.popInt();
        }

        context.setPendingArrayDimensions(counts);
        context.setPendingNewClass(instruction.resolveClass());
        return DispatchResult.NEW_ARRAY;
    }

    private DispatchResult dispatchInvokeDynamic(StackFrame frame, ConcreteStack stack, DispatchContext context, InvokeDynamicInstruction instruction) {
        int bootstrapMethodIndex = instruction.getBootstrapMethodAttrIndex();
        int nameAndTypeIndex = instruction.getNameAndTypeIndex();
        int cpIndex = instruction.getCpIndex();

        String methodSignature = instruction.resolveMethod();
        int slashIndex = methodSignature.indexOf('(');
        String methodName = slashIndex > 0 ? methodSignature.substring(0, slashIndex) : methodSignature;
        String descriptor = slashIndex > 0 ? methodSignature.substring(slashIndex) : "()V";

        InvokeDynamicInfo info = new InvokeDynamicInfo(
            bootstrapMethodIndex,
            methodName,
            descriptor,
            cpIndex
        );
        context.setPendingInvokeDynamic(info);

        return DispatchResult.INVOKE_DYNAMIC;
    }

    private MethodHandleInfo resolveMethodHandle(ConstPool constPool, int cpIndex) {
        Item<?> item = constPool.getItem(cpIndex);
        if (!(item instanceof MethodHandleItem)) {
            return new MethodHandleInfo(0, "Unknown", "unknown", "()V");
        }
        MethodHandleItem mhItem = (MethodHandleItem) item;
        int refKind = mhItem.getValue().getReferenceKind();
        int refIndex = mhItem.getValue().getReferenceIndex();

        Item<?> refItem = constPool.getItem(refIndex);
        String owner = "Unknown";
        String name = "unknown";
        String descriptor = "()V";

        if (refItem instanceof MethodRefItem) {
            MethodRefItem methodRef = (MethodRefItem) refItem;
            owner = methodRef.getClassName();
            name = methodRef.getName();
            descriptor = methodRef.getDescriptor();
        } else if (refItem instanceof InterfaceRefItem) {
            InterfaceRefItem methodRef = (InterfaceRefItem) refItem;
            owner = methodRef.getOwner();
            name = methodRef.getName();
            descriptor = methodRef.getDescriptor();
        } else if (refItem instanceof FieldRefItem) {
            FieldRefItem fieldRef = (FieldRefItem) refItem;
            owner = fieldRef.getClassName();
            name = fieldRef.getName();
            descriptor = fieldRef.getDescriptor();
        }

        return new MethodHandleInfo(refKind, owner, name, descriptor);
    }

    private MethodTypeInfo resolveMethodType(ConstPool constPool, int cpIndex) {
        Item<?> item = constPool.getItem(cpIndex);
        if (!(item instanceof MethodTypeItem)) {
            return new MethodTypeInfo("()V");
        }
        MethodTypeItem mtItem = (MethodTypeItem) item;
        int descIndex = mtItem.getValue();
        Item<?> descItem = constPool.getItem(descIndex);
        if (descItem instanceof Utf8Item) {
            return new MethodTypeInfo(((Utf8Item) descItem).getValue());
        }
        return new MethodTypeInfo("()V");
    }

    private ConstantDynamicInfo resolveConstantDynamic(ConstPool constPool, int cpIndex) {
        Item<?> item = constPool.getItem(cpIndex);
        if (!(item instanceof ConstantDynamicItem)) {
            return new ConstantDynamicInfo(0, "unknown", "Ljava/lang/Object;", cpIndex);
        }
        ConstantDynamicItem cdItem = (ConstantDynamicItem) item;
        return new ConstantDynamicInfo(
            cdItem.getBootstrapMethodAttrIndex(),
            cdItem.getName(),
            cdItem.getDescriptor(),
            cpIndex
        );
    }
}

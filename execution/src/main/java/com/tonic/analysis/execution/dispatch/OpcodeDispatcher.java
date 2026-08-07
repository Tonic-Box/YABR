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
import com.tonic.util.Opcode;

import static com.tonic.util.Opcode.*;

/**
 * Interpreter core that executes one bytecode instruction per call against a frame,
 * deferring invokes, field access, and allocation to the dispatch context.
 */
public final class OpcodeDispatcher
{

    /**
     * Outcome of a dispatch step, directing the interpreter's next action.
     */
    public enum DispatchResult
    {
        /**
         * The instruction finished in place and the program counter already
         * advanced; nothing more is owed by the interpreter.
         */
        CONTINUE,
        /**
         * Control transfers to the branch target the dispatcher left on the
         * context.
         */
        BRANCH,
        /**
         * A call was reached; the interpreter must resolve the target and push a
         * new frame.
         */
        INVOKE,
        /**
         * An invokedynamic call site was reached; the interpreter must link it
         * through its bootstrap method before the call can proceed.
         */
        INVOKE_DYNAMIC,
        /**
         * A dynamic constant was loaded and the interpreter must run its
         * bootstrap method to produce the value.
         */
        CONSTANT_DYNAMIC,
        /**
         * A method handle constant was loaded and the interpreter must
         * materialize it from the handle info left on the context.
         */
        METHOD_HANDLE,
        /**
         * A {@code MethodType} constant was loaded and the interpreter must
         * materialize it from the descriptor left on the context.
         */
        METHOD_TYPE,
        /**
         * The current frame completes and hands its return value, if any, to the
         * caller.
         */
        RETURN,
        /**
         * The interpreter must raise an exception it detected itself, such as a
         * division by zero, rather than one the code threw explicitly.
         */
        THROW,
        /**
         * An {@code athrow} was executed; the thrown reference is on the stack
         * and needs handler lookup.
         */
        ATHROW,
        /**
         * A field read is pending and must be serviced against the heap before
         * execution resumes.
         */
        FIELD_GET,
        /**
         * A field write is pending and must be serviced against the heap before
         * execution resumes.
         */
        FIELD_PUT,
        /**
         * An instance must be allocated for the pending {@code new}, before its
         * constructor runs.
         */
        NEW_OBJECT,
        /**
         * An array must be allocated, covering the primitive, reference, and
         * multi-dimensional forms; the dimension counts are left on the context.
         */
        NEW_ARRAY,
        /**
         * A cast check was performed; the dispatcher already applied it, so no
         * follow-up is needed.
         */
        CHECKCAST,
        /**
         * A type test was performed; the dispatcher already pushed its result.
         */
        INSTANCEOF
    }

    /**
     * Executes the frame's current instruction, mutating its stack, locals, and PC.
     * @param frame the frame whose current instruction is executed
     * @param context services for constant resolution and pending-operation handoff
     * @return how the interpreter should proceed
     * @throws IllegalStateException if no instruction exists at the frame's PC
     * @throws UnsupportedOperationException for jsr/ret or unimplemented opcodes
     */
    public DispatchResult dispatch(StackFrame frame, DispatchContext context)
    {
        Instruction instruction = frame.getCurrentInstruction();
        if (instruction == null)
        {
            throw new IllegalStateException("No instruction at PC " + frame.getPC());
        }

        int opcode = instruction.getOpcode();
        ConcreteStack stack = frame.getStack();
        ConcreteLocals locals = frame.getLocals();

        switch (Opcode.fromCode(opcode))
        {
            case NOP:
                return dispatchNop(frame, instruction);

            case ACONST_NULL:
                return dispatchAConstNull(frame, stack, instruction);

            case ICONST_M1: case ICONST_0: case ICONST_1: case ICONST_2:
            case ICONST_3: case ICONST_4: case ICONST_5:
                return dispatchIConst(frame, stack, instruction, opcode);

            case LCONST_0: case LCONST_1:
                return dispatchLConst(frame, stack, instruction, opcode);

            case FCONST_0: case FCONST_1: case FCONST_2:
                return dispatchFConst(frame, stack, instruction, opcode);

            case DCONST_0: case DCONST_1:
                return dispatchDConst(frame, stack, instruction, opcode);

            case BIPUSH:
                return dispatchBipush(frame, stack, (BipushInstruction) instruction);

            case SIPUSH:
                return dispatchSipush(frame, stack, (SipushInstruction) instruction);

            case LDC:
                return dispatchLdc(frame, stack, context, (LdcInstruction) instruction);

            case LDC_W:
                return dispatchLdcW(frame, stack, context, (LdcWInstruction) instruction);

            case LDC2_W:
                return dispatchLdc2W(frame, stack, context, (Ldc2WInstruction) instruction);

            case ILOAD:
                return dispatchILoad(frame, stack, locals, (ILoadInstruction) instruction);

            case LLOAD:
                return dispatchLLoad(frame, stack, locals, (LLoadInstruction) instruction);

            case FLOAD:
                return dispatchFLoad(frame, stack, locals, (FLoadInstruction) instruction);

            case DLOAD:
                return dispatchDLoad(frame, stack, locals, (DLoadInstruction) instruction);

            case ALOAD:
                return dispatchALoad(frame, stack, locals, (ALoadInstruction) instruction);

            case ILOAD_0: case ILOAD_1: case ILOAD_2: case ILOAD_3:
                return dispatchILoadN(frame, stack, locals, instruction, opcode);

            case LLOAD_0: case LLOAD_1: case LLOAD_2: case LLOAD_3:
                return dispatchLLoadN(frame, stack, locals, instruction, opcode);

            case FLOAD_0: case FLOAD_1: case FLOAD_2: case FLOAD_3:
                return dispatchFLoadN(frame, stack, locals, instruction, opcode);

            case DLOAD_0: case DLOAD_1: case DLOAD_2: case DLOAD_3:
                return dispatchDLoadN(frame, stack, locals, instruction, opcode);

            case ALOAD_0: case ALOAD_1: case ALOAD_2: case ALOAD_3:
                return dispatchALoadN(frame, stack, locals, instruction, opcode);

            case IALOAD:
                return dispatchIALoad(frame, stack, context, instruction);

            case LALOAD:
                return dispatchLALoad(frame, stack, context, instruction);

            case FALOAD:
                return dispatchFALoad(frame, stack, context, instruction);

            case DALOAD:
                return dispatchDALoad(frame, stack, context, instruction);

            case AALOAD:
                return dispatchAALoad(frame, stack, context, instruction);

            case BALOAD:
                return dispatchBALoad(frame, stack, context, instruction);

            case CALOAD:
                return dispatchCALoad(frame, stack, context, instruction);

            case SALOAD:
                return dispatchSALoad(frame, stack, context, instruction);

            case ISTORE:
                return dispatchIStore(frame, stack, locals, (IStoreInstruction) instruction);

            case LSTORE:
                return dispatchLStore(frame, stack, locals, (LStoreInstruction) instruction);

            case FSTORE:
                return dispatchFStore(frame, stack, locals, (FStoreInstruction) instruction);

            case DSTORE:
                return dispatchDStore(frame, stack, locals, (DStoreInstruction) instruction);

            case ASTORE:
                return dispatchAStore(frame, stack, locals, (AStoreInstruction) instruction);

            case ISTORE_0: case ISTORE_1: case ISTORE_2: case ISTORE_3:
                return dispatchIStoreN(frame, stack, locals, instruction, opcode);

            case LSTORE_0: case LSTORE_1: case LSTORE_2: case LSTORE_3:
                return dispatchLStoreN(frame, stack, locals, instruction, opcode);

            case FSTORE_0: case FSTORE_1: case FSTORE_2: case FSTORE_3:
                return dispatchFStoreN(frame, stack, locals, instruction, opcode);

            case DSTORE_0: case DSTORE_1: case DSTORE_2: case DSTORE_3:
                return dispatchDStoreN(frame, stack, locals, instruction, opcode);

            case ASTORE_0: case ASTORE_1: case ASTORE_2: case ASTORE_3:
                return dispatchAStoreN(frame, stack, locals, instruction, opcode);

            case IASTORE:
                return dispatchIAStore(frame, stack, context, instruction);

            case LASTORE:
                return dispatchLAStore(frame, stack, context, instruction);

            case FASTORE:
                return dispatchFAStore(frame, stack, context, instruction);

            case DASTORE:
                return dispatchDAStore(frame, stack, context, instruction);

            case AASTORE:
                return dispatchAAStore(frame, stack, context, instruction);

            case BASTORE:
                return dispatchBAStore(frame, stack, context, instruction);

            case CASTORE:
                return dispatchCAStore(frame, stack, context, instruction);

            case SASTORE:
                return dispatchSAStore(frame, stack, context, instruction);

            case POP:
                return dispatchPop(frame, stack, instruction);

            case POP2:
                return dispatchPop2(frame, stack, instruction);

            case DUP:
                return dispatchDup(frame, stack, instruction);

            case DUP_X1:
                return dispatchDupX1(frame, stack, instruction);

            case DUP_X2:
                return dispatchDupX2(frame, stack, instruction);

            case DUP2:
                return dispatchDup2(frame, stack, instruction);

            case DUP2_X1:
                return dispatchDup2X1(frame, stack, instruction);

            case DUP2_X2:
                return dispatchDup2X2(frame, stack, instruction);

            case SWAP:
                return dispatchSwap(frame, stack, instruction);

            case IADD: case LADD: case FADD: case DADD:
            case ISUB: case LSUB: case FSUB: case DSUB:
            case IMUL: case LMUL: case FMUL: case DMUL:
            case IDIV: case LDIV: case FDIV: case DDIV:
            case IREM: case LREM: case FREM: case DREM:
                return dispatchArithmetic(frame, stack, (ArithmeticInstruction) instruction);

            case INEG:
                return dispatchINeg(frame, stack, instruction);

            case LNEG:
                return dispatchLNeg(frame, stack, instruction);

            case FNEG:
                return dispatchFNeg(frame, stack, instruction);

            case DNEG:
                return dispatchDNeg(frame, stack, instruction);

            case ISHL: case LSHL: case ISHR: case LSHR:
            case IUSHR: case LUSHR:
                return dispatchShift(frame, stack, (ArithmeticShiftInstruction) instruction);

            case IAND:
                return dispatchIAnd(frame, stack, instruction);

            case LAND:
                return dispatchLAnd(frame, stack, instruction);

            case IOR:
                return dispatchIOr(frame, stack, instruction);

            case LOR:
                return dispatchLOr(frame, stack, instruction);

            case IXOR:
                return dispatchIXor(frame, stack, instruction);

            case LXOR:
                return dispatchLXor(frame, stack, instruction);

            case IINC:
                return dispatchIInc(frame, locals, (IIncInstruction) instruction);

            case I2L:
                return dispatchI2L(frame, stack, instruction);

            case I2F: case I2D:
            case L2I: case L2F: case L2D:
            case F2I: case F2L: case F2D:
            case D2I: case D2L: case D2F:
                return dispatchConversion(frame, stack, (ConversionInstruction) instruction);

            case I2B: case I2C: case I2S:
                return dispatchNarrowingConversion(frame, stack, (NarrowingConversionInstruction) instruction);

            case LCMP: case FCMPL: case FCMPG: case DCMPL: case DCMPG:
                return dispatchCompare(frame, stack, (CompareInstruction) instruction);

            case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE:
            case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE:
            case IF_ACMPEQ: case IF_ACMPNE:
            case IFNULL: case IFNONNULL:
                return dispatchConditionalBranch(frame, stack, context, (ConditionalBranchInstruction) instruction);

            case GOTO:
                return dispatchGoto(context, (GotoInstruction) instruction);

            case JSR:
                throw new UnsupportedOperationException("jsr is not supported (legacy)");

            case RET:
                throw new UnsupportedOperationException("ret is not supported (legacy)");

            case TABLESWITCH:
                return dispatchTableSwitch(stack, context, (TableSwitchInstruction) instruction);

            case LOOKUPSWITCH:
                return dispatchLookupSwitch(stack, context, (LookupSwitchInstruction) instruction);

            case IRETURN: case LRETURN: case FRETURN: case DRETURN: case ARETURN: case RETURN_:
                return dispatchReturn();

            case GETSTATIC: case GETFIELD:
                return dispatchGetField(context, (GetFieldInstruction) instruction);

            case PUTSTATIC: case PUTFIELD:
                return dispatchPutField(context, (PutFieldInstruction) instruction);

            case INVOKEVIRTUAL:
                return dispatchInvokeVirtual(context, (InvokeVirtualInstruction) instruction);

            case INVOKESPECIAL:
                return dispatchInvokeSpecial(context, (InvokeSpecialInstruction) instruction);

            case INVOKESTATIC:
                return dispatchInvokeStatic(context, (InvokeStaticInstruction) instruction);

            case INVOKEINTERFACE:
                return dispatchInvokeInterface(context, (InvokeInterfaceInstruction) instruction);

            case INVOKEDYNAMIC:
                return dispatchInvokeDynamic(context, (InvokeDynamicInstruction) instruction);

            case NEW:
                return dispatchNew(context, (NewObjectInstruction) instruction);

            case NEWARRAY:
                return dispatchNewArray(stack, context, (NewPrimitiveArrayInstruction) instruction);

            case ANEWARRAY:
                return dispatchANewArray(stack, context, (ANewArrayInstruction) instruction);

            case ARRAYLENGTH:
                return dispatchArrayLength(frame, stack, instruction);

            case ATHROW:
                return dispatchAThrow();

            case CHECKCAST:
                return dispatchCheckCast(frame, stack, context, (CheckCastInstruction) instruction);

            case INSTANCEOF:
                return dispatchInstanceOf(frame, stack, context, (InstanceOfInstruction) instruction);

            case MONITORENTER:
                return dispatchMonitorEnter(frame, stack, instruction);

            case MONITOREXIT:
                return dispatchMonitorExit(frame, stack, instruction);

            case WIDE:
                return dispatchWide(frame, stack, locals, (WideInstruction) instruction);

            case MULTIANEWARRAY:
                return dispatchMultiANewArray(stack, context, (MultiANewArrayInstruction) instruction);

            case GOTO_W:
                return dispatchGotoW(context, (GotoInstruction) instruction);

            case JSR_W:
                throw new UnsupportedOperationException("jsr_w is not supported (legacy)");

            default:
                throw new UnsupportedOperationException("Opcode 0x" + Integer.toHexString(opcode) + " not implemented");
        }
    }

    private DispatchResult dispatchNop(StackFrame frame, Instruction instruction)
    {
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAConstNull(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.pushNull();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode)
    {
        int value = opcode - ICONST_0.getCode();
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode)
    {
        long value = opcode - LCONST_0.getCode();
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode)
    {
        float value = opcode - FCONST_0.getCode();
        stack.pushFloat(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDConst(StackFrame frame, ConcreteStack stack, Instruction instruction, int opcode)
    {
        double value = opcode - DCONST_0.getCode();
        stack.pushDouble(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchBipush(StackFrame frame, ConcreteStack stack, BipushInstruction instruction)
    {
        stack.pushInt(instruction.getValue());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchSipush(StackFrame frame, ConcreteStack stack, SipushInstruction instruction)
    {
        stack.pushInt(instruction.getValue());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLdc(StackFrame frame, ConcreteStack stack, DispatchContext context, LdcInstruction instruction)
    {
        int index = instruction.getCpIndex();
        LdcInstruction.ConstantType type = instruction.getConstantType();

        switch (type)
        {
            case INTEGER:
                stack.pushInt(context.resolveIntConstant(index));
                break;
            case FLOAT:
                stack.pushFloat(context.resolveFloatConstant(index));
                break;
            case STRING:
                stack.pushReference(context.resolveStringObject(index));
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

    private DispatchResult dispatchLdcW(StackFrame frame, ConcreteStack stack, DispatchContext context, LdcWInstruction instruction)
    {
        int index = instruction.getCpIndex();
        LdcInstruction.ConstantType type = instruction.getConstantType();

        switch (type)
        {
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
                stack.pushReference(context.resolveStringObject(index));
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

    private DispatchResult dispatchLdc2W(StackFrame frame, ConcreteStack stack, DispatchContext context, Ldc2WInstruction instruction)
    {
        int index = instruction.getCpIndex();
        LdcInstruction.ConstantType type = instruction.getConstantType();

        switch (type)
        {
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

    private DispatchResult dispatchILoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, ILoadInstruction instruction)
    {
        int value = locals.getInt(instruction.getVarIndex());
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLLoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, LLoadInstruction instruction)
    {
        long value = locals.getLong(instruction.getVarIndex());
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFLoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, FLoadInstruction instruction)
    {
        float value = locals.getFloat(instruction.getVarIndex());
        stack.pushFloat(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDLoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, DLoadInstruction instruction)
    {
        double value = locals.getDouble(instruction.getVarIndex());
        stack.pushDouble(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchALoad(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, ALoadInstruction instruction)
    {
        ObjectInstance value = locals.getReference(instruction.getVarIndex());
        if (value == null)
        {
            stack.pushNull();
        }
        else
        {
            stack.pushReference(value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchILoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - ILOAD_0.getCode();
        int value = locals.getInt(index);
        stack.pushInt(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLLoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - LLOAD_0.getCode();
        long value = locals.getLong(index);
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFLoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - FLOAD_0.getCode();
        float value = locals.getFloat(index);
        stack.pushFloat(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDLoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - DLOAD_0.getCode();
        double value = locals.getDouble(index);
        stack.pushDouble(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchALoadN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - ALOAD_0.getCode();
        ObjectInstance value = locals.getReference(index);
        if (value == null)
        {
            stack.pushNull();
        }
        else
        {
            stack.pushReference(value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchLALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchFALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchDALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchAALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
        int index = stack.popInt();
        ObjectInstance arrayRef = stack.popReference();
        context.checkNullReference(arrayRef, "aaload");
        ArrayInstance array = context.getArray(arrayRef);
        context.checkArrayBounds(array, index);
        Object value = array.get(index);
        if (value == null)
        {
            stack.pushNull();
        }
        else
        {
            stack.pushReference((ObjectInstance) value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchBALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchCALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchSALoad(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchIStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, IStoreInstruction instruction)
    {
        int value = stack.popInt();
        locals.setInt(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, LStoreInstruction instruction)
    {
        long value = stack.popLong();
        locals.setLong(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, FStoreInstruction instruction)
    {
        float value = stack.popFloat();
        locals.setFloat(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, DStoreInstruction instruction)
    {
        double value = stack.popDouble();
        locals.setDouble(instruction.getVarIndex(), value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAStore(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, AStoreInstruction instruction)
    {
        ObjectInstance value = stack.popReference();
        if (value == null)
        {
            locals.setNull(instruction.getVarIndex());
        }
        else
        {
            locals.setReference(instruction.getVarIndex(), value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - ISTORE_0.getCode();
        int value = stack.popInt();
        locals.setInt(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - LSTORE_0.getCode();
        long value = stack.popLong();
        locals.setLong(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - FSTORE_0.getCode();
        float value = stack.popFloat();
        locals.setFloat(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - DSTORE_0.getCode();
        double value = stack.popDouble();
        locals.setDouble(index, value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAStoreN(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, Instruction instruction, int opcode)
    {
        int index = opcode - ASTORE_0.getCode();
        ObjectInstance value = stack.popReference();
        if (value == null)
        {
            locals.setNull(index);
        }
        else
        {
            locals.setReference(index, value);
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchLAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchFAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchDAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchAAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchBAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchCAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchSAStore(StackFrame frame, ConcreteStack stack, DispatchContext context, Instruction instruction)
    {
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

    private DispatchResult dispatchPop(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.pop();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchPop2(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        ConcreteValue value1 = stack.peek();
        if (value1.isWide())
        {
            stack.pop();
        }
        else
        {
            stack.pop();
            stack.pop();
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.dup();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDupX1(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.dupX1();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDupX2(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.dupX2();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup2(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.dup2();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup2X1(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.dup2X1();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDup2X2(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.dup2X2();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchSwap(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.swap();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchArithmetic(StackFrame frame, ConcreteStack stack, ArithmeticInstruction instruction)
    {
        ArithmeticInstruction.ArithmeticType type = instruction.getType();

        switch (type)
        {
            case IADD:
            {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 + v2);
                break;
            }
            case LADD:
            {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 + v2);
                break;
            }
            case FADD:
            {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 + v2);
                break;
            }
            case DADD:
            {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 + v2);
                break;
            }
            case ISUB:
            {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 - v2);
                break;
            }
            case LSUB:
            {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 - v2);
                break;
            }
            case FSUB:
            {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 - v2);
                break;
            }
            case DSUB:
            {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 - v2);
                break;
            }
            case IMUL:
            {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 * v2);
                break;
            }
            case LMUL:
            {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 * v2);
                break;
            }
            case FMUL:
            {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 * v2);
                break;
            }
            case DMUL:
            {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 * v2);
                break;
            }
            case IDIV:
            {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 / v2);
                break;
            }
            case LDIV:
            {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 / v2);
                break;
            }
            case FDIV:
            {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 / v2);
                break;
            }
            case DDIV:
            {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 / v2);
                break;
            }
            case IREM:
            {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 % v2);
                break;
            }
            case LREM:
            {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 % v2);
                break;
            }
            case FREM:
            {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                stack.pushFloat(v1 % v2);
                break;
            }
            case DREM:
            {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                stack.pushDouble(v1 % v2);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchINeg(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        int value = stack.popInt();
        stack.pushInt(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLNeg(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        long value = stack.popLong();
        stack.pushLong(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchFNeg(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        float value = stack.popFloat();
        stack.pushFloat(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchDNeg(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        double value = stack.popDouble();
        stack.pushDouble(-value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchShift(StackFrame frame, ConcreteStack stack, ArithmeticShiftInstruction instruction)
    {
        ArithmeticShiftInstruction.ShiftType type = instruction.getType();

        switch (type)
        {
            case ISHL:
            {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value << shiftAmount);
                break;
            }
            case LSHL:
            {
                int shiftAmount = stack.popInt();
                long value = stack.popLong();
                stack.pushLong(value << shiftAmount);
                break;
            }
            case ISHR:
            {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value >> shiftAmount);
                break;
            }
            case LSHR:
            {
                int shiftAmount = stack.popInt();
                long value = stack.popLong();
                stack.pushLong(value >> shiftAmount);
                break;
            }
            case IUSHR:
            {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value >>> shiftAmount);
                break;
            }
            case LUSHR:
            {
                int shiftAmount = stack.popInt();
                long value = stack.popLong();
                stack.pushLong(value >>> shiftAmount);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIAnd(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        int v2 = stack.popInt();
        int v1 = stack.popInt();
        stack.pushInt(v1 & v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLAnd(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        long v2 = stack.popLong();
        long v1 = stack.popLong();
        stack.pushLong(v1 & v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIOr(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        int v2 = stack.popInt();
        int v1 = stack.popInt();
        stack.pushInt(v1 | v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLOr(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        long v2 = stack.popLong();
        long v1 = stack.popLong();
        stack.pushLong(v1 | v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIXor(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        int v2 = stack.popInt();
        int v1 = stack.popInt();
        stack.pushInt(v1 ^ v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchLXor(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        long v2 = stack.popLong();
        long v1 = stack.popLong();
        stack.pushLong(v1 ^ v2);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchIInc(StackFrame frame, ConcreteLocals locals, IIncInstruction instruction)
    {
        int currentValue = locals.getInt(instruction.getVarIndex());
        locals.setInt(instruction.getVarIndex(), currentValue + instruction.getConstValue());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchI2L(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        int value = stack.popInt();
        stack.pushLong(value);
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchConversion(StackFrame frame, ConcreteStack stack, ConversionInstruction instruction)
    {
        ConversionInstruction.ConversionType type = instruction.getType();

        switch (type)
        {
            case I2F:
            {
                int value = stack.popInt();
                stack.pushFloat((float) value);
                break;
            }
            case I2D:
            {
                int value = stack.popInt();
                stack.pushDouble(value);
                break;
            }
            case L2I:
            {
                long value = stack.popLong();
                stack.pushInt((int) value);
                break;
            }
            case L2F:
            {
                long value = stack.popLong();
                stack.pushFloat((float) value);
                break;
            }
            case L2D:
            {
                long value = stack.popLong();
                stack.pushDouble((double) value);
                break;
            }
            case F2I:
            {
                float value = stack.popFloat();
                stack.pushInt((int) value);
                break;
            }
            case F2L:
            {
                float value = stack.popFloat();
                stack.pushLong((long) value);
                break;
            }
            case F2D:
            {
                float value = stack.popFloat();
                stack.pushDouble(value);
                break;
            }
            case D2I:
            {
                double value = stack.popDouble();
                stack.pushInt((int) value);
                break;
            }
            case D2L:
            {
                double value = stack.popDouble();
                stack.pushLong((long) value);
                break;
            }
            case D2F:
            {
                double value = stack.popDouble();
                stack.pushFloat((float) value);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchNarrowingConversion(StackFrame frame, ConcreteStack stack, NarrowingConversionInstruction instruction)
    {
        NarrowingConversionInstruction.NarrowingType type = instruction.getType();

        switch (type)
        {
            case I2B:
            {
                int value = stack.popInt();
                stack.pushInt((byte) value);
                break;
            }
            case I2C:
            {
                int value = stack.popInt();
                stack.pushInt((char) value);
                break;
            }
            case I2S:
            {
                int value = stack.popInt();
                stack.pushInt((short) value);
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchCompare(StackFrame frame, ConcreteStack stack, CompareInstruction instruction)
    {
        CompareInstruction.CompareType type = instruction.getType();

        switch (type)
        {
            case LCMP:
            {
                long value2 = stack.popLong();
                long value1 = stack.popLong();
                stack.pushInt(Long.compare(value1, value2));
                break;
            }
            case FCMPL:
            {
                float value2 = stack.popFloat();
                float value1 = stack.popFloat();
                if (Float.isNaN(value1) || Float.isNaN(value2))
                {
                    stack.pushInt(-1);
                }
                else
                {
                    stack.pushInt(Float.compare(value1, value2));
                }
                break;
            }
            case FCMPG:
            {
                float value2 = stack.popFloat();
                float value1 = stack.popFloat();
                if (Float.isNaN(value1) || Float.isNaN(value2))
                {
                    stack.pushInt(1);
                }
                else
                {
                    stack.pushInt(Float.compare(value1, value2));
                }
                break;
            }
            case DCMPL:
            {
                double value2 = stack.popDouble();
                double value1 = stack.popDouble();
                if (Double.isNaN(value1) || Double.isNaN(value2))
                {
                    stack.pushInt(-1);
                }
                else
                {
                    stack.pushInt(Double.compare(value1, value2));
                }
                break;
            }
            case DCMPG:
            {
                double value2 = stack.popDouble();
                double value1 = stack.popDouble();
                if (Double.isNaN(value1) || Double.isNaN(value2))
                {
                    stack.pushInt(1);
                }
                else
                {
                    stack.pushInt(Double.compare(value1, value2));
                }
                break;
            }
        }

        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchConditionalBranch(StackFrame frame, ConcreteStack stack, DispatchContext context, ConditionalBranchInstruction instruction)
    {
        ConditionalBranchInstruction.BranchType type = instruction.getType();
        boolean takeBranch = false;

        switch (type)
        {
            case IFEQ:
            {
                int value = stack.popInt();
                takeBranch = (value == 0);
                break;
            }
            case IFNE:
            {
                int value = stack.popInt();
                takeBranch = (value != 0);
                break;
            }
            case IFLT:
            {
                int value = stack.popInt();
                takeBranch = (value < 0);
                break;
            }
            case IFGE:
            {
                int value = stack.popInt();
                takeBranch = (value >= 0);
                break;
            }
            case IFGT:
            {
                int value = stack.popInt();
                takeBranch = (value > 0);
                break;
            }
            case IFLE:
            {
                int value = stack.popInt();
                takeBranch = (value <= 0);
                break;
            }
            case IF_ICMPEQ:
            {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 == value2);
                break;
            }
            case IF_ICMPNE:
            {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 != value2);
                break;
            }
            case IF_ICMPLT:
            {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 < value2);
                break;
            }
            case IF_ICMPGE:
            {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 >= value2);
                break;
            }
            case IF_ICMPGT:
            {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 > value2);
                break;
            }
            case IF_ICMPLE:
            {
                int value2 = stack.popInt();
                int value1 = stack.popInt();
                takeBranch = (value1 <= value2);
                break;
            }
            case IF_ACMPEQ:
            {
                ObjectInstance value2 = stack.popReference();
                ObjectInstance value1 = stack.popReference();
                takeBranch = (value1 == value2);
                break;
            }
            case IF_ACMPNE:
            {
                ObjectInstance value2 = stack.popReference();
                ObjectInstance value1 = stack.popReference();
                takeBranch = (value1 != value2);
                break;
            }
            case IFNULL:
            {
                ConcreteValue value = stack.pop();
                takeBranch = value.isNull();
                break;
            }
            case IFNONNULL:
            {
                ConcreteValue value = stack.pop();
                takeBranch = !value.isNull();
                break;
            }
        }

        if (takeBranch)
        {
            int target = instruction.getOffset() + instruction.getBranchOffset();
            context.setBranchTarget(target);
            return DispatchResult.BRANCH;
        }
        else
        {
            frame.advancePC(instruction.getLength());
            return DispatchResult.CONTINUE;
        }
    }

    private DispatchResult dispatchGoto(DispatchContext context, GotoInstruction instruction)
    {
        int target = instruction.getOffset() + instruction.getBranchOffset();
        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchGotoW(DispatchContext context, GotoInstruction instruction)
    {
        int target = instruction.getOffset() + instruction.getBranchOffsetWide();
        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchTableSwitch(ConcreteStack stack, DispatchContext context, TableSwitchInstruction instruction)
    {
        int index = stack.popInt();
        int target;

        if (index >= instruction.getLow() && index <= instruction.getHigh())
        {
            Integer offset = instruction.getJumpOffsets().get(index);
            if (offset != null)
            {
                target = instruction.getOffset() + offset;
            }
            else
            {
                target = instruction.getOffset() + instruction.getDefaultOffset();
            }
        }
        else
        {
            target = instruction.getOffset() + instruction.getDefaultOffset();
        }

        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchLookupSwitch(ConcreteStack stack, DispatchContext context, LookupSwitchInstruction instruction)
    {
        int key = stack.popInt();
        int target;

        Integer offset = instruction.getMatchOffsets().get(key);
        if (offset != null)
        {
            target = instruction.getOffset() + offset;
        }
        else
        {
            target = instruction.getOffset() + instruction.getDefaultOffset();
        }

        context.setBranchTarget(target);
        return DispatchResult.BRANCH;
    }

    private DispatchResult dispatchReturn()
    {
        return DispatchResult.RETURN;
    }

    private DispatchResult dispatchGetField(DispatchContext context, GetFieldInstruction instruction)
    {
        FieldInfo fieldInfo = new FieldInfo(
            instruction.getOwnerClass(),
            instruction.getFieldName(),
            instruction.getFieldDescriptor(),
            instruction.isStatic()
        );
        context.setPendingFieldAccess(fieldInfo);
        return DispatchResult.FIELD_GET;
    }

    private DispatchResult dispatchPutField(DispatchContext context, PutFieldInstruction instruction)
    {
        FieldInfo fieldInfo = new FieldInfo(
            instruction.getOwnerClass(),
            instruction.getFieldName(),
            instruction.getFieldDescriptor(),
            instruction.isStatic()
        );
        context.setPendingFieldAccess(fieldInfo);
        return DispatchResult.FIELD_PUT;
    }

    private DispatchResult dispatchInvokeVirtual(DispatchContext context, InvokeVirtualInstruction instruction)
    {
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

    private DispatchResult dispatchInvokeSpecial(DispatchContext context, InvokeSpecialInstruction instruction)
    {
        MethodInfo methodInfo = new MethodInfo(
            instruction.getOwnerClass(),
            instruction.getMethodName(),
            instruction.getMethodDescriptor(),
            false,
            false,
            true
        );
        context.setPendingInvoke(methodInfo);
        return DispatchResult.INVOKE;
    }

    private DispatchResult dispatchInvokeStatic(DispatchContext context, InvokeStaticInstruction instruction)
    {
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

    private DispatchResult dispatchInvokeInterface(DispatchContext context, InvokeInterfaceInstruction instruction)
    {
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

    private DispatchResult dispatchNew(DispatchContext context, NewObjectInstruction instruction)
    {
        String className = instruction.resolveClass();
        context.setPendingNewClass(className);
        return DispatchResult.NEW_OBJECT;
    }

    private DispatchResult dispatchNewArray(ConcreteStack stack, DispatchContext context, NewPrimitiveArrayInstruction instruction)
    {
        int count = stack.popInt();
        context.setPendingArrayDimensions(new int[]{count});

        String componentType;
        int typeCode = instruction.getArrayType().getCode();
        switch (typeCode)
        {
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

    private DispatchResult dispatchANewArray(ConcreteStack stack, DispatchContext context, ANewArrayInstruction instruction)
    {
        int count = stack.popInt();
        context.setPendingArrayDimensions(new int[]{count});
        String className = instruction.resolveClass();
        context.setPendingNewClass("L" + className + ";");
        return DispatchResult.NEW_ARRAY;
    }

    private DispatchResult dispatchArrayLength(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        ConcreteValue arrayRef = stack.pop();
        if (arrayRef.isNull())
        {
            throw new NullPointerException("Cannot get length of null array");
        }
        ObjectInstance obj = arrayRef.asReference();
        if (!(obj instanceof ArrayInstance))
        {
            throw new IllegalStateException("Object is not an array");
        }
        ArrayInstance array = (ArrayInstance) obj;
        stack.pushInt(array.getLength());
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchAThrow()
    {
        return DispatchResult.ATHROW;
    }

    private DispatchResult dispatchCheckCast(StackFrame frame, ConcreteStack stack, DispatchContext context, CheckCastInstruction instruction)
    {
        ConcreteValue ref = stack.peek();
        if (!ref.isNull())
        {
            ObjectInstance obj = ref.asReference();
            context.checkCast(obj, instruction.resolveClass());
        }
        frame.advancePC(instruction.getLength());
        return DispatchResult.CHECKCAST;
    }

    private DispatchResult dispatchInstanceOf(StackFrame frame, ConcreteStack stack, DispatchContext context, InstanceOfInstruction instruction)
    {
        ConcreteValue ref = stack.pop();

        int result = 0;
        if (!ref.isNull())
        {
            ObjectInstance obj = ref.asReference();
            if (context.isInstanceOf(obj, instruction.resolveClass()))
            {
                result = 1;
            }
        }
        stack.pushInt(result);
        frame.advancePC(instruction.getLength());
        return DispatchResult.INSTANCEOF;
    }

    private DispatchResult dispatchMonitorEnter(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.pop();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchMonitorExit(StackFrame frame, ConcreteStack stack, Instruction instruction)
    {
        stack.pop();
        frame.advancePC(instruction.getLength());
        return DispatchResult.CONTINUE;
    }

    private DispatchResult dispatchWide(StackFrame frame, ConcreteStack stack, ConcreteLocals locals, WideInstruction instruction)
    {
        Opcode op = instruction.getModifiedOpcode();
        int varIndex = instruction.getVarIndex();

        switch (op)
        {
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
                if (ref == null)
                {
                    stack.pushNull();
                }
                else
                {
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
                if (val == null)
                {
                    locals.setNull(varIndex);
                }
                else
                {
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

    private DispatchResult dispatchMultiANewArray(ConcreteStack stack, DispatchContext context, MultiANewArrayInstruction instruction)
    {
        int dimensions = instruction.getDimensions();
        int[] counts = new int[dimensions];

        for (int i = dimensions - 1; i >= 0; i--)
        {
            counts[i] = stack.popInt();
        }

        context.setPendingArrayDimensions(counts);
        context.setPendingNewClass(instruction.resolveClass());
        return DispatchResult.NEW_ARRAY;
    }

    private DispatchResult dispatchInvokeDynamic(DispatchContext context, InvokeDynamicInstruction instruction)
    {
        int bootstrapMethodIndex = instruction.getBootstrapMethodAttrIndex();
        int cpIndex = instruction.getCpIndex();

        String methodSignature = instruction.resolveMethod();
        int slashIndex = methodSignature.indexOf('(');
        String methodName = slashIndex > 0 ? methodSignature.substring(0, slashIndex) : methodSignature;
        String descriptor = slashIndex > 0 ? methodSignature.substring(slashIndex) : "()V";

        InvokeDynamicInfo info = new InvokeDynamicInfo(bootstrapMethodIndex, methodName, descriptor, cpIndex);
        context.setPendingInvokeDynamic(info);

        return DispatchResult.INVOKE_DYNAMIC;
    }

    private MethodHandleInfo resolveMethodHandle(ConstPool constPool, int cpIndex)
    {
        Item<?> item = constPool.getItem(cpIndex);
        if (!(item instanceof MethodHandleItem))
        {
            return new MethodHandleInfo(0, "Unknown", "unknown", "()V");
        }
        MethodHandleItem mhItem = (MethodHandleItem) item;
        int refKind = mhItem.getValue().getReferenceKind();
        int refIndex = mhItem.getValue().getReferenceIndex();

        Item<?> refItem = constPool.getItem(refIndex);
        String owner = "Unknown";
        String name = "unknown";
        String descriptor = "()V";

        if (refItem instanceof MethodRefItem)
        {
            MethodRefItem methodRef = (MethodRefItem) refItem;
            owner = methodRef.getClassName();
            name = methodRef.getName();
            descriptor = methodRef.getDescriptor();
        }
        else if (refItem instanceof InterfaceRefItem)
        {
            InterfaceRefItem methodRef = (InterfaceRefItem) refItem;
            owner = methodRef.getOwner();
            name = methodRef.getName();
            descriptor = methodRef.getDescriptor();
        }
        else if (refItem instanceof FieldRefItem)
        {
            FieldRefItem fieldRef = (FieldRefItem) refItem;
            owner = fieldRef.getClassName();
            name = fieldRef.getName();
            descriptor = fieldRef.getDescriptor();
        }

        return new MethodHandleInfo(refKind, owner, name, descriptor);
    }

    private MethodTypeInfo resolveMethodType(ConstPool constPool, int cpIndex)
    {
        Item<?> item = constPool.getItem(cpIndex);
        if (!(item instanceof MethodTypeItem))
        {
            return new MethodTypeInfo("()V");
        }
        MethodTypeItem mtItem = (MethodTypeItem) item;
        int descIndex = mtItem.getValue();
        Item<?> descItem = constPool.getItem(descIndex);
        if (descItem instanceof Utf8Item)
        {
            return new MethodTypeInfo(((Utf8Item) descItem).getValue());
        }
        return new MethodTypeInfo("()V");
    }

    private ConstantDynamicInfo resolveConstantDynamic(ConstPool constPool, int cpIndex)
    {
        Item<?> item = constPool.getItem(cpIndex);
        if (!(item instanceof ConstantDynamicItem))
        {
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

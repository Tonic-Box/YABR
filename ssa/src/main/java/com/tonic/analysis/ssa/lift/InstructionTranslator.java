package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.instruction.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.ir.NewArrayInstruction;
import com.tonic.analysis.ssa.ir.NewInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.MethodHandle;
import java.util.*;

import com.tonic.util.Opcode;

import static com.tonic.util.Opcode.*;

/**
 * Translator from JVM bytecode instructions to IR instructions during lifting.
 */
public class InstructionTranslator
{

    private final ConstPool constPool;
    private final BootstrapMethodsAttribute bsmAttr;
    private final Map<Integer, IRBlock> offsetToBlock;

    /**
     * Creates a translator with no bootstrap methods attribute.
     * @param constPool the constant pool of the class being lifted
     */
    public InstructionTranslator(ConstPool constPool)
    {
        this(constPool, null);
    }

    /**
     * Creates a translator.
     * @param constPool the constant pool of the class being lifted
     * @param bsmAttr the bootstrap methods attribute for invokedynamic resolution, or null
     */
    public InstructionTranslator(ConstPool constPool, BootstrapMethodsAttribute bsmAttr)
    {
        this.constPool = constPool;
        this.bsmAttr = bsmAttr;
        this.offsetToBlock = new HashMap<>();
    }

    /**
     * @return the offset to block
     */
    public Map<Integer, IRBlock> getOffsetToBlock()
    {
        return offsetToBlock;
    }

    /**
     * Registers a block at a bytecode offset.
     * @param offset the bytecode offset
     * @param block the IR block
     */
    public void registerBlock(int offset, IRBlock block)
    {
        offsetToBlock.put(offset, block);
    }

    /**
     * Translates a bytecode instruction to IR.
     * @param instr the bytecode instruction
     * @param state the abstract interpreter state
     * @param block the current IR block
     */
    public void translate(Instruction instr, AbstractState state, IRBlock block)
    {
        int opcode = instr.getOpcode();
        int emittedFrom = block.getInstructions().size();

        switch (Opcode.fromCode(opcode))
        {
            case NOP: break;
            case ACONST_NULL: translateAConstNull(state, block); break;
            case ICONST_M1: case ICONST_0: case ICONST_1: case ICONST_2: case ICONST_3: case ICONST_4: case ICONST_5: translateIConst(opcode - ICONST_0.getCode(), state, block); break;
            case LCONST_0: case LCONST_1: translateLConst(opcode - LCONST_0.getCode(), state, block); break;
            case FCONST_0: case FCONST_1: case FCONST_2: translateFConst(opcode - FCONST_0.getCode(), state, block); break;
            case DCONST_0: case DCONST_1: translateDConst(opcode - DCONST_0.getCode(), state, block); break;
            case BIPUSH: translateBipush((BipushInstruction) instr, state, block); break;
            case SIPUSH: translateSipush((SipushInstruction) instr, state, block); break;
            case LDC: translateLdc((LdcInstruction) instr, state, block); break;
            case LDC_W: translateLdcW((LdcWInstruction) instr, state, block); break;
            case LDC2_W: translateLdc2W((Ldc2WInstruction) instr, state, block); break;
            case ILOAD: translateILoad(((ILoadInstruction) instr).getVarIndex(), state, block); break;
            case LLOAD: translateLLoad(((LLoadInstruction) instr).getVarIndex(), state, block); break;
            case FLOAD: translateFLoad(((FLoadInstruction) instr).getVarIndex(), state, block); break;
            case DLOAD: translateDLoad(((DLoadInstruction) instr).getVarIndex(), state, block); break;
            case ALOAD: translateALoad(((ALoadInstruction) instr).getVarIndex(), state, block); break;
            case ILOAD_0: case ILOAD_1: case ILOAD_2: case ILOAD_3: translateILoad(opcode - ILOAD_0.getCode(), state, block); break;
            case LLOAD_0: case LLOAD_1: case LLOAD_2: case LLOAD_3: translateLLoad(opcode - LLOAD_0.getCode(), state, block); break;
            case FLOAD_0: case FLOAD_1: case FLOAD_2: case FLOAD_3: translateFLoad(opcode - FLOAD_0.getCode(), state, block); break;
            case DLOAD_0: case DLOAD_1: case DLOAD_2: case DLOAD_3: translateDLoad(opcode - DLOAD_0.getCode(), state, block); break;
            case ALOAD_0: case ALOAD_1: case ALOAD_2: case ALOAD_3: translateALoad(opcode - ALOAD_0.getCode(), state, block); break;
            case IALOAD: case LALOAD: case FALOAD: case DALOAD: case AALOAD: case BALOAD: case CALOAD: case SALOAD: translateArrayLoad(opcode, state, block); break;
            case ISTORE: translateIStore(((IStoreInstruction) instr).getVarIndex(), state, block); break;
            case LSTORE: translateLStore(((LStoreInstruction) instr).getVarIndex(), state, block); break;
            case FSTORE: translateFStore(((FStoreInstruction) instr).getVarIndex(), state, block); break;
            case DSTORE: translateDStore(((DStoreInstruction) instr).getVarIndex(), state, block); break;
            case ASTORE: translateAStore(((AStoreInstruction) instr).getVarIndex(), state, block); break;
            case ISTORE_0: case ISTORE_1: case ISTORE_2: case ISTORE_3: translateIStore(opcode - ISTORE_0.getCode(), state, block); break;
            case LSTORE_0: case LSTORE_1: case LSTORE_2: case LSTORE_3: translateLStore(opcode - LSTORE_0.getCode(), state, block); break;
            case FSTORE_0: case FSTORE_1: case FSTORE_2: case FSTORE_3: translateFStore(opcode - FSTORE_0.getCode(), state, block); break;
            case DSTORE_0: case DSTORE_1: case DSTORE_2: case DSTORE_3: translateDStore(opcode - DSTORE_0.getCode(), state, block); break;
            case ASTORE_0: case ASTORE_1: case ASTORE_2: case ASTORE_3: translateAStore(opcode - ASTORE_0.getCode(), state, block); break;
            case IASTORE: case LASTORE: case FASTORE: case DASTORE: case AASTORE: case BASTORE: case CASTORE: case SASTORE: translateArrayStore(state, block); break;
            case POP: state.pop(); break;
            case POP2: translatePop2(state); break;
            case DUP: translateDup(state); break;
            case DUP_X1: translateDupX1(state); break;
            case DUP_X2: translateDupX2(state); break;
            case DUP2: translateDup2(state); break;
            case DUP2_X1: translateDup2X1(state); break;
            case DUP2_X2: translateDup2X2(state); break;
            case SWAP: translateSwap(state); break;
            case IADD: case LADD: case FADD: case DADD: translateBinaryOp(BinaryOp.ADD, opcode, state, block); break;
            case ISUB: case LSUB: case FSUB: case DSUB: translateBinaryOp(BinaryOp.SUB, opcode, state, block); break;
            case IMUL: case LMUL: case FMUL: case DMUL: translateBinaryOp(BinaryOp.MUL, opcode, state, block); break;
            case IDIV: case LDIV: case FDIV: case DDIV: translateBinaryOp(BinaryOp.DIV, opcode, state, block); break;
            case IREM: case LREM: case FREM: case DREM: translateBinaryOp(BinaryOp.REM, opcode, state, block); break;
            case INEG: case LNEG: case FNEG: case DNEG: translateNeg(opcode, state, block); break;
            case ISHL: case LSHL: translateBinaryOp(BinaryOp.SHL, opcode, state, block); break;
            case ISHR: case LSHR: translateBinaryOp(BinaryOp.SHR, opcode, state, block); break;
            case IUSHR: case LUSHR: translateBinaryOp(BinaryOp.USHR, opcode, state, block); break;
            case IAND: case LAND: translateBinaryOp(BinaryOp.AND, opcode, state, block); break;
            case IOR: case LOR: translateBinaryOp(BinaryOp.OR, opcode, state, block); break;
            case IXOR: case LXOR: translateBinaryOp(BinaryOp.XOR, opcode, state, block); break;
            case IINC: translateIinc((IIncInstruction) instr, state, block); break;
            case I2L: translateConvert(UnaryOp.I2L, state, block, PrimitiveType.LONG); break;
            case I2F: translateConvert(UnaryOp.I2F, state, block, PrimitiveType.FLOAT); break;
            case I2D: translateConvert(UnaryOp.I2D, state, block, PrimitiveType.DOUBLE); break;
            case L2I: translateConvert(UnaryOp.L2I, state, block, PrimitiveType.INT); break;
            case L2F: translateConvert(UnaryOp.L2F, state, block, PrimitiveType.FLOAT); break;
            case L2D: translateConvert(UnaryOp.L2D, state, block, PrimitiveType.DOUBLE); break;
            case F2I: translateConvert(UnaryOp.F2I, state, block, PrimitiveType.INT); break;
            case F2L: translateConvert(UnaryOp.F2L, state, block, PrimitiveType.LONG); break;
            case F2D: translateConvert(UnaryOp.F2D, state, block, PrimitiveType.DOUBLE); break;
            case D2I: translateConvert(UnaryOp.D2I, state, block, PrimitiveType.INT); break;
            case D2L: translateConvert(UnaryOp.D2L, state, block, PrimitiveType.LONG); break;
            case D2F: translateConvert(UnaryOp.D2F, state, block, PrimitiveType.FLOAT); break;
            case I2B: translateConvert(UnaryOp.I2B, state, block, PrimitiveType.BYTE); break;
            case I2C: translateConvert(UnaryOp.I2C, state, block, PrimitiveType.CHAR); break;
            case I2S: translateConvert(UnaryOp.I2S, state, block, PrimitiveType.SHORT); break;
            case LCMP: translateCmp(BinaryOp.LCMP, state, block); break;
            case FCMPL: translateCmp(BinaryOp.FCMPL, state, block); break;
            case FCMPG: translateCmp(BinaryOp.FCMPG, state, block); break;
            case DCMPL: translateCmp(BinaryOp.DCMPL, state, block); break;
            case DCMPG: translateCmp(BinaryOp.DCMPG, state, block); break;
            case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE: translateIfZero((ConditionalBranchInstruction) instr, opcode, state, block); break;
            case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE: translateIfICmp((ConditionalBranchInstruction) instr, opcode, state, block); break;
            case IF_ACMPEQ: case IF_ACMPNE: translateIfACmp((ConditionalBranchInstruction) instr, opcode, state, block); break;
            case GOTO: case GOTO_W: translateGoto((GotoInstruction) instr, block); break;
            case JSR: case JSR_W: translateJsr((JsrInstruction) instr, state, block); break;
            case RET: translateRet(block); break;
            case TABLESWITCH: translateTableSwitch((TableSwitchInstruction) instr, state, block); break;
            case LOOKUPSWITCH: translateLookupSwitch((LookupSwitchInstruction) instr, state, block); break;
            case IRETURN: case LRETURN: case FRETURN: case DRETURN: case ARETURN: translateReturn(state, block); break;
            case RETURN_: translateVoidReturn(block); break;
            case GETSTATIC: translateGetStatic((GetFieldInstruction) instr, state, block); break;
            case PUTSTATIC: translatePutStatic((PutFieldInstruction) instr, state, block); break;
            case GETFIELD: translateGetField((GetFieldInstruction) instr, state, block); break;
            case PUTFIELD: translatePutField((PutFieldInstruction) instr, state, block); break;
            case INVOKEVIRTUAL: translateInvokeVirtual((InvokeVirtualInstruction) instr, state, block); break;
            case INVOKESPECIAL: translateInvokeSpecial((InvokeSpecialInstruction) instr, state, block); break;
            case INVOKESTATIC: translateInvokeStatic((InvokeStaticInstruction) instr, state, block); break;
            case INVOKEINTERFACE: translateInvokeInterface((InvokeInterfaceInstruction) instr, state, block); break;
            case INVOKEDYNAMIC: translateInvokeDynamic((InvokeDynamicInstruction) instr, state, block); break;
            case NEW: translateNew((NewObjectInstruction) instr, state, block); break;
            case NEWARRAY: translateNewArray((NewPrimitiveArrayInstruction) instr, state, block); break;
            case ANEWARRAY: translateANewArray((ANewArrayInstruction) instr, state, block); break;
            case ARRAYLENGTH: translateArrayLength(state, block); break;
            case ATHROW: translateAThrow(state, block); break;
            case CHECKCAST: translateCheckCast((CheckCastInstruction) instr, state, block); break;
            case INSTANCEOF: translateInstanceOf((InstanceOfInstruction) instr, state, block); break;
            case MONITORENTER: translateMonitorEnter(state, block); break;
            case MONITOREXIT: translateMonitorExit(state, block); break;
            case WIDE: translateWide(instr, state, block); break;
            case MULTIANEWARRAY: translateMultiANewArray((MultiANewArrayInstruction) instr, state, block); break;
            case IFNULL: translateIfNull((ConditionalBranchInstruction) instr, state, block); break;
            case IFNONNULL: translateIfNonNull((ConditionalBranchInstruction) instr, state, block); break;
            default: throw new UnsupportedOperationException("Unsupported opcode: 0x" + Integer.toHexString(opcode));
        }

        List<IRInstruction> emitted = block.getInstructions();
        for (int i = emittedFrom; i < emitted.size(); i++)
        {
            IRInstruction ir = emitted.get(i);
            if (ir.getBytecodeOffset() < 0)
            {
                ir.setBytecodeOffset(instr.getOffset());
            }
        }
    }

    private void translateAConstNull(AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(ReferenceType.OBJECT);
        block.addInstruction(new ConstantInstruction(result, NullConstant.INSTANCE));
        state.push(result);
    }

    private void translateIConst(int value, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(result, IntConstant.of(value)));
        state.push(result);
    }

    private void translateLConst(int value, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.LONG);
        block.addInstruction(new ConstantInstruction(result, LongConstant.of(value)));
        state.push(result);
    }

    private void translateFConst(int value, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.FLOAT);
        block.addInstruction(new ConstantInstruction(result, FloatConstant.of(value)));
        state.push(result);
    }

    private void translateDConst(int value, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
        block.addInstruction(new ConstantInstruction(result, DoubleConstant.of(value)));
        state.push(result);
    }

    private void translateBipush(BipushInstruction instr, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(result, IntConstant.of(instr.getValue())));
        state.push(result);
    }

    private void translateSipush(SipushInstruction instr, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(result, IntConstant.of(instr.getValue())));
        state.push(result);
    }

    private void translateLdc(LdcInstruction instr, AbstractState state, IRBlock block)
    {
        int cpIndex = instr.getCpIndex();
        Item<?> item = constPool.getItem(cpIndex);
        Constant constant = itemToConstant(item, cpIndex);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private void translateLdcW(LdcWInstruction instr, AbstractState state, IRBlock block)
    {
        int cpIndex = instr.getCpIndex();
        Item<?> item = constPool.getItem(cpIndex);
        Constant constant = itemToConstant(item, cpIndex);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private void translateLdc2W(Ldc2WInstruction instr, AbstractState state, IRBlock block)
    {
        int cpIndex = instr.getCpIndex();
        Item<?> item = constPool.getItem(cpIndex);
        Constant constant = itemToConstant(item, cpIndex);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private Constant itemToConstant(Item<?> item, int cpIndex)
    {
        if (item instanceof IntegerItem)
        {
            IntegerItem intItem = (IntegerItem) item;
            return IntConstant.of(intItem.getValue());
        }
        else if (item instanceof LongItem)
        {
            LongItem longItem = (LongItem) item;
            return LongConstant.of(longItem.getValue());
        }
        else if (item instanceof FloatItem)
        {
            FloatItem floatItem = (FloatItem) item;
            return FloatConstant.of(floatItem.getValue());
        }
        else if (item instanceof DoubleItem)
        {
            DoubleItem doubleItem = (DoubleItem) item;
            return DoubleConstant.of(doubleItem.getValue());
        }
        else if (item instanceof StringRefItem)
        {
            StringRefItem stringItem = (StringRefItem) item;
            Utf8Item utf8 = (Utf8Item) constPool.getItem(stringItem.getValue());
            return new StringConstant(utf8.getValue());
        }
        else if (item instanceof ClassRefItem)
        {
            ClassRefItem classItem = (ClassRefItem) item;
            return new ClassConstant(classItem.getClassName());
        }
        else if (item instanceof MethodHandleItem)
        {
            MethodHandleItem mhItem = (MethodHandleItem) item;
            return resolveMethodHandle(mhItem);
        }
        else if (item instanceof MethodTypeItem)
        {
            MethodTypeItem mtItem = (MethodTypeItem) item;
            int descIndex = mtItem.getValue();
            Utf8Item utf8 = (Utf8Item) constPool.getItem(descIndex);
            return new MethodTypeConstant(utf8.getValue());
        }
        else if (item instanceof ConstantDynamicItem)
        {
            ConstantDynamicItem cdItem = (ConstantDynamicItem) item;
            return resolveDynamicConstant(cdItem, cpIndex);
        }
        throw new UnsupportedOperationException("Unsupported constant type: " + item.getClass());
    }

    /**
     * Resolves a MethodHandle constant pool item to an IR MethodHandleConstant.
     */
    private MethodHandleConstant resolveMethodHandle(MethodHandleItem mhItem)
    {
        MethodHandle mh = mhItem.getValue();
        int refKind = mh.getReferenceKind();
        int refIndex = mh.getReferenceIndex();
        Item<?> refItem = constPool.getItem(refIndex);

        String owner;
        String name;
        String desc;

        // The reference index points to different item types based on reference kind
        if (refItem instanceof FieldRefItem)
        {
            FieldRefItem fieldRef = (FieldRefItem) refItem;
            owner = fieldRef.getOwner();
            name = fieldRef.getName();
            desc = fieldRef.getDescriptor();
        }
        else if (refItem instanceof MethodRefItem)
        {
            MethodRefItem methodRef = (MethodRefItem) refItem;
            owner = methodRef.getOwner();
            name = methodRef.getName();
            desc = methodRef.getDescriptor();
        }
        else if (refItem instanceof InterfaceRefItem)
        {
            InterfaceRefItem interfaceRef = (InterfaceRefItem) refItem;
            owner = interfaceRef.getOwner();
            name = interfaceRef.getName();
            desc = interfaceRef.getDescriptor();
        }
        else
        {
            throw new UnsupportedOperationException("Unsupported MethodHandle reference type: " + refItem.getClass());
        }

        return new MethodHandleConstant(refKind, owner, name, desc);
    }

    /**
     * Resolves a ConstantDynamic constant pool item to an IR DynamicConstant.
     */
    private DynamicConstant resolveDynamicConstant(ConstantDynamicItem cdItem, int cpIndex)
    {
        String name = cdItem.getName();
        String desc = cdItem.getDescriptor();
        int bsmIndex = cdItem.getBootstrapMethodAttrIndex();
        return new DynamicConstant(name, desc, bsmIndex, cpIndex);
    }

    private void translateILoad(int index, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateLLoad(int index, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.LONG);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateFLoad(int index, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.FLOAT);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateDLoad(int index, AbstractState state, IRBlock block)
    {
        SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateALoad(int index, AbstractState state, IRBlock block)
    {
        Value local = state.getLocal(index);
        // A caught-exception value (named "exc_<handler>", seeded onto the handler's entry stack) is a
        // free-standing SSA value captured at handler entry into its OWN register by the capture marker - it
        // is not tied to this local's index slot. Forward it directly instead of emitting a LoadLocal that
        // would read the (separately allocated) local-index slot: otherwise the capture register and the load
        // slot diverge and the handler body reads an unwritten slot (a corrupt, un-decompilable handler).
        // Reassigning the local makes its current value no longer the exception, so normal loads are
        // unaffected - only a direct re-load of the still-live caught exception is forwarded.
        if (local instanceof SSAValue && ((SSAValue) local).getName() != null
                && ((SSAValue) local).getName().startsWith("exc_"))
        {
            state.push(local);
            return;
        }
        IRType type = (local != null && local.getType() != null && local.getType().isReference())
                      ? local.getType() : ReferenceType.OBJECT;
        SSAValue result = new SSAValue(type);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateArrayLoad(int opcode, AbstractState state, IRBlock block)
    {
        Value index = state.pop();
        Value array = state.pop();
        IRType elemType = getArrayElementType(opcode);
        if (opcode == AALOAD.getCode() && array.getType() instanceof ArrayType)
        {
            ArrayType arrayType = (ArrayType) array.getType();
            elemType = arrayType.getDimensions() > 1
                    ? new ArrayType(arrayType.getElementType(), arrayType.getDimensions() - 1)
                    : arrayType.getElementType();
        }
        SSAValue result = new SSAValue(elemType);
        block.addInstruction(ArrayAccessInstruction.createLoad(result, array, index));
        state.push(result);
    }

    private void translateIStore(int index, AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateLStore(int index, AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateFStore(int index, AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateDStore(int index, AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateAStore(int index, AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateArrayStore(AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        Value index = state.pop();
        Value array = state.pop();
        block.addInstruction(ArrayAccessInstruction.createStore(array, index, value));
    }

    /**
     * Translates the pop2 instruction.
     */
    private void translatePop2(AbstractState state)
    {
        Value top = state.peek();
        if (top.getType() != null && top.getType().isTwoSlot())
        {
            // Category-2 value (long/double) - single pop
            state.pop();
        }
        else
        {
            // Two category-1 values - double pop
            state.pop();
            state.pop();
        }
    }

    private void translateDup(AbstractState state)
    {
        Value top = state.peek();
        state.push(top);
    }

    private void translateDupX1(AbstractState state)
    {
        Value v1 = state.pop();
        Value v2 = state.pop();
        state.push(v1);
        state.push(v2);
        state.push(v1);
    }

    /**
     * dup_x2 - duplicate top value and insert two or three down.
     */
    private void translateDupX2(AbstractState state)
    {
        Value v1 = state.pop();
        Value v2 = state.peek();

        if (v2.getType() != null && v2.getType().isTwoSlot())
        {
            v2 = state.pop();
            state.push(v1);
            state.push(v2);
            state.push(v1);
        }
        else
        {
            v2 = state.pop();
            Value v3 = state.pop();
            state.push(v1);
            state.push(v3);
            state.push(v2);
            state.push(v1);
        }
    }

    /**
     * dup2 - duplicate top one or two values.
     */
    private void translateDup2(AbstractState state)
    {
        Value v1 = state.peek();
        if (v1.getType() != null && v1.getType().isTwoSlot())
        {
            // Form 1: single category-2 value
            state.push(v1);
        }
        else
        {
            // Form 2: two category-1 values
            v1 = state.pop();
            Value v2 = state.peek();
            state.push(v1);
            state.push(v2);
            state.push(v1);
        }
    }

    /**
     * dup2_x1 - duplicate top one or two values and insert beneath.
     */
    private void translateDup2X1(AbstractState state)
    {
        Value v1 = state.peek();
        if (v1.getType() != null && v1.getType().isTwoSlot())
        {
            // Form 1: category-2 value over category-1 value
            v1 = state.pop();
            Value v2 = state.pop();
            state.push(v1);
            state.push(v2);
            state.push(v1);
        }
        else
        {
            // Form 2: two category-1 values over one category-1 value
            v1 = state.pop();
            Value v2 = state.pop();
            Value v3 = state.pop();
            state.push(v2);
            state.push(v1);
            state.push(v3);
            state.push(v2);
            state.push(v1);
        }
    }

    /**
     * dup2_x2 - duplicate top one or two values and insert beneath.
     */
    private void translateDup2X2(AbstractState state)
    {
        Value v1 = state.peek();
        boolean v1TwoSlot = v1.getType() != null && v1.getType().isTwoSlot();

        v1 = state.pop();
        Value v2;
        if (v1TwoSlot)
        {
            // Forms 1 or 3: category-2 on top
            v2 = state.peek();
            boolean v2TwoSlot = v2.getType() != null && v2.getType().isTwoSlot();

            if (v2TwoSlot)
            {
                // Form 1: category-2 over category-2
                state.push(v1);
                state.push(v2);
                state.push(v1);
            }
            else
            {
                // Form 3: category-2 over two category-1
                v2 = state.pop();
                Value v3 = state.pop();
                state.push(v1);
                state.push(v3);
                state.push(v2);
                state.push(v1);
            }
        }
        else
        {
            // Forms 2 or 4: two category-1 on top
            v2 = state.pop();
            Value v3 = state.peek();
            boolean v3TwoSlot = v3.getType() != null && v3.getType().isTwoSlot();

            if (v3TwoSlot)
            {
                // Form 2: two category-1 over category-2
                state.push(v2);
                state.push(v1);
                state.push(v3);
                state.push(v2);
                state.push(v1);
            }
            else
            {
                // Form 4: two category-1 over two category-1
                v3 = state.pop();
                Value v4 = state.pop();
                state.push(v2);
                state.push(v1);
                state.push(v4);
                state.push(v3);
                state.push(v2);
                state.push(v1);
            }
        }
    }

    private void translateSwap(AbstractState state)
    {
        Value v1 = state.pop();
        Value v2 = state.pop();
        state.push(v1);
        state.push(v2);
    }

    private void translateBinaryOp(BinaryOp op, int opcode, AbstractState state, IRBlock block)
    {
        Value right = state.pop();
        Value left = state.pop();
        IRType resultType = getBinaryOpResultType(opcode);
        SSAValue result = new SSAValue(resultType);
        block.addInstruction(new BinaryOpInstruction(result, op, left, right));
        state.push(result);
    }

    private void translateNeg(int opcode, AbstractState state, IRBlock block)
    {
        Value operand = state.pop();
        IRType resultType = getNegResultType(opcode);
        SSAValue result = new SSAValue(resultType);
        block.addInstruction(new UnaryOpInstruction(result, UnaryOp.NEG, operand));
        state.push(result);
    }

    private void translateIinc(IIncInstruction instr, AbstractState state, IRBlock block)
    {
        int index = instr.getVarIndex();
        int increment = instr.getConstValue();

        SSAValue loaded = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new LoadLocalInstruction(loaded, index));

        SSAValue incConst = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(incConst, IntConstant.of(increment)));

        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, loaded, incConst));

        state.setLocal(index, result);
        block.addInstruction(new StoreLocalInstruction(index, result));
    }

    private void translateConvert(UnaryOp op, AbstractState state, IRBlock block, IRType resultType)
    {
        Value operand = state.pop();
        SSAValue result = new SSAValue(resultType);
        block.addInstruction(new UnaryOpInstruction(result, op, operand));
        state.push(result);
    }

    private void translateCmp(BinaryOp op, AbstractState state, IRBlock block)
    {
        Value right = state.pop();
        Value left = state.pop();
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new BinaryOpInstruction(result, op, left, right));
        state.push(result);
    }

    private void translateIfZero(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block)
    {
        Value operand = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        CompareOp cmpOp;
        if (opcode == IFEQ.getCode()) cmpOp = CompareOp.IFEQ;
        else if (opcode == IFNE.getCode()) cmpOp = CompareOp.IFNE;
        else if (opcode == IFLT.getCode()) cmpOp = CompareOp.IFLT;
        else if (opcode == IFGE.getCode()) cmpOp = CompareOp.IFGE;
        else if (opcode == IFGT.getCode()) cmpOp = CompareOp.IFGT;
        else if (opcode == IFLE.getCode()) cmpOp = CompareOp.IFLE;
        else throw new IllegalStateException();

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(cmpOp, operand, trueBlock, falseBlock));
    }

    private void translateIfICmp(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block)
    {
        Value right = state.pop();
        Value left = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        CompareOp cmpOp;
        if (opcode == IF_ICMPEQ.getCode()) cmpOp = CompareOp.EQ;
        else if (opcode == IF_ICMPNE.getCode()) cmpOp = CompareOp.NE;
        else if (opcode == IF_ICMPLT.getCode()) cmpOp = CompareOp.LT;
        else if (opcode == IF_ICMPGE.getCode()) cmpOp = CompareOp.GE;
        else if (opcode == IF_ICMPGT.getCode()) cmpOp = CompareOp.GT;
        else if (opcode == IF_ICMPLE.getCode()) cmpOp = CompareOp.LE;
        else throw new IllegalStateException();

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(cmpOp, left, right, trueBlock, falseBlock));
    }

    private void translateIfACmp(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block)
    {
        Value right = state.pop();
        Value left = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        CompareOp cmpOp = opcode == IF_ACMPEQ.getCode() ? CompareOp.ACMPEQ : CompareOp.ACMPNE;

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(cmpOp, left, right, trueBlock, falseBlock));
    }

    private void translateIfNull(ConditionalBranchInstruction instr, AbstractState state, IRBlock block)
    {
        Value operand = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(CompareOp.IFNULL, operand, trueBlock, falseBlock));
    }

    private void translateIfNonNull(ConditionalBranchInstruction instr, AbstractState state, IRBlock block)
    {
        Value operand = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(CompareOp.IFNONNULL, operand, trueBlock, falseBlock));
    }

    private void translateGoto(GotoInstruction instr, IRBlock block)
    {
        int target = instr.getOffset() + instr.getBranchOffset();
        IRBlock targetBlock = offsetToBlock.get(target);
        block.addInstruction(SimpleInstruction.createGoto(targetBlock));
    }

    /**
     * Tracks JSR call sites: maps subroutine entry offset to list of continuation blocks.
     */
    private final Map<Integer, List<IRBlock>> jsrContinuations = new HashMap<>();

    /**
     * Translates JSR/JSR_W instructions.
     */
    private void translateJsr(JsrInstruction instr, AbstractState state, IRBlock block)
    {
        int jsrOffset = instr.getOffset();
        int subroutineEntry = jsrOffset + instr.getBranchOffset();
        int continuationOffset = jsrOffset + instr.getLength();

        IRBlock subroutineBlock = offsetToBlock.get(subroutineEntry);
        IRBlock continuationBlock = offsetToBlock.get(continuationOffset);

        // Track this JSR's continuation for when RET is encountered
        jsrContinuations.computeIfAbsent(subroutineEntry, k -> new ArrayList<>())
                .add(continuationBlock);

        // JSR pushes the return address onto the stack in original bytecode.
        // Since we're converting JSR/RET to GOTO (subroutine inlining), we need
        // to push a dummy value that will be consumed by the following ASTORE.
        // We use PrimitiveType.INT since the ASTORE will pop it and store to a local,
        // and the local won't actually be used (RET is converted to GOTO).
        // However, since we want dead code elimination to remove this entirely,
        // we create a marker value with a special name prefix.
        SSAValue returnAddr = new SSAValue(PrimitiveType.INT, "jsr_retaddr_" + jsrOffset);
        block.addInstruction(new ConstantInstruction(returnAddr, IntConstant.of(continuationOffset)));
        state.push(returnAddr);

        block.addInstruction(SimpleInstruction.createGoto(subroutineBlock));
    }

    /**
     * Translates RET instruction.
     */
    private void translateRet(IRBlock block)
    {
        // Find all possible continuations for this subroutine
        // In the simple case (single JSR to this subroutine), there's exactly one continuation
        // For multiple JSRs, we need to handle all possible return targets

        // Collect all continuation blocks from all JSR call sites
        List<IRBlock> allContinuations = new ArrayList<>();
        for (List<IRBlock> continuations : jsrContinuations.values())
        {
            allContinuations.addAll(continuations);
        }

        if (allContinuations.isEmpty())
        {
            // No JSR was found - this shouldn't happen in valid bytecode
            throw new IllegalStateException("RET instruction without corresponding JSR");
        }

        IRBlock continuation = allContinuations.get(0);
        block.addInstruction(SimpleInstruction.createGoto(continuation));
    }

    private void translateTableSwitch(TableSwitchInstruction instr, AbstractState state, IRBlock block)
    {
        Value key = state.pop();
        int defaultTarget = instr.getOffset() + instr.getDefaultOffset();
        IRBlock defaultBlock = offsetToBlock.get(defaultTarget);

        SwitchInstruction switchInstr = new SwitchInstruction(key, defaultBlock);
        for (Map.Entry<Integer, Integer> entry : instr.getJumpOffsets().entrySet())
        {
            int caseTarget = instr.getOffset() + entry.getValue();
            IRBlock caseBlock = offsetToBlock.get(caseTarget);
            switchInstr.addCase(entry.getKey(), caseBlock);
        }
        block.addInstruction(switchInstr);
    }

    private void translateLookupSwitch(LookupSwitchInstruction instr, AbstractState state, IRBlock block)
    {
        Value key = state.pop();
        int defaultTarget = instr.getOffset() + instr.getDefaultOffset();
        IRBlock defaultBlock = offsetToBlock.get(defaultTarget);

        SwitchInstruction switchInstr = new SwitchInstruction(key, defaultBlock);
        for (Map.Entry<Integer, Integer> entry : instr.getMatchOffsets().entrySet())
        {
            int caseTarget = instr.getOffset() + entry.getValue();
            IRBlock caseBlock = offsetToBlock.get(caseTarget);
            switchInstr.addCase(entry.getKey(), caseBlock);
        }
        block.addInstruction(switchInstr);
    }

    private void translateReturn(AbstractState state, IRBlock block)
    {
        Value returnValue = state.pop();
        block.addInstruction(new ReturnInstruction(returnValue));
    }

    private void translateVoidReturn(IRBlock block)
    {
        block.addInstruction(new ReturnInstruction());
    }

    private void translateGetStatic(GetFieldInstruction instr, AbstractState state, IRBlock block)
    {
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        String owner = fieldRef.getOwner();
        String name = fieldRef.getName();
        String desc = fieldRef.getDescriptor();
        IRType fieldType = IRType.fromDescriptor(desc);
        SSAValue result = new SSAValue(fieldType);
        block.addInstruction(FieldAccessInstruction.createStaticLoad(result, owner, name, desc));
        state.push(result);
    }

    private void translatePutStatic(PutFieldInstruction instr, AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        block.addInstruction(FieldAccessInstruction.createStaticStore(
                fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), value));
    }

    private void translateGetField(GetFieldInstruction instr, AbstractState state, IRBlock block)
    {
        Value objectRef = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        IRType fieldType = IRType.fromDescriptor(fieldRef.getDescriptor());
        SSAValue result = new SSAValue(fieldType);
        block.addInstruction(FieldAccessInstruction.createLoad(
                result, fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), objectRef));
        state.push(result);
    }

    private void translatePutField(PutFieldInstruction instr, AbstractState state, IRBlock block)
    {
        Value value = state.pop();
        Value objectRef = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        block.addInstruction(FieldAccessInstruction.createStore(
                fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), objectRef, value));
    }

    private void translateInvokeVirtual(InvokeVirtualInstruction instr, AbstractState state, IRBlock block)
    {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokevirtual");
        translateInvoke(InvokeType.VIRTUAL, ref[0], ref[1], ref[2], false, state, block);
    }

    private void translateInvokeSpecial(InvokeSpecialInstruction instr, AbstractState state, IRBlock block)
    {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokespecial");
        translateInvoke(InvokeType.SPECIAL, ref[0], ref[1], ref[2], false, state, block);
    }

    private void translateInvokeStatic(InvokeStaticInstruction instr, AbstractState state, IRBlock block)
    {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokestatic");
        translateInvoke(InvokeType.STATIC, ref[0], ref[1], ref[2], true, state, block);
    }

    private void translateInvokeInterface(InvokeInterfaceInstruction instr, AbstractState state, IRBlock block)
    {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokeinterface");
        translateInvoke(InvokeType.INTERFACE, ref[0], ref[1], ref[2], false, state, block);
    }

    /**
     * Extracts method reference info from either MethodRefItem or InterfaceRefItem.
     * @return array of [owner, name, descriptor]
     */
    private String[] getMethodRefInfo(int cpIndex, String opcode)
    {
        Item<?> refItem = constPool.getItem(cpIndex);
        if (refItem instanceof MethodRefItem)
        {
            MethodRefItem methodRef = (MethodRefItem) refItem;
            return new String[] { methodRef.getOwner(), methodRef.getName(), methodRef.getDescriptor() };
        }
        else if (refItem instanceof InterfaceRefItem)
        {
            InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
            return new String[] { ifaceRef.getOwner(), ifaceRef.getName(), ifaceRef.getDescriptor() };
        }
        throw new IllegalStateException("Unexpected ref type for " + opcode + ": " + refItem.getClass());
    }

    private void translateInvokeDynamic(InvokeDynamicInstruction instr, AbstractState state, IRBlock block)
    {
        int cpIndex = instr.getCpIndex();
        InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(cpIndex);
        String desc = item.getDescriptor();
        String name = item.getName();
        int argCount = countMethodArgs(desc);

        List<Value> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++)
        {
            args.add(0, state.pop());
        }

        int bsmIndex = item.getValue().getBootstrapMethodAttrIndex();
        BootstrapMethodInfo bootstrapInfo = extractBootstrapInfo(bsmIndex);

        String returnDesc = desc.substring(desc.indexOf(')') + 1);
        if (returnDesc.equals("V"))
        {
            block.addInstruction(new InvokeInstruction(InvokeType.DYNAMIC, "", name, desc, args, cpIndex, bootstrapInfo));
        }
        else
        {
            IRType returnType = IRType.fromDescriptor(returnDesc);
            SSAValue result = new SSAValue(returnType);
            block.addInstruction(new InvokeInstruction(result, InvokeType.DYNAMIC, "", name, desc, args, cpIndex, bootstrapInfo));
            state.push(result);
        }
    }

    private BootstrapMethodInfo extractBootstrapInfo(int bsmIndex)
    {
        if (bsmAttr == null || bsmIndex < 0 || bsmIndex >= bsmAttr.getBootstrapMethods().size())
        {
            return null;
        }
        BootstrapMethod bsm = bsmAttr.getBootstrapMethods().get(bsmIndex);

        Item<?> bsmItem = constPool.getItem(bsm.getBootstrapMethodRef());
        if (!(bsmItem instanceof MethodHandleItem))
        {
            return null;
        }
        MethodHandleConstant bsmHandle = resolveMethodHandle((MethodHandleItem) bsmItem);

        List<Constant> bsmArgs = new ArrayList<>();
        for (Integer argIndex : bsm.getBootstrapArguments())
        {
            Item<?> argItem = constPool.getItem(argIndex);
            bsmArgs.add(itemToConstant(argItem, argIndex));
        }
        return new BootstrapMethodInfo(bsmHandle, bsmArgs);
    }

    private void translateInvoke(InvokeType invokeType, String owner, String name, String desc, boolean isStatic, AbstractState state, IRBlock block)
    {
        int argCount = countMethodArgs(desc);
        if (!isStatic)
        {
            argCount++;
        }

        List<Value> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++)
        {
            args.add(0, state.pop());
        }

        String returnDesc = desc.substring(desc.indexOf(')') + 1);
        if (returnDesc.equals("V"))
        {
            block.addInstruction(new InvokeInstruction(invokeType, owner, name, desc, args));
        }
        else
        {
            IRType returnType = IRType.fromDescriptor(returnDesc);
            SSAValue result = new SSAValue(returnType);
            block.addInstruction(new InvokeInstruction(result, invokeType, owner, name, desc, args));
            state.push(result);
        }
    }

    private void translateNew(NewObjectInstruction instr, AbstractState state, IRBlock block)
    {
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        String className = classRef.getClassName();
        SSAValue result = new SSAValue(new ReferenceType(className));
        block.addInstruction(new NewInstruction(result, className));
        state.push(result);
    }

    private void translateNewArray(NewPrimitiveArrayInstruction instr, AbstractState state, IRBlock block)
    {
        Value length = state.pop();
        IRType elemType = getNewArrayElementType(instr.getArrayType().getCode());
        SSAValue result = new SSAValue(new ArrayType(elemType));
        block.addInstruction(new NewArrayInstruction(result, elemType, length));
        state.push(result);
    }

    private void translateANewArray(ANewArrayInstruction instr, AbstractState state, IRBlock block)
    {
        Value length = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        String elemName = classRef.getClassName();
        IRType elemType = elemName.startsWith("[")
                ? IRType.fromDescriptor(elemName)
                : new ReferenceType(elemName);
        SSAValue result = new SSAValue(new ArrayType(elemType));
        block.addInstruction(new NewArrayInstruction(result, elemType, length));
        state.push(result);
    }

    private void translateMultiANewArray(MultiANewArrayInstruction instr, AbstractState state, IRBlock block)
    {
        int dims = instr.getDimensions();
        List<Value> dimensions = new ArrayList<>();
        for (int i = 0; i < dims; i++)
        {
            dimensions.add(0, state.pop());
        }

        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType arrayType = IRType.fromDescriptor(classRef.getClassName());
        SSAValue result = new SSAValue(arrayType);
        IRType elemType = ((ArrayType) arrayType).getElementType();
        block.addInstruction(new NewArrayInstruction(result, elemType, dimensions));
        state.push(result);
    }

    private void translateArrayLength(AbstractState state, IRBlock block)
    {
        Value array = state.pop();
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(SimpleInstruction.createArrayLength(result, array));
        state.push(result);
    }

    private void translateAThrow(AbstractState state, IRBlock block)
    {
        Value exception = state.pop();
        block.addInstruction(SimpleInstruction.createThrow(exception));
    }

    private void translateCheckCast(CheckCastInstruction instr, AbstractState state, IRBlock block)
    {
        Value objectRef = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType targetType = IRType.fromInternalName(classRef.getClassName());
        SSAValue result = new SSAValue(targetType);
        block.addInstruction(TypeCheckInstruction.createCast(result, objectRef, targetType));
        state.push(result);
    }

    private void translateInstanceOf(InstanceOfInstruction instr, AbstractState state, IRBlock block)
    {
        Value objectRef = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType checkType = IRType.fromInternalName(classRef.getClassName());
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(TypeCheckInstruction.createInstanceOf(result, objectRef, checkType));
        state.push(result);
    }

    private void translateMonitorEnter(AbstractState state, IRBlock block)
    {
        Value objectRef = state.pop();
        block.addInstruction(SimpleInstruction.createMonitorEnter(objectRef));
    }

    private void translateMonitorExit(AbstractState state, IRBlock block)
    {
        Value objectRef = state.pop();
        block.addInstruction(SimpleInstruction.createMonitorExit(objectRef));
    }

    private IRType getArrayElementType(int opcode)
    {
        if (opcode == IALOAD.getCode()) return PrimitiveType.INT;
        if (opcode == LALOAD.getCode()) return PrimitiveType.LONG;
        if (opcode == FALOAD.getCode()) return PrimitiveType.FLOAT;
        if (opcode == DALOAD.getCode()) return PrimitiveType.DOUBLE;
        if (opcode == AALOAD.getCode()) return ReferenceType.OBJECT;
        if (opcode == BALOAD.getCode()) return PrimitiveType.BYTE;
        if (opcode == CALOAD.getCode()) return PrimitiveType.CHAR;
        if (opcode == SALOAD.getCode()) return PrimitiveType.SHORT;
        return PrimitiveType.INT;
    }

    private IRType getBinaryOpResultType(int opcode)
    {
        if (opcode >= ISHL.getCode() && opcode <= LXOR.getCode())
        {
            return (opcode % 2 == 0) ? PrimitiveType.INT : PrimitiveType.LONG;
        }
        int typeVariant = (opcode - IADD.getCode()) % 4;
        if (typeVariant == 0) return PrimitiveType.INT;
        if (typeVariant == 1) return PrimitiveType.LONG;
        if (typeVariant == 2) return PrimitiveType.FLOAT;
        if (typeVariant == 3) return PrimitiveType.DOUBLE;
        return PrimitiveType.INT;
    }

    private IRType getNegResultType(int opcode)
    {
        if (opcode == INEG.getCode()) return PrimitiveType.INT;
        if (opcode == LNEG.getCode()) return PrimitiveType.LONG;
        if (opcode == FNEG.getCode()) return PrimitiveType.FLOAT;
        if (opcode == DNEG.getCode()) return PrimitiveType.DOUBLE;
        return PrimitiveType.INT;
    }

    private IRType getNewArrayElementType(int atype)
    {
        switch (atype)
        {
            case 4: return PrimitiveType.BOOLEAN;
            case 5: return PrimitiveType.CHAR;
            case 6: return PrimitiveType.FLOAT;
            case 7: return PrimitiveType.DOUBLE;
            case 8: return PrimitiveType.BYTE;
            case 9: return PrimitiveType.SHORT;
            case 10: return PrimitiveType.INT;
            case 11: return PrimitiveType.LONG;
            default: throw new IllegalArgumentException("Unknown array type: " + atype);
        }
    }

    private int countMethodArgs(String descriptor)
    {
        int count = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')')
        {
            char c = descriptor.charAt(i);
            if (c == 'L')
            {
                i = descriptor.indexOf(';', i) + 1;
                count++;
            }
            else if (c == '[')
            {
                i++;
            }
            else
            {
                i++;
                count++;
            }
        }
        return count;
    }

    private void translateWide(Instruction instr, AbstractState state, IRBlock block)
    {
        // Handle WideIIncInstruction (wide iinc) separately from WideInstruction
        if (instr instanceof WideIIncInstruction)
        {
            WideIIncInstruction wideIInc = (WideIIncInstruction) instr;
            translateWideIInc(wideIInc, state, block);
            return;
        }

        if (!(instr instanceof WideInstruction))
        {
            throw new UnsupportedOperationException("Expected WideInstruction or WideIIncInstruction, got: " + instr.getClass().getName());
        }
        WideInstruction wide = (WideInstruction) instr;

        int varIndex = wide.getVarIndex();
        switch (wide.getModifiedOpcode())
        {
            case ILOAD: translateILoad(varIndex, state, block); break;
            case LLOAD: translateLLoad(varIndex, state, block); break;
            case FLOAD: translateFLoad(varIndex, state, block); break;
            case DLOAD: translateDLoad(varIndex, state, block); break;
            case ALOAD: translateALoad(varIndex, state, block); break;
            case ISTORE: translateIStore(varIndex, state, block); break;
            case LSTORE: translateLStore(varIndex, state, block); break;
            case FSTORE: translateFStore(varIndex, state, block); break;
            case DSTORE: translateDStore(varIndex, state, block); break;
            case ASTORE: translateAStore(varIndex, state, block); break;
            case IINC:
            {
                int increment = wide.getConstValue();
                SSAValue loaded = new SSAValue(PrimitiveType.INT);
                block.addInstruction(new LoadLocalInstruction(loaded, varIndex));
                SSAValue incConst = new SSAValue(PrimitiveType.INT);
                block.addInstruction(new ConstantInstruction(incConst, IntConstant.of(increment)));
                SSAValue result = new SSAValue(PrimitiveType.INT);
                block.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, loaded, incConst));
                state.setLocal(varIndex, result);
                block.addInstruction(new StoreLocalInstruction(varIndex, result));
                break;
            }
            default: throw new UnsupportedOperationException("Unsupported WIDE opcode: " + wide.getModifiedOpcode());
        }
    }

    private void translateWideIInc(WideIIncInstruction instr, AbstractState state, IRBlock block)
    {
        int varIndex = instr.getVarIndex();
        int increment = instr.getConstValue();

        SSAValue loaded = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new LoadLocalInstruction(loaded, varIndex));

        SSAValue incConst = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(incConst, IntConstant.of(increment)));

        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, loaded, incConst));

        state.setLocal(varIndex, result);
        block.addInstruction(new StoreLocalInstruction(varIndex, result));
    }
}

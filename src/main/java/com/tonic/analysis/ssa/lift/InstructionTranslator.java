package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.instruction.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.MethodHandle;
import lombok.Getter;

import java.util.*;

import static com.tonic.utill.Opcode.*;

/**
 * Translates JVM bytecode instructions to IR instructions.
 */
public class InstructionTranslator {

    private final ConstPool constPool;
    @Getter
    private final Map<Integer, IRBlock> offsetToBlock;

    /**
     * Creates a new instruction translator.
     *
     * @param constPool the constant pool
     */
    public InstructionTranslator(ConstPool constPool) {
        this.constPool = constPool;
        this.offsetToBlock = new HashMap<>();
    }

    /**
     * Registers a block at a bytecode offset.
     *
     * @param offset the bytecode offset
     * @param block the IR block
     */
    public void registerBlock(int offset, IRBlock block) {
        offsetToBlock.put(offset, block);
    }

    /**
     * Translates a bytecode instruction to IR.
     *
     * @param instr the bytecode instruction
     * @param state the abstract interpreter state
     * @param block the current IR block
     */
    public void translate(Instruction instr, AbstractState state, IRBlock block) {
        int opcode = instr.getOpcode();

        switch (opcode) {
            case 0x00: break;
            case 0x01: translateAConstNull(state, block); break;
            case 0x02: case 0x03: case 0x04: case 0x05: case 0x06: case 0x07: case 0x08: translateIConst(opcode - 0x03, state, block); break;
            case 0x09: case 0x0A: translateLConst(opcode - 0x09, state, block); break;
            case 0x0B: case 0x0C: case 0x0D: translateFConst(opcode - 0x0B, state, block); break;
            case 0x0E: case 0x0F: translateDConst(opcode - 0x0E, state, block); break;
            case 0x10: translateBipush((BipushInstruction) instr, state, block); break;
            case 0x11: translateSipush((SipushInstruction) instr, state, block); break;
            case 0x12: translateLdc((LdcInstruction) instr, state, block); break;
            case 0x13: translateLdcW((LdcWInstruction) instr, state, block); break;
            case 0x14: translateLdc2W((Ldc2WInstruction) instr, state, block); break;
            case 0x15: translateILoad(((ILoadInstruction) instr).getVarIndex(), state, block); break;
            case 0x16: translateLLoad(((LLoadInstruction) instr).getVarIndex(), state, block); break;
            case 0x17: translateFLoad(((FLoadInstruction) instr).getVarIndex(), state, block); break;
            case 0x18: translateDLoad(((DLoadInstruction) instr).getVarIndex(), state, block); break;
            case 0x19: translateALoad(((ALoadInstruction) instr).getVarIndex(), state, block); break;
            case 0x1A: case 0x1B: case 0x1C: case 0x1D: translateILoad(opcode - 0x1A, state, block); break;
            case 0x1E: case 0x1F: case 0x20: case 0x21: translateLLoad(opcode - 0x1E, state, block); break;
            case 0x22: case 0x23: case 0x24: case 0x25: translateFLoad(opcode - 0x22, state, block); break;
            case 0x26: case 0x27: case 0x28: case 0x29: translateDLoad(opcode - 0x26, state, block); break;
            case 0x2A: case 0x2B: case 0x2C: case 0x2D: translateALoad(opcode - 0x2A, state, block); break;
            case 0x2E: case 0x2F: case 0x30: case 0x31: case 0x32: case 0x33: case 0x34: case 0x35: translateArrayLoad(opcode, state, block); break;
            case 0x36: translateIStore(((IStoreInstruction) instr).getVarIndex(), state, block); break;
            case 0x37: translateLStore(((LStoreInstruction) instr).getVarIndex(), state, block); break;
            case 0x38: translateFStore(((FStoreInstruction) instr).getVarIndex(), state, block); break;
            case 0x39: translateDStore(((DStoreInstruction) instr).getVarIndex(), state, block); break;
            case 0x3A: translateAStore(((AStoreInstruction) instr).getVarIndex(), state, block); break;
            case 0x3B: case 0x3C: case 0x3D: case 0x3E: translateIStore(opcode - 0x3B, state, block); break;
            case 0x3F: case 0x40: case 0x41: case 0x42: translateLStore(opcode - 0x3F, state, block); break;
            case 0x43: case 0x44: case 0x45: case 0x46: translateFStore(opcode - 0x43, state, block); break;
            case 0x47: case 0x48: case 0x49: case 0x4A: translateDStore(opcode - 0x47, state, block); break;
            case 0x4B: case 0x4C: case 0x4D: case 0x4E: translateAStore(opcode - 0x4B, state, block); break;
            case 0x4F: case 0x50: case 0x51: case 0x52: case 0x53: case 0x54: case 0x55: case 0x56: translateArrayStore(opcode, state, block); break;
            case 0x57: state.pop(); break;
            case 0x58: translatePop2(state); break;
            case 0x59: translateDup(state); break;
            case 0x5A: translateDupX1(state); break;
            case 0x5B: translateDupX2(state); break;
            case 0x5C: translateDup2(state); break;
            case 0x5D: translateDup2X1(state); break;
            case 0x5E: translateDup2X2(state); break;
            case 0x5F: translateSwap(state); break;
            case 0x60: case 0x61: case 0x62: case 0x63: translateBinaryOp(BinaryOp.ADD, opcode, state, block); break;
            case 0x64: case 0x65: case 0x66: case 0x67: translateBinaryOp(BinaryOp.SUB, opcode, state, block); break;
            case 0x68: case 0x69: case 0x6A: case 0x6B: translateBinaryOp(BinaryOp.MUL, opcode, state, block); break;
            case 0x6C: case 0x6D: case 0x6E: case 0x6F: translateBinaryOp(BinaryOp.DIV, opcode, state, block); break;
            case 0x70: case 0x71: case 0x72: case 0x73: translateBinaryOp(BinaryOp.REM, opcode, state, block); break;
            case 0x74: case 0x75: case 0x76: case 0x77: translateNeg(opcode, state, block); break;
            case 0x78: case 0x79: translateBinaryOp(BinaryOp.SHL, opcode, state, block); break;
            case 0x7A: case 0x7B: translateBinaryOp(BinaryOp.SHR, opcode, state, block); break;
            case 0x7C: case 0x7D: translateBinaryOp(BinaryOp.USHR, opcode, state, block); break;
            case 0x7E: case 0x7F: translateBinaryOp(BinaryOp.AND, opcode, state, block); break;
            case 0x80: case 0x81: translateBinaryOp(BinaryOp.OR, opcode, state, block); break;
            case 0x82: case 0x83: translateBinaryOp(BinaryOp.XOR, opcode, state, block); break;
            case 0x84: translateIinc((IIncInstruction) instr, state, block); break;
            case 0x85: translateConvert(UnaryOp.I2L, state, block, PrimitiveType.LONG); break;
            case 0x86: translateConvert(UnaryOp.I2F, state, block, PrimitiveType.FLOAT); break;
            case 0x87: translateConvert(UnaryOp.I2D, state, block, PrimitiveType.DOUBLE); break;
            case 0x88: translateConvert(UnaryOp.L2I, state, block, PrimitiveType.INT); break;
            case 0x89: translateConvert(UnaryOp.L2F, state, block, PrimitiveType.FLOAT); break;
            case 0x8A: translateConvert(UnaryOp.L2D, state, block, PrimitiveType.DOUBLE); break;
            case 0x8B: translateConvert(UnaryOp.F2I, state, block, PrimitiveType.INT); break;
            case 0x8C: translateConvert(UnaryOp.F2L, state, block, PrimitiveType.LONG); break;
            case 0x8D: translateConvert(UnaryOp.F2D, state, block, PrimitiveType.DOUBLE); break;
            case 0x8E: translateConvert(UnaryOp.D2I, state, block, PrimitiveType.INT); break;
            case 0x8F: translateConvert(UnaryOp.D2L, state, block, PrimitiveType.LONG); break;
            case 0x90: translateConvert(UnaryOp.D2F, state, block, PrimitiveType.FLOAT); break;
            case 0x91: translateConvert(UnaryOp.I2B, state, block, PrimitiveType.BYTE); break;
            case 0x92: translateConvert(UnaryOp.I2C, state, block, PrimitiveType.CHAR); break;
            case 0x93: translateConvert(UnaryOp.I2S, state, block, PrimitiveType.SHORT); break;
            case 0x94: translateCmp(BinaryOp.LCMP, state, block); break;
            case 0x95: translateCmp(BinaryOp.FCMPL, state, block); break;
            case 0x96: translateCmp(BinaryOp.FCMPG, state, block); break;
            case 0x97: translateCmp(BinaryOp.DCMPL, state, block); break;
            case 0x98: translateCmp(BinaryOp.DCMPG, state, block); break;
            case 0x99: case 0x9A: case 0x9B: case 0x9C: case 0x9D: case 0x9E: translateIfZero((ConditionalBranchInstruction) instr, opcode, state, block); break;
            case 0x9F: case 0xA0: case 0xA1: case 0xA2: case 0xA3: case 0xA4: translateIfICmp((ConditionalBranchInstruction) instr, opcode, state, block); break;
            case 0xA5: case 0xA6: translateIfACmp((ConditionalBranchInstruction) instr, opcode, state, block); break;
            case 0xA7: case 0xC8: translateGoto((com.tonic.analysis.instruction.GotoInstruction) instr, state, block); break;
            case 0xA8: case 0xC9: translateJsr((JsrInstruction) instr, state, block); break;
            case 0xA9: translateRet((RetInstruction) instr, state, block); break;
            case 0xAA: translateTableSwitch((TableSwitchInstruction) instr, state, block); break;
            case 0xAB: translateLookupSwitch((LookupSwitchInstruction) instr, state, block); break;
            case 0xAC: case 0xAD: case 0xAE: case 0xAF: case 0xB0: translateReturn(state, block); break;
            case 0xB1: translateVoidReturn(block); break;
            case 0xB2: translateGetStatic((com.tonic.analysis.instruction.GetFieldInstruction) instr, state, block); break;
            case 0xB3: translatePutStatic((com.tonic.analysis.instruction.PutFieldInstruction) instr, state, block); break;
            case 0xB4: translateGetField((com.tonic.analysis.instruction.GetFieldInstruction) instr, state, block); break;
            case 0xB5: translatePutField((com.tonic.analysis.instruction.PutFieldInstruction) instr, state, block); break;
            case 0xB6: translateInvokeVirtual((InvokeVirtualInstruction) instr, state, block); break;
            case 0xB7: translateInvokeSpecial((InvokeSpecialInstruction) instr, state, block); break;
            case 0xB8: translateInvokeStatic((InvokeStaticInstruction) instr, state, block); break;
            case 0xB9: translateInvokeInterface((InvokeInterfaceInstruction) instr, state, block); break;
            case 0xBA: translateInvokeDynamic((InvokeDynamicInstruction) instr, state, block); break;
            case 0xBB: translateNew((com.tonic.analysis.instruction.NewInstruction) instr, state, block); break;
            case 0xBC: translateNewArray((com.tonic.analysis.instruction.NewArrayInstruction) instr, state, block); break;
            case 0xBD: translateANewArray((ANewArrayInstruction) instr, state, block); break;
            case 0xBE: translateArrayLength(state, block); break;
            case 0xBF: translateAThrow(state, block); break;
            case 0xC0: translateCheckCast((CheckCastInstruction) instr, state, block); break;
            case 0xC1: translateInstanceOf((com.tonic.analysis.instruction.InstanceOfInstruction) instr, state, block); break;
            case 0xC2: translateMonitorEnter(state, block); break;
            case 0xC3: translateMonitorExit(state, block); break;
            case 0xC4: translateWide(instr, state, block); break;
            case 0xC5: translateMultiANewArray((MultiANewArrayInstruction) instr, state, block); break;
            case 0xC6: translateIfNull((ConditionalBranchInstruction) instr, state, block); break;
            case 0xC7: translateIfNonNull((ConditionalBranchInstruction) instr, state, block); break;
            default: throw new UnsupportedOperationException("Unsupported opcode: 0x" + Integer.toHexString(opcode));
        }
    }

    private void translateAConstNull(AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(ReferenceType.OBJECT);
        block.addInstruction(new ConstantInstruction(result, NullConstant.INSTANCE));
        state.push(result);
    }

    private void translateIConst(int value, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(result, IntConstant.of(value)));
        state.push(result);
    }

    private void translateLConst(int value, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.LONG);
        block.addInstruction(new ConstantInstruction(result, LongConstant.of(value)));
        state.push(result);
    }

    private void translateFConst(int value, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.FLOAT);
        block.addInstruction(new ConstantInstruction(result, FloatConstant.of(value)));
        state.push(result);
    }

    private void translateDConst(int value, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
        block.addInstruction(new ConstantInstruction(result, DoubleConstant.of(value)));
        state.push(result);
    }

    private void translateBipush(BipushInstruction instr, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(result, IntConstant.of(instr.getValue())));
        state.push(result);
    }

    private void translateSipush(SipushInstruction instr, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(result, IntConstant.of(instr.getValue())));
        state.push(result);
    }

    private void translateLdc(LdcInstruction instr, AbstractState state, IRBlock block) {
        int cpIndex = instr.getCpIndex();
        Item<?> item = constPool.getItem(cpIndex);
        Constant constant = itemToConstant(item, cpIndex);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private void translateLdcW(LdcWInstruction instr, AbstractState state, IRBlock block) {
        int cpIndex = instr.getCpIndex();
        Item<?> item = constPool.getItem(cpIndex);
        Constant constant = itemToConstant(item, cpIndex);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private void translateLdc2W(Ldc2WInstruction instr, AbstractState state, IRBlock block) {
        int cpIndex = instr.getCpIndex();
        Item<?> item = constPool.getItem(cpIndex);
        Constant constant = itemToConstant(item, cpIndex);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private Constant itemToConstant(Item<?> item) {
        return itemToConstant(item, 0);
    }

    private Constant itemToConstant(Item<?> item, int cpIndex) {
        if (item instanceof IntegerItem) {
            IntegerItem intItem = (IntegerItem) item;
            return IntConstant.of(intItem.getValue());
        } else if (item instanceof LongItem) {
            LongItem longItem = (LongItem) item;
            return LongConstant.of(longItem.getValue());
        } else if (item instanceof FloatItem) {
            FloatItem floatItem = (FloatItem) item;
            return FloatConstant.of(floatItem.getValue());
        } else if (item instanceof DoubleItem) {
            DoubleItem doubleItem = (DoubleItem) item;
            return DoubleConstant.of(doubleItem.getValue());
        } else if (item instanceof StringRefItem) {
            StringRefItem stringItem = (StringRefItem) item;
            Utf8Item utf8 = (Utf8Item) constPool.getItem(stringItem.getValue());
            return new StringConstant(utf8.getValue());
        } else if (item instanceof ClassRefItem) {
            ClassRefItem classItem = (ClassRefItem) item;
            return new ClassConstant(classItem.getClassName());
        } else if (item instanceof MethodHandleItem) {
            MethodHandleItem mhItem = (MethodHandleItem) item;
            return resolveMethodHandle(mhItem);
        } else if (item instanceof MethodTypeItem) {
            MethodTypeItem mtItem = (MethodTypeItem) item;
            int descIndex = mtItem.getValue();
            Utf8Item utf8 = (Utf8Item) constPool.getItem(descIndex);
            return new MethodTypeConstant(utf8.getValue());
        } else if (item instanceof ConstantDynamicItem) {
            ConstantDynamicItem cdItem = (ConstantDynamicItem) item;
            return resolveDynamicConstant(cdItem, cpIndex);
        }
        throw new UnsupportedOperationException("Unsupported constant type: " + item.getClass());
    }

    /**
     * Resolves a MethodHandle constant pool item to an IR MethodHandleConstant.
     */
    private MethodHandleConstant resolveMethodHandle(MethodHandleItem mhItem) {
        MethodHandle mh = mhItem.getValue();
        int refKind = mh.getReferenceKind();
        int refIndex = mh.getReferenceIndex();
        Item<?> refItem = constPool.getItem(refIndex);

        String owner;
        String name;
        String desc;

        // The reference index points to different item types based on reference kind
        if (refItem instanceof FieldRefItem) {
            FieldRefItem fieldRef = (FieldRefItem) refItem;
            owner = fieldRef.getOwner();
            name = fieldRef.getName();
            desc = fieldRef.getDescriptor();
        } else if (refItem instanceof MethodRefItem) {
            MethodRefItem methodRef = (MethodRefItem) refItem;
            owner = methodRef.getOwner();
            name = methodRef.getName();
            desc = methodRef.getDescriptor();
        } else if (refItem instanceof InterfaceRefItem) {
            InterfaceRefItem interfaceRef = (InterfaceRefItem) refItem;
            owner = interfaceRef.getOwner();
            name = interfaceRef.getName();
            desc = interfaceRef.getDescriptor();
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported MethodHandle reference type: " + refItem.getClass());
        }

        return new MethodHandleConstant(refKind, owner, name, desc);
    }

    /**
     * Resolves a ConstantDynamic constant pool item to an IR DynamicConstant.
     */
    private DynamicConstant resolveDynamicConstant(ConstantDynamicItem cdItem, int cpIndex) {
        String name = cdItem.getName();
        String desc = cdItem.getDescriptor();
        int bsmIndex = cdItem.getBootstrapMethodAttrIndex();
        return new DynamicConstant(name, desc, bsmIndex, cpIndex);
    }

    private void translateILoad(int index, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateLLoad(int index, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.LONG);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateFLoad(int index, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.FLOAT);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateDLoad(int index, AbstractState state, IRBlock block) {
        SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateALoad(int index, AbstractState state, IRBlock block) {
        Value local = state.getLocal(index);
        IRType type = (local != null && local.getType() instanceof ReferenceType)
                      ? local.getType() : ReferenceType.OBJECT;
        SSAValue result = new SSAValue(type);
        block.addInstruction(new LoadLocalInstruction(result, index));
        state.push(result);
    }

    private void translateArrayLoad(int opcode, AbstractState state, IRBlock block) {
        Value index = state.pop();
        Value array = state.pop();
        IRType elemType = getArrayElementType(opcode);
        SSAValue result = new SSAValue(elemType);
        block.addInstruction(ArrayAccessInstruction.createLoad(result, array, index));
        state.push(result);
    }

    private void translateIStore(int index, AbstractState state, IRBlock block) {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateLStore(int index, AbstractState state, IRBlock block) {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateFStore(int index, AbstractState state, IRBlock block) {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateDStore(int index, AbstractState state, IRBlock block) {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateAStore(int index, AbstractState state, IRBlock block) {
        Value value = state.pop();
        state.setLocal(index, value);
        block.addInstruction(new StoreLocalInstruction(index, value));
    }

    private void translateArrayStore(int opcode, AbstractState state, IRBlock block) {
        Value value = state.pop();
        Value index = state.pop();
        Value array = state.pop();
        block.addInstruction(ArrayAccessInstruction.createStore(array, index, value));
    }

    /**
     * Translates the pop2 instruction.
     * pop2 pops either:
     * - Two category-1 values (int, float, reference), OR
     * - One category-2 value (long, double)
     *
     * In our SSA representation, longs and doubles are single Values with
     * a type marker, so we check the type to determine how many pops to do.
     */
    private void translatePop2(AbstractState state) {
        Value top = state.peek();
        if (top.getType() != null && top.getType().isTwoSlot()) {
            // Category-2 value (long/double) - single pop
            state.pop();
        } else {
            // Two category-1 values - double pop
            state.pop();
            state.pop();
        }
    }

    private void translateDup(AbstractState state) {
        Value top = state.peek();
        state.push(top);
    }

    private void translateDupX1(AbstractState state) {
        Value v1 = state.pop();
        Value v2 = state.pop();
        state.push(v1);
        state.push(v2);
        state.push(v1);
    }

    private void translateDupX2(AbstractState state) {
        Value v1 = state.pop();
        Value v2 = state.pop();
        Value v3 = state.pop();
        state.push(v1);
        state.push(v3);
        state.push(v2);
        state.push(v1);
    }

    /**
     * dup2 - duplicate top one or two values:
     * Form 1 (category-2): ..., value → ..., value, value
     * Form 2 (two category-1): ..., value2, value1 → ..., value2, value1, value2, value1
     */
    private void translateDup2(AbstractState state) {
        Value v1 = state.peek();
        if (v1.getType() != null && v1.getType().isTwoSlot()) {
            // Form 1: single category-2 value
            state.push(v1);
        } else {
            // Form 2: two category-1 values
            v1 = state.pop();
            Value v2 = state.peek();
            state.push(v1);
            state.push(v2);
            state.push(v1);
        }
    }

    /**
     * dup2_x1 - duplicate top one or two values and insert beneath:
     * Form 1 (category-2): ..., value2, value1 → ..., value1, value2, value1
     * Form 2 (two category-1): ..., value3, value2, value1 → ..., value2, value1, value3, value2, value1
     */
    private void translateDup2X1(AbstractState state) {
        Value v1 = state.peek();
        if (v1.getType() != null && v1.getType().isTwoSlot()) {
            // Form 1: category-2 value over category-1 value
            v1 = state.pop();
            Value v2 = state.pop();
            state.push(v1);
            state.push(v2);
            state.push(v1);
        } else {
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
     * dup2_x2 - duplicate top one or two values and insert beneath:
     * Form 1: category-2 over category-2
     * Form 2: two category-1 over category-2
     * Form 3: category-2 over two category-1
     * Form 4: two category-1 over two category-1
     */
    private void translateDup2X2(AbstractState state) {
        Value v1 = state.peek();
        boolean v1TwoSlot = v1.getType() != null && v1.getType().isTwoSlot();

        if (v1TwoSlot) {
            // Forms 1 or 3: category-2 on top
            v1 = state.pop();
            Value v2 = state.peek();
            boolean v2TwoSlot = v2.getType() != null && v2.getType().isTwoSlot();

            if (v2TwoSlot) {
                // Form 1: category-2 over category-2
                state.push(v1);
                state.push(v2);
                state.push(v1);
            } else {
                // Form 3: category-2 over two category-1
                v2 = state.pop();
                Value v3 = state.pop();
                state.push(v1);
                state.push(v3);
                state.push(v2);
                state.push(v1);
            }
        } else {
            // Forms 2 or 4: two category-1 on top
            v1 = state.pop();
            Value v2 = state.pop();
            Value v3 = state.peek();
            boolean v3TwoSlot = v3.getType() != null && v3.getType().isTwoSlot();

            if (v3TwoSlot) {
                // Form 2: two category-1 over category-2
                state.push(v2);
                state.push(v1);
                state.push(v3);
                state.push(v2);
                state.push(v1);
            } else {
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

    private void translateSwap(AbstractState state) {
        Value v1 = state.pop();
        Value v2 = state.pop();
        state.push(v1);
        state.push(v2);
    }

    private void translateBinaryOp(BinaryOp op, int opcode, AbstractState state, IRBlock block) {
        Value right = state.pop();
        Value left = state.pop();
        IRType resultType = getBinaryOpResultType(opcode);
        SSAValue result = new SSAValue(resultType);
        block.addInstruction(new BinaryOpInstruction(result, op, left, right));
        state.push(result);
    }

    private void translateNeg(int opcode, AbstractState state, IRBlock block) {
        Value operand = state.pop();
        IRType resultType = getNegResultType(opcode);
        SSAValue result = new SSAValue(resultType);
        block.addInstruction(new UnaryOpInstruction(result, UnaryOp.NEG, operand));
        state.push(result);
    }

    private void translateIinc(IIncInstruction instr, AbstractState state, IRBlock block) {
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

    private void translateConvert(UnaryOp op, AbstractState state, IRBlock block, IRType resultType) {
        Value operand = state.pop();
        SSAValue result = new SSAValue(resultType);
        block.addInstruction(new UnaryOpInstruction(result, op, operand));
        state.push(result);
    }

    private void translateCmp(BinaryOp op, AbstractState state, IRBlock block) {
        Value right = state.pop();
        Value left = state.pop();
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new BinaryOpInstruction(result, op, left, right));
        state.push(result);
    }

    private void translateIfZero(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block) {
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

    private void translateIfICmp(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block) {
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

    private void translateIfACmp(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block) {
        Value right = state.pop();
        Value left = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        CompareOp cmpOp = opcode == IF_ACMPEQ.getCode() ? CompareOp.ACMPEQ : CompareOp.ACMPNE;

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(cmpOp, left, right, trueBlock, falseBlock));
    }

    private void translateIfNull(ConditionalBranchInstruction instr, AbstractState state, IRBlock block) {
        Value operand = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(CompareOp.IFNULL, operand, trueBlock, falseBlock));
    }

    private void translateIfNonNull(ConditionalBranchInstruction instr, AbstractState state, IRBlock block) {
        Value operand = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(CompareOp.IFNONNULL, operand, trueBlock, falseBlock));
    }

    private void translateGoto(com.tonic.analysis.instruction.GotoInstruction instr, AbstractState state, IRBlock block) {
        int target = instr.getOffset() + instr.getBranchOffset();
        IRBlock targetBlock = offsetToBlock.get(target);
        block.addInstruction(SimpleInstruction.createGoto(targetBlock));
    }

    /**
     * Tracks JSR call sites: maps subroutine entry offset to list of continuation blocks.
     * This is needed because RET returns to the continuation of the calling JSR.
     */
    private final Map<Integer, List<IRBlock>> jsrContinuations = new HashMap<>();

    /**
     * Translates JSR/JSR_W instructions.
     * JSR pushes the return address (continuation offset) and jumps to the subroutine.
     * We convert this to a GOTO to the subroutine and track the continuation for RET.
     */
    private void translateJsr(JsrInstruction instr, AbstractState state, IRBlock block) {
        int jsrOffset = instr.getOffset();
        int subroutineEntry = jsrOffset + instr.getBranchOffset();
        int continuationOffset = jsrOffset + instr.getLength();

        // Get target blocks
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

        // Jump to the subroutine
        block.addInstruction(SimpleInstruction.createGoto(subroutineBlock));
    }

    /**
     * Translates RET instruction.
     * RET returns from a subroutine to the address stored in a local variable.
     * Since we track JSR continuations, we convert RET to GOTO the continuation.
     */
    private void translateRet(RetInstruction instr, AbstractState state, IRBlock block) {
        // Find all possible continuations for this subroutine
        // In the simple case (single JSR to this subroutine), there's exactly one continuation
        // For multiple JSRs, we need to handle all possible return targets

        // Collect all continuation blocks from all JSR call sites
        List<IRBlock> allContinuations = new ArrayList<>();
        for (List<IRBlock> continuations : jsrContinuations.values()) {
            allContinuations.addAll(continuations);
        }

        if (allContinuations.isEmpty()) {
            // No JSR was found - this shouldn't happen in valid bytecode
            throw new IllegalStateException("RET instruction without corresponding JSR");
        }

        if (allContinuations.size() == 1) {
            // Single continuation - simple case, just GOTO
            IRBlock continuation = allContinuations.get(0);
            block.addInstruction(SimpleInstruction.createGoto(continuation));
        } else {
            // Multiple possible continuations
            // For now, we handle this by jumping to the first one
            // A more sophisticated approach would use a switch based on the return address
            // but that requires runtime dispatch which SSA doesn't support directly
            IRBlock firstContinuation = allContinuations.get(0);
            block.addInstruction(SimpleInstruction.createGoto(firstContinuation));
        }
    }

    private void translateTableSwitch(TableSwitchInstruction instr, AbstractState state, IRBlock block) {
        Value key = state.pop();
        int defaultTarget = instr.getOffset() + instr.getDefaultOffset();
        IRBlock defaultBlock = offsetToBlock.get(defaultTarget);

        SwitchInstruction switchInstr = new SwitchInstruction(key, defaultBlock);
        for (Map.Entry<Integer, Integer> entry : instr.getJumpOffsets().entrySet()) {
            int caseTarget = instr.getOffset() + entry.getValue();
            IRBlock caseBlock = offsetToBlock.get(caseTarget);
            switchInstr.addCase(entry.getKey(), caseBlock);
        }
        block.addInstruction(switchInstr);
    }

    private void translateLookupSwitch(LookupSwitchInstruction instr, AbstractState state, IRBlock block) {
        Value key = state.pop();
        int defaultTarget = instr.getOffset() + instr.getDefaultOffset();
        IRBlock defaultBlock = offsetToBlock.get(defaultTarget);

        SwitchInstruction switchInstr = new SwitchInstruction(key, defaultBlock);
        for (Map.Entry<Integer, Integer> entry : instr.getMatchOffsets().entrySet()) {
            int caseTarget = instr.getOffset() + entry.getValue();
            IRBlock caseBlock = offsetToBlock.get(caseTarget);
            switchInstr.addCase(entry.getKey(), caseBlock);
        }
        block.addInstruction(switchInstr);
    }

    private void translateReturn(AbstractState state, IRBlock block) {
        Value returnValue = state.pop();
        block.addInstruction(new com.tonic.analysis.ssa.ir.ReturnInstruction(returnValue));
    }

    private void translateVoidReturn(IRBlock block) {
        block.addInstruction(new com.tonic.analysis.ssa.ir.ReturnInstruction());
    }

    private void translateGetStatic(com.tonic.analysis.instruction.GetFieldInstruction instr, AbstractState state, IRBlock block) {
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        String owner = fieldRef.getOwner();
        String name = fieldRef.getName();
        String desc = fieldRef.getDescriptor();
        IRType fieldType = IRType.fromDescriptor(desc);
        SSAValue result = new SSAValue(fieldType);
        block.addInstruction(FieldAccessInstruction.createStaticLoad(result, owner, name, desc));
        state.push(result);
    }

    private void translatePutStatic(com.tonic.analysis.instruction.PutFieldInstruction instr, AbstractState state, IRBlock block) {
        Value value = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        block.addInstruction(FieldAccessInstruction.createStaticStore(
                fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), value));
    }

    private void translateGetField(com.tonic.analysis.instruction.GetFieldInstruction instr, AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        IRType fieldType = IRType.fromDescriptor(fieldRef.getDescriptor());
        SSAValue result = new SSAValue(fieldType);
        block.addInstruction(FieldAccessInstruction.createLoad(
                result, fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), objectRef));
        state.push(result);
    }

    private void translatePutField(com.tonic.analysis.instruction.PutFieldInstruction instr, AbstractState state, IRBlock block) {
        Value value = state.pop();
        Value objectRef = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        block.addInstruction(FieldAccessInstruction.createStore(
                fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), objectRef, value));
    }

    private void translateInvokeVirtual(InvokeVirtualInstruction instr, AbstractState state, IRBlock block) {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokevirtual");
        translateInvoke(InvokeType.VIRTUAL, ref[0], ref[1], ref[2], false, state, block);
    }

    private void translateInvokeSpecial(InvokeSpecialInstruction instr, AbstractState state, IRBlock block) {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokespecial");
        translateInvoke(InvokeType.SPECIAL, ref[0], ref[1], ref[2], false, state, block);
    }

    private void translateInvokeStatic(InvokeStaticInstruction instr, AbstractState state, IRBlock block) {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokestatic");
        translateInvoke(InvokeType.STATIC, ref[0], ref[1], ref[2], true, state, block);
    }

    private void translateInvokeInterface(InvokeInterfaceInstruction instr, AbstractState state, IRBlock block) {
        String[] ref = getMethodRefInfo(instr.getMethodIndex(), "invokeinterface");
        translateInvoke(InvokeType.INTERFACE, ref[0], ref[1], ref[2], false, state, block);
    }

    /**
     * Extracts method reference info from either MethodRefItem or InterfaceRefItem.
     * Since Java 8, invokestatic and invokespecial can reference InterfaceMethodRef.
     * @return array of [owner, name, descriptor]
     */
    private String[] getMethodRefInfo(int cpIndex, String opcode) {
        Item<?> refItem = constPool.getItem(cpIndex);
        if (refItem instanceof MethodRefItem) {
            MethodRefItem methodRef = (MethodRefItem) refItem;
            return new String[] { methodRef.getOwner(), methodRef.getName(), methodRef.getDescriptor() };
        } else if (refItem instanceof InterfaceRefItem) {
            InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
            return new String[] { ifaceRef.getOwner(), ifaceRef.getName(), ifaceRef.getDescriptor() };
        }
        throw new IllegalStateException("Unexpected ref type for " + opcode + ": " + refItem.getClass());
    }

    private void translateInvokeDynamic(InvokeDynamicInstruction instr, AbstractState state, IRBlock block) {
        int cpIndex = instr.getCpIndex();
        InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(cpIndex);
        String desc = item.getDescriptor();
        String name = item.getName();
        int argCount = countMethodArgs(desc);

        List<Value> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++) {
            args.add(0, state.pop());
        }

        String returnDesc = desc.substring(desc.indexOf(')') + 1);
        if (returnDesc.equals("V")) {
            block.addInstruction(new InvokeInstruction(InvokeType.DYNAMIC, "", name, desc, args, cpIndex));
        } else {
            IRType returnType = IRType.fromDescriptor(returnDesc);
            SSAValue result = new SSAValue(returnType);
            block.addInstruction(new InvokeInstruction(result, InvokeType.DYNAMIC, "", name, desc, args, cpIndex));
            state.push(result);
        }
    }

    private void translateInvoke(InvokeType invokeType, String owner, String name, String desc,
                                  boolean isStatic, AbstractState state, IRBlock block) {
        int argCount = countMethodArgs(desc);
        if (!isStatic) {
            argCount++;
        }

        List<Value> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++) {
            args.add(0, state.pop());
        }

        String returnDesc = desc.substring(desc.indexOf(')') + 1);
        if (returnDesc.equals("V")) {
            block.addInstruction(new InvokeInstruction(invokeType, owner, name, desc, args));
        } else {
            IRType returnType = IRType.fromDescriptor(returnDesc);
            SSAValue result = new SSAValue(returnType);
            block.addInstruction(new InvokeInstruction(result, invokeType, owner, name, desc, args));
            state.push(result);
        }
    }

    private void translateNew(com.tonic.analysis.instruction.NewInstruction instr, AbstractState state, IRBlock block) {
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        String className = classRef.getClassName();
        SSAValue result = new SSAValue(new ReferenceType(className));
        block.addInstruction(new com.tonic.analysis.ssa.ir.NewInstruction(result, className));
        state.push(result);
    }

    private void translateNewArray(com.tonic.analysis.instruction.NewArrayInstruction instr, AbstractState state, IRBlock block) {
        Value length = state.pop();
        IRType elemType = getNewArrayElementType(instr.getArrayType().getCode());
        SSAValue result = new SSAValue(new ArrayType(elemType));
        block.addInstruction(new com.tonic.analysis.ssa.ir.NewArrayInstruction(result, elemType, length));
        state.push(result);
    }

    private void translateANewArray(ANewArrayInstruction instr, AbstractState state, IRBlock block) {
        Value length = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType elemType = new ReferenceType(classRef.getClassName());
        SSAValue result = new SSAValue(new ArrayType(elemType));
        block.addInstruction(new com.tonic.analysis.ssa.ir.NewArrayInstruction(result, elemType, length));
        state.push(result);
    }

    private void translateMultiANewArray(MultiANewArrayInstruction instr, AbstractState state, IRBlock block) {
        int dims = instr.getDimensions();
        List<Value> dimensions = new ArrayList<>();
        for (int i = 0; i < dims; i++) {
            dimensions.add(0, state.pop());
        }

        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType arrayType = IRType.fromDescriptor(classRef.getClassName());
        SSAValue result = new SSAValue(arrayType);
        IRType elemType = ((ArrayType) arrayType).getElementType();
        block.addInstruction(new com.tonic.analysis.ssa.ir.NewArrayInstruction(result, elemType, dimensions));
        state.push(result);
    }

    private void translateArrayLength(AbstractState state, IRBlock block) {
        Value array = state.pop();
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(SimpleInstruction.createArrayLength(result, array));
        state.push(result);
    }

    private void translateAThrow(AbstractState state, IRBlock block) {
        Value exception = state.pop();
        block.addInstruction(SimpleInstruction.createThrow(exception));
    }

    private void translateCheckCast(CheckCastInstruction instr, AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType targetType = IRType.fromInternalName(classRef.getClassName());
        SSAValue result = new SSAValue(targetType);
        block.addInstruction(TypeCheckInstruction.createCast(result, objectRef, targetType));
        state.push(result);
    }

    private void translateInstanceOf(com.tonic.analysis.instruction.InstanceOfInstruction instr, AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType checkType = IRType.fromInternalName(classRef.getClassName());
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(TypeCheckInstruction.createInstanceOf(result, objectRef, checkType));
        state.push(result);
    }

    private void translateMonitorEnter(AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        block.addInstruction(SimpleInstruction.createMonitorEnter(objectRef));
    }

    private void translateMonitorExit(AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        block.addInstruction(SimpleInstruction.createMonitorExit(objectRef));
    }

    private IRType getArrayElementType(int opcode) {
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

    private IRType getBinaryOpResultType(int opcode) {
        if (opcode >= ISHL.getCode() && opcode <= LXOR.getCode()) {
            return (opcode % 2 == 0) ? PrimitiveType.INT : PrimitiveType.LONG;
        }
        int typeVariant = (opcode - IADD.getCode()) % 4;
        if (typeVariant == 0) return PrimitiveType.INT;
        if (typeVariant == 1) return PrimitiveType.LONG;
        if (typeVariant == 2) return PrimitiveType.FLOAT;
        if (typeVariant == 3) return PrimitiveType.DOUBLE;
        return PrimitiveType.INT;
    }

    private IRType getNegResultType(int opcode) {
        if (opcode == INEG.getCode()) return PrimitiveType.INT;
        if (opcode == LNEG.getCode()) return PrimitiveType.LONG;
        if (opcode == FNEG.getCode()) return PrimitiveType.FLOAT;
        if (opcode == DNEG.getCode()) return PrimitiveType.DOUBLE;
        return PrimitiveType.INT;
    }

    private IRType getNewArrayElementType(int atype) {
        switch (atype) {
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

    private int countMethodArgs(String descriptor) {
        int count = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                i = descriptor.indexOf(';', i) + 1;
                count++;
            } else if (c == '[') {
                i++;
            } else {
                i++;
                count++;
            }
        }
        return count;
    }

    private void translateWide(Instruction instr, AbstractState state, IRBlock block) {
        // Handle WideIIncInstruction (wide iinc) separately from WideInstruction
        if (instr instanceof WideIIncInstruction) {
            WideIIncInstruction wideIInc = (WideIIncInstruction) instr;
            translateWideIInc(wideIInc, state, block);
            return;
        }

        if (!(instr instanceof WideInstruction)) {
            throw new UnsupportedOperationException("Expected WideInstruction or WideIIncInstruction, got: " + instr.getClass().getName());
        }
        WideInstruction wide = (WideInstruction) instr;

        int varIndex = wide.getVarIndex();
        switch (wide.getModifiedOpcode()) {
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
            case IINC: {
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

    private void translateWideIInc(WideIIncInstruction instr, AbstractState state, IRBlock block) {
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

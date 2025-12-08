package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.instruction.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import lombok.Getter;

import java.util.*;

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
            case 0x00 -> {}
            case 0x01 -> translateAConstNull(state, block);
            case 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> translateIConst(opcode - 0x03, state, block);
            case 0x09, 0x0A -> translateLConst(opcode - 0x09, state, block);
            case 0x0B, 0x0C, 0x0D -> translateFConst(opcode - 0x0B, state, block);
            case 0x0E, 0x0F -> translateDConst(opcode - 0x0E, state, block);
            case 0x10 -> translateBipush((BipushInstruction) instr, state, block);
            case 0x11 -> translateSipush((SipushInstruction) instr, state, block);
            case 0x12 -> translateLdc((LdcInstruction) instr, state, block);
            case 0x13 -> translateLdcW((LdcWInstruction) instr, state, block);
            case 0x14 -> translateLdc2W((Ldc2WInstruction) instr, state, block);
            case 0x15 -> translateILoad(((ILoadInstruction) instr).getVarIndex(), state, block);
            case 0x16 -> translateLLoad(((LLoadInstruction) instr).getVarIndex(), state, block);
            case 0x17 -> translateFLoad(((FLoadInstruction) instr).getVarIndex(), state, block);
            case 0x18 -> translateDLoad(((DLoadInstruction) instr).getVarIndex(), state, block);
            case 0x19 -> translateALoad(((ALoadInstruction) instr).getVarIndex(), state, block);
            case 0x1A, 0x1B, 0x1C, 0x1D -> translateILoad(opcode - 0x1A, state, block);
            case 0x1E, 0x1F, 0x20, 0x21 -> translateLLoad(opcode - 0x1E, state, block);
            case 0x22, 0x23, 0x24, 0x25 -> translateFLoad(opcode - 0x22, state, block);
            case 0x26, 0x27, 0x28, 0x29 -> translateDLoad(opcode - 0x26, state, block);
            case 0x2A, 0x2B, 0x2C, 0x2D -> translateALoad(opcode - 0x2A, state, block);
            case 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35 -> translateArrayLoad(opcode, state, block);
            case 0x36 -> translateIStore(((IStoreInstruction) instr).getVarIndex(), state, block);
            case 0x37 -> translateLStore(((LStoreInstruction) instr).getVarIndex(), state, block);
            case 0x38 -> translateFStore(((FStoreInstruction) instr).getVarIndex(), state, block);
            case 0x39 -> translateDStore(((DStoreInstruction) instr).getVarIndex(), state, block);
            case 0x3A -> translateAStore(((AStoreInstruction) instr).getVarIndex(), state, block);
            case 0x3B, 0x3C, 0x3D, 0x3E -> translateIStore(opcode - 0x3B, state, block);
            case 0x3F, 0x40, 0x41, 0x42 -> translateLStore(opcode - 0x3F, state, block);
            case 0x43, 0x44, 0x45, 0x46 -> translateFStore(opcode - 0x43, state, block);
            case 0x47, 0x48, 0x49, 0x4A -> translateDStore(opcode - 0x47, state, block);
            case 0x4B, 0x4C, 0x4D, 0x4E -> translateAStore(opcode - 0x4B, state, block);
            case 0x4F, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56 -> translateArrayStore(opcode, state, block);
            case 0x57 -> state.pop();
            case 0x58 -> { state.pop(); state.pop(); }
            case 0x59 -> translateDup(state);
            case 0x5A -> translateDupX1(state);
            case 0x5B -> translateDupX2(state);
            case 0x5C -> translateDup2(state);
            case 0x5D -> translateDup2X1(state);
            case 0x5E -> translateDup2X2(state);
            case 0x5F -> translateSwap(state);
            case 0x60, 0x61, 0x62, 0x63 -> translateBinaryOp(BinaryOp.ADD, opcode, state, block);
            case 0x64, 0x65, 0x66, 0x67 -> translateBinaryOp(BinaryOp.SUB, opcode, state, block);
            case 0x68, 0x69, 0x6A, 0x6B -> translateBinaryOp(BinaryOp.MUL, opcode, state, block);
            case 0x6C, 0x6D, 0x6E, 0x6F -> translateBinaryOp(BinaryOp.DIV, opcode, state, block);
            case 0x70, 0x71, 0x72, 0x73 -> translateBinaryOp(BinaryOp.REM, opcode, state, block);
            case 0x74, 0x75, 0x76, 0x77 -> translateNeg(opcode, state, block);
            case 0x78, 0x79 -> translateBinaryOp(BinaryOp.SHL, opcode, state, block);
            case 0x7A, 0x7B -> translateBinaryOp(BinaryOp.SHR, opcode, state, block);
            case 0x7C, 0x7D -> translateBinaryOp(BinaryOp.USHR, opcode, state, block);
            case 0x7E, 0x7F -> translateBinaryOp(BinaryOp.AND, opcode, state, block);
            case 0x80, 0x81 -> translateBinaryOp(BinaryOp.OR, opcode, state, block);
            case 0x82, 0x83 -> translateBinaryOp(BinaryOp.XOR, opcode, state, block);
            case 0x84 -> translateIinc((IIncInstruction) instr, state, block);
            case 0x85 -> translateConvert(UnaryOp.I2L, state, block, PrimitiveType.LONG);
            case 0x86 -> translateConvert(UnaryOp.I2F, state, block, PrimitiveType.FLOAT);
            case 0x87 -> translateConvert(UnaryOp.I2D, state, block, PrimitiveType.DOUBLE);
            case 0x88 -> translateConvert(UnaryOp.L2I, state, block, PrimitiveType.INT);
            case 0x89 -> translateConvert(UnaryOp.L2F, state, block, PrimitiveType.FLOAT);
            case 0x8A -> translateConvert(UnaryOp.L2D, state, block, PrimitiveType.DOUBLE);
            case 0x8B -> translateConvert(UnaryOp.F2I, state, block, PrimitiveType.INT);
            case 0x8C -> translateConvert(UnaryOp.F2L, state, block, PrimitiveType.LONG);
            case 0x8D -> translateConvert(UnaryOp.F2D, state, block, PrimitiveType.DOUBLE);
            case 0x8E -> translateConvert(UnaryOp.D2I, state, block, PrimitiveType.INT);
            case 0x8F -> translateConvert(UnaryOp.D2L, state, block, PrimitiveType.LONG);
            case 0x90 -> translateConvert(UnaryOp.D2F, state, block, PrimitiveType.FLOAT);
            case 0x91 -> translateConvert(UnaryOp.I2B, state, block, PrimitiveType.BYTE);
            case 0x92 -> translateConvert(UnaryOp.I2C, state, block, PrimitiveType.CHAR);
            case 0x93 -> translateConvert(UnaryOp.I2S, state, block, PrimitiveType.SHORT);
            case 0x94 -> translateCmp(BinaryOp.LCMP, state, block);
            case 0x95 -> translateCmp(BinaryOp.FCMPL, state, block);
            case 0x96 -> translateCmp(BinaryOp.FCMPG, state, block);
            case 0x97 -> translateCmp(BinaryOp.DCMPL, state, block);
            case 0x98 -> translateCmp(BinaryOp.DCMPG, state, block);
            case 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E -> translateIfZero((ConditionalBranchInstruction) instr, opcode, state, block);
            case 0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4 -> translateIfICmp((ConditionalBranchInstruction) instr, opcode, state, block);
            case 0xA5, 0xA6 -> translateIfACmp((ConditionalBranchInstruction) instr, opcode, state, block);
            case 0xA7, 0xC8 -> translateGoto((com.tonic.analysis.instruction.GotoInstruction) instr, state, block);
            case 0xA8, 0xC9 -> translateJsr((JsrInstruction) instr, state, block);
            case 0xA9 -> translateRet((RetInstruction) instr, state, block);
            case 0xAA -> translateTableSwitch((TableSwitchInstruction) instr, state, block);
            case 0xAB -> translateLookupSwitch((LookupSwitchInstruction) instr, state, block);
            case 0xAC, 0xAD, 0xAE, 0xAF, 0xB0 -> translateReturn(state, block);
            case 0xB1 -> translateVoidReturn(block);
            case 0xB2 -> translateGetStatic((com.tonic.analysis.instruction.GetFieldInstruction) instr, state, block);
            case 0xB3 -> translatePutStatic((com.tonic.analysis.instruction.PutFieldInstruction) instr, state, block);
            case 0xB4 -> translateGetField((com.tonic.analysis.instruction.GetFieldInstruction) instr, state, block);
            case 0xB5 -> translatePutField((com.tonic.analysis.instruction.PutFieldInstruction) instr, state, block);
            case 0xB6 -> translateInvokeVirtual((InvokeVirtualInstruction) instr, state, block);
            case 0xB7 -> translateInvokeSpecial((InvokeSpecialInstruction) instr, state, block);
            case 0xB8 -> translateInvokeStatic((InvokeStaticInstruction) instr, state, block);
            case 0xB9 -> translateInvokeInterface((InvokeInterfaceInstruction) instr, state, block);
            case 0xBA -> translateInvokeDynamic((InvokeDynamicInstruction) instr, state, block);
            case 0xBB -> translateNew((com.tonic.analysis.instruction.NewInstruction) instr, state, block);
            case 0xBC -> translateNewArray((com.tonic.analysis.instruction.NewArrayInstruction) instr, state, block);
            case 0xBD -> translateANewArray((ANewArrayInstruction) instr, state, block);
            case 0xBE -> translateArrayLength(state, block);
            case 0xBF -> translateAThrow(state, block);
            case 0xC0 -> translateCheckCast((CheckCastInstruction) instr, state, block);
            case 0xC1 -> translateInstanceOf((com.tonic.analysis.instruction.InstanceOfInstruction) instr, state, block);
            case 0xC2 -> translateMonitorEnter(state, block);
            case 0xC3 -> translateMonitorExit(state, block);
            case 0xC5 -> translateMultiANewArray((MultiANewArrayInstruction) instr, state, block);
            case 0xC6 -> translateIfNull((ConditionalBranchInstruction) instr, state, block);
            case 0xC7 -> translateIfNonNull((ConditionalBranchInstruction) instr, state, block);
            default -> throw new UnsupportedOperationException("Unsupported opcode: 0x" + Integer.toHexString(opcode));
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
        Item<?> item = constPool.getItem(instr.getCpIndex());
        Constant constant = itemToConstant(item);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private void translateLdcW(LdcWInstruction instr, AbstractState state, IRBlock block) {
        Item<?> item = constPool.getItem(instr.getCpIndex());
        Constant constant = itemToConstant(item);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private void translateLdc2W(Ldc2WInstruction instr, AbstractState state, IRBlock block) {
        Item<?> item = constPool.getItem(instr.getCpIndex());
        Constant constant = itemToConstant(item);
        SSAValue result = new SSAValue(constant.getType());
        block.addInstruction(new ConstantInstruction(result, constant));
        state.push(result);
    }

    private Constant itemToConstant(Item<?> item) {
        if (item instanceof IntegerItem intItem) {
            return IntConstant.of(intItem.getValue());
        } else if (item instanceof LongItem longItem) {
            return LongConstant.of(longItem.getValue());
        } else if (item instanceof FloatItem floatItem) {
            return FloatConstant.of(floatItem.getValue());
        } else if (item instanceof DoubleItem doubleItem) {
            return DoubleConstant.of(doubleItem.getValue());
        } else if (item instanceof StringRefItem stringItem) {
            Utf8Item utf8 = (Utf8Item) constPool.getItem(stringItem.getValue());
            return new StringConstant(utf8.getValue());
        } else if (item instanceof ClassRefItem classItem) {
            return new ClassConstant(classItem.getClassName());
        }
        throw new UnsupportedOperationException("Unsupported constant type: " + item.getClass());
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
        block.addInstruction(new ArrayLoadInstruction(result, array, index));
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
        block.addInstruction(new ArrayStoreInstruction(array, index, value));
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

    private void translateDup2(AbstractState state) {
        Value v1 = state.pop();
        Value v2 = state.peek();
        state.push(v1);
        state.push(v2);
        state.push(v1);
    }

    private void translateDup2X1(AbstractState state) {
        Value v1 = state.pop();
        Value v2 = state.pop();
        Value v3 = state.pop();
        state.push(v2);
        state.push(v1);
        state.push(v3);
        state.push(v2);
        state.push(v1);
    }

    private void translateDup2X2(AbstractState state) {
        Value v1 = state.pop();
        Value v2 = state.pop();
        Value v3 = state.pop();
        Value v4 = state.pop();
        state.push(v2);
        state.push(v1);
        state.push(v4);
        state.push(v3);
        state.push(v2);
        state.push(v1);
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

        CompareOp cmpOp = switch (opcode) {
            case 0x99 -> CompareOp.IFEQ;
            case 0x9A -> CompareOp.IFNE;
            case 0x9B -> CompareOp.IFLT;
            case 0x9C -> CompareOp.IFGE;
            case 0x9D -> CompareOp.IFGT;
            case 0x9E -> CompareOp.IFLE;
            default -> throw new IllegalStateException();
        };

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(cmpOp, operand, trueBlock, falseBlock));
    }

    private void translateIfICmp(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block) {
        Value right = state.pop();
        Value left = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        CompareOp cmpOp = switch (opcode) {
            case 0x9F -> CompareOp.EQ;
            case 0xA0 -> CompareOp.NE;
            case 0xA1 -> CompareOp.LT;
            case 0xA2 -> CompareOp.GE;
            case 0xA3 -> CompareOp.GT;
            case 0xA4 -> CompareOp.LE;
            default -> throw new IllegalStateException();
        };

        IRBlock trueBlock = offsetToBlock.get(target);
        IRBlock falseBlock = offsetToBlock.get(fallthrough);

        block.addInstruction(new BranchInstruction(cmpOp, left, right, trueBlock, falseBlock));
    }

    private void translateIfACmp(ConditionalBranchInstruction instr, int opcode, AbstractState state, IRBlock block) {
        Value right = state.pop();
        Value left = state.pop();
        int target = instr.getOffset() + instr.getBranchOffset();
        int fallthrough = instr.getOffset() + instr.getLength();

        CompareOp cmpOp = opcode == 0xA5 ? CompareOp.ACMPEQ : CompareOp.ACMPNE;

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
        block.addInstruction(new com.tonic.analysis.ssa.ir.GotoInstruction(targetBlock));
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
        block.addInstruction(new com.tonic.analysis.ssa.ir.GotoInstruction(subroutineBlock));
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
            block.addInstruction(new com.tonic.analysis.ssa.ir.GotoInstruction(continuation));
        } else {
            // Multiple possible continuations
            // For now, we handle this by jumping to the first one
            // A more sophisticated approach would use a switch based on the return address
            // but that requires runtime dispatch which SSA doesn't support directly
            IRBlock firstContinuation = allContinuations.get(0);
            block.addInstruction(new com.tonic.analysis.ssa.ir.GotoInstruction(firstContinuation));
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
        block.addInstruction(new com.tonic.analysis.ssa.ir.GetFieldInstruction(result, owner, name, desc));
        state.push(result);
    }

    private void translatePutStatic(com.tonic.analysis.instruction.PutFieldInstruction instr, AbstractState state, IRBlock block) {
        Value value = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        block.addInstruction(new com.tonic.analysis.ssa.ir.PutFieldInstruction(
                fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), value));
    }

    private void translateGetField(com.tonic.analysis.instruction.GetFieldInstruction instr, AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        IRType fieldType = IRType.fromDescriptor(fieldRef.getDescriptor());
        SSAValue result = new SSAValue(fieldType);
        block.addInstruction(new com.tonic.analysis.ssa.ir.GetFieldInstruction(
                result, fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), objectRef));
        state.push(result);
    }

    private void translatePutField(com.tonic.analysis.instruction.PutFieldInstruction instr, AbstractState state, IRBlock block) {
        Value value = state.pop();
        Value objectRef = state.pop();
        FieldRefItem fieldRef = (FieldRefItem) constPool.getItem(instr.getFieldIndex());
        block.addInstruction(new com.tonic.analysis.ssa.ir.PutFieldInstruction(
                fieldRef.getOwner(), fieldRef.getName(), fieldRef.getDescriptor(), objectRef, value));
    }

    private void translateInvokeVirtual(InvokeVirtualInstruction instr, AbstractState state, IRBlock block) {
        MethodRefItem methodRef = (MethodRefItem) constPool.getItem(instr.getMethodIndex());
        translateInvoke(InvokeType.VIRTUAL, methodRef.getOwner(), methodRef.getName(),
                methodRef.getDescriptor(), false, state, block);
    }

    private void translateInvokeSpecial(InvokeSpecialInstruction instr, AbstractState state, IRBlock block) {
        MethodRefItem methodRef = (MethodRefItem) constPool.getItem(instr.getMethodIndex());
        translateInvoke(InvokeType.SPECIAL, methodRef.getOwner(), methodRef.getName(),
                methodRef.getDescriptor(), false, state, block);
    }

    private void translateInvokeStatic(InvokeStaticInstruction instr, AbstractState state, IRBlock block) {
        MethodRefItem methodRef = (MethodRefItem) constPool.getItem(instr.getMethodIndex());
        translateInvoke(InvokeType.STATIC, methodRef.getOwner(), methodRef.getName(),
                methodRef.getDescriptor(), true, state, block);
    }

    private void translateInvokeInterface(InvokeInterfaceInstruction instr, AbstractState state, IRBlock block) {
        InterfaceRefItem methodRef = (InterfaceRefItem) constPool.getItem(instr.getMethodIndex());
        translateInvoke(InvokeType.INTERFACE, methodRef.getOwner(), methodRef.getName(),
                methodRef.getDescriptor(), false, state, block);
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
        block.addInstruction(new com.tonic.analysis.ssa.ir.ArrayLengthInstruction(result, array));
        state.push(result);
    }

    private void translateAThrow(AbstractState state, IRBlock block) {
        Value exception = state.pop();
        block.addInstruction(new ThrowInstruction(exception));
    }

    private void translateCheckCast(CheckCastInstruction instr, AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType targetType = IRType.fromInternalName(classRef.getClassName());
        SSAValue result = new SSAValue(targetType);
        block.addInstruction(new CastInstruction(result, objectRef, targetType));
        state.push(result);
    }

    private void translateInstanceOf(com.tonic.analysis.instruction.InstanceOfInstruction instr, AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(instr.getClassIndex());
        IRType checkType = IRType.fromInternalName(classRef.getClassName());
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new com.tonic.analysis.ssa.ir.InstanceOfInstruction(result, objectRef, checkType));
        state.push(result);
    }

    private void translateMonitorEnter(AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        block.addInstruction(new com.tonic.analysis.ssa.ir.MonitorEnterInstruction(objectRef));
    }

    private void translateMonitorExit(AbstractState state, IRBlock block) {
        Value objectRef = state.pop();
        block.addInstruction(new com.tonic.analysis.ssa.ir.MonitorExitInstruction(objectRef));
    }

    private IRType getArrayElementType(int opcode) {
        return switch (opcode) {
            case 0x2E -> PrimitiveType.INT;
            case 0x2F -> PrimitiveType.LONG;
            case 0x30 -> PrimitiveType.FLOAT;
            case 0x31 -> PrimitiveType.DOUBLE;
            case 0x32 -> ReferenceType.OBJECT;
            case 0x33 -> PrimitiveType.BYTE;
            case 0x34 -> PrimitiveType.CHAR;
            case 0x35 -> PrimitiveType.SHORT;
            default -> PrimitiveType.INT;
        };
    }

    private IRType getBinaryOpResultType(int opcode) {
        int typeVariant = (opcode - 0x60) % 4;
        return switch (typeVariant) {
            case 0 -> PrimitiveType.INT;
            case 1 -> PrimitiveType.LONG;
            case 2 -> PrimitiveType.FLOAT;
            case 3 -> PrimitiveType.DOUBLE;
            default -> PrimitiveType.INT;
        };
    }

    private IRType getNegResultType(int opcode) {
        return switch (opcode) {
            case 0x74 -> PrimitiveType.INT;
            case 0x75 -> PrimitiveType.LONG;
            case 0x76 -> PrimitiveType.FLOAT;
            case 0x77 -> PrimitiveType.DOUBLE;
            default -> PrimitiveType.INT;
        };
    }

    private IRType getNewArrayElementType(int atype) {
        return switch (atype) {
            case 4 -> PrimitiveType.BOOLEAN;
            case 5 -> PrimitiveType.CHAR;
            case 6 -> PrimitiveType.FLOAT;
            case 7 -> PrimitiveType.DOUBLE;
            case 8 -> PrimitiveType.BYTE;
            case 9 -> PrimitiveType.SHORT;
            case 10 -> PrimitiveType.INT;
            case 11 -> PrimitiveType.LONG;
            default -> throw new IllegalArgumentException("Unknown array type: " + atype);
        };
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
}

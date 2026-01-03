package com.tonic.analysis.verifier.structural;

import com.tonic.analysis.verifier.ErrorCollector;
import com.tonic.analysis.verifier.VerificationError;
import com.tonic.analysis.verifier.VerificationErrorType;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.utill.Opcode;

import static com.tonic.utill.Opcode.*;

public class OperandValidator {
    private final ConstPool constPool;

    public OperandValidator(ConstPool constPool) {
        this.constPool = constPool;
    }

    public void validate(int opcode, int offset, byte[] bytecode, ErrorCollector collector) {
        if (opcode == LDC.getCode()) {
            validateLdc(offset, bytecode, collector);
        } else if (opcode == LDC_W.getCode()) {
            validateLdcW(offset, bytecode, collector);
        } else if (opcode == LDC2_W.getCode()) {
            validateLdc2W(offset, bytecode, collector);
        } else if (opcode == GETSTATIC.getCode() || opcode == PUTSTATIC.getCode() ||
                   opcode == GETFIELD.getCode() || opcode == PUTFIELD.getCode()) {
            validateFieldRef(offset, bytecode, opcode, collector);
        } else if (opcode == INVOKEVIRTUAL.getCode() || opcode == INVOKESPECIAL.getCode() ||
                   opcode == INVOKESTATIC.getCode()) {
            validateMethodRef(offset, bytecode, opcode, collector);
        } else if (opcode == INVOKEINTERFACE.getCode()) {
            validateInterfaceMethodRef(offset, bytecode, collector);
        } else if (opcode == INVOKEDYNAMIC.getCode()) {
            validateInvokeDynamic(offset, bytecode, collector);
        } else if (opcode == NEW.getCode() || opcode == ANEWARRAY.getCode() ||
                   opcode == CHECKCAST.getCode() || opcode == INSTANCEOF.getCode()) {
            validateClassRef(offset, bytecode, opcode, collector);
        } else if (opcode == NEWARRAY.getCode()) {
            validateNewArray(offset, bytecode, collector);
        } else if (opcode == MULTIANEWARRAY.getCode()) {
            validateMultiANewArray(offset, bytecode, collector);
        }
    }

    private void validateLdc(int offset, byte[] bytecode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 1 >= bytecode.length) return;

        int cpIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof IntegerItem) &&
            !(item instanceof FloatItem) &&
            !(item instanceof StringRefItem) &&
            !(item instanceof ClassRefItem) &&
            !(item instanceof MethodTypeItem) &&
            !(item instanceof MethodHandleItem)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    "LDC references invalid constant pool entry type: " + item.getClass().getSimpleName()
            ));
        }
    }

    private void validateLdcW(int offset, byte[] bytecode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 2 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof IntegerItem) &&
            !(item instanceof FloatItem) &&
            !(item instanceof StringRefItem) &&
            !(item instanceof ClassRefItem) &&
            !(item instanceof MethodTypeItem) &&
            !(item instanceof MethodHandleItem)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    "LDC_W references invalid constant pool entry type: " + item.getClass().getSimpleName()
            ));
        }
    }

    private void validateLdc2W(int offset, byte[] bytecode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 2 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof LongItem) && !(item instanceof DoubleItem)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    "LDC2_W references invalid constant pool entry type: " + item.getClass().getSimpleName() +
                    " (expected Long or Double)"
            ));
        }
    }

    private void validateFieldRef(int offset, byte[] bytecode, int opcode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 2 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof FieldRefItem)) {
            String opName = getOpcodeNameForField(opcode);
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    opName + " references non-Fieldref constant pool entry: " + item.getClass().getSimpleName()
            ));
        }
    }

    private void validateMethodRef(int offset, byte[] bytecode, int opcode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 2 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof MethodRefItem) && !(item instanceof InterfaceRefItem)) {
            String opName = getOpcodeNameForMethod(opcode);
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    opName + " references non-Methodref constant pool entry: " + item.getClass().getSimpleName()
            ));
        }
    }

    private void validateInterfaceMethodRef(int offset, byte[] bytecode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 4 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof InterfaceRefItem)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    "INVOKEINTERFACE references non-InterfaceMethodref constant pool entry: " + item.getClass().getSimpleName()
            ));
        }

        int count = Byte.toUnsignedInt(bytecode[offset + 3]);
        if (count == 0) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_OPERAND,
                    offset,
                    "INVOKEINTERFACE count must be non-zero"
            ));
        }

        int zero = Byte.toUnsignedInt(bytecode[offset + 4]);
        if (zero != 0) {
            collector.addWarning(new VerificationError(
                    VerificationErrorType.INVALID_OPERAND,
                    offset,
                    "INVOKEINTERFACE fourth operand byte should be zero",
                    VerificationError.Severity.WARNING
            ));
        }
    }

    private void validateInvokeDynamic(int offset, byte[] bytecode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 4 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof InvokeDynamicItem)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    "INVOKEDYNAMIC references non-InvokeDynamic constant pool entry: " + item.getClass().getSimpleName()
            ));
        }

        int zero1 = Byte.toUnsignedInt(bytecode[offset + 3]);
        int zero2 = Byte.toUnsignedInt(bytecode[offset + 4]);
        if (zero1 != 0 || zero2 != 0) {
            collector.addWarning(new VerificationError(
                    VerificationErrorType.INVALID_OPERAND,
                    offset,
                    "INVOKEDYNAMIC third and fourth operand bytes should be zero",
                    VerificationError.Severity.WARNING
            ));
        }
    }

    private void validateClassRef(int offset, byte[] bytecode, int opcode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 2 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) return;

        if (!(item instanceof ClassRefItem)) {
            String opName = getOpcodeNameForClass(opcode);
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    opName + " references non-Class constant pool entry: " + item.getClass().getSimpleName()
            ));
        }
    }

    private void validateNewArray(int offset, byte[] bytecode, ErrorCollector collector) {
        if (offset + 1 >= bytecode.length) return;

        int atype = Byte.toUnsignedInt(bytecode[offset + 1]);
        if (atype < 4 || atype > 11) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_OPERAND,
                    offset,
                    "NEWARRAY atype " + atype + " is not a valid primitive type code (4-11)"
            ));
        }
    }

    private void validateMultiANewArray(int offset, byte[] bytecode, ErrorCollector collector) {
        if (constPool == null) return;
        if (offset + 3 >= bytecode.length) return;

        int cpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
        validateConstantPoolIndex(cpIndex, offset, collector);
        if (collector.shouldStop()) return;

        Item<?> item = constPool.getItem(cpIndex);
        if (item != null && !(item instanceof ClassRefItem)) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_TYPE,
                    offset,
                    "MULTIANEWARRAY references non-Class constant pool entry: " + item.getClass().getSimpleName()
            ));
        }

        int dimensions = Byte.toUnsignedInt(bytecode[offset + 3]);
        if (dimensions == 0) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_OPERAND,
                    offset,
                    "MULTIANEWARRAY dimensions must be at least 1"
            ));
        }
    }

    private void validateConstantPoolIndex(int cpIndex, int offset, ErrorCollector collector) {
        if (constPool == null) return;

        if (cpIndex <= 0) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_INDEX,
                    offset,
                    "Constant pool index " + cpIndex + " must be greater than 0"
            ));
            return;
        }

        Item<?> item = constPool.getItem(cpIndex);
        if (item == null) {
            collector.addError(new VerificationError(
                    VerificationErrorType.INVALID_CONSTANT_POOL_INDEX,
                    offset,
                    "Constant pool index " + cpIndex + " is out of bounds or invalid"
            ));
        }
    }

    private String getOpcodeNameForField(int opcode) {
        Opcode op = Opcode.fromCode(opcode);
        return op != Opcode.UNKNOWN ? op.getMnemonic().toUpperCase() : "FIELD_OP";
    }

    private String getOpcodeNameForMethod(int opcode) {
        Opcode op = Opcode.fromCode(opcode);
        return op != Opcode.UNKNOWN ? op.getMnemonic().toUpperCase() : "INVOKE_OP";
    }

    private String getOpcodeNameForClass(int opcode) {
        Opcode op = Opcode.fromCode(opcode);
        return op != Opcode.UNKNOWN ? op.getMnemonic().toUpperCase() : "CLASS_OP";
    }
}

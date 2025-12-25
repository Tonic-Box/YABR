package com.tonic.analysis.verifier.structural;

import com.tonic.analysis.verifier.ErrorCollector;
import com.tonic.analysis.verifier.VerificationError;
import com.tonic.analysis.verifier.VerificationErrorType;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;

public class OperandValidator {
    private final ConstPool constPool;

    public OperandValidator(ConstPool constPool) {
        this.constPool = constPool;
    }

    public void validate(int opcode, int offset, byte[] bytecode, ErrorCollector collector) {
        switch (opcode) {
            case 0x12:
                validateLdc(offset, bytecode, collector);
                break;
            case 0x13:
                validateLdcW(offset, bytecode, collector);
                break;
            case 0x14:
                validateLdc2W(offset, bytecode, collector);
                break;
            case 0xB2:
            case 0xB3:
            case 0xB4:
            case 0xB5:
                validateFieldRef(offset, bytecode, opcode, collector);
                break;
            case 0xB6:
            case 0xB7:
            case 0xB8:
                validateMethodRef(offset, bytecode, opcode, collector);
                break;
            case 0xB9:
                validateInterfaceMethodRef(offset, bytecode, collector);
                break;
            case 0xBA:
                validateInvokeDynamic(offset, bytecode, collector);
                break;
            case 0xBB:
            case 0xBD:
            case 0xC0:
            case 0xC1:
                validateClassRef(offset, bytecode, opcode, collector);
                break;
            case 0xBC:
                validateNewArray(offset, bytecode, collector);
                break;
            case 0xC5:
                validateMultiANewArray(offset, bytecode, collector);
                break;
            default:
                break;
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
        switch (opcode) {
            case 0xB2: return "GETSTATIC";
            case 0xB3: return "PUTSTATIC";
            case 0xB4: return "GETFIELD";
            case 0xB5: return "PUTFIELD";
            default: return "FIELD_OP";
        }
    }

    private String getOpcodeNameForMethod(int opcode) {
        switch (opcode) {
            case 0xB6: return "INVOKEVIRTUAL";
            case 0xB7: return "INVOKESPECIAL";
            case 0xB8: return "INVOKESTATIC";
            default: return "INVOKE_OP";
        }
    }

    private String getOpcodeNameForClass(int opcode) {
        switch (opcode) {
            case 0xBB: return "NEW";
            case 0xBD: return "ANEWARRAY";
            case 0xC0: return "CHECKCAST";
            case 0xC1: return "INSTANCEOF";
            default: return "CLASS_OP";
        }
    }
}

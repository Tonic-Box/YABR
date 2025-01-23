package com.tonic.analysis.ir.types;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class to map opcodes to their corresponding ExpressionType and StatementType.
 */
public class OpcodeTypeMapper {
    // Sets of opcodes for different categories
    public static final Set<Integer> CONDITIONAL_BRANCH_OPCODES = new HashSet<>();
    public static final Set<Integer> UNCONDITIONAL_BRANCH_OPCODES = new HashSet<>();
    public static final Set<Integer> SWITCH_OPCODES = new HashSet<>();
    public static final Set<Integer> RETURN_OPCODES = new HashSet<>();
    public static final Set<Integer> INVOKEDYNAMIC_OPCODES = new HashSet<>();
    public static final Set<Integer> METHOD_CALL_OPCODES = new HashSet<>();
    public static final Set<Integer> FIELD_ACCESS_OPCODES = new HashSet<>();
    public static final Set<Integer> VARIABLE_LOAD_OPCODES = new HashSet<>();
    public static final Set<Integer> VARIABLE_STORE_OPCODES = new HashSet<>();
    public static final Set<Integer> ARITHMETIC_OPCODES = new HashSet<>();
    public static final Set<Integer> LOGICAL_OPCODES = new HashSet<>();
    public static final Set<Integer> ARRAY_OPERATION_OPCODES = new HashSet<>();
    public static final Set<Integer> TYPE_CONVERSION_OPCODES = new HashSet<>();
    public static final Set<Integer> OBJECT_CREATION_OPCODES = new HashSet<>();
    public static final Set<Integer> ARRAY_LENGTH_OPCODES = new HashSet<>();
    public static final Set<Integer> MONITOR_OPERATION_OPCODES = new HashSet<>();
    public static final Set<Integer> POP_OPCODES = new HashSet<>();
    public static final Set<Integer> DUPLICATE_OPCODES = new HashSet<>();
    public static final Set<Integer> SWAP_OPCODES = new HashSet<>();
    public static final Set<Integer> NOP_OPCODES = new HashSet<>();
    public static final Set<Integer> BLOCK_TERMINATOR_OPCODES = new HashSet<>();
    public static final Set<Integer> CONSTANT_PUSH_OPCODES = new HashSet<>();

    static {
        // Initialize CONDITIONAL_BRANCH_OPCODES
        CONDITIONAL_BRANCH_OPCODES.add(0x99); // IFEQ
        CONDITIONAL_BRANCH_OPCODES.add(0x9A); // IFNE
        CONDITIONAL_BRANCH_OPCODES.add(0x9B); // IFLT
        CONDITIONAL_BRANCH_OPCODES.add(0x9C); // IFGE
        CONDITIONAL_BRANCH_OPCODES.add(0x9D); // IFGT
        CONDITIONAL_BRANCH_OPCODES.add(0x9E); // IFLE
        CONDITIONAL_BRANCH_OPCODES.add(0x9F); // IF_ICMPEQ
        CONDITIONAL_BRANCH_OPCODES.add(0xA0); // IF_ICMPNE
        CONDITIONAL_BRANCH_OPCODES.add(0xA1); // IF_ICMPLT
        CONDITIONAL_BRANCH_OPCODES.add(0xA2); // IF_ICMPGE
        CONDITIONAL_BRANCH_OPCODES.add(0xA3); // IF_ICMPGT
        CONDITIONAL_BRANCH_OPCODES.add(0xA4); // IF_ICMPLE
        CONDITIONAL_BRANCH_OPCODES.add(0xA5); // IF_ACMPEQ
        CONDITIONAL_BRANCH_OPCODES.add(0xA6); // IF_ACMPNE

        // Initialize UNCONDITIONAL_BRANCH_OPCODES
        UNCONDITIONAL_BRANCH_OPCODES.add(0xA7); // GOTO
        UNCONDITIONAL_BRANCH_OPCODES.add(0xC8); // GOTO_W

        // Initialize SWITCH_OPCODES
        SWITCH_OPCODES.add(0xAA); // TABLESWITCH
        SWITCH_OPCODES.add(0xAB); // LOOKUPSWITCH

        // Initialize RETURN_OPCODES
        RETURN_OPCODES.add(0xAC); // IRETURN
        RETURN_OPCODES.add(0xAD); // LRETURN
        RETURN_OPCODES.add(0xAE); // FRETURN
        RETURN_OPCODES.add(0xAF); // DRETURN
        RETURN_OPCODES.add(0xB0); // ARETURN
        RETURN_OPCODES.add(0xB1); // RETURN

        // Initialize INVOKEDYNAMIC_OPCODES
        INVOKEDYNAMIC_OPCODES.add(0xBA); // INVOKEDYNAMIC

        // Initialize METHOD_CALL_OPCODES
        METHOD_CALL_OPCODES.add(0xB6); // INVOKEVIRTUAL
        METHOD_CALL_OPCODES.add(0xB7); // INVOKESPECIAL
        METHOD_CALL_OPCODES.add(0xB8); // INVOKESTATIC
        METHOD_CALL_OPCODES.add(0xB9); // INVOKEINTERFACE

        // Initialize FIELD_ACCESS_OPCODES
        FIELD_ACCESS_OPCODES.add(0xB2); // GETSTATIC
        FIELD_ACCESS_OPCODES.add(0xB3); // PUTSTATIC
        FIELD_ACCESS_OPCODES.add(0xB4); // GETFIELD
        FIELD_ACCESS_OPCODES.add(0xB5); // PUTFIELD

        // Initialize VARIABLE_LOAD_OPCODES
        VARIABLE_LOAD_OPCODES.add(0x15); // ILOAD
        VARIABLE_LOAD_OPCODES.add(0x16); // LLOAD
        VARIABLE_LOAD_OPCODES.add(0x17); // FLOAD
        VARIABLE_LOAD_OPCODES.add(0x18); // DLOAD
        VARIABLE_LOAD_OPCODES.add(0x19); // ALOAD
        VARIABLE_LOAD_OPCODES.add(0x1A); // ILOAD_0
        VARIABLE_LOAD_OPCODES.add(0x1B); // ILOAD_1
        VARIABLE_LOAD_OPCODES.add(0x1C); // ILOAD_2
        VARIABLE_LOAD_OPCODES.add(0x1D); // ILOAD_3
        VARIABLE_LOAD_OPCODES.add(0x1E); // LLOAD_0
        VARIABLE_LOAD_OPCODES.add(0x1F); // LLOAD_1
        VARIABLE_LOAD_OPCODES.add(0x20); // LLOAD_2
        VARIABLE_LOAD_OPCODES.add(0x21); // LLOAD_3
        VARIABLE_LOAD_OPCODES.add(0x22); // FLOAD_0
        VARIABLE_LOAD_OPCODES.add(0x23); // FLOAD_1
        VARIABLE_LOAD_OPCODES.add(0x24); // FLOAD_2
        VARIABLE_LOAD_OPCODES.add(0x25); // FLOAD_3
        VARIABLE_LOAD_OPCODES.add(0x26); // DLOAD_0
        VARIABLE_LOAD_OPCODES.add(0x27); // DLOAD_1
        VARIABLE_LOAD_OPCODES.add(0x28); // DLOAD_2
        VARIABLE_LOAD_OPCODES.add(0x29); // DLOAD_3
        VARIABLE_LOAD_OPCODES.add(0x2A); // ALOAD_0
        VARIABLE_LOAD_OPCODES.add(0x2B); // ALOAD_1
        VARIABLE_LOAD_OPCODES.add(0x2C); // ALOAD_2
        VARIABLE_LOAD_OPCODES.add(0x2D); // ALOAD_3

        // Initialize VARIABLE_STORE_OPCODES
        VARIABLE_STORE_OPCODES.add(0x36); // ISTORE
        VARIABLE_STORE_OPCODES.add(0x37); // LSTORE
        VARIABLE_STORE_OPCODES.add(0x38); // FSTORE
        VARIABLE_STORE_OPCODES.add(0x39); // DSTORE
        VARIABLE_STORE_OPCODES.add(0x3A); // ASTORE
        VARIABLE_STORE_OPCODES.add(0x3B); // ISTORE_0
        VARIABLE_STORE_OPCODES.add(0x3C); // ISTORE_1
        VARIABLE_STORE_OPCODES.add(0x3D); // ISTORE_2
        VARIABLE_STORE_OPCODES.add(0x3E); // ISTORE_3
        VARIABLE_STORE_OPCODES.add(0x3F); // LSTORE_0
        VARIABLE_STORE_OPCODES.add(0x40); // LSTORE_1
        VARIABLE_STORE_OPCODES.add(0x41); // LSTORE_2
        VARIABLE_STORE_OPCODES.add(0x42); // LSTORE_3
        VARIABLE_STORE_OPCODES.add(0x43); // FSTORE_0
        VARIABLE_STORE_OPCODES.add(0x44); // FSTORE_1
        VARIABLE_STORE_OPCODES.add(0x45); // FSTORE_2
        VARIABLE_STORE_OPCODES.add(0x46); // FSTORE_3
        VARIABLE_STORE_OPCODES.add(0x47); // DSTORE_0
        VARIABLE_STORE_OPCODES.add(0x48); // DSTORE_1
        VARIABLE_STORE_OPCODES.add(0x49); // DSTORE_2
        VARIABLE_STORE_OPCODES.add(0x4A); // DSTORE_3
        VARIABLE_STORE_OPCODES.add(0x4B); // ASTORE_0
        VARIABLE_STORE_OPCODES.add(0x4C); // ASTORE_1
        VARIABLE_STORE_OPCODES.add(0x4D); // ASTORE_2
        VARIABLE_STORE_OPCODES.add(0x4E); // ASTORE_3

        // Initialize ARITHMETIC_OPCODES
        ARITHMETIC_OPCODES.add(0x60); // IADD
        ARITHMETIC_OPCODES.add(0x61); // LADD
        ARITHMETIC_OPCODES.add(0x62); // FADD
        ARITHMETIC_OPCODES.add(0x63); // DADD
        ARITHMETIC_OPCODES.add(0x64); // ISUB
        ARITHMETIC_OPCODES.add(0x65); // LSUB
        ARITHMETIC_OPCODES.add(0x66); // FSUB
        ARITHMETIC_OPCODES.add(0x67); // DSUB
        ARITHMETIC_OPCODES.add(0x68); // IMUL
        ARITHMETIC_OPCODES.add(0x69); // LMUL
        ARITHMETIC_OPCODES.add(0x6A); // FMUL
        ARITHMETIC_OPCODES.add(0x6B); // DMUL
        ARITHMETIC_OPCODES.add(0x6C); // IDIV
        ARITHMETIC_OPCODES.add(0x6D); // LDIV
        ARITHMETIC_OPCODES.add(0x6E); // FDIV
        ARITHMETIC_OPCODES.add(0x6F); // DDIV
        ARITHMETIC_OPCODES.add(0x70); // IREM
        ARITHMETIC_OPCODES.add(0x71); // LREM
        ARITHMETIC_OPCODES.add(0x72); // FREM
        ARITHMETIC_OPCODES.add(0x73); // DREM
        ARITHMETIC_OPCODES.add(0x74); // INEG
        ARITHMETIC_OPCODES.add(0x75); // LNEG
        ARITHMETIC_OPCODES.add(0x76); // FNEG
        ARITHMETIC_OPCODES.add(0x77); // DNEG
        ARITHMETIC_OPCODES.add(0x78); // ISHL
        ARITHMETIC_OPCODES.add(0x79); // LSHL
        ARITHMETIC_OPCODES.add(0x7A); // ISHR
        ARITHMETIC_OPCODES.add(0x7B); // LSHR
        ARITHMETIC_OPCODES.add(0x7C); // IUSHR
        ARITHMETIC_OPCODES.add(0x7D); // LUSHR
        ARITHMETIC_OPCODES.add(0x7E); // IAND
        ARITHMETIC_OPCODES.add(0x7F); // LAND
        ARITHMETIC_OPCODES.add(0x80); // IOR
        ARITHMETIC_OPCODES.add(0x81); // LOR
        ARITHMETIC_OPCODES.add(0x82); // IXOR
        ARITHMETIC_OPCODES.add(0x83); // LXOR

        // Initialize LOGICAL_OPCODES (reusing ARITHMETIC_OPCODES as they overlap)
        LOGICAL_OPCODES.addAll(ARITHMETIC_OPCODES);

        // Initialize ARRAY_OPERATION_OPCODES
        ARRAY_OPERATION_OPCODES.add(0x2E); // IALOAD
        ARRAY_OPERATION_OPCODES.add(0x2F); // LALOAD
        ARRAY_OPERATION_OPCODES.add(0x30); // FALOAD
        ARRAY_OPERATION_OPCODES.add(0x31); // DALOAD
        ARRAY_OPERATION_OPCODES.add(0x32); // AALOAD
        ARRAY_OPERATION_OPCODES.add(0x33); // BALOAD
        ARRAY_OPERATION_OPCODES.add(0x34); // CALOAD
        ARRAY_OPERATION_OPCODES.add(0x35); // SALOAD
        ARRAY_OPERATION_OPCODES.add(0x4F); // IASTORE
        ARRAY_OPERATION_OPCODES.add(0x50); // LASTORE
        ARRAY_OPERATION_OPCODES.add(0x51); // FASTORE
        ARRAY_OPERATION_OPCODES.add(0x52); // DASTORE
        ARRAY_OPERATION_OPCODES.add(0x53); // AASTORE
        ARRAY_OPERATION_OPCODES.add(0x54); // BASTORE
        ARRAY_OPERATION_OPCODES.add(0x55); // CASTORE
        ARRAY_OPERATION_OPCODES.add(0x56); // SASTORE

        // Initialize TYPE_CONVERSION_OPCODES
        TYPE_CONVERSION_OPCODES.add(0x85); // I2L
        TYPE_CONVERSION_OPCODES.add(0x86); // I2F
        TYPE_CONVERSION_OPCODES.add(0x87); // I2D
        TYPE_CONVERSION_OPCODES.add(0x88); // L2I
        TYPE_CONVERSION_OPCODES.add(0x89); // L2F
        TYPE_CONVERSION_OPCODES.add(0x8A); // L2D
        TYPE_CONVERSION_OPCODES.add(0x8B); // F2I
        TYPE_CONVERSION_OPCODES.add(0x8C); // F2L
        TYPE_CONVERSION_OPCODES.add(0x8D); // F2D
        TYPE_CONVERSION_OPCODES.add(0x8E); // D2I
        TYPE_CONVERSION_OPCODES.add(0x8F); // D2L
        TYPE_CONVERSION_OPCODES.add(0x90); // D2F
        TYPE_CONVERSION_OPCODES.add(0x91); // I2B
        TYPE_CONVERSION_OPCODES.add(0x92); // I2C
        TYPE_CONVERSION_OPCODES.add(0x93); // I2S

        // Initialize OBJECT_CREATION_OPCODES
        OBJECT_CREATION_OPCODES.add(0xBB); // NEW
        OBJECT_CREATION_OPCODES.add(0xBC); // NEWARRAY
        OBJECT_CREATION_OPCODES.add(0xBD); // ANEWARRAY
        OBJECT_CREATION_OPCODES.add(0xC5); // MULTIANEWARRAY

        // Initialize ARRAY_LENGTH_OPCODES
        ARRAY_LENGTH_OPCODES.add(0xBE); // ARRAYLENGTH

        // Initialize MONITOR_OPERATION_OPCODES
        MONITOR_OPERATION_OPCODES.add(0xC2); // MONITORENTER
        MONITOR_OPERATION_OPCODES.add(0xC3); // MONITOREXIT

        // Initialize POP_OPCODES
        POP_OPCODES.add(0x57); // POP
        POP_OPCODES.add(0x58); // POP2

        // Initialize DUPLICATE_OPCODES
        DUPLICATE_OPCODES.add(0x59); // DUP
        DUPLICATE_OPCODES.add(0x5A); // DUP_X1
        DUPLICATE_OPCODES.add(0x5B); // DUP_X2
        DUPLICATE_OPCODES.add(0x5C); // DUP2
        DUPLICATE_OPCODES.add(0x5D); // DUP2_X1
        DUPLICATE_OPCODES.add(0x5E); // DUP2_X2

        // Initialize SWAP_OPCODES
        SWAP_OPCODES.add(0x5F); // SWAP

        // Initialize NOP_OPCODES
        NOP_OPCODES.add(0x00); // NOP

        // Initialize BLOCK_TERMINATOR_OPCODES
        BLOCK_TERMINATOR_OPCODES.addAll(RETURN_OPCODES);
        BLOCK_TERMINATOR_OPCODES.addAll(CONDITIONAL_BRANCH_OPCODES);
        BLOCK_TERMINATOR_OPCODES.addAll(UNCONDITIONAL_BRANCH_OPCODES);
        BLOCK_TERMINATOR_OPCODES.addAll(SWITCH_OPCODES);
        BLOCK_TERMINATOR_OPCODES.add(0xBA); // INVOKEDYNAMIC
        BLOCK_TERMINATOR_OPCODES.addAll(METHOD_CALL_OPCODES);
        BLOCK_TERMINATOR_OPCODES.add(0xBF); // ATHROW

        // Initialize CONSTANT_PUSH_OPCODES
        CONSTANT_PUSH_OPCODES.add(0x10); // BIPUSH
        CONSTANT_PUSH_OPCODES.add(0x11); // SIPUSH
        CONSTANT_PUSH_OPCODES.add(0x12); // LDC
        CONSTANT_PUSH_OPCODES.add(0x13); // LDC_W
        CONSTANT_PUSH_OPCODES.add(0x14); // LDC2_W
    }

    /**
     * Determines if the opcode corresponds to an ExpressionType.
     *
     * @param opcode The opcode of the instruction.
     * @return True if it's an Expression opcode, else false.
     */
    public static boolean isExpressionOpcode(int opcode) {
        return CONDITIONAL_BRANCH_OPCODES.contains(opcode) ||
                UNCONDITIONAL_BRANCH_OPCODES.contains(opcode) ||
                SWITCH_OPCODES.contains(opcode) ||
                RETURN_OPCODES.contains(opcode) ||
                INVOKEDYNAMIC_OPCODES.contains(opcode) ||
                METHOD_CALL_OPCODES.contains(opcode) ||
                FIELD_ACCESS_OPCODES.contains(opcode) ||
                VARIABLE_LOAD_OPCODES.contains(opcode) ||
                VARIABLE_STORE_OPCODES.contains(opcode) ||
                ARITHMETIC_OPCODES.contains(opcode) ||
                LOGICAL_OPCODES.contains(opcode) ||
                ARRAY_OPERATION_OPCODES.contains(opcode) ||
                TYPE_CONVERSION_OPCODES.contains(opcode) ||
                OBJECT_CREATION_OPCODES.contains(opcode) ||
                ARRAY_LENGTH_OPCODES.contains(opcode) ||
                MONITOR_OPERATION_OPCODES.contains(opcode) ||
                POP_OPCODES.contains(opcode) ||
                DUPLICATE_OPCODES.contains(opcode) ||
                SWAP_OPCODES.contains(opcode) ||
                NOP_OPCODES.contains(opcode);
    }

    /**
     * Determines the ExpressionType based on the opcode.
     *
     * @param opcode The opcode of the instruction.
     * @return The corresponding ExpressionType.
     */
    public static ExpressionType getExpressionType(int opcode) {
        if (CONDITIONAL_BRANCH_OPCODES.contains(opcode)) {
            return ExpressionType.CONDITIONAL_BRANCH;
        } else if (UNCONDITIONAL_BRANCH_OPCODES.contains(opcode)) {
            return ExpressionType.UNCONDITIONAL_BRANCH;
        } else if (SWITCH_OPCODES.contains(opcode)) {
            return ExpressionType.SWITCH;
        } else if (RETURN_OPCODES.contains(opcode)) {
            return ExpressionType.RETURN;
        } else if (INVOKEDYNAMIC_OPCODES.contains(opcode)) {
            return ExpressionType.INVOKEDYNAMIC;
        } else if (METHOD_CALL_OPCODES.contains(opcode)) {
            return ExpressionType.METHOD_CALL;
        } else if (FIELD_ACCESS_OPCODES.contains(opcode)) {
            return ExpressionType.FIELD_ACCESS;
        } else if (VARIABLE_LOAD_OPCODES.contains(opcode)) {
            return ExpressionType.VARIABLE_LOAD;
        } else if (VARIABLE_STORE_OPCODES.contains(opcode)) {
            return ExpressionType.VARIABLE_STORE;
        } else if (ARITHMETIC_OPCODES.contains(opcode)) {
            return ExpressionType.ARITHMETIC_OPERATION;
        } else if (LOGICAL_OPCODES.contains(opcode)) {
            return ExpressionType.LOGICAL_OPERATION;
        } else if (ARRAY_OPERATION_OPCODES.contains(opcode)) {
            return ExpressionType.ARRAY_OPERATION;
        } else if (TYPE_CONVERSION_OPCODES.contains(opcode)) {
            return ExpressionType.TYPE_CONVERSION;
        } else if (OBJECT_CREATION_OPCODES.contains(opcode)) {
            return ExpressionType.OBJECT_CREATION;
        } else if (ARRAY_LENGTH_OPCODES.contains(opcode)) {
            return ExpressionType.ARRAY_LENGTH;
        } else if (MONITOR_OPERATION_OPCODES.contains(opcode)) {
            return ExpressionType.MONITOR_OPERATION;
        } else if (POP_OPCODES.contains(opcode)) {
            return ExpressionType.POP;
        } else if (DUPLICATE_OPCODES.contains(opcode)) {
            return ExpressionType.DUPLICATE;
        } else if (SWAP_OPCODES.contains(opcode)) {
            return ExpressionType.SWAP;
        } else if (NOP_OPCODES.contains(opcode)) {
            return ExpressionType.NOP;
        } else if(CONSTANT_PUSH_OPCODES.contains(opcode)) {
            return ExpressionType.CONSTANT_PUSH;
        } else {
            return ExpressionType.OTHER;
        }
    }
}

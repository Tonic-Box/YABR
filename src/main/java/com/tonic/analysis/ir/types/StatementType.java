package com.tonic.analysis.ir.types;

/**
 * Enum representing the types of Statements in bytecode.
 */
public enum StatementType {
    VARIABLE_LOAD,
    VARIABLE_STORE,
    ARITHMETIC_OPERATION,
    LOGICAL_OPERATION,
    ARRAY_OPERATION,
    TYPE_CONVERSION,
    OBJECT_CREATION,
    ARRAY_LENGTH,
    MONITOR_OPERATION,
    POP,
    DUPLICATE,
    SWAP,
    NOP,
    OTHER
}
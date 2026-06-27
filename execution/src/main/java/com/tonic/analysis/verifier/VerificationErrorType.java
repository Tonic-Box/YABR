package com.tonic.analysis.verifier;

public enum VerificationErrorType {
    INVALID_OPCODE("Invalid or reserved opcode"),
    INVALID_OPERAND("Invalid operand value"),
    INVALID_CONSTANT_POOL_INDEX("Constant pool index out of bounds"),
    INVALID_CONSTANT_POOL_TYPE("Constant pool entry has wrong type"),
    INVALID_BRANCH_TARGET("Branch target is not a valid instruction boundary"),
    INSTRUCTION_FALLS_OFF_END("Code falls off the end without return"),
    INVALID_WIDE_OPCODE("Wide prefix not followed by valid wideable opcode"),
    CODE_TOO_LONG("Code attribute exceeds maximum length"),
    INVALID_LOCAL_INDEX("Local variable index out of bounds"),

    STACK_UNDERFLOW("Stack underflow - not enough operands"),
    STACK_OVERFLOW("Stack exceeds max_stack limit"),
    TYPE_MISMATCH("Operand type does not match instruction requirement"),
    UNINITIALIZED_ACCESS("Access to uninitialized object"),
    INCOMPATIBLE_RETURN_TYPE("Return type incompatible with method signature"),
    INVALID_ARRAY_TYPE("Invalid array element type"),
    LOCALS_OVERFLOW("Local variable access exceeds max_locals"),
    UNINITIALIZED_LOCAL("Access to uninitialized local variable"),
    MERGE_CONFLICT("Type states cannot be merged at control flow join"),

    PATH_DOES_NOT_RETURN("Execution path does not end with return/throw"),
    UNREACHABLE_CODE("Unreachable code detected"),
    INVALID_EXCEPTION_HANDLER("Invalid exception handler range"),
    EXCEPTION_HANDLER_OVERLAP("Overlapping exception handlers for same type"),
    INVALID_CATCH_TYPE("Catch type is not a Throwable subclass"),
    JSR_MISMATCH("JSR/RET subroutine mismatch"),

    MISSING_STACKMAP_FRAME("Missing StackMapTable frame at branch target"),
    FRAME_TYPE_MISMATCH("StackMapTable frame type does not match computed type"),
    FRAME_STACK_MISMATCH("StackMapTable stack does not match computed stack"),
    FRAME_LOCALS_MISMATCH("StackMapTable locals do not match computed locals"),
    INVALID_FRAME_OFFSET("StackMapTable frame offset is invalid");

    private final String description;

    VerificationErrorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

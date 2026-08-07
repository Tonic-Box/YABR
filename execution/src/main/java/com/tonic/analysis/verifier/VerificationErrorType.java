package com.tonic.analysis.verifier;

/**
 * Category of a verification finding, spanning structural, type, control-flow, and stack-map checks.
 */
public enum VerificationErrorType
{
    /**
     * A byte in the code stream is not an opcode the JVM assigns, or is one reserved for internal use.
     */
    INVALID_OPCODE("Invalid or reserved opcode"),
    /**
     * An opcode is recognized but its immediate operand is out of the range that opcode accepts.
     */
    INVALID_OPERAND("Invalid operand value"),
    /**
     * An instruction names a constant pool slot that does not exist in the class.
     */
    INVALID_CONSTANT_POOL_INDEX("Constant pool index out of bounds"),
    /**
     * A constant pool reference resolves to an entry of a kind the instruction cannot use.
     */
    INVALID_CONSTANT_POOL_TYPE("Constant pool entry has wrong type"),
    /**
     * A branch aims at an offset that falls mid-instruction or outside the method body.
     */
    INVALID_BRANCH_TARGET("Branch target is not a valid instruction boundary"),
    /**
     * The last instruction can fall through, running past the end of the method body.
     */
    INSTRUCTION_FALLS_OFF_END("Code falls off the end without return"),
    /**
     * A WIDE prefix precedes an opcode that has no wide-index form.
     */
    INVALID_WIDE_OPCODE("Wide prefix not followed by valid wideable opcode"),
    /**
     * A method body is longer than the 65535 bytes the class file format permits.
     */
    CODE_TOO_LONG("Code attribute exceeds maximum length"),
    /**
     * A load or store names a local slot outside the frame's declared range.
     */
    INVALID_LOCAL_INDEX("Local variable index out of bounds"),

    /**
     * An instruction pops more operands than the stack holds at that point.
     */
    STACK_UNDERFLOW("Stack underflow - not enough operands"),
    /**
     * The operand stack grows past the max_stack the Code attribute declares.
     */
    STACK_OVERFLOW("Stack exceeds max_stack limit"),
    /**
     * An instruction is handed an operand of the wrong type, such as a reference where an int is due.
     */
    TYPE_MISMATCH("Operand type does not match instruction requirement"),
    /**
     * A freshly allocated object is used before its constructor has run.
     */
    UNINITIALIZED_ACCESS("Access to uninitialized object"),
    /**
     * A return opcode or returned value disagrees with the method descriptor's return type.
     */
    INCOMPATIBLE_RETURN_TYPE("Return type incompatible with method signature"),
    /**
     * An array instruction is applied to a value whose element type it cannot operate on.
     */
    INVALID_ARRAY_TYPE("Invalid array element type"),
    /**
     * The locals a method actually uses outgrow the max_locals its Code attribute declares.
     */
    LOCALS_OVERFLOW("Local variable access exceeds max_locals"),
    /**
     * A local is read on a path that never wrote it, so it holds no defined value there.
     */
    UNINITIALIZED_LOCAL("Access to uninitialized local variable"),
    /**
     * Paths reaching a join disagree on stack depth or on types with no common supertype.
     */
    MERGE_CONFLICT("Type states cannot be merged at control flow join"),

    /**
     * A reachable path reaches its end without a return or throw to terminate the method.
     */
    PATH_DOES_NOT_RETURN("Execution path does not end with return/throw"),
    /**
     * Instructions no path from entry can arrive at, leaving them unverifiable dead weight.
     */
    UNREACHABLE_CODE("Unreachable code detected"),
    /**
     * A handler's protected range is malformed, such as an empty, inverted, or out-of-code span.
     */
    INVALID_EXCEPTION_HANDLER("Invalid exception handler range"),
    /**
     * Two handlers cover the same type over overlapping ranges, leaving the winner ambiguous.
     */
    EXCEPTION_HANDLER_OVERLAP("Overlapping exception handlers for same type"),
    /**
     * A handler names a catch type that does not descend from Throwable, so it can never match.
     */
    INVALID_CATCH_TYPE("Catch type is not a Throwable subclass"),
    /**
     * A legacy subroutine is malformed, its RET not pairing with the JSR that entered it.
     */
    JSR_MISMATCH("JSR/RET subroutine mismatch"),

    /**
     * A jump target or handler entry has no stack map frame, so its incoming state is undeclared.
     */
    MISSING_STACKMAP_FRAME("Missing StackMapTable frame at branch target"),
    /**
     * An individual slot in a declared frame names a type incompatible with the computed one.
     */
    FRAME_TYPE_MISMATCH("StackMapTable frame type does not match computed type"),
    /**
     * The operand stack a declared frame promises differs from the one the verifier derived.
     */
    FRAME_STACK_MISMATCH("StackMapTable stack does not match computed stack"),
    /**
     * The locals a declared frame promises differ from those the verifier derived at that offset.
     */
    FRAME_LOCALS_MISMATCH("StackMapTable locals do not match computed locals"),
    /**
     * A stack map frame claims an offset that is out of range or not greater than its predecessor.
     */
    INVALID_FRAME_OFFSET("StackMapTable frame offset is invalid");

    private final String description;

    VerificationErrorType(String description)
    {
        this.description = description;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }
}

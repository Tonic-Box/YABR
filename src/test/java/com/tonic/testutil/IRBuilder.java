package com.tonic.testutil;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for creating SSA IR methods for testing.
 * Provides a simple DSL for constructing IR methods programmatically.
 *
 * <p>Example usage:
 * <pre>{@code
 * IRMethod method = IRBuilder.staticMethod("com/test/Math", "add", "(II)I")
 *     .entry()
 *         .iconst(5, "a")
 *         .iconst(3, "b")
 *         .add("a", "b", "result")
 *         .ireturn("result")
 *     .build();
 * }</pre>
 */
public class IRBuilder {

    private final IRMethod method;
    private final Map<String, Value> values = new HashMap<>();
    private IRBlock currentBlock;

    private IRBuilder(String owner, String name, String descriptor, boolean isStatic) {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        this.method = new IRMethod(owner, name, descriptor, isStatic);
    }

    /**
     * Creates a builder for a static method.
     *
     * @param owner owning class internal name
     * @param name method name
     * @param descriptor method descriptor
     * @return a new IRBuilder
     */
    public static IRBuilder staticMethod(String owner, String name, String descriptor) {
        return new IRBuilder(owner, name, descriptor, true);
    }

    /**
     * Creates a builder for an instance method.
     *
     * @param owner owning class internal name
     * @param name method name
     * @param descriptor method descriptor
     * @return a new IRBuilder
     */
    public static IRBuilder instanceMethod(String owner, String name, String descriptor) {
        return new IRBuilder(owner, name, descriptor, false);
    }

    /**
     * Creates a builder for a void static method with no parameters.
     *
     * @param name method name
     * @return a new IRBuilder
     */
    public static IRBuilder voidMethod(String name) {
        return staticMethod("com/test/Test", name, "()V");
    }

    /**
     * Creates a builder for an int-returning static method.
     *
     * @param name method name
     * @return a new IRBuilder
     */
    public static IRBuilder intMethod(String name) {
        return staticMethod("com/test/Test", name, "()I");
    }

    // ========== Block Management ==========

    /**
     * Creates the entry block and sets it as current.
     *
     * @return this builder
     */
    public IRBuilder entry() {
        currentBlock = new IRBlock("entry");
        method.addBlock(currentBlock);
        method.setEntryBlock(currentBlock);
        return this;
    }

    /**
     * Creates a named block and sets it as current.
     *
     * @param name block name
     * @return this builder
     */
    public IRBuilder block(String name) {
        currentBlock = new IRBlock(name);
        method.addBlock(currentBlock);
        return this;
    }

    /**
     * Gets or creates a block by name.
     *
     * @param name block name
     * @return the block
     */
    public IRBlock getBlock(String name) {
        for (IRBlock b : method.getBlocks()) {
            if (b.getName().equals(name)) {
                return b;
            }
        }
        IRBlock newBlock = new IRBlock(name);
        method.addBlock(newBlock);
        return newBlock;
    }

    // ========== Value Management ==========

    /**
     * Registers a value with a name for later reference.
     *
     * @param name the value name
     * @param value the value
     * @return this builder
     */
    public IRBuilder define(String name, Value value) {
        values.put(name, value);
        return this;
    }

    /**
     * Gets a value by name, creating an SSA value if not found.
     *
     * @param name the value name
     * @return the value
     */
    public Value get(String name) {
        return values.computeIfAbsent(name, n -> new SSAValue(PrimitiveType.INT, n));
    }

    /**
     * Gets an SSAValue by name.
     *
     * @param name the value name
     * @return the SSAValue
     */
    public SSAValue getSSA(String name) {
        Value v = get(name);
        if (v instanceof SSAValue) {
            return (SSAValue) v;
        }
        throw new IllegalArgumentException(name + " is not an SSAValue");
    }

    // ========== Constant Instructions ==========

    /**
     * Adds an integer constant instruction.
     *
     * @param value the constant value
     * @param result name for the result
     * @return this builder
     */
    public IRBuilder iconst(int value, String result) {
        SSAValue res = new SSAValue(PrimitiveType.INT, result);
        values.put(result, res);

        ConstantInstruction inst = new ConstantInstruction(res, IntConstant.of(value));
        res.setDefinition(inst);
        currentBlock.addInstruction(inst);
        return this;
    }

    /**
     * Adds a long constant instruction.
     *
     * @param value the constant value
     * @param result name for the result
     * @return this builder
     */
    public IRBuilder lconst(long value, String result) {
        SSAValue res = new SSAValue(PrimitiveType.LONG, result);
        values.put(result, res);

        ConstantInstruction inst = new ConstantInstruction(res, LongConstant.of(value));
        res.setDefinition(inst);
        currentBlock.addInstruction(inst);
        return this;
    }

    /**
     * Adds a null constant instruction.
     *
     * @param result name for the result
     * @return this builder
     */
    public IRBuilder nullConst(String result) {
        SSAValue res = new SSAValue(new ReferenceType("java/lang/Object"), result);
        values.put(result, res);

        ConstantInstruction inst = new ConstantInstruction(res, NullConstant.INSTANCE);
        res.setDefinition(inst);
        currentBlock.addInstruction(inst);
        return this;
    }

    // ========== Binary Operations ==========

    /**
     * Adds an integer addition instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder add(String left, String right, String result) {
        return binaryOp(BinaryOp.ADD, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds an integer subtraction instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder sub(String left, String right, String result) {
        return binaryOp(BinaryOp.SUB, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds an integer multiplication instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder mul(String left, String right, String result) {
        return binaryOp(BinaryOp.MUL, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds an integer division instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder div(String left, String right, String result) {
        return binaryOp(BinaryOp.DIV, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds an integer remainder instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder rem(String left, String right, String result) {
        return binaryOp(BinaryOp.REM, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds a bitwise AND instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder and(String left, String right, String result) {
        return binaryOp(BinaryOp.AND, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds a bitwise OR instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder or(String left, String right, String result) {
        return binaryOp(BinaryOp.OR, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds a bitwise XOR instruction.
     *
     * @param left left operand name
     * @param right right operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder xor(String left, String right, String result) {
        return binaryOp(BinaryOp.XOR, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds a shift left instruction.
     *
     * @param left value to shift
     * @param right shift amount
     * @param result result name
     * @return this builder
     */
    public IRBuilder shl(String left, String right, String result) {
        return binaryOp(BinaryOp.SHL, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds an arithmetic shift right instruction.
     *
     * @param left value to shift
     * @param right shift amount
     * @param result result name
     * @return this builder
     */
    public IRBuilder shr(String left, String right, String result) {
        return binaryOp(BinaryOp.SHR, PrimitiveType.INT, left, right, result);
    }

    /**
     * Adds a logical shift right instruction.
     *
     * @param left value to shift
     * @param right shift amount
     * @param result result name
     * @return this builder
     */
    public IRBuilder ushr(String left, String right, String result) {
        return binaryOp(BinaryOp.USHR, PrimitiveType.INT, left, right, result);
    }

    private IRBuilder binaryOp(BinaryOp op, IRType type, String left, String right, String result) {
        SSAValue res = new SSAValue(type, result);
        values.put(result, res);

        BinaryOpInstruction inst = new BinaryOpInstruction(res, op, get(left), get(right));
        res.setDefinition(inst);
        currentBlock.addInstruction(inst);
        return this;
    }

    // ========== Unary Operations ==========

    /**
     * Adds a negation instruction.
     *
     * @param operand operand name
     * @param result result name
     * @return this builder
     */
    public IRBuilder neg(String operand, String result) {
        SSAValue res = new SSAValue(PrimitiveType.INT, result);
        values.put(result, res);

        UnaryOpInstruction inst = new UnaryOpInstruction(res, UnaryOp.NEG, get(operand));
        res.setDefinition(inst);
        currentBlock.addInstruction(inst);
        return this;
    }

    // ========== Copy Instructions ==========

    /**
     * Adds a copy instruction.
     *
     * @param source source value name
     * @param result result name
     * @return this builder
     */
    public IRBuilder copy(String source, String result) {
        Value src = get(source);
        SSAValue res = new SSAValue(
                src instanceof SSAValue ? ((SSAValue) src).getType() : PrimitiveType.INT,
                result);
        values.put(result, res);

        CopyInstruction inst = new CopyInstruction(res, src);
        res.setDefinition(inst);
        currentBlock.addInstruction(inst);
        return this;
    }

    // ========== Phi Instructions ==========

    /**
     * Adds a phi instruction.
     *
     * @param result result name
     * @param type the value type
     * @return this builder
     */
    public IRBuilder phi(String result, IRType type) {
        SSAValue res = new SSAValue(type, result);
        values.put(result, res);

        PhiInstruction phi = new PhiInstruction(res);
        res.setDefinition(phi);
        currentBlock.addPhi(phi);
        return this;
    }

    /**
     * Adds an incoming value to the last phi instruction.
     *
     * @param blockName predecessor block name
     * @param valueName incoming value name
     * @return this builder
     */
    public IRBuilder phiIncoming(String blockName, String valueName) {
        if (currentBlock.getPhiInstructions().isEmpty()) {
            throw new IllegalStateException("No phi instruction to add incoming to");
        }
        PhiInstruction phi = currentBlock.getPhiInstructions()
                .get(currentBlock.getPhiInstructions().size() - 1);
        IRBlock pred = getBlock(blockName);
        phi.addIncoming(get(valueName), pred);
        return this;
    }

    // ========== Control Flow ==========

    /**
     * Adds an unconditional goto instruction.
     *
     * @param targetName target block name
     * @return this builder
     */
    public IRBuilder goTo(String targetName) {
        IRBlock target = getBlock(targetName);
        GotoInstruction inst = new GotoInstruction(target);
        currentBlock.addInstruction(inst);
        currentBlock.addSuccessor(target);
        return this;
    }

    /**
     * Adds a conditional branch instruction.
     *
     * @param op comparison operation
     * @param left left operand name
     * @param right right operand name
     * @param trueName true branch block name
     * @param falseName false branch block name
     * @return this builder
     */
    public IRBuilder branch(CompareOp op, String left, String right, String trueName, String falseName) {
        IRBlock trueBlock = getBlock(trueName);
        IRBlock falseBlock = getBlock(falseName);

        BranchInstruction inst = new BranchInstruction(op, get(left), get(right), trueBlock, falseBlock);
        currentBlock.addInstruction(inst);
        currentBlock.addSuccessor(trueBlock);
        currentBlock.addSuccessor(falseBlock);
        return this;
    }

    /**
     * Adds a conditional branch comparing against zero.
     *
     * @param op comparison operation (e.g., IFEQ, IFNE)
     * @param operand operand name
     * @param trueName true branch block name
     * @param falseName false branch block name
     * @return this builder
     */
    public IRBuilder branchZero(CompareOp op, String operand, String trueName, String falseName) {
        IRBlock trueBlock = getBlock(trueName);
        IRBlock falseBlock = getBlock(falseName);

        BranchInstruction inst = new BranchInstruction(op, get(operand), IntConstant.ZERO, trueBlock, falseBlock);
        currentBlock.addInstruction(inst);
        currentBlock.addSuccessor(trueBlock);
        currentBlock.addSuccessor(falseBlock);
        return this;
    }

    // ========== Return Instructions ==========

    /**
     * Adds a void return instruction.
     *
     * @return this builder
     */
    public IRBuilder vreturn() {
        ReturnInstruction inst = new ReturnInstruction(null);
        currentBlock.addInstruction(inst);
        return this;
    }

    /**
     * Adds an integer return instruction.
     *
     * @param valueName value to return
     * @return this builder
     */
    public IRBuilder ireturn(String valueName) {
        ReturnInstruction inst = new ReturnInstruction(get(valueName));
        currentBlock.addInstruction(inst);
        return this;
    }

    /**
     * Adds a return instruction for any value.
     *
     * @param valueName value to return (or null for void)
     * @return this builder
     */
    public IRBuilder ret(String valueName) {
        Value val = valueName != null ? get(valueName) : null;
        ReturnInstruction inst = new ReturnInstruction(val);
        currentBlock.addInstruction(inst);
        return this;
    }

    // ========== Build ==========

    /**
     * Returns the current block being built.
     *
     * @return the current IRBlock
     */
    public IRBlock getCurrentBlock() {
        return currentBlock;
    }

    /**
     * Builds and returns the IR method.
     *
     * @return the constructed IRMethod
     */
    public IRMethod build() {
        return method;
    }

    /**
     * Gets the map of all defined values.
     *
     * @return the values map
     */
    public Map<String, Value> getValues() {
        return values;
    }
}

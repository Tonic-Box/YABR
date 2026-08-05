package com.tonic.analysis.ssa.visitor;

import com.tonic.analysis.ssa.ir.*;

/**
 * Visitor interface for IR instructions.
 */
public interface IRVisitor<T>
{

    /**
     * Visits a phi that merges values arriving from several predecessors.
     *
     * @param phi the instruction
     * @return the visitor result
     */
    T visitPhi(PhiInstruction phi);

    /**
     * Visits a two-operand arithmetic, logical, or comparison instruction.
     *
     * @param binaryOp the instruction
     * @return the visitor result
     */
    T visitBinaryOp(BinaryOpInstruction binaryOp);

    /**
     * Visits a one-operand instruction such as a negation or a numeric conversion.
     *
     * @param unaryOp the instruction
     * @return the visitor result
     */
    T visitUnaryOp(UnaryOpInstruction unaryOp);

    /**
     * Visits a conditional branch and its two targets.
     *
     * @param branch the instruction
     * @return the visitor result
     */
    T visitBranch(BranchInstruction branch);

    /**
     * Visits a multi-way branch on an int key.
     *
     * @param switchInstr the instruction
     * @return the visitor result
     */
    T visitSwitch(SwitchInstruction switchInstr);

    /**
     * Visits a return, with or without a value.
     *
     * @param returnInstr the instruction
     * @return the visitor result
     */
    T visitReturn(ReturnInstruction returnInstr);

    /**
     * Visits a read of a local variable slot.
     *
     * @param loadLocal the instruction
     * @return the visitor result
     */
    T visitLoadLocal(LoadLocalInstruction loadLocal);

    /**
     * Visits a write to a local variable slot.
     *
     * @param storeLocal the instruction
     * @return the visitor result
     */
    T visitStoreLocal(StoreLocalInstruction storeLocal);

    /**
     * Visits a method invocation.
     *
     * @param invoke the instruction
     * @return the visitor result
     */
    T visitInvoke(InvokeInstruction invoke);

    /**
     * Visits an uninitialized object allocation, before its constructor call.
     *
     * @param newInstr the instruction
     * @return the visitor result
     */
    T visitNew(NewInstruction newInstr);

    /**
     * Visits an array allocation.
     *
     * @param newArray the instruction
     * @return the visitor result
     */
    T visitNewArray(NewArrayInstruction newArray);

    /**
     * Visits a copy of one SSA value into another.
     *
     * @param copy the instruction
     * @return the visitor result
     */
    T visitCopy(CopyInstruction copy);

    /**
     * Visits a constant load.
     *
     * @param constant the instruction
     * @return the visitor result
     */
    T visitConstant(ConstantInstruction constant);

    /**
     * Visits a field read or write.
     *
     * @param fieldAccess the instruction
     * @return the visitor result
     */
    T visitFieldAccess(FieldAccessInstruction fieldAccess);

    /**
     * Visits an array element read or write.
     *
     * @param arrayAccess the instruction
     * @return the visitor result
     */
    T visitArrayAccess(ArrayAccessInstruction arrayAccess);

    /**
     * Visits an instanceof test or a checkcast.
     *
     * @param typeCheck the instruction
     * @return the visitor result
     */
    T visitTypeCheck(TypeCheckInstruction typeCheck);

    /**
     * Visits a single-operand instruction: arraylength, a monitor op, throw, goto, or catch.
     *
     * @param simple the instruction
     * @return the visitor result
     */
    T visitSimple(SimpleInstruction simple);
}

package com.tonic.analysis.ssa.visitor;

import com.tonic.analysis.ssa.ir.*;

/**
 * Abstract visitor with default implementations returning null.
 */
public abstract class AbstractIRVisitor<T> implements IRVisitor<T> {

    protected T defaultValue() {
        return null;
    }

    @Override
    public T visitPhi(PhiInstruction phi) {
        return defaultValue();
    }

    @Override
    public T visitBinaryOp(BinaryOpInstruction binaryOp) {
        return defaultValue();
    }

    @Override
    public T visitUnaryOp(UnaryOpInstruction unaryOp) {
        return defaultValue();
    }

    @Override
    public T visitBranch(BranchInstruction branch) {
        return defaultValue();
    }

    @Override
    public T visitSwitch(SwitchInstruction switchInstr) {
        return defaultValue();
    }

    @Override
    public T visitReturn(ReturnInstruction returnInstr) {
        return defaultValue();
    }

    @Override
    public T visitLoadLocal(LoadLocalInstruction loadLocal) {
        return defaultValue();
    }

    @Override
    public T visitStoreLocal(StoreLocalInstruction storeLocal) {
        return defaultValue();
    }

    @Override
    public T visitInvoke(InvokeInstruction invoke) {
        return defaultValue();
    }

    @Override
    public T visitNew(NewInstruction newInstr) {
        return defaultValue();
    }

    @Override
    public T visitNewArray(NewArrayInstruction newArray) {
        return defaultValue();
    }

    @Override
    public T visitCopy(CopyInstruction copy) {
        return defaultValue();
    }

    @Override
    public T visitConstant(ConstantInstruction constant) {
        return defaultValue();
    }

    @Override
    public T visitFieldAccess(FieldAccessInstruction fieldAccess) {
        return defaultValue();
    }

    @Override
    public T visitArrayAccess(ArrayAccessInstruction arrayAccess) {
        return defaultValue();
    }

    @Override
    public T visitTypeCheck(TypeCheckInstruction typeCheck) {
        return defaultValue();
    }

    @Override
    public T visitSimple(SimpleInstruction simple) {
        return defaultValue();
    }
}

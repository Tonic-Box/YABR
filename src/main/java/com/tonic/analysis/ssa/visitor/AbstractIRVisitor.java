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
    public T visitGoto(GotoInstruction gotoInstr) {
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
    public T visitThrow(ThrowInstruction throwInstr) {
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
    public T visitGetField(GetFieldInstruction getField) {
        return defaultValue();
    }

    @Override
    public T visitPutField(PutFieldInstruction putField) {
        return defaultValue();
    }

    @Override
    public T visitArrayLoad(ArrayLoadInstruction arrayLoad) {
        return defaultValue();
    }

    @Override
    public T visitArrayStore(ArrayStoreInstruction arrayStore) {
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
    public T visitArrayLength(ArrayLengthInstruction arrayLength) {
        return defaultValue();
    }

    @Override
    public T visitInstanceOf(InstanceOfInstruction instanceOf) {
        return defaultValue();
    }

    @Override
    public T visitCast(CastInstruction cast) {
        return defaultValue();
    }

    @Override
    public T visitMonitorEnter(MonitorEnterInstruction monitorEnter) {
        return defaultValue();
    }

    @Override
    public T visitMonitorExit(MonitorExitInstruction monitorExit) {
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
}

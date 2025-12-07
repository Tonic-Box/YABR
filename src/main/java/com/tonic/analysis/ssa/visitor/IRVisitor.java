package com.tonic.analysis.ssa.visitor;

import com.tonic.analysis.ssa.ir.*;

/**
 * Visitor interface for IR instructions.
 */
public interface IRVisitor<T> {

    T visitPhi(PhiInstruction phi);

    T visitBinaryOp(BinaryOpInstruction binaryOp);

    T visitUnaryOp(UnaryOpInstruction unaryOp);

    T visitGoto(GotoInstruction gotoInstr);

    T visitBranch(BranchInstruction branch);

    T visitSwitch(SwitchInstruction switchInstr);

    T visitReturn(ReturnInstruction returnInstr);

    T visitThrow(ThrowInstruction throwInstr);

    T visitLoadLocal(LoadLocalInstruction loadLocal);

    T visitStoreLocal(StoreLocalInstruction storeLocal);

    T visitGetField(GetFieldInstruction getField);

    T visitPutField(PutFieldInstruction putField);

    T visitArrayLoad(ArrayLoadInstruction arrayLoad);

    T visitArrayStore(ArrayStoreInstruction arrayStore);

    T visitInvoke(InvokeInstruction invoke);

    T visitNew(NewInstruction newInstr);

    T visitNewArray(NewArrayInstruction newArray);

    T visitArrayLength(ArrayLengthInstruction arrayLength);

    T visitInstanceOf(InstanceOfInstruction instanceOf);

    T visitCast(CastInstruction cast);

    T visitMonitorEnter(MonitorEnterInstruction monitorEnter);

    T visitMonitorExit(MonitorExitInstruction monitorExit);

    T visitCopy(CopyInstruction copy);

    T visitConstant(ConstantInstruction constant);
}

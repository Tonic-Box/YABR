package com.tonic.analysis.ssa.visitor;

import com.tonic.analysis.ssa.ir.*;

/**
 * Visitor interface for IR instructions.
 */
public interface IRVisitor<T> {

    T visitPhi(PhiInstruction phi);

    T visitBinaryOp(BinaryOpInstruction binaryOp);

    T visitUnaryOp(UnaryOpInstruction unaryOp);

    T visitBranch(BranchInstruction branch);

    T visitSwitch(SwitchInstruction switchInstr);

    T visitReturn(ReturnInstruction returnInstr);

    T visitLoadLocal(LoadLocalInstruction loadLocal);

    T visitStoreLocal(StoreLocalInstruction storeLocal);

    T visitInvoke(InvokeInstruction invoke);

    T visitNew(NewInstruction newInstr);

    T visitNewArray(NewArrayInstruction newArray);

    T visitCopy(CopyInstruction copy);

    T visitConstant(ConstantInstruction constant);

    T visitFieldAccess(FieldAccessInstruction fieldAccess);

    T visitArrayAccess(ArrayAccessInstruction arrayAccess);

    T visitTypeCheck(TypeCheckInstruction typeCheck);

    T visitSimple(SimpleInstruction simple);
}

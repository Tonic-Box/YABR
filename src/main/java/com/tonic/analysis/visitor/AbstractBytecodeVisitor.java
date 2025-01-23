package com.tonic.analysis.visitor;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.MethodEntry;

import java.io.IOException;

public class AbstractBytecodeVisitor implements Visitor<MethodEntry>
{
    protected CodeWriter codeWriter;
    protected MethodEntry method;
    @Override
    public void process(MethodEntry method) throws IOException {
        codeWriter = new CodeWriter(method);
        this.method = method;
        codeWriter.accept(this);
        codeWriter.write();
    }

    public void visit(NopInstruction instr) {}

    public void visit(AConstNullInstruction instr) {}

    public void visit(IConstInstruction instr) {}

    public void visit(LConstInstruction instr) {}

    public void visit(FConstInstruction instr) {}

    public void visit(DConstInstruction instr) {}

    public void visit(BipushInstruction instr) {}

    public void visit(SipushInstruction instr) {}

    public void visit(LdcInstruction instr) {}

    public void visit(LdcWInstruction instr) {}

    public void visit(Ldc2WInstruction instr) {}

    public void visit(ILoadInstruction instr) {}

    public void visit(LLoadInstruction instr) {}

    public void visit(FLoadInstruction instr) {}

    public void visit(DLoadInstruction instr) {}

    public void visit(ALoadInstruction instr) {}

    public void visit(IStoreInstruction instr) {}

    public void visit(LStoreInstruction instr) {}

    public void visit(FStoreInstruction instr) {}

    public void visit(DStoreInstruction instr) {

    }

    public void visit(AStoreInstruction instr) {}

    public void visit(PopInstruction instr) {}

    public void visit(Pop2Instruction instr) {}

    public void visit(DupInstruction instr) {}

    public void visit(SwapInstruction instr) {}

    public void visit(ArithmeticInstruction instr) {}

    public void visit(INegInstruction instr) {}

    public void visit(LNegInstruction instr) {}

    public void visit(FNegInstruction instr) {}

    public void visit(DNegInstruction instr) {}

    public void visit(ArithmeticShiftInstruction instr) {}

    public void visit(IAndInstruction instr) {}

    public void visit(IOrInstruction instr) {}

    public void visit(IXorInstruction instr) {}

    public void visit(IIncInstruction instr) {}

    public void visit(ConversionInstruction instr) {}

    public void visit(NarrowingConversionInstruction instr) {}

    public void visit(CompareInstruction instr) {}

    public void visit(ConditionalBranchInstruction instr) {}

    public void visit(GotoInstruction instr) {}

    public void visit(JsrInstruction instr) {}

    public void visit(RetInstruction instr) {}

    public void visit(InvokeVirtualInstruction instr) {}

    public void visit(InvokeSpecialInstruction instr) {}

    public void visit(InvokeStaticInstruction instr) {}

    public void visit(InvokeInterfaceInstruction instr) {}

    public void visit(InvokeDynamicInstruction instr) {}

    public void visit(GetFieldInstruction instr) {}

    public void visit(PutFieldInstruction instr) {}

    public void visit(NewInstruction instr) {}

    public void visit(NewArrayInstruction instr) {}

    public void visit(ANewArrayInstruction instr) {}

    public void visit(ArrayLengthInstruction instr) {}

    public void visit(ATHROWInstruction instr) {}

    public void visit(CheckCastInstruction instr) {}

    public void visit(MultiANewArrayInstruction instr) {}

    public void visit(LookupSwitchInstruction instr) {}

    public void visit(TableSwitchInstruction instr) {}

    public void visit(WideInstruction instr) {}

    public void visit(ReturnInstruction instr) {}

    public void visit(UnknownInstruction instr) {}

    public void visit(AALoadInstruction instr) {}

    public void visit(AAStoreInstruction instr) {}

    public void visit(BALOADInstruction instr) {}

    public void visit(BAStoreInstruction instr) {}

    public void visit(CALoadInstruction instr) {}

    public void visit(CAStoreInstruction instr) {}

    public void visit(SALoadInstruction instr) {}

    public void visit(DALoadInstruction instr) {}

    public void visit(DAStoreInstruction instr) {}

    public void visit(FALoadInstruction instr) {}

    public void visit(FAStoreInstruction instr) {}

    public void visit(IALoadInstruction instr) {}

    public void visit(I2LInstruction instr) {}

    public void visit(IAStoreInstruction instr) {}

    public void visit(InstanceOfInstruction instr) {}

    public void visit(LALoadInstruction instr) {}

    public void visit(LAStoreInstruction instr) {}

    public void visit(LorInstruction instr) {}

    public void visit(LXorInstruction instr) {}

    public void visit(MonitorEnterInstruction instr) {}

    public void visit(MonitorExitInstruction instr) {}

    public void visit(SAStoreInstruction instr) {}

    public void visit(WideIIncInstruction instr) {}
}

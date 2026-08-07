package com.tonic.analysis.visitor;
import com.tonic.parser.visitor.Visitor;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.MethodEntry;

import java.io.IOException;

/**
 * Base visitor over a method's decoded instructions whose per-instruction visit hooks are all no-ops.
 */
public abstract class AbstractBytecodeVisitor implements Visitor<MethodEntry>
{
    protected CodeWriter codeWriter;
    protected MethodEntry method;

    /**
     * Processes the given method by visiting its bytecode instructions.
     * @param method the method entry to process
     * @throws IOException if an I/O error occurs during processing
     */
    @Override
    public void process(MethodEntry method) throws IOException
    {
        codeWriter = new CodeWriter(method);
        this.method = method;
        codeWriter.accept(this);
        codeWriter.write();
    }

    /**
     * Visits a nop instruction.
     * @param instr the visited instruction
     */
    public void visit(NopInstruction instr) {}

    /**
     * Visits an aconst_null instruction.
     * @param instr the visited instruction
     */
    public void visit(AConstNullInstruction instr) {}

    /**
     * Visits an iconst instruction.
     * @param instr the visited instruction
     */
    public void visit(IConstInstruction instr) {}

    /**
     * Visits an lconst instruction.
     * @param instr the visited instruction
     */
    public void visit(LConstInstruction instr) {}

    /**
     * Visits an fconst instruction.
     * @param instr the visited instruction
     */
    public void visit(FConstInstruction instr) {}

    /**
     * Visits a dconst instruction.
     * @param instr the visited instruction
     */
    public void visit(DConstInstruction instr) {}

    /**
     * Visits a bipush instruction.
     * @param instr the visited instruction
     */
    public void visit(BipushInstruction instr) {}

    /**
     * Visits a sipush instruction.
     * @param instr the visited instruction
     */
    public void visit(SipushInstruction instr) {}

    /**
     * Visits an ldc instruction.
     * @param instr the visited instruction
     */
    public void visit(LdcInstruction instr) {}

    /**
     * Visits an ldc_w instruction.
     * @param instr the visited instruction
     */
    public void visit(LdcWInstruction instr) {}

    /**
     * Visits an ldc2_w instruction.
     * @param instr the visited instruction
     */
    public void visit(Ldc2WInstruction instr) {}

    /**
     * Visits an iload instruction.
     * @param instr the visited instruction
     */
    public void visit(ILoadInstruction instr) {}

    /**
     * Visits an lload instruction.
     * @param instr the visited instruction
     */
    public void visit(LLoadInstruction instr) {}

    /**
     * Visits an fload instruction.
     * @param instr the visited instruction
     */
    public void visit(FLoadInstruction instr) {}

    /**
     * Visits a dload instruction.
     * @param instr the visited instruction
     */
    public void visit(DLoadInstruction instr) {}

    /**
     * Visits an aload instruction.
     * @param instr the visited instruction
     */
    public void visit(ALoadInstruction instr) {}

    /**
     * Visits an istore instruction.
     * @param instr the visited instruction
     */
    public void visit(IStoreInstruction instr) {}

    /**
     * Visits an lstore instruction.
     * @param instr the visited instruction
     */
    public void visit(LStoreInstruction instr) {}

    /**
     * Visits an fstore instruction.
     * @param instr the visited instruction
     */
    public void visit(FStoreInstruction instr) {}

    /**
     * Visits a dstore instruction.
     * @param instr the visited instruction
     */
    public void visit(DStoreInstruction instr)
    {

    }

    /**
     * Visits an astore instruction.
     * @param instr the visited instruction
     */
    public void visit(AStoreInstruction instr) {}

    /**
     * Visits a pop instruction.
     * @param instr the visited instruction
     */
    public void visit(PopInstruction instr) {}

    /**
     * Visits a pop2 instruction.
     * @param instr the visited instruction
     */
    public void visit(Pop2Instruction instr) {}

    /**
     * Visits a dup-family stack duplication instruction.
     * @param instr the visited instruction
     */
    public void visit(DupInstruction instr) {}

    /**
     * Visits a swap instruction.
     * @param instr the visited instruction
     */
    public void visit(SwapInstruction instr) {}

    /**
     * Visits an arithmetic instruction (add/sub/mul/div/rem).
     * @param instr the visited instruction
     */
    public void visit(ArithmeticInstruction instr) {}

    /**
     * Visits an ineg instruction.
     * @param instr the visited instruction
     */
    public void visit(INegInstruction instr) {}

    /**
     * Visits an lneg instruction.
     * @param instr the visited instruction
     */
    public void visit(LNegInstruction instr) {}

    /**
     * Visits an fneg instruction.
     * @param instr the visited instruction
     */
    public void visit(FNegInstruction instr) {}

    /**
     * Visits a dneg instruction.
     * @param instr the visited instruction
     */
    public void visit(DNegInstruction instr) {}

    /**
     * Visits a shift instruction (ishl/ishr/iushr and long forms).
     * @param instr the visited instruction
     */
    public void visit(ArithmeticShiftInstruction instr) {}

    /**
     * Visits an iand instruction.
     * @param instr the visited instruction
     */
    public void visit(IAndInstruction instr) {}

    /**
     * Visits an ior instruction.
     * @param instr the visited instruction
     */
    public void visit(IOrInstruction instr) {}

    /**
     * Visits an ixor instruction.
     * @param instr the visited instruction
     */
    public void visit(IXorInstruction instr) {}

    /**
     * Visits an iinc instruction.
     * @param instr the visited instruction
     */
    public void visit(IIncInstruction instr) {}

    /**
     * Visits a primitive conversion instruction (i2f, l2d, ...).
     * @param instr the visited instruction
     */
    public void visit(ConversionInstruction instr) {}

    /**
     * Visits a narrowing conversion instruction (i2b/i2c/i2s).
     * @param instr the visited instruction
     */
    public void visit(NarrowingConversionInstruction instr) {}

    /**
     * Visits a compare instruction (lcmp/fcmpl/fcmpg/dcmpl/dcmpg).
     * @param instr the visited instruction
     */
    public void visit(CompareInstruction instr) {}

    /**
     * Visits a conditional branch instruction (if_*).
     * @param instr the visited instruction
     */
    public void visit(ConditionalBranchInstruction instr) {}

    /**
     * Visits a goto or goto_w instruction.
     * @param instr the visited instruction
     */
    public void visit(GotoInstruction instr) {}

    /**
     * Visits a jsr instruction.
     * @param instr the visited instruction
     */
    public void visit(JsrInstruction instr) {}

    /**
     * Visits a ret instruction.
     * @param instr the visited instruction
     */
    public void visit(RetInstruction instr) {}

    /**
     * Visits an invokevirtual instruction.
     * @param instr the visited instruction
     */
    public void visit(InvokeVirtualInstruction instr) {}

    /**
     * Visits an invokespecial instruction.
     * @param instr the visited instruction
     */
    public void visit(InvokeSpecialInstruction instr) {}

    /**
     * Visits an invokestatic instruction.
     * @param instr the visited instruction
     */
    public void visit(InvokeStaticInstruction instr) {}

    /**
     * Visits an invokeinterface instruction.
     * @param instr the visited instruction
     */
    public void visit(InvokeInterfaceInstruction instr) {}

    /**
     * Visits an invokedynamic instruction.
     * @param instr the visited instruction
     */
    public void visit(InvokeDynamicInstruction instr) {}

    /**
     * Visits a getfield or getstatic instruction.
     * @param instr the visited instruction
     */
    public void visit(GetFieldInstruction instr) {}

    /**
     * Visits a putfield or putstatic instruction.
     * @param instr the visited instruction
     */
    public void visit(PutFieldInstruction instr) {}

    /**
     * Visits a new instruction.
     * @param instr the visited instruction
     */
    public void visit(NewObjectInstruction instr) {}

    /**
     * Visits a newarray instruction.
     * @param instr the visited instruction
     */
    public void visit(NewPrimitiveArrayInstruction instr) {}

    /**
     * Visits an anewarray instruction.
     * @param instr the visited instruction
     */
    public void visit(ANewArrayInstruction instr) {}

    /**
     * Visits an arraylength instruction.
     * @param instr the visited instruction
     */
    public void visit(ArrayLengthInstruction instr) {}

    /**
     * Visits an athrow instruction.
     * @param instr the visited instruction
     */
    public void visit(ATHROWInstruction instr) {}

    /**
     * Visits a checkcast instruction.
     * @param instr the visited instruction
     */
    public void visit(CheckCastInstruction instr) {}

    /**
     * Visits a multianewarray instruction.
     * @param instr the visited instruction
     */
    public void visit(MultiANewArrayInstruction instr) {}

    /**
     * Visits a lookupswitch instruction.
     * @param instr the visited instruction
     */
    public void visit(LookupSwitchInstruction instr) {}

    /**
     * Visits a tableswitch instruction.
     * @param instr the visited instruction
     */
    public void visit(TableSwitchInstruction instr) {}

    /**
     * Visits a wide-prefixed instruction.
     * @param instr the visited instruction
     */
    public void visit(WideInstruction instr) {}

    /**
     * Visits a return instruction (ireturn/lreturn/freturn/dreturn/areturn/return).
     * @param instr the visited instruction
     */
    public void visit(MethodReturnInstruction instr) {}

    /**
     * Visits an unrecognized or truncated instruction.
     * @param instr the visited instruction
     */
    public void visit(UnknownInstruction instr) {}

    /**
     * Visits an aaload instruction.
     * @param instr the visited instruction
     */
    public void visit(AALoadInstruction instr) {}

    /**
     * Visits an aastore instruction.
     * @param instr the visited instruction
     */
    public void visit(AAStoreInstruction instr) {}

    /**
     * Visits a baload instruction.
     * @param instr the visited instruction
     */
    public void visit(BALOADInstruction instr) {}

    /**
     * Visits a bastore instruction.
     * @param instr the visited instruction
     */
    public void visit(BAStoreInstruction instr) {}

    /**
     * Visits a caload instruction.
     * @param instr the visited instruction
     */
    public void visit(CALoadInstruction instr) {}

    /**
     * Visits a castore instruction.
     * @param instr the visited instruction
     */
    public void visit(CAStoreInstruction instr) {}

    /**
     * Visits a saload instruction.
     * @param instr the visited instruction
     */
    public void visit(SALoadInstruction instr) {}

    /**
     * Visits a daload instruction.
     * @param instr the visited instruction
     */
    public void visit(DALoadInstruction instr) {}

    /**
     * Visits a dastore instruction.
     * @param instr the visited instruction
     */
    public void visit(DAStoreInstruction instr) {}

    /**
     * Visits an faload instruction.
     * @param instr the visited instruction
     */
    public void visit(FALoadInstruction instr) {}

    /**
     * Visits an fastore instruction.
     * @param instr the visited instruction
     */
    public void visit(FAStoreInstruction instr) {}

    /**
     * Visits an iaload instruction.
     * @param instr the visited instruction
     */
    public void visit(IALoadInstruction instr) {}

    /**
     * Visits an i2l instruction.
     * @param instr the visited instruction
     */
    public void visit(I2LInstruction instr) {}

    /**
     * Visits an iastore instruction.
     * @param instr the visited instruction
     */
    public void visit(IAStoreInstruction instr) {}

    /**
     * Visits an instanceof instruction.
     * @param instr the visited instruction
     */
    public void visit(InstanceOfInstruction instr) {}

    /**
     * Visits an laload instruction.
     * @param instr the visited instruction
     */
    public void visit(LALoadInstruction instr) {}

    /**
     * Visits an lastore instruction.
     * @param instr the visited instruction
     */
    public void visit(LAStoreInstruction instr) {}

    /**
     * Visits an lor instruction.
     * @param instr the visited instruction
     */
    public void visit(LorInstruction instr) {}

    /**
     * Visits an lxor instruction.
     * @param instr the visited instruction
     */
    public void visit(LXorInstruction instr) {}

    /**
     * Visits a monitorenter instruction.
     * @param instr the visited instruction
     */
    public void visit(MonitorEnterInstruction instr) {}

    /**
     * Visits a monitorexit instruction.
     * @param instr the visited instruction
     */
    public void visit(MonitorExitInstruction instr) {}

    /**
     * Visits a sastore instruction.
     * @param instr the visited instruction
     */
    public void visit(SAStoreInstruction instr) {}

    /**
     * Visits a wide iinc instruction.
     * @param instr the visited instruction
     */
    public void visit(WideIIncInstruction instr) {}
}

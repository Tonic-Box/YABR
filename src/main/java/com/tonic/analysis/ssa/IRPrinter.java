package com.tonic.analysis.ssa;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.Value;

/**
 * Utility class for formatting SSA IR instructions and blocks as human-readable strings.
 */
public final class IRPrinter {

    private IRPrinter() {}

    /**
     * Formats an IR instruction as a human-readable string.
     *
     * @param instr the instruction to format
     * @return the formatted string representation
     */
    public static String format(IRInstruction instr) {
        StringBuilder sb = new StringBuilder();

        if (instr.getResult() != null) {
            sb.append(instr.getResult()).append(" = ");
        }

        if (instr instanceof PhiInstruction) {
            PhiInstruction phi = (PhiInstruction) instr;
            sb.append("phi(");
            boolean first = true;
            for (java.util.Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey().getName()).append(":").append(entry.getValue());
                first = false;
            }
            sb.append(")");
        } else if (instr instanceof BinaryOpInstruction) {
            BinaryOpInstruction bin = (BinaryOpInstruction) instr;
            sb.append(bin.getOp()).append(" ").append(bin.getLeft()).append(", ").append(bin.getRight());
        } else if (instr instanceof UnaryOpInstruction) {
            UnaryOpInstruction un = (UnaryOpInstruction) instr;
            sb.append(un.getOp()).append(" ").append(un.getOperand());
        } else if (instr instanceof ConstantInstruction) {
            ConstantInstruction ci = (ConstantInstruction) instr;
            sb.append("const ").append(ci.getConstant());
        } else if (instr instanceof LoadLocalInstruction) {
            LoadLocalInstruction load = (LoadLocalInstruction) instr;
            sb.append("load local[").append(load.getLocalIndex()).append("]");
        } else if (instr instanceof StoreLocalInstruction) {
            StoreLocalInstruction store = (StoreLocalInstruction) instr;
            sb.append("store local[").append(store.getLocalIndex()).append("] = ").append(store.getValue());
        } else if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            sb.append("invoke ").append(invoke.getInvokeType()).append(" ");
            if (!invoke.getOwner().isEmpty()) {
                sb.append(invoke.getOwner()).append(".");
            }
            sb.append(invoke.getName()).append("(").append(invoke.getArguments().size()).append(" args)");
        } else if (instr instanceof GetFieldInstruction) {
            GetFieldInstruction get = (GetFieldInstruction) instr;
            sb.append("getfield ").append(get.getOwner()).append(".").append(get.getName());
        } else if (instr instanceof PutFieldInstruction) {
            PutFieldInstruction put = (PutFieldInstruction) instr;
            sb.append("putfield ").append(put.getOwner()).append(".").append(put.getName());
        } else if (instr instanceof BranchInstruction) {
            BranchInstruction br = (BranchInstruction) instr;
            sb.append("if ").append(br.getLeft()).append(" ").append(br.getCondition());
            if (br.getRight() != null) {
                sb.append(" ").append(br.getRight());
            }
            sb.append(" goto ").append(br.getTrueTarget().getName())
              .append(" else ").append(br.getFalseTarget().getName());
        } else if (instr instanceof GotoInstruction) {
            GotoInstruction gt = (GotoInstruction) instr;
            sb.append("goto ").append(gt.getTarget().getName());
        } else if (instr instanceof ReturnInstruction) {
            ReturnInstruction ret = (ReturnInstruction) instr;
            sb.append("return");
            if (ret.getReturnValue() != null) {
                sb.append(" ").append(ret.getReturnValue());
            }
        } else if (instr instanceof NewInstruction) {
            NewInstruction ni = (NewInstruction) instr;
            sb.append("new ").append(ni.getClassName());
        } else if (instr instanceof ArrayLoadInstruction) {
            ArrayLoadInstruction al = (ArrayLoadInstruction) instr;
            sb.append("arrayload ").append(al.getArray()).append("[").append(al.getIndex()).append("]");
        } else if (instr instanceof ArrayStoreInstruction) {
            ArrayStoreInstruction as = (ArrayStoreInstruction) instr;
            sb.append("arraystore ").append(as.getArray()).append("[").append(as.getIndex()).append("] = ").append(as.getValue());
        } else if (instr instanceof ThrowInstruction) {
            ThrowInstruction th = (ThrowInstruction) instr;
            sb.append("throw ").append(th.getException());
        } else if (instr instanceof CastInstruction) {
            CastInstruction cast = (CastInstruction) instr;
            sb.append("checkcast ").append(cast.getObjectRef()).append(" to ").append(cast.getTargetType());
        } else if (instr instanceof SwitchInstruction) {
            SwitchInstruction sw = (SwitchInstruction) instr;
            sb.append("switch ").append(sw.getKey()).append(" [").append(sw.getCases().size()).append(" cases]");
        } else if (instr instanceof NewArrayInstruction) {
            NewArrayInstruction na = (NewArrayInstruction) instr;
            sb.append("newarray ").append(na.getElementType());
        } else if (instr instanceof ArrayLengthInstruction) {
            ArrayLengthInstruction al = (ArrayLengthInstruction) instr;
            sb.append("arraylength ").append(al.getArray());
        } else if (instr instanceof InstanceOfInstruction) {
            InstanceOfInstruction iof = (InstanceOfInstruction) instr;
            sb.append("instanceof ").append(iof.getObjectRef()).append(" ").append(iof.getCheckType());
        } else if (instr instanceof MonitorEnterInstruction) {
            MonitorEnterInstruction me = (MonitorEnterInstruction) instr;
            sb.append("monitorenter ").append(me.getObjectRef());
        } else if (instr instanceof MonitorExitInstruction) {
            MonitorExitInstruction me = (MonitorExitInstruction) instr;
            sb.append("monitorexit ").append(me.getObjectRef());
        } else {
            sb.append(instr.getClass().getSimpleName());
        }

        return sb.toString();
    }

    /**
     * Formats an IR block header with predecessors and successors.
     *
     * @param block the block to format
     * @return the formatted string representation
     */
    public static String formatBlockHeader(IRBlock block) {
        StringBuilder sb = new StringBuilder();
        sb.append("Block: ").append(block.getName());
        sb.append("\n  Predecessors: ").append(block.getPredecessors().stream()
                .map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));
        sb.append("\n  Successors: ").append(block.getSuccessors().stream()
                .map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));
        return sb.toString();
    }

    /**
     * Formats an entire IR method as a human-readable string.
     *
     * @param method the IR method to format
     * @return the formatted string representation
     */
    public static String format(IRMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append("Method: ").append(method.getName()).append(method.getDescriptor());
        sb.append("\nBlocks: ").append(method.getBlocks().size());
        sb.append("\n");

        for (IRBlock block : method.getBlocksInOrder()) {
            sb.append("\n").append(formatBlockHeader(block)).append("\n");

            for (PhiInstruction phi : block.getPhiInstructions()) {
                sb.append("    [PHI] ").append(format(phi)).append("\n");
            }

            for (IRInstruction instr : block.getInstructions()) {
                sb.append("    ").append(format(instr)).append("\n");
            }
        }

        return sb.toString();
    }
}

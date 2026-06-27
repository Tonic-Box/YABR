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
        } else if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fai = (FieldAccessInstruction) instr;
            if (fai.isLoad()) {
                sb.append(fai.isStatic() ? "getstatic " : "getfield ");
                sb.append(fai.getOwner()).append(".").append(fai.getName());
            } else {
                sb.append(fai.isStatic() ? "putstatic " : "putfield ");
                sb.append(fai.getOwner()).append(".").append(fai.getName());
            }
        } else if (instr instanceof BranchInstruction) {
            BranchInstruction br = (BranchInstruction) instr;
            sb.append("if ").append(br.getLeft()).append(" ").append(br.getCondition());
            if (br.getRight() != null) {
                sb.append(" ").append(br.getRight());
            }
            sb.append(" goto ").append(br.getTrueTarget().getName())
              .append(" else ").append(br.getFalseTarget().getName());
        } else if (instr instanceof SimpleInstruction) {
            SimpleInstruction si = (SimpleInstruction) instr;
            switch (si.getOp()) {
                case GOTO:
                    sb.append("goto ").append(si.getTarget().getName());
                    break;
                case ATHROW:
                    sb.append("throw ").append(si.getOperand());
                    break;
                case ARRAYLENGTH:
                    sb.append("arraylength ").append(si.getOperand());
                    break;
                case MONITORENTER:
                    sb.append("monitorenter ").append(si.getOperand());
                    break;
                case MONITOREXIT:
                    sb.append("monitorexit ").append(si.getOperand());
                    break;
            }
        } else if (instr instanceof ReturnInstruction) {
            ReturnInstruction ret = (ReturnInstruction) instr;
            sb.append("return");
            if (ret.getReturnValue() != null) {
                sb.append(" ").append(ret.getReturnValue());
            }
        } else if (instr instanceof NewInstruction) {
            NewInstruction ni = (NewInstruction) instr;
            sb.append("new ").append(ni.getClassName());
        } else if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction aai = (ArrayAccessInstruction) instr;
            if (aai.isLoad()) {
                sb.append("arrayload ").append(aai.getArray()).append("[").append(aai.getIndex()).append("]");
            } else {
                sb.append("arraystore ").append(aai.getArray()).append("[").append(aai.getIndex()).append("] = ").append(aai.getValue());
            }
        } else if (instr instanceof TypeCheckInstruction) {
            TypeCheckInstruction tci = (TypeCheckInstruction) instr;
            if (tci.isCast()) {
                sb.append("checkcast ").append(tci.getOperand()).append(" to ").append(tci.getTargetType());
            } else {
                sb.append("instanceof ").append(tci.getOperand()).append(" ").append(tci.getTargetType());
            }
        } else if (instr instanceof SwitchInstruction) {
            SwitchInstruction sw = (SwitchInstruction) instr;
            sb.append("switch ").append(sw.getKey()).append(" [").append(sw.getCases().size()).append(" cases]");
        } else if (instr instanceof NewArrayInstruction) {
            NewArrayInstruction na = (NewArrayInstruction) instr;
            sb.append("newarray ").append(na.getElementType());
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

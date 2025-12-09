package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

public class DebugBBMethod {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: DebugBBMethod <classfile>");
            return;
        }

        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(args[0]));
        ConstPool constPool = cf.getConstPool();

        String targetMethod = args.length > 1 ? args[1] : "run";
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(targetMethod)) {
                System.out.println("=== Method: " + method.getName() + " ===");
                // Skip descriptor

                SSA ssa = new SSA(constPool);
                boolean optimize = args.length > 2 && args[2].equals("--optimize");
                if (optimize) {
                    ssa.withRedundantCopyElimination()
                       .withCopyPropagation()
                       .withDeadCodeElimination();
                }
                IRMethod irMethod = ssa.lift(method);
                if (optimize) {
                    ssa.runTransforms(irMethod);
                }

                System.out.println("\n=== Exception Handlers ===");
                for (ExceptionHandler handler : irMethod.getExceptionHandlers()) {
                    System.out.println("Handler: " + handler.getHandlerBlock().getName() +
                        " type=" + (handler.isCatchAll() ? "all" : handler.getCatchType()) +
                        " tryStart=" + (handler.getTryStart() != null ? handler.getTryStart().getName() : "null") +
                        " tryEnd=" + (handler.getTryEnd() != null ? handler.getTryEnd().getName() : "null"));
                }

                System.out.println("\n=== All Blocks ===");
                for (IRBlock block : irMethod.getBlocks()) {
                    System.out.println("\n--- Block: " + block.getName() + " ---");
                    System.out.println("Predecessors: " + block.getPredecessors().stream().map(IRBlock::getName).toList());
                    System.out.println("Successors: " + block.getSuccessors().stream().map(IRBlock::getName).toList());

                    // Print PHI instructions first
                    for (PhiInstruction phi : block.getPhiInstructions()) {
                        System.out.println("  [PHI] " + phi);
                    }

                    for (IRInstruction instr : block.getInstructions()) {
                        System.out.println("  " + instr);
                    }

                    IRInstruction term = block.getTerminator();
                    if (term != null) {
                        System.out.println("  [TERM] " + term);
                        if (term instanceof BranchInstruction branch) {
                            System.out.println("    trueTarget: " + branch.getTrueTarget().getName());
                            System.out.println("    falseTarget: " + branch.getFalseTarget().getName());
                        }
                    }
                }
                break;
            }
        }
    }
}

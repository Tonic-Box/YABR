package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

public class DebugLoopDetection {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DebugLoopDetection <classfile> <methodName>");
            return;
        }

        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(args[0]));
        ConstPool constPool = cf.getConstPool();
        String methodName = args[1];

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                System.out.println("=== Method: " + method.getName() + " ===");

                SSA ssa = new SSA(constPool);
                IRMethod irMethod = ssa.lift(method);

                DominatorTree domTree = new DominatorTree(irMethod);
                domTree.compute();

                LoopAnalysis loopAnalysis = new LoopAnalysis(irMethod, domTree);
                loopAnalysis.compute();

                System.out.println("\n=== Loops ===");
                for (LoopAnalysis.Loop loop : loopAnalysis.getLoops()) {
                    IRBlock header = loop.getHeader();
                    System.out.println("Loop header: " + header.getName());
                    System.out.println("  Blocks: " + loop.getBlocks().stream().map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));
                    System.out.println("  Header predecessors: " + header.getPredecessors().stream().map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));
                    
                    // Check isDoWhilePattern logic
                    boolean hasExternalPred = false;
                    for (IRBlock pred : header.getPredecessors()) {
                        if (!loop.contains(pred)) {
                            System.out.println("    Pred " + pred.getName() + " is OUTSIDE loop");
                            hasExternalPred = true;
                        } else {
                            System.out.println("    Pred " + pred.getName() + " is INSIDE loop");
                        }
                    }
                    System.out.println("  isDoWhile would return: " + !hasExternalPred);
                }
                break;
            }
        }
    }
}

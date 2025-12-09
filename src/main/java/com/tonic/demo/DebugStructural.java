package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.source.recovery.StructuralAnalyzer;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

public class DebugStructural {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DebugStructural <classfile> <methodName>");
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

                System.out.println("\n=== Loop Analysis ===");
                for (var loop : loopAnalysis.getLoops()) {
                    System.out.println("Loop header: " + loop.getHeader().getName());
                    System.out.println("  Blocks: " + loop.getBlocks().stream().map(IRBlock::getName).toList());
                }

                StructuralAnalyzer analyzer = new StructuralAnalyzer(irMethod, domTree, loopAnalysis);
                analyzer.analyze();

                System.out.println("\n=== Region Info ===");
                for (IRBlock block : irMethod.getBlocks()) {
                    var info = analyzer.getRegionInfo(block);
                    if (info != null) {
                        System.out.println(block.getName() + ": " + info.getType() + 
                            (info.getLoopBody() != null ? " body=" + info.getLoopBody().getName() : "") +
                            (info.getLoopExit() != null ? " exit=" + info.getLoopExit().getName() : "") +
                            (info.isConditionNegated() ? " NEGATED" : ""));
                    }
                }
                break;
            }
        }
    }
}

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

public class DebugLoopMapping {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DebugLoopMapping <classfile> <methodName>");
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

                System.out.println("\n=== All loops in order ===");
                int i = 0;
                for (var loop : loopAnalysis.getLoops()) {
                    System.out.println("Loop " + i++ + ": header=" + loop.getHeader().getName() +
                        " blocks=" + loop.getBlocks().stream().map(IRBlock::getName).toList());
                }

                System.out.println("\n=== Check B82 ===");
                IRBlock b82 = null;
                for (IRBlock block : irMethod.getBlocks()) {
                    if (block.getName().equals("B82")) {
                        b82 = block;
                        break;
                    }
                }

                if (b82 != null) {
                    System.out.println("isLoopHeader(B82): " + loopAnalysis.isLoopHeader(b82));
                    var loop = loopAnalysis.getLoop(b82);
                    if (loop != null) {
                        System.out.println("getLoop(B82) returns: header=" + loop.getHeader().getName() +
                            " blocks=" + loop.getBlocks().stream().map(IRBlock::getName).toList());
                    } else {
                        System.out.println("getLoop(B82) returns null!");
                    }
                }
                break;
            }
        }
    }
}

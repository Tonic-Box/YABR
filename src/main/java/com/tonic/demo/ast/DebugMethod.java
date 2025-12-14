package com.tonic.demo.ast;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.source.recovery.StructuralAnalyzer;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import java.io.FileInputStream;

public class DebugMethod {
    public static void main(String[] args) throws Exception {
        String classPath = args[0];
        String methodName = args[1];

        ClassPool pool = ClassPool.getDefault();
        ClassFile cf = pool.loadClass(new FileInputStream(classPath));

        MethodEntry method = null;
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }

        if (method == null) {
            System.out.println("Method not found: " + methodName);
            return;
        }

        SSA ssa = new SSA(cf.getConstPool());
        IRMethod ir = ssa.lift(method);

        DominatorTree domTree = new DominatorTree(ir);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(ir, domTree);
        loops.compute();

        StructuralAnalyzer analyzer = new StructuralAnalyzer(ir, domTree, loops);
        analyzer.analyze();

        System.out.println("=== Structural Analysis ===");
        for (IRBlock block : ir.getBlocks()) {
            var info = analyzer.getRegionInfo(block);
            if (info != null) {
                System.out.println("\nBlock " + block.getId() + ": " + info.getType());
                if (info.getLoopBody() != null) {
                    System.out.println("  Loop body: Block " + info.getLoopBody().getId());
                }
                if (info.getLoopExit() != null) {
                    System.out.println("  Loop exit: Block " + info.getLoopExit().getId());
                }
                if (info.getLoop() != null) {
                    System.out.println("  Loop blocks: " + info.getLoop().getBlocks().stream()
                        .map(b -> String.valueOf(b.getId()))
                        .collect(java.util.stream.Collectors.toList()));
                }
                if (info.getThenBlock() != null) {
                    System.out.println("  Then block: Block " + info.getThenBlock().getId());
                }
                if (info.getElseBlock() != null) {
                    System.out.println("  Else block: Block " + info.getElseBlock().getId());
                }
                if (info.getMergeBlock() != null) {
                    System.out.println("  Merge block: Block " + info.getMergeBlock().getId());
                }
            }
        }

        System.out.println("\n=== IR Blocks ===");
        for (IRBlock block : ir.getBlocks()) {
            System.out.println("\nBlock " + block.getId() + ":");
            System.out.println("  Successors: " + block.getSuccessors().stream().map(b -> String.valueOf(b.getId())).collect(java.util.stream.Collectors.toList()));
            System.out.println("  Predecessors: " + block.getPredecessors().stream().map(b -> String.valueOf(b.getId())).collect(java.util.stream.Collectors.toList()));
            System.out.println("  Is loop header: " + loops.isLoopHeader(block));
            if (loops.isLoopHeader(block)) {
                var loop = loops.getLoops().stream().filter(l -> l.getHeader() == block).findFirst().orElse(null);
                if (loop != null) {
                    System.out.println("  Loop blocks: " + loop.getBlocks().stream().map(b -> String.valueOf(b.getId())).collect(java.util.stream.Collectors.toList()));
                }
            }
            System.out.println("  Instructions:");
            for (IRInstruction instr : block.getInstructions()) {
                System.out.println("    " + instr);
            }
        }
    }
}

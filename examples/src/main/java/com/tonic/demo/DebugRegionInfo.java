package com.tonic.demo;

import com.tonic.analysis.source.recovery.StructuralAnalyzer;
import com.tonic.analysis.source.recovery.StructuralAnalyzer.RegionInfo;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.PostDominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

public class DebugRegionInfo {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: DebugRegionInfo <classfile>");
            return;
        }

        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(args[0]));
        ConstPool constPool = cf.getConstPool();

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals("an")) {
                System.out.println("=== Method: " + method.getName() + " ===");

                SSA ssa = new SSA(constPool);
                IRMethod irMethod = ssa.lift(method);

                DominatorTree domTree = new DominatorTree(irMethod);
                domTree.compute();
                LoopAnalysis loopAnalysis = new LoopAnalysis(irMethod, domTree);
                loopAnalysis.compute();
                PostDominatorTree postDom = new PostDominatorTree(irMethod);
                postDom.compute();

                System.out.println("\n=== Post-Dominators for Key Blocks ===");
                for (IRBlock block : irMethod.getBlocks()) {
                    IRBlock ipdom = postDom.getImmediatePostDominator(block);
                    System.out.println(block.getName() + " -> ipdom: " + (ipdom != null ? ipdom.getName() : "null"));
                }

                StructuralAnalyzer analyzer = new StructuralAnalyzer(irMethod, domTree, loopAnalysis);
                analyzer.analyze();

                System.out.println("\n=== Region Info for All Blocks ===");
                for (IRBlock block : irMethod.getBlocks()) {
                    RegionInfo info = analyzer.getRegionInfo(block);
                    System.out.println("\nBlock " + block.getName() + ":");
                    if (info == null) {
                        System.out.println("  RegionInfo: null");
                    } else {
                        System.out.println("  Type: " + info.getType());
                        if (info.getMergeBlock() != null) {
                            System.out.println("  Merge: " + info.getMergeBlock().getName());
                        }
                        if (info.getThenBlock() != null) {
                            System.out.println("  ThenBlock: " + info.getThenBlock().getName());
                        }
                        if (info.getElseBlock() != null) {
                            System.out.println("  ElseBlock: " + info.getElseBlock().getName());
                        }
                        if (info.getLoopExit() != null) {
                            System.out.println("  LoopExit: " + info.getLoopExit().getName());
                        }
                    }
                }
                break;
            }
        }
    }
}

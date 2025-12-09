package com.tonic.demo;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.ClassFileUtil;
import com.tonic.utill.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Demonstrates the SSA-form IR system capabilities using SSAShowcase class.
 */
public class TestSSADemo {

    private static int successCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) throws IOException {
        Logger.setLog(false);
        ClassPool classPool = ClassPool.getDefault();

        try (InputStream is = TestSSADemo.class.getResourceAsStream("SSAShowcase.class")) {
            if (is == null) {
                throw new IOException("Resource 'SSAShowcase.class' not found. Make sure it's compiled to resources.");
            }

            ClassFile classFile = classPool.loadClass(is);
            ConstPool constPool = classFile.getConstPool();

            System.out.println("SSA IR System Demo");
            System.out.println("Class: " + classFile.getClassName());
            System.out.println("Methods: " + classFile.getMethods().size());
            System.out.println();

            List<String> showcaseMethods = List.of(
                "constantFolding",
                "partialConstantFolding",
                "copyPropagation",
                "deadCodeElimination",
                "deadStoreElimination",
                "simpleConditional",
                "nestedConditional",
                "simpleLoop",
                "combinedOptimizations",
                "arithmetic",
                // Strength Reduction tests
                "strengthReductionMul",
                "strengthReductionDiv",
                "strengthReductionMod",
                // Algebraic Simplification tests
                "algebraicAddSub",
                "algebraicMul",
                "algebraicBitwise",
                "algebraicSelfOps",
                // Combined new optimizations
                "combinedNewOptimizations",
                // New optimization tests
                "phiConstantProp",
                "peepholeOpt",
                "commonSubexpr",
                "nullCheckTest",
                "conditionalConst",
                "loopInvariant",
                "inductionVar",
                // Reassociation tests
                "reassociateConstants",
                "reassociateMul",
                "reassociateMultiVar",
                // Loop Predication tests
                "loopPredicationSimple",
                "loopPredicationRedundant",
                "loopPredicationLimit",
                // Bit-Tracking DCE tests
                "bdceMaskDead",
                "bdceShiftMask",
                "bdceCascade",
                "bdceAllLive",
                // Correlated Value Propagation tests
                "cvpRedundantCheck",
                "cvpImpossibleCheck",
                "cvpNestedRange",
                "cvpNoOptimization"
            );

            for (MethodEntry method : classFile.getMethods()) {
                String methodName = method.getName();

                if (methodName.startsWith("<") || !showcaseMethods.contains(methodName)) {
                    continue;
                }

                if (method.getCodeAttribute() == null) {
                    continue;
                }

                processMethod(method, constPool);
            }

            printSummary();

            classFile.setClassName("SSAShowcase_out");
            classFile.computeFrames();
            classFile.rebuild();

            ClassFileUtil.saveClassFile(classFile.write(), "C:\\test\\new", "SSAShowcase_out");
            System.out.println("Output: SSAShowcase_out.class");
        }
    }

    private static void processMethod(MethodEntry method, ConstPool constPool) {
        String methodName = method.getName();
        String desc = method.getDesc();

        System.out.println("--- " + methodName + desc + " ---");

        try {
            analyzeAndTransformMethod(method, constPool);
            successCount++;
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(System.out);
            failCount++;
        }
        System.out.println();
    }

    private static void analyzeAndTransformMethod(MethodEntry method, ConstPool constPool) {
        SSA ssa = new SSA(constPool);

        // Lift to SSA
        IRMethod irMethod = ssa.lift(method);

        if (irMethod.getEntryBlock() == null) {
            System.out.println("(empty method)");
            return;
        }

        int blockCount = irMethod.getBlocks().size();
        int instrCount = countInstructions(irMethod);
        int phiCount = countPhis(irMethod);
        System.out.println("Lifted: " + blockCount + " blocks, " + instrCount + " instrs, " + phiCount + " phis");

        // Analysis
        DominatorTree domTree = ssa.computeDominators(irMethod);
        LivenessAnalysis liveness = ssa.computeLiveness(irMethod);
        DefUseChains defUse = ssa.computeDefUse(irMethod);
        LoopAnalysis loops = ssa.computeLoops(irMethod);

        int domTreeDepth = computeDomTreeDepth(domTree, irMethod);
        int maxLiveVars = computeMaxLiveVars(liveness, irMethod);
        System.out.println("Analysis: " + countDefinitions(defUse) + " defs, " +
                          countUses(defUse) + " uses, " + loops.getLoops().size() + " loops, " +
                          "domDepth=" + domTreeDepth + ", maxLive=" + maxLiveVars);

        // IR before optimization
        System.out.println("\nBefore:");
        int instrBefore = countInstructions(irMethod);
        printIRDetails(irMethod);

        // Optimize with all available transforms including new ones
        SSA optimizer = new SSA(constPool).withAllOptimizations();
        optimizer.runTransforms(irMethod);

        // IR after optimization
        System.out.println("\nAfter:");
        int instrAfter = countInstructions(irMethod);
        printIRDetails(irMethod);

        int eliminated = instrBefore - instrAfter;
        if (eliminated > 0) {
            System.out.println("Eliminated " + eliminated + " instrs (" +
                String.format("%.1f", (eliminated * 100.0 / instrBefore)) + "%)");
        }

        // Lower back to bytecode
        ssa.lower(irMethod, method);
        CodeWriter cw = new CodeWriter(method);
        System.out.println("Lowered: " + cw.getBytecode().length + " bytes");
    }

    private static int countInstructions(IRMethod irMethod) {
        int count = 0;
        for (IRBlock block : irMethod.getBlocks()) {
            count += block.getInstructions().size();
        }
        return count;
    }

    private static int countPhis(IRMethod irMethod) {
        int count = 0;
        for (IRBlock block : irMethod.getBlocks()) {
            count += block.getPhiInstructions().size();
        }
        return count;
    }

    private static int countDefinitions(DefUseChains defUse) {
        return defUse.getDefinitions().size();
    }

    private static int countUses(DefUseChains defUse) {
        int total = 0;
        for (SSAValue value : defUse.getDefinitions().keySet()) {
            total += defUse.getUses(value).size();
        }
        return total;
    }

    private static void printIRDetails(IRMethod irMethod) {
        for (IRBlock block : irMethod.getBlocksInOrder()) {
            System.out.println("  " + block.getName() + ":");

            for (PhiInstruction phi : block.getPhiInstructions()) {
                System.out.println("    " + IRPrinter.format(phi));
            }

            for (IRInstruction instr : block.getInstructions()) {
                System.out.println("    " + IRPrinter.format(instr));
            }
        }
    }

    private static void printSummary() {
        System.out.println("Summary: " + successCount + " passed, " + failCount + " failed, " +
                          (successCount + failCount) + " total");
    }

    private static int computeDomTreeDepth(DominatorTree domTree, IRMethod irMethod) {
        if (irMethod.getEntryBlock() == null) return 0;
        return computeDepth(domTree, irMethod.getEntryBlock(), 0);
    }

    private static int computeDepth(DominatorTree domTree, IRBlock block, int currentDepth) {
        int maxDepth = currentDepth;
        for (IRBlock child : domTree.getDominatorTreeChildren(block)) {
            maxDepth = Math.max(maxDepth, computeDepth(domTree, child, currentDepth + 1));
        }
        return maxDepth;
    }

    private static int computeMaxLiveVars(LivenessAnalysis liveness, IRMethod irMethod) {
        int max = 0;
        for (IRBlock block : irMethod.getBlocks()) {
            int liveIn = liveness.getLiveIn().getOrDefault(block, java.util.Collections.emptySet()).size();
            int liveOut = liveness.getLiveOut().getOrDefault(block, java.util.Collections.emptySet()).size();
            max = Math.max(max, Math.max(liveIn, liveOut));
        }
        return max;
    }
}

package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

public class DebugAnalyzeLoop {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DebugAnalyzeLoop <classfile> <methodName>");
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

                System.out.println("\n=== Analyzing loops ===");
                for (LoopAnalysis.Loop loop : loopAnalysis.getLoops()) {
                    IRBlock header = loop.getHeader();
                    System.out.println("\n--- Loop header: " + header.getName() + " ---");
                    System.out.println("Loop blocks: " + loop.getBlocks().stream().map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));

                    IRInstruction terminator = header.getTerminator();
                    if (terminator instanceof BranchInstruction) {
                        BranchInstruction branch = (BranchInstruction) terminator;
                        IRBlock trueTarget = branch.getTrueTarget();
                        IRBlock falseTarget = branch.getFalseTarget();

                        System.out.println("Branch trueTarget: " + trueTarget.getName());
                        System.out.println("Branch falseTarget: " + falseTarget.getName());
                        System.out.println("loop.contains(trueTarget): " + loop.contains(trueTarget));
                        System.out.println("loop.contains(falseTarget): " + loop.contains(falseTarget));

                        // Simulate analyzeLoop logic
                        IRBlock bodyBlock;
                        IRBlock exitBlock;
                        boolean conditionNegated;

                        if (loop.contains(trueTarget) && !loop.contains(falseTarget)) {
                            bodyBlock = trueTarget;
                            exitBlock = falseTarget;
                            conditionNegated = false;
                            System.out.println("Case 1: true in loop, false out -> body=" + bodyBlock.getName() + ", exit=" + exitBlock.getName());
                        } else if (loop.contains(falseTarget) && !loop.contains(trueTarget)) {
                            bodyBlock = falseTarget;
                            exitBlock = trueTarget;
                            conditionNegated = true;
                            System.out.println("Case 2: false in loop, true out -> body=" + bodyBlock.getName() + ", exit=" + exitBlock.getName() + " NEGATED");
                        } else if (loop.contains(trueTarget) && loop.contains(falseTarget)) {
                            bodyBlock = trueTarget;
                            exitBlock = null;
                            conditionNegated = false;
                            System.out.println("Case 3: both in loop -> body=" + bodyBlock.getName() + ", exit=null");
                        } else {
                            System.out.println("Case 4: IRREDUCIBLE (both outside)");
                            continue;
                        }

                        // Check isDoWhilePattern
                        boolean isDoWhile = true;
                        for (IRBlock pred : header.getPredecessors()) {
                            if (!loop.contains(pred)) {
                                System.out.println("isDoWhilePattern: pred " + pred.getName() + " is OUTSIDE loop -> NOT do-while");
                                isDoWhile = false;
                                break;
                            } else {
                                System.out.println("isDoWhilePattern: pred " + pred.getName() + " is INSIDE loop");
                            }
                        }
                        System.out.println("isDoWhilePattern result: " + isDoWhile);

                        // Check isForLoopPattern (simplified)
                        boolean isForLoop = false;
                        for (IRBlock block : loop.getBlocks()) {
                            if (block == header) continue;
                            for (IRBlock succ : block.getSuccessors()) {
                                if (succ == header) {
                                    System.out.println("isForLoopPattern: " + block.getName() + " has back-edge to header");
                                    // Check for increment
                                    for (IRInstruction instr : block.getInstructions()) {
                                        if (instr instanceof com.tonic.analysis.ssa.ir.BinaryOpInstruction) {
                                            com.tonic.analysis.ssa.ir.BinaryOpInstruction binOp = (com.tonic.analysis.ssa.ir.BinaryOpInstruction) instr;
                                            com.tonic.analysis.ssa.ir.BinaryOp op = binOp.getOp();
                                            if (op == com.tonic.analysis.ssa.ir.BinaryOp.ADD || op == com.tonic.analysis.ssa.ir.BinaryOp.SUB) {
                                                System.out.println("  Found increment: " + op);
                                                isForLoop = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        System.out.println("isForLoopPattern result: " + isForLoop);

                        // Final decision
                        String loopType;
                        if (isDoWhile) {
                            loopType = "DO_WHILE_LOOP";
                        } else if (isForLoop) {
                            loopType = "FOR_LOOP";
                        } else {
                            loopType = "WHILE_LOOP";
                        }
                        System.out.println("FINAL: " + loopType + " body=" + (bodyBlock != null ? bodyBlock.getName() : "null") +
                            " exit=" + (exitBlock != null ? exitBlock.getName() : "null"));
                    } else {
                        System.out.println("Header terminator is NOT a branch: " + (terminator != null ? terminator.getClass().getSimpleName() : "null"));
                    }
                }
                break;
            }
        }
    }
}

package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.BinaryOpInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

public class DebugForLoop {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DebugForLoop <classfile> <methodName>");
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

                for (com.tonic.analysis.ssa.analysis.LoopAnalysis.Loop loop : loopAnalysis.getLoops()) {
                    IRBlock header = loop.getHeader();
                    System.out.println("\nLoop header: " + header.getName());
                    
                    // Check isForLoopPattern
                    for (IRBlock block : loop.getBlocks()) {
                        if (block == header) continue;
                        System.out.println("  Checking block " + block.getName());
                        System.out.println("    Successors: " + block.getSuccessors().stream().map(IRBlock::getName).collect(java.util.stream.Collectors.toList()));
                        
                        for (IRBlock succ : block.getSuccessors()) {
                            if (succ == header) {
                                System.out.println("    Has back-edge to header");
                                // Check for increment
                                boolean hasIncrement = false;
                                for (IRInstruction instr : block.getInstructions()) {
                                    if (instr instanceof BinaryOpInstruction) {
                                        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                                        BinaryOp op = binOp.getOp();
                                        System.out.println("      Found BinaryOp: " + op);
                                        if (op == BinaryOp.ADD || op == BinaryOp.SUB) {
                                            hasIncrement = true;
                                        }
                                    }
                                }
                                System.out.println("    hasIncrement: " + hasIncrement);
                            }
                        }
                    }
                }
                break;
            }
        }
    }
}

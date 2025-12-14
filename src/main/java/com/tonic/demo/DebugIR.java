package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

/**
 * Debug utility to inspect SSA IR for a method.
 */
public class DebugIR {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DebugIR <path-to-class-file> <method-name>");
            return;
        }

        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(args[0]));
        ConstPool constPool = cf.getConstPool();
        String targetMethod = args[1];

        for (MethodEntry method : cf.getMethods()) {
            String methodName = method.getName();
            String methodDesc = method.getDesc();
            // Match by name, or by name+desc if target includes descriptor
            boolean matches = targetMethod.equals(methodName) ||
                             targetMethod.equals(methodName + methodDesc);
            if (!matches) {
                continue;
            }

            System.out.println("=== Method: " + methodName + " ===");

            if (method.getCodeAttribute() == null) {
                System.out.println("No code attribute");
                continue;
            }

            SSA ssa = new SSA(constPool);
            IRMethod irMethod = ssa.lift(method);

            // Print ALL blocks including PHIs
            for (IRBlock block : irMethod.getBlocks()) {
                System.out.println("\nBlock " + block.getId() + ":");
                // Print PHI instructions
                for (var phi : block.getPhiInstructions()) {
                    System.out.println("  [PHI] " + phi);
                }
                for (IRInstruction instr : block.getInstructions()) {
                    System.out.println("  " + instr);
                    if (instr instanceof InvokeInstruction) {
                        InvokeInstruction inv = (InvokeInstruction) instr;
                        SSAValue result = inv.getResult();
                        if (result != null) {
                            System.out.println("    ^ invoke result=" + result + ", uses=" + result.getUses());
                        }
                    }
                }
            }
            break;
        }
    }
}

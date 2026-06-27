package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.llvm.LlvmLowering;
import com.tonic.analysis.ssa.llvm.LlvmLoweringConfig;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

/**
 * Demo: lift each method of a class to SSA and lower it to LLVM IR using the full object/runtime
 * model (the {@code jvm_*} runtime ABI). Any method that still cannot be lowered is reported as
 * skipped (its unsupported construct named).
 *
 * <p>Usage: {@code LlvmLoweringDemo <path-to-class-file>}
 */
public final class LlvmLoweringDemo {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: LlvmLoweringDemo <path-to-class-file>");
            return;
        }
        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(args[0]));
        LlvmLowering llvm = new LlvmLowering(LlvmLoweringConfig.fullObjectModel());

        for (MethodEntry method : cf.getMethods()) {
            if (method.getCodeAttribute() == null) {
                continue;
            }
            IRMethod ir = new SSA(cf.getConstPool()).lift(method);
            if (ir == null) {
                continue;
            }
            String header = "; ---- " + method.getName() + method.getDesc() + " ----";
            try {
                System.out.println(header);
                System.out.println(llvm.lower(ir));
            } catch (UnsupportedOperationException e) {
                System.out.println(header + " [skipped: " + e.getMessage() + "]");
            }
            System.out.println();
        }
    }
}

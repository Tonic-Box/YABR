package com.tonic.demo.ast;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.value.LongConstant;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;

/**
 * Demo application for decompiling Java class files to source code.
 */
public class Decompile {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: Decompile <path-to-class-file> [--simple]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --simple    Use simple class names (default)");
            System.out.println("  --fqn       Use fully qualified class names");
            return;
        }

        // Parse options
        boolean useFqn = false;
        String classPath = null;

        for (String arg : args) {
            if (arg.equals("--fqn")) {
                useFqn = true;
            } else if (arg.equals("--simple")) {
                useFqn = false;
            } else if (!arg.startsWith("--")) {
                classPath = arg;
            }
        }

        if (classPath == null) {
            System.err.println("Error: No class file specified");
            return;
        }

        // Load the class file
        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(classPath));

        for (MethodEntry method : cf.getMethods()) {
            if (method.getCodeAttribute() == null) continue;

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod irMethod = ssa.lift(method);
            if (irMethod == null) continue;

            boolean has1000L = irMethod.getBlocks().stream()
                    .flatMap(b -> b.getInstructions().stream())
                    .anyMatch(insn -> insn instanceof ConstantInstruction ci
                            && ci.getConstant() instanceof LongConstant lc
                            && lc.getValue().equals(1000L));

            if (has1000L) {
                System.out.println("# Found: " + method.getName() + method.getDesc());
                BlockStmt ast = MethodRecoverer.recoverMethod(irMethod, method);
                System.out.println(SourceEmitter.emit(ast));
                System.out.println("\n\n");
            }
        }

//        // Configure the emitter
//        SourceEmitterConfig config = SourceEmitterConfig.builder()
//                .useFullyQualifiedNames(useFqn)
//                .alwaysUseBraces(true)
//                .useVarKeyword(false)
//                .build();
//
//        // Decompile and print
//        String source = ClassDecompiler.decompile(cf, config);
//        System.out.println(source);
    }
}

package com.tonic.demo.ast;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompilerConfig;
import com.tonic.analysis.source.decompile.TransformPreset;
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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demo application for decompiling Java class files to source code.
 */
public class Decompile {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: Decompile <path-to-class-file> [options]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --simple       Use simple class names (default)");
            System.out.println("  --fqn          Use fully qualified class names");
            System.out.println("  --preset=NAME  Use transform preset: none, minimal, standard, aggressive");
            return;
        }

        // Parse options
        boolean useFqn = false;
        String classPath = null;
        TransformPreset preset = TransformPreset.NONE;

        for (String arg : args) {
            if (arg.equals("--fqn")) {
                useFqn = true;
            } else if (arg.equals("--simple")) {
                useFqn = false;
            } else if (arg.startsWith("--preset=")) {
                String presetName = arg.substring("--preset=".length()).toUpperCase();
                try {
                    preset = TransformPreset.valueOf(presetName);
                } catch (IllegalArgumentException e) {
                    System.err.println("Unknown preset: " + presetName);
                    System.err.println("Available presets: none, minimal, standard, aggressive");
                    return;
                }
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
                    .anyMatch(insn -> {
                        if (insn instanceof ConstantInstruction) {
                            ConstantInstruction ci = (ConstantInstruction) insn;
                            if (ci.getConstant() instanceof LongConstant) {
                                LongConstant lc = (LongConstant) ci.getConstant();
                                return lc.getValue().equals(1000L);
                            }
                        }
                        return false;
                    });

            if (has1000L) {
                System.out.println("# Found: " + method.getName() + method.getDesc());
                BlockStmt ast = MethodRecoverer.recoverMethod(irMethod, method);
                System.out.println(SourceEmitter.emit(ast));
                System.out.println("\n\n");
            }
        }

        // Configure the emitter
        SourceEmitterConfig emitterConfig = SourceEmitterConfig.builder()
                .useFullyQualifiedNames(useFqn)
                .alwaysUseBraces(true)
                .useVarKeyword(false)
                .build();

        // Configure decompiler with preset
        DecompilerConfig decompilerConfig = DecompilerConfig.builder()
                .emitterConfig(emitterConfig)
                .preset(preset)
                .build();

        // Decompile and print
        System.out.println("Decompiling class: " + cf.getClassName());
        String source = new ClassDecompiler(cf, decompilerConfig).decompile();
        Files.writeString(Path.of(cf.getClassName().replace("/", ".") + ".java"), source);

        System.out.println("Decompiled with preset: " + preset);
        System.out.println("Output written to: output.java");
    }
}

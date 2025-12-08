package com.tonic.demo.ast;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

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

        // Configure the emitter
        SourceEmitterConfig config = SourceEmitterConfig.builder()
                .useFullyQualifiedNames(useFqn)
                .alwaysUseBraces(true)
                .useVarKeyword(false)
                .build();

        // Decompile and print
        String source = ClassDecompiler.decompile(cf, config);
        System.out.println(source);
    }
}

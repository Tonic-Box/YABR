package com.tonic.analysis;

import com.tonic.analysis.instruction.InvokeDynamicInstruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import com.tonic.type.MethodHandle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the verbose disassembler resolves an invokedynamic's bootstrap and renders a
 * StringConcatFactory recipe with {@code {arg}} markers rather than leaking the raw {@code } tag.
 */
class DisassemblyBootstrapTest {

    private static final char TAG_ARG = (char) 1;
    private static final String RECIPE = "a" + TAG_ARG + "b";

    private static final MethodHandle CONCAT_BSM = new MethodHandle(MethodHandle.H_INVOKESTATIC,
            "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;");

    private static String disassembleConcat() {
        ClassFile cf = ClassBuilder.create("DynPrint")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "concat", "(I)V")
                    .code()
                        .iload(0)
                        .invokedynamic("makeConcatWithConstants", "(I)Ljava/lang/String;", CONCAT_BSM, RECIPE)
                        .pop()
                        .vreturn()
                    .end()
                .end()
                .build();
        MethodEntry method = cf.getMethod("concat", "(I)V");
        return CodePrinter.prettyPrintCode(method.getCodeAttribute(), DisassemblyOptions.verbose());
    }

    @Test
    void verboseRendersRecipeArgMarker() {
        String output = disassembleConcat();
        assertTrue(output.contains("BSM:"), output);
        assertTrue(output.contains("makeConcatWithConstants"), output);
        assertTrue(output.contains("{arg}"), output);
    }

    @Test
    void verboseDoesNotLeakRawRecipeTag() {
        String output = disassembleConcat();
        assertFalse(output.indexOf(TAG_ARG) >= 0, "raw \\u0001 tag must not appear in output");
    }

    /**
     * A concat whose {@code {const}} is a CONSTANT_Dynamic backed by {@code ConstantBootstraps.invoke} of a
     * MethodHandle - the gamepack shape. The disassembly must surface the nested bootstrap AND the method it
     * invokes (the MethodHandle argument), not an opaque {@code UnknownReference}.
     */
    @Test
    void verboseShowsNestedCondyBootstrapCall() {
        ClassFile cf = ClassBuilder.create("DynCondy")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "concat", "(I)V")
                    .code()
                        .iload(0)
                        .invokedynamic("makeConcatWithConstants", "(I)Ljava/lang/String;", CONCAT_BSM, RECIPE)
                        .pop()
                        .vreturn()
                    .end()
                .end()
                .build();

        ConstPool cp = cf.getConstPool();
        int mhArg = cp.getIndexOf(cp.findOrAddMethodHandle(MethodHandle.H_INVOKESTATIC,
                "Callee", "call", "()Ljava/lang/String;"));
        int condyBsm = cf.addBootstrapMethod(
                cp.getIndexOf(cp.findOrAddMethodHandle(MethodHandle.H_INVOKESTATIC,
                        "java/lang/invoke/ConstantBootstraps", "invoke",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;"
                                + "Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;")),
                List.of(mhArg));
        int condyCp = cp.getIndexOf(cp.findOrAddConstantDynamic(condyBsm, "C", "Ljava/lang/String;"));

        MethodEntry concat = cf.getMethod("concat", "(I)V");
        int concatBsm = new CodeWriter(concat).getInstructionList().stream()
                .filter(InvokeDynamicInstruction.class::isInstance)
                .map(i -> ((InvokeDynamicInstruction) i).getBootstrapMethodAttrIndex())
                .findFirst().orElseThrow();
        cf.getBootstrapMethodsAttribute().getBootstrapMethods().get(concatBsm).getBootstrapArguments().add(condyCp);

        String out = CodePrinter.prettyPrintCode(concat.getCodeAttribute(), DisassemblyOptions.verbose());
        assertTrue(out.contains("condy"), out);
        assertTrue(out.contains("ConstantBootstraps.invoke"), out);
        assertTrue(out.contains("Callee.call"), out);
    }
}

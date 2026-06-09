package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.builder.CodeBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.type.MethodHandle;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises {@link CodeBuilder#detached()} + {@link CodeBuilder#assemble(ClassFile)}: a snippet is
 * hand-assembled against a target's constant pool and spliced into an already-parsed method via
 * {@code replaceBody(ClonedRange)}. Covers straight-line code, a forward branch/label (proving the
 * identity targets relink + frames regenerate), and a String {@code ldc} (proving the constant is
 * added to the target pool and the spliced index resolves).
 */
class CodeBuilderSnippetTest {

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static void replaceWith(ClassFile cf, String name, CodeWriter.ClonedRange snippet) throws Exception {
        CodeWriter cw = new CodeWriter(method(cf, name));
        cw.replaceBody(snippet);
        cw.write();
    }

    @Test
    void assembleAndSpliceDetachedSnippets() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        pool.loadPlatformClass("java/lang/Object.class");
        pool.loadPlatformClass("java/lang/String.class");

        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        ClassFile calc = pool.createNewClass("Calc", new AccessBuilder().setPublic().build());
        calc.createNewMethodWithDescriptor(pubStatic, "add", "(II)I");
        calc.createNewMethodWithDescriptor(pubStatic, "abs", "(I)I");
        calc.createNewMethodWithDescriptor(pubStatic, "tag", "()Ljava/lang/String;");

        int methodsBefore = calc.getMethods().size();

        CodeWriter.ClonedRange add = CodeBuilder.detached()
                .iload(0).iload(1).iadd().ireturn()
                .assemble(calc);

        // Forward branch over a label: return |n| without a builtin, forcing identity-target relink.
        CodeWriter.ClonedRange abs = CodeBuilder.detached()
                .iload(0).ifge("pos")
                .iload(0).ineg().ireturn()
                .label("pos")
                .iload(0).ireturn()
                .assemble(calc);

        CodeWriter.ClonedRange tag = CodeBuilder.detached()
                .ldc("hi").areturn()
                .assemble(calc);

        // assemble() must leave no trace: its throwaway scratch method is never registered.
        assertEquals(methodsBefore, calc.getMethods().size(), "assemble leaked a scratch method");

        replaceWith(calc, "add", add);
        replaceWith(calc, "abs", abs);
        replaceWith(calc, "tag", tag);

        Class<?> clazz = TestUtils.loadAndVerify(calc);
        assertEquals(7, (int) clazz.getMethod("add", int.class, int.class).invoke(null, 3, 4));
        assertEquals(5, (int) clazz.getMethod("abs", int.class).invoke(null, -5));
        assertEquals(5, (int) clazz.getMethod("abs", int.class).invoke(null, 5));
        assertEquals(0, (int) clazz.getMethod("abs", int.class).invoke(null, 0));
        assertEquals("hi", clazz.getMethod("tag").invoke(null));
    }

    @Test
    void assembleAndSpliceInvokeDynamic() throws Exception {
        // A detached invokedynamic must add its bootstrap to the target's BootstrapMethods (no parent
        // ClassBuilder). StringConcatFactory is on the Java 11 test JVM, so the spliced call links + runs.
        ClassPool pool = TestUtils.emptyPool();
        pool.loadPlatformClass("java/lang/Object.class");
        pool.loadPlatformClass("java/lang/String.class");

        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        ClassFile cf = pool.createNewClass("Concat", new AccessBuilder().setPublic().build());
        cf.createNewMethodWithDescriptor(pubStatic, "tag", "(I)Ljava/lang/String;");

        MethodHandle bootstrap = new MethodHandle(
                MethodHandle.H_INVOKESTATIC,
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;");

        CodeWriter.ClonedRange snippet = CodeBuilder.detached()
                .iload(0)
                .invokedynamic("makeConcatWithConstants", "(I)Ljava/lang/String;", bootstrap, "v")
                .areturn()
                .assemble(cf);

        assertNotNull(cf.getBootstrapMethodsAttribute(), "assemble did not create a BootstrapMethods attribute");
        assertEquals(1, cf.getBootstrapMethodsAttribute().getBootstrapMethods().size());

        replaceWith(cf, "tag", snippet);

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals("v5", clazz.getMethod("tag", int.class).invoke(null, 5));
    }

    @Test
    void assembleAndSpliceTryCatch() throws Exception {
        // try { return o.hashCode(); } catch (NullPointerException e) { return -1; }
        ClassPool pool = TestUtils.emptyPool();
        pool.loadPlatformClass("java/lang/Object.class");

        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        ClassFile cf = pool.createNewClass("Guard", new AccessBuilder().setPublic().build());
        cf.createNewMethodWithDescriptor(pubStatic, "hash", "(Ljava/lang/Object;)I");

        CodeWriter.ClonedRange snippet = CodeBuilder.detached()
                .label("try")
                .aload(0)
                .invokevirtual("java/lang/Object", "hashCode", "()I")
                .ireturn()
                .label("handler")
                .pop()
                .iconst(-1)
                .ireturn()
                .trycatch("try", "handler", "handler", "java/lang/NullPointerException")
                .assemble(cf);

        replaceWith(cf, "hash", snippet);

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(42, (int) clazz.getMethod("hash", Object.class).invoke(null, 42));
        assertEquals(-1, (int) clazz.getMethod("hash", Object.class).invoke(null, new Object[]{null}));
    }
}

package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeDynamicInstruction;
import com.tonic.analysis.instruction.InvokeInsn;
import com.tonic.analysis.instruction.InvokeInterfaceInstruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.InvokeStaticInstruction;
import com.tonic.analysis.instruction.InvokeVirtualInstruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the convenience gaps: CodeWriter.getInstructionList, ClassFile lookups, InvokeInsn. */
class ApiConvenienceTest {

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void getInstructionListIsOrderedAndIdentityIndexed() {
        ClassFile cf = ClassBuilder.create("IL")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "m", "()I")
                .code().iconst(1).iconst(2).iadd().ireturn().end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "m"));
        List<Instruction> list = cw.getInstructionList();

        List<Instruction> iterated = new ArrayList<>();
        cw.getInstructions().forEach(iterated::add);
        assertEquals(iterated, list, "list must match iteration order");
        assertEquals(cw.getInstructionCount(), list.size());
        assertEquals(0, list.indexOf(list.get(0)), "indexOf resolves by identity");
    }

    @Test
    void classFileByNameLookups() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.createNewClass("T", new AccessBuilder().setPublic().build());
        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethodWithDescriptor(pubStatic, "foo", "(I)I");
        cf.createNewMethodWithDescriptor(pubStatic, "foo", "(J)J");
        cf.createNewMethodWithDescriptor(pubStatic, "<clinit>", "()V");
        cf.createNewField(new AccessBuilder().setPublic().build(), "x", "I", new ArrayList<>());
        cf.addInterface("java/lang/Runnable");

        assertNotNull(cf.getMethod("foo"));
        assertEquals("(J)J", cf.getMethod("foo", "(J)J").getDesc());
        assertNull(cf.getMethod("foo", "(D)D"));
        assertEquals(2, cf.getMethods("foo").size());
        assertNotNull(cf.getStaticInitializer());
        assertNotNull(cf.getField("x"));
        assertNull(cf.getField("nope"));
        assertEquals(List.of("java/lang/Runnable"), cf.getInterfaceNames());
    }

    @Test
    void invokeInsnUnifiesTheFourInvokesAndExcludesIndy() {
        // Type relationship: the four owner-bearing invokes implement InvokeInsn; invokedynamic doesn't.
        assertTrue(InvokeInsn.class.isAssignableFrom(InvokeVirtualInstruction.class));
        assertTrue(InvokeInsn.class.isAssignableFrom(InvokeSpecialInstruction.class));
        assertTrue(InvokeInsn.class.isAssignableFrom(InvokeStaticInstruction.class));
        assertTrue(InvokeInsn.class.isAssignableFrom(InvokeInterfaceInstruction.class));
        assertFalse(InvokeInsn.class.isAssignableFrom(InvokeDynamicInstruction.class));

        // Behavioural: extract the callee uniformly from a real invokevirtual + invokestatic body
        // (return Integer.parseInt(s.trim())).
        ClassFile calls = ClassBuilder.create("Calls")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "parse", "(Ljava/lang/String;)I")
                .code()
                    .aload(0)
                    .invokevirtual("java/lang/String", "trim", "()Ljava/lang/String;")
                    .invokestatic("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I")
                    .ireturn()
                .end().end().build();

        int invokes = 0;
        for (Instruction i : new CodeWriter(method(calls, "parse")).getInstructions()) {
            if (i instanceof InvokeInsn) {
                InvokeInsn call = (InvokeInsn) i;
                assertNotNull(call.getOwnerClass());
                assertNotNull(call.getMethodName());
                assertNotNull(call.getMethodDescriptor());
                if (call.getMethodName().equals("parseInt")) {
                    assertEquals("java/lang/Integer", call.getOwnerClass());
                    assertTrue(call.isStatic());
                }
                invokes++;
            }
        }
        assertEquals(2, invokes, "both the invokevirtual and invokestatic are InvokeInsn");
    }
}

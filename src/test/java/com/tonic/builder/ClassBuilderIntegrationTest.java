package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;

class ClassBuilderIntegrationTest {

    @Nested
    class SimpleClassGeneration {

        @Test
        void generateSimpleClass() {
            ClassFile cf = ClassBuilder.create("com/test/SimpleClass")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_PUBLIC)

                .addMethod(AccessFlags.ACC_PUBLIC, "<init>", "()V")
                .code()
                    .aload(0)
                    .invokespecial("java/lang/Object", "<init>", "()V")
                    .vreturn()
                .end()
                .end()

                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "add", "(II)I")
                .code()
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .end()
                .end()

                .build();

            assertEquals("com/test/SimpleClass", cf.getClassName());
            Set<String> methodNames = getMethodNames(cf);
            assertTrue(methodNames.contains("<init>"));
            assertTrue(methodNames.contains("add"));
        }

        @Test
        void generateClassWithFields() {
            ClassFile cf = ClassBuilder.create("com/test/FieldClass")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_PUBLIC)

                .addField(AccessFlags.ACC_PRIVATE | AccessFlags.ACC_STATIC, "counter", "I")
                .end()

                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "getCounter", "()I")
                .code()
                    .getstatic("com/test/FieldClass", "counter", "I")
                    .ireturn()
                .end()
                .end()

                .build();

            assertEquals(1, cf.getFields().size());
            assertEquals("counter", cf.getFields().get(0).getName());
        }
    }

    @Nested
    class ControlFlowTests {

        @Test
        void generateMethodWithBranches() {
            ClassFile cf = ClassBuilder.create("com/test/BranchClass")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_PUBLIC)

                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "max", "(II)I")
                .code()
                    .iload(0)
                    .iload(1)
                    .if_icmpge("first")
                    .iload(1)
                    .ireturn()
                    .label("first")
                    .iload(0)
                    .ireturn()
                .end()
                .end()

                .build();

            assertNotNull(cf);
            assertTrue(getMethodNames(cf).contains("max"));
        }

        @Test
        void generateMethodWithLoop() {
            ClassFile cf = ClassBuilder.create("com/test/LoopClass")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_PUBLIC)

                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "sum", "(I)I")
                .maxLocals(3)
                .maxStack(3)
                .code()
                    .iconst(0)
                    .istore(1)
                    .iconst(0)
                    .istore(2)
                    .label("loop")
                    .iload(2)
                    .iload(0)
                    .if_icmpge("end")
                    .iload(1)
                    .iload(2)
                    .iadd()
                    .istore(1)
                    .iinc(2, 1)
                    .goto_("loop")
                    .label("end")
                    .iload(1)
                    .ireturn()
                .end()
                .end()

                .build();

            assertNotNull(cf);
            assertTrue(getMethodNames(cf).contains("sum"));
            MethodEntry sumMethod = findMethod(cf, "sum");
            assertNotNull(sumMethod);
            assertEquals(3, sumMethod.getCodeAttribute().getMaxLocals());
            assertEquals(3, sumMethod.getCodeAttribute().getMaxStack());
        }
    }

    @Nested
    class ArithmeticTests {

        @Test
        void generateArithmeticMethods() {
            ClassFile cf = ClassBuilder.create("com/test/ArithClass")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_PUBLIC)

                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "multiply", "(II)I")
                .code()
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .end()
                .end()

                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "negate", "(I)I")
                .code()
                    .iload(0)
                    .ineg()
                    .ireturn()
                .end()
                .end()

                .build();

            assertNotNull(cf);
            Set<String> methodNames = getMethodNames(cf);
            assertTrue(methodNames.contains("multiply"));
            assertTrue(methodNames.contains("negate"));
        }
    }

    @Nested
    class BytecodeVerificationTests {

        @Test
        void generatedBytecodePassesVerification() {
            ClassFile cf = ClassBuilder.create("com/test/VerifyClass")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_PUBLIC)

                .addMethod(AccessFlags.ACC_PUBLIC, "<init>", "()V")
                .code()
                    .aload(0)
                    .invokespecial("java/lang/Object", "<init>", "()V")
                    .vreturn()
                .end()
                .end()

                .addMethod(AccessFlags.ACC_PUBLIC, "getValue", "()I")
                .code()
                    .iconst(42)
                    .ireturn()
                .end()
                .end()

                .build();

            assertNotNull(cf);
            Set<String> methodNames = getMethodNames(cf);
            assertTrue(methodNames.contains("<init>"));
            assertTrue(methodNames.contains("getValue"));
        }

        @Test
        void classFileMagicNumberIsCorrect() {
            byte[] bytes = ClassBuilder.create("com/test/MagicClass")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "()V")
                .code()
                    .vreturn()
                .end()
                .end()
                .toByteArray();

            assertEquals((byte) 0xCA, bytes[0]);
            assertEquals((byte) 0xFE, bytes[1]);
            assertEquals((byte) 0xBA, bytes[2]);
            assertEquals((byte) 0xBE, bytes[3]);
        }
    }

    @Nested
    class LambdaLikeClassGeneration {

        @Test
        void generateLambdaLikeClass() {
            ClassFile cf = ClassBuilder.create("com/example/MyClass$$Lambda$1")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_FINAL, AccessFlags.ACC_SYNTHETIC)
                .superClass("java/lang/Object")
                .interfaces("java/util/function/Function")

                .addField(AccessFlags.ACC_PRIVATE | AccessFlags.ACC_FINAL, "arg$0", "Ljava/lang/String;")
                .end()

                .addMethod(AccessFlags.ACC_PRIVATE, "<init>", "(Ljava/lang/String;)V")
                .code()
                    .aload(0)
                    .invokespecial("java/lang/Object", "<init>", "()V")
                    .aload(0)
                    .aload(1)
                    .putfield("com/example/MyClass$$Lambda$1", "arg$0", "Ljava/lang/String;")
                    .vreturn()
                .end()
                .end()

                .addMethod(AccessFlags.ACC_PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;")
                .code()
                    .aload(0)
                    .getfield("com/example/MyClass$$Lambda$1", "arg$0", "Ljava/lang/String;")
                    .areturn()
                .end()
                .end()

                .build();

            assertNotNull(cf);
            assertEquals("com/example/MyClass$$Lambda$1", cf.getClassName());
            assertTrue((cf.getAccess() & AccessFlags.ACC_FINAL) != 0);
            assertTrue((cf.getAccess() & AccessFlags.ACC_SYNTHETIC) != 0);
        }
    }

    private static Set<String> getMethodNames(ClassFile cf) {
        return cf.getMethods().stream()
            .map(MethodEntry::getName)
            .collect(Collectors.toSet());
    }

    private static MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}

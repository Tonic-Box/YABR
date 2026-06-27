package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class MethodBuilderTest {

    @Nested
    class BasicMethodCreation {

        @Test
        void addMethodCreatesMethodEntry() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC, "doSomething", "()V")
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "doSomething");
            assertNotNull(method);
            assertEquals("()V", method.getDesc());
        }

        @Test
        void addMethodWithParameters() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC, "add", "(II)I")
                .code()
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "add");
            assertNotNull(method);
            assertEquals("(II)I", method.getDesc());
        }
    }

    @Nested
    class MethodAccessFlags {

        @Test
        void publicMethod() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC, "publicMethod", "()V")
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "publicMethod");
            assertTrue((method.getAccess() & AccessFlags.ACC_PUBLIC) != 0);
        }

        @Test
        void staticMethod() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "staticMethod", "()V")
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "staticMethod");
            assertTrue((method.getAccess() & AccessFlags.ACC_STATIC) != 0);
        }
    }

    @Nested
    class CodeBuilderAccess {

        @Test
        void codeReturnsCodeBuilder() {
            ClassBuilder cb = ClassBuilder.create("com/test/MethodTest");
            MethodBuilder mb = cb.addMethod(AccessFlags.ACC_PUBLIC, "test", "()V");
            CodeBuilder code = mb.code();

            assertNotNull(code);
        }

        @Test
        void codeReturnsSameInstanceOnMultipleCalls() {
            ClassBuilder cb = ClassBuilder.create("com/test/MethodTest");
            MethodBuilder mb = cb.addMethod(AccessFlags.ACC_PUBLIC, "test", "()V");

            CodeBuilder code1 = mb.code();
            CodeBuilder code2 = mb.code();

            assertSame(code1, code2);
        }
    }

    @Nested
    class MaxStackAndLocals {

        @Test
        void maxStackSetsValue() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "()V")
                .maxStack(10)
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "test");
            assertEquals(10, method.getCodeAttribute().getMaxStack());
        }

        @Test
        void maxLocalsSetsValue() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "()V")
                .maxLocals(5)
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "test");
            assertEquals(5, method.getCodeAttribute().getMaxLocals());
        }
    }

    @Nested
    class EndMethod {

        @Test
        void endReturnsClassBuilder() {
            ClassBuilder cb = ClassBuilder.create("com/test/MethodTest");
            MethodBuilder mb = cb.addMethod(AccessFlags.ACC_PUBLIC, "test", "()V");
            ClassBuilder returned = mb.end();

            assertSame(cb, returned);
        }
    }

    @Nested
    class ExceptionsTests {

        @Test
        void exceptionsAddsException() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC, "throwsMethod", "()V")
                .exceptions("java/io/IOException")
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "throwsMethod");
            assertNotNull(method);
        }

        @Test
        void exceptionsAddMultipleExceptions() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC, "throwsMultiple", "()V")
                .exceptions("java/io/IOException", "java/lang/IllegalStateException")
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "throwsMultiple");
            assertNotNull(method);
        }
    }

    @Nested
    class BuildMethodTests {

        @Test
        void buildMethodWithNullCode() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .access(AccessFlags.ACC_PUBLIC, AccessFlags.ACC_ABSTRACT)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_ABSTRACT, "abstractMethod", "()V")
                .end()
                .build();

            MethodEntry method = findMethod(cf, "abstractMethod");
            assertNotNull(method);
            assertNull(method.getCodeAttribute());
        }

        @Test
        void buildMethodWithMaxStackOverride() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "()V")
                .maxStack(20)
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "test");
            assertNotNull(method.getCodeAttribute());
            assertEquals(20, method.getCodeAttribute().getMaxStack());
        }

        @Test
        void buildMethodWithMaxLocalsOverride() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "()V")
                .maxLocals(15)
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "test");
            assertNotNull(method.getCodeAttribute());
            assertEquals(15, method.getCodeAttribute().getMaxLocals());
        }

        @Test
        void buildMethodWithBothMaxStackAndMaxLocals() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "()V")
                .maxStack(25)
                .maxLocals(30)
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            MethodEntry method = findMethod(cf, "test");
            assertNotNull(method.getCodeAttribute());
            assertEquals(25, method.getCodeAttribute().getMaxStack());
            assertEquals(30, method.getCodeAttribute().getMaxLocals());
        }
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}

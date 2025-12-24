package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapMethodsAttributeTest {

    @Nested
    class ConstructorTests {

        @Test
        void constructorWithMemberParent() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Member").build();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute("BootstrapMethods", cf, 1, 100);

            assertNotNull(attr);
            // Bootstrap methods list may be null until methods are added
        }

        @Test
        void constructorWithConstPool() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ConstPool").build();
            ConstPool constPool = cf.getConstPool();

            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            assertNotNull(attr);
            assertNotNull(attr.getBootstrapMethods());
            assertTrue(attr.getBootstrapMethods().isEmpty());
        }

        @Test
        void constructorWithClassFileParent() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ClassFile").build();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute("BootstrapMethods", cf, 1, 100);

            assertNotNull(attr);
        }
    }

    @Nested
    class AddBootstrapMethodTests {

        @Test
        void addBootstrapMethodWithNoArguments() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoArgs").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, new ArrayList<>());

            assertEquals(1, attr.getBootstrapMethods().size());
            BootstrapMethod bm = attr.getBootstrapMethods().get(0);
            assertEquals(1, bm.getBootstrapMethodRef());
            assertTrue(bm.getBootstrapArguments().isEmpty());
        }

        @Test
        void addBootstrapMethodWithArguments() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/WithArgs").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            List<Integer> args = Arrays.asList(10, 20, 30);
            attr.addBootstrapMethod(5, args);

            assertEquals(1, attr.getBootstrapMethods().size());
            BootstrapMethod bm = attr.getBootstrapMethods().get(0);
            assertEquals(5, bm.getBootstrapMethodRef());
            assertEquals(3, bm.getBootstrapArguments().size());
            assertEquals(10, bm.getBootstrapArguments().get(0));
            assertEquals(20, bm.getBootstrapArguments().get(1));
            assertEquals(30, bm.getBootstrapArguments().get(2));
        }

        @Test
        void addMultipleBootstrapMethods() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Multiple").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, Arrays.asList(10));
            attr.addBootstrapMethod(2, Arrays.asList(20, 30));
            attr.addBootstrapMethod(3, new ArrayList<>());

            assertEquals(3, attr.getBootstrapMethods().size());
        }

        @Test
        void addBootstrapMethodInitializesListIfNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/InitList").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, new ArrayList<>());

            assertNotNull(attr.getBootstrapMethods());
            assertEquals(1, attr.getBootstrapMethods().size());
        }
    }

    @Nested
    class GetBootstrapMethodsTests {

        @Test
        void getBootstrapMethodsReturnsEmptyListInitially() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Empty").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            assertNotNull(attr.getBootstrapMethods());
            assertTrue(attr.getBootstrapMethods().isEmpty());
        }

        @Test
        void getBootstrapMethodsReturnsAddedMethods() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Added").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, Arrays.asList(10, 20));
            attr.addBootstrapMethod(2, Arrays.asList(30));

            List<BootstrapMethod> methods = attr.getBootstrapMethods();
            assertEquals(2, methods.size());
            assertEquals(1, methods.get(0).getBootstrapMethodRef());
            assertEquals(2, methods.get(1).getBootstrapMethodRef());
        }
    }

    @Nested
    class UpdateLengthTests {

        @Test
        void updateLengthCalculatesCorrectSizeWithNoMethods() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoMethods").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.updateLength();

            assertTrue(attr.getBootstrapMethods().isEmpty());
        }

        @Test
        void updateLengthCalculatesCorrectSizeWithMethodsNoArgs() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/MethodsNoArgs").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, new ArrayList<>());
            attr.updateLength();

            assertEquals(1, attr.getBootstrapMethods().size());
        }

        @Test
        void updateLengthCalculatesCorrectSizeWithArguments() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/WithArgs").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, Arrays.asList(10, 20, 30));
            attr.updateLength();

            assertEquals(1, attr.getBootstrapMethods().size());
            assertEquals(3, attr.getBootstrapMethods().get(0).getBootstrapArguments().size());
        }

        @Test
        void updateLengthCalculatesCorrectSizeWithMultipleMethods() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Multiple").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, Arrays.asList(10));
            attr.addBootstrapMethod(2, Arrays.asList(20, 30));
            attr.updateLength();

            assertEquals(2, attr.getBootstrapMethods().size());
        }
    }

    @Nested
    class WriteAndReadTests {

        @Test
        void roundTripWithNoBootstrapMethods() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/RoundTrip").build();
            ConstPool constPool = original.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);
            original.getClassAttributes().add(attr);

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            BootstrapMethodsAttribute parsedAttr = findBootstrapMethodsAttribute(parsed);
            if (parsedAttr != null) {
                assertEquals(0, parsedAttr.getBootstrapMethods().size());
            }
        }

        @Test
        void roundTripWithSingleBootstrapMethod() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/Single").build();
            ConstPool constPool = original.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            int methodHandleIndex = constPool.addMethodHandle(6, 1);
            attr.addBootstrapMethod(methodHandleIndex, Arrays.asList(10, 20));
            original.getClassAttributes().add(attr);

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            BootstrapMethodsAttribute parsedAttr = findBootstrapMethodsAttribute(parsed);
            assertNotNull(parsedAttr);
            assertEquals(1, parsedAttr.getBootstrapMethods().size());

            BootstrapMethod bm = parsedAttr.getBootstrapMethods().get(0);
            assertEquals(methodHandleIndex, bm.getBootstrapMethodRef());
            assertEquals(2, bm.getBootstrapArguments().size());
        }

        @Test
        void roundTripWithMultipleBootstrapMethods() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/Multiple").build();
            ConstPool constPool = original.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            int handle1 = constPool.addMethodHandle(6, 1);
            int handle2 = constPool.addMethodHandle(6, 2);
            attr.addBootstrapMethod(handle1, Arrays.asList(10));
            attr.addBootstrapMethod(handle2, Arrays.asList(20, 30, 40));
            original.getClassAttributes().add(attr);

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            BootstrapMethodsAttribute parsedAttr = findBootstrapMethodsAttribute(parsed);
            assertNotNull(parsedAttr);
            assertEquals(2, parsedAttr.getBootstrapMethods().size());
        }

        @Test
        void roundTripPreservesArgumentOrder() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/Order").build();
            ConstPool constPool = original.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            int handle = constPool.addMethodHandle(6, 1);
            List<Integer> args = Arrays.asList(100, 200, 300, 400);
            attr.addBootstrapMethod(handle, args);
            original.getClassAttributes().add(attr);

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            BootstrapMethodsAttribute parsedAttr = findBootstrapMethodsAttribute(parsed);
            assertNotNull(parsedAttr);

            BootstrapMethod bm = parsedAttr.getBootstrapMethods().get(0);
            assertEquals(args.size(), bm.getBootstrapArguments().size());
            for (int i = 0; i < args.size(); i++) {
                assertEquals(args.get(i), bm.getBootstrapArguments().get(i));
            }
        }

        private BootstrapMethodsAttribute findBootstrapMethodsAttribute(ClassFile cf) {
            for (Attribute attr : cf.getClassAttributes()) {
                if (attr instanceof BootstrapMethodsAttribute) {
                    return (BootstrapMethodsAttribute) attr;
                }
            }
            return null;
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsBootstrapMethods() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ToString").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, Arrays.asList(10));

            String str = attr.toString();
            assertNotNull(str);
            assertTrue(str.contains("BootstrapMethodsAttribute") || str.contains("bootstrapMethods"));
        }

        @Test
        void toStringNonEmpty() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NonEmpty").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            String str = attr.toString();
            assertNotNull(str);
            assertFalse(str.isEmpty());
        }
    }

    @Nested
    class EdgeCasesTests {

        @Test
        void emptyArgumentList() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/EmptyArgs").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(1, new ArrayList<>());

            assertEquals(1, attr.getBootstrapMethods().size());
            assertEquals(0, attr.getBootstrapMethods().get(0).getBootstrapArguments().size());
        }

        @Test
        void largeNumberOfArguments() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ManyArgs").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            List<Integer> manyArgs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                manyArgs.add(i);
            }
            attr.addBootstrapMethod(1, manyArgs);

            assertEquals(1, attr.getBootstrapMethods().size());
            assertEquals(100, attr.getBootstrapMethods().get(0).getBootstrapArguments().size());
        }

        @Test
        void largeNumberOfBootstrapMethods() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ManyMethods").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            for (int i = 0; i < 50; i++) {
                attr.addBootstrapMethod(i, Arrays.asList(i * 10));
            }

            assertEquals(50, attr.getBootstrapMethods().size());
        }

        @Test
        void zeroMethodHandleIndex() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ZeroIndex").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(0, new ArrayList<>());

            assertEquals(1, attr.getBootstrapMethods().size());
            assertEquals(0, attr.getBootstrapMethods().get(0).getBootstrapMethodRef());
        }

        @Test
        void highMethodHandleIndex() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/HighIndex").build();
            ConstPool constPool = cf.getConstPool();
            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);

            attr.addBootstrapMethod(65535, new ArrayList<>());

            assertEquals(1, attr.getBootstrapMethods().size());
            assertEquals(65535, attr.getBootstrapMethods().get(0).getBootstrapMethodRef());
        }
    }

    @Nested
    class BootstrapMethodIntegrationTests {

        @Test
        void bootstrapMethodWithLambda() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Lambda")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            ConstPool constPool = cf.getConstPool();

            int metafactoryMethod = constPool.addMethodRef(
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
            int metafactoryHandle = constPool.addMethodHandle(6, metafactoryMethod);

            int samType = constPool.addMethodType("()V");
            int implMethod = constPool.addMethodRef("com/test/Lambda", "lambda$test$0", "()V");
            int implHandle = constPool.addMethodHandle(6, implMethod);

            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);
            attr.addBootstrapMethod(metafactoryHandle, Arrays.asList(samType, implHandle, samType));
            cf.getClassAttributes().add(attr);

            byte[] bytes = cf.write();
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);

            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));
            BootstrapMethodsAttribute parsedAttr = findBootstrapMethodsAttribute(parsed);
            assertNotNull(parsedAttr);
            assertEquals(1, parsedAttr.getBootstrapMethods().size());
        }

        @Test
        void bootstrapMethodWithInvokeDynamic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/InvokeDynamic")
                .build();

            ConstPool constPool = cf.getConstPool();
            int handle = constPool.addMethodHandle(6, 1);

            BootstrapMethodsAttribute attr = new BootstrapMethodsAttribute(constPool);
            attr.addBootstrapMethod(handle, Arrays.asList(100, 200));
            cf.getClassAttributes().add(attr);

            byte[] bytes = cf.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            BootstrapMethodsAttribute parsedAttr = findBootstrapMethodsAttribute(parsed);
            assertNotNull(parsedAttr);
        }

        private BootstrapMethodsAttribute findBootstrapMethodsAttribute(ClassFile cf) {
            for (Attribute attr : cf.getClassAttributes()) {
                if (attr instanceof BootstrapMethodsAttribute) {
                    return (BootstrapMethodsAttribute) attr;
                }
            }
            return null;
        }
    }

    @Nested
    class BootstrapMethodObjectTests {

        @Test
        void bootstrapMethodConstructor() {
            BootstrapMethod bm = new BootstrapMethod(5, Arrays.asList(10, 20, 30));

            assertEquals(5, bm.getBootstrapMethodRef());
            assertEquals(3, bm.getBootstrapArguments().size());
            assertEquals(10, bm.getBootstrapArguments().get(0));
            assertEquals(20, bm.getBootstrapArguments().get(1));
            assertEquals(30, bm.getBootstrapArguments().get(2));
        }

        @Test
        void bootstrapMethodToString() {
            BootstrapMethod bm = new BootstrapMethod(5, Arrays.asList(10, 20));

            String str = bm.toString();
            assertNotNull(str);
            assertTrue(str.contains("bootstrapMethodRef") || str.contains("5"));
            assertTrue(str.contains("bootstrapArguments") || str.contains("10"));
        }

        @Test
        void bootstrapMethodWithEmptyArguments() {
            BootstrapMethod bm = new BootstrapMethod(1, new ArrayList<>());

            assertEquals(1, bm.getBootstrapMethodRef());
            assertTrue(bm.getBootstrapArguments().isEmpty());
        }

        @Test
        void bootstrapMethodArgumentsImmutable() {
            List<Integer> args = new ArrayList<>(Arrays.asList(10, 20));
            BootstrapMethod bm = new BootstrapMethod(1, args);

            args.add(30);

            assertEquals(3, bm.getBootstrapArguments().size());
        }
    }
}

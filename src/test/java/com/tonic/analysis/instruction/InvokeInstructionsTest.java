package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class InvokeInstructionsTest {

    private ConstPool constPool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() {
        classFile = new ClassFile("TestClass", 0);
        constPool = classFile.getConstPool();
    }

    private int setupMethodRef(String className, String methodName, String descriptor) {
        MethodRefItem methodRef = constPool.findOrAddMethodRef(className, methodName, descriptor);
        return constPool.getIndexOf(methodRef);
    }

    private int setupInterfaceRef(String interfaceName, String methodName, String descriptor) {
        InterfaceRefItem interfaceRef = constPool.findOrAddInterfaceRef(interfaceName, methodName, descriptor);
        return constPool.getIndexOf(interfaceRef);
    }

    private int setupInvokeDynamic(String methodName, String descriptor, int bootstrapMethodIndex) {
        int nameAndTypeIndex = constPool.addNameAndType(methodName, descriptor);
        return constPool.addInvokeDynamic(bootstrapMethodIndex, nameAndTypeIndex);
    }

    @Nested
    class InvokeVirtualInstructionTests {

        @Test
        void constructorSetsCorrectOpcode() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(0xB6, instr.getOpcode());
        }

        @Test
        void constructorRejectsInvalidOpcode() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new InvokeVirtualInstruction(constPool, 0xB7, 0, methodIndex);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for InvokeVirtualInstruction"));
        }

        @Test
        void hasCorrectLength() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(3, instr.getLength());
        }

        @Test
        void getMethodIndexReturnsCorrectValue() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(methodIndex, instr.getMethodIndex());
        }

        @Test
        void getStackChangeWithSingleParamAndObjectReturn() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void getStackChangeWithNoParamsAndVoidReturn() {
            int methodIndex = setupMethodRef("java/lang/Object", "notify", "()V");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void getStackChangeWithMultipleParamsAndIntReturn() {
            int methodIndex = setupMethodRef("java/lang/String", "indexOf", "(Ljava/lang/String;I)I");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(-2, instr.getStackChange());
        }

        @Test
        void getStackChangeWithLongReturn() {
            int methodIndex = setupMethodRef("java/lang/Object", "hashCode", "()J");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(1, instr.getStackChange());
        }

        @Test
        void getLocalChangeAlwaysZero() {
            int methodIndex = setupMethodRef("java/lang/String", "length", "()I");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void getMethodNameReturnsCorrectName() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals("substring", instr.getMethodName());
        }

        @Test
        void getMethodDescriptorReturnsCorrectDescriptor() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals("(I)Ljava/lang/String;", instr.getMethodDescriptor());
        }

        @Test
        void getOwnerClassReturnsCorrectClass() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(I)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals("java/lang/String", instr.getOwnerClass());
        }

        @Test
        void toStringContainsInvokevirtualMnemonic() {
            int methodIndex = setupMethodRef("java/lang/String", "length", "()I");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            String result = instr.toString();
            assertTrue(result.contains("INVOKEVIRTUAL"));
            assertTrue(result.contains("#" + methodIndex));
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            int methodIndex = setupMethodRef("java/lang/String", "length", "()I");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(3, bytes.length);
            assertEquals((byte) 0xB6, bytes[0]);
            assertEquals((methodIndex >> 8) & 0xFF, bytes[1] & 0xFF);
            assertEquals(methodIndex & 0xFF, bytes[2] & 0xFF);
        }

        @Test
        void acceptsVisitor() {
            int methodIndex = setupMethodRef("java/lang/String", "length", "()I");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);
            TestVisitor visitor = new TestVisitor();

            instr.accept(visitor);

            assertTrue(visitor.visitedInvokeVirtual);
        }
    }

    @Nested
    class InvokeStaticInstructionTests {

        @Test
        void constructorSetsCorrectOpcode() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals(0xB8, instr.getOpcode());
        }

        @Test
        void constructorRejectsInvalidOpcode() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new InvokeStaticInstruction(constPool, 0xB6, 0, methodIndex);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for InvokeStaticInstruction"));
        }

        @Test
        void hasCorrectLength() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals(3, instr.getLength());
        }

        @Test
        void getStackChangeWithTwoParamsAndIntReturn() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void getStackChangeWithNoParamsAndObjectReturn() {
            int methodIndex = setupMethodRef("java/lang/System", "currentTimeMillis", "()J");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals(2, instr.getStackChange());
        }

        @Test
        void getLocalChangeAlwaysZero() {
            int methodIndex = setupMethodRef("java/lang/Math", "abs", "(I)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void getMethodNameReturnsCorrectName() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals("max", instr.getMethodName());
        }

        @Test
        void getMethodDescriptorReturnsCorrectDescriptor() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals("(II)I", instr.getMethodDescriptor());
        }

        @Test
        void getOwnerClassReturnsCorrectClass() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals("java/lang/Math", instr.getOwnerClass());
        }

        @Test
        void toStringContainsInvokestaticMnemonic() {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            String result = instr.toString();
            assertTrue(result.contains("INVOKESTATIC"));
            assertTrue(result.contains("#" + methodIndex));
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            int methodIndex = setupMethodRef("java/lang/Math", "max", "(II)I");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(3, bytes.length);
            assertEquals((byte) 0xB8, bytes[0]);
            assertEquals((methodIndex >> 8) & 0xFF, bytes[1] & 0xFF);
            assertEquals(methodIndex & 0xFF, bytes[2] & 0xFF);
        }
    }

    @Nested
    class InvokeSpecialInstructionTests {

        @Test
        void constructorSetsCorrectOpcode() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals(0xB7, instr.getOpcode());
        }

        @Test
        void constructorRejectsInvalidOpcode() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new InvokeSpecialInstruction(constPool, 0xB8, 0, methodIndex);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for InvokeSpecialInstruction"));
        }

        @Test
        void hasCorrectLength() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals(3, instr.getLength());
        }

        @Test
        void getStackChangeWithConstructor() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void getStackChangeWithSuperMethodCall() {
            int methodIndex = setupMethodRef("java/lang/Object", "toString", "()Ljava/lang/String;");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void getLocalChangeAlwaysZero() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void getMethodNameReturnsCorrectName() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals("<init>", instr.getMethodName());
        }

        @Test
        void getMethodDescriptorReturnsCorrectDescriptor() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals("()V", instr.getMethodDescriptor());
        }

        @Test
        void getOwnerClassReturnsCorrectClass() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals("java/lang/Object", instr.getOwnerClass());
        }

        @Test
        void getOwnerNameConvertsSlashesToDots() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals("java.lang.Object", instr.getOwnerName());
        }

        @Test
        void toStringContainsInvokespecialMnemonic() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            String result = instr.toString();
            assertTrue(result.contains("INVOKESPECIAL"));
            assertTrue(result.contains("#" + methodIndex));
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(3, bytes.length);
            assertEquals((byte) 0xB7, bytes[0]);
            assertEquals((methodIndex >> 8) & 0xFF, bytes[1] & 0xFF);
            assertEquals(methodIndex & 0xFF, bytes[2] & 0xFF);
        }

        @Test
        void acceptsVisitor() {
            int methodIndex = setupMethodRef("java/lang/Object", "<init>", "()V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);
            TestVisitor visitor = new TestVisitor();

            instr.accept(visitor);

            assertTrue(visitor.visitedInvokeSpecial);
        }
    }

    @Nested
    class InvokeInterfaceInstructionTests {

        @Test
        void constructorSetsCorrectOpcode() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            assertEquals(0xB9, instr.getOpcode());
        }

        @Test
        void constructorRejectsInvalidOpcode() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new InvokeInterfaceInstruction(constPool, 0xB8, 0, methodIndex, 1);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for InvokeInterfaceInstruction"));
        }

        @Test
        void hasCorrectLength() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            assertEquals(5, instr.getLength());
        }

        @Test
        void getCountReturnsCorrectValue() {
            int methodIndex = setupInterfaceRef("java/util/List", "add", "(Ljava/lang/Object;)Z");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 2);

            assertEquals(2, instr.getCount());
        }

        @Test
        void getStackChangeWithNoParamsAndIntReturn() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void getStackChangeWithOneParamAndBooleanReturn() {
            int methodIndex = setupInterfaceRef("java/util/List", "add", "(Ljava/lang/Object;)Z");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 2);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void getLocalChangeAlwaysZero() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void getMethodNameReturnsCorrectName() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            assertEquals("size", instr.getMethodName());
        }

        @Test
        void getMethodDescriptorReturnsCorrectDescriptor() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            assertEquals("()I", instr.getMethodDescriptor());
        }

        @Test
        void getOwnerClassReturnsCorrectInterface() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            assertEquals("java/util/List", instr.getOwnerClass());
        }

        @Test
        void toStringContainsInvokeinterfaceMnemonic() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            String result = instr.toString();
            assertTrue(result.contains("INVOKEINTERFACE"));
            assertTrue(result.contains("#" + methodIndex));
            assertTrue(result.contains("1"));
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(5, bytes.length);
            assertEquals((byte) 0xB9, bytes[0]);
            assertEquals((methodIndex >> 8) & 0xFF, bytes[1] & 0xFF);
            assertEquals(methodIndex & 0xFF, bytes[2] & 0xFF);
            assertEquals(1, bytes[3]);
            assertEquals(0, bytes[4]);
        }

        @Test
        void acceptsVisitor() {
            int methodIndex = setupInterfaceRef("java/util/List", "size", "()I");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 1);
            TestVisitor visitor = new TestVisitor();

            instr.accept(visitor);

            assertTrue(visitor.visitedInvokeInterface);
        }
    }

    @Nested
    class InvokeDynamicInstructionTests {

        @Test
        void constructorSetsCorrectOpcode() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            assertEquals(0xBA, instr.getOpcode());
        }

        @Test
        void constructorRejectsInvalidOpcode() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new InvokeDynamicInstruction(constPool, 0xB8, 0, cpIndex);
            });

            assertTrue(exception.getMessage().contains("Invalid opcode for InvokeDynamicInstruction"));
        }

        @Test
        void hasCorrectLength() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            assertEquals(5, instr.getLength());
        }

        @Test
        void getCpIndexReturnsCorrectValue() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            assertEquals(cpIndex, instr.getCpIndex());
        }

        @Test
        void getBootstrapMethodAttrIndexReturnsCorrectValue() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 5);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            assertEquals(5, instr.getBootstrapMethodAttrIndex());
        }

        @Test
        void getStackChangeWithNoParamsAndObjectReturn() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            assertEquals(1, instr.getStackChange());
        }

        @Test
        void getStackChangeWithOneParamAndVoidReturn() {
            int cpIndex = setupInvokeDynamic("accept", "(Ljava/lang/Object;)V", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            assertEquals(-1, instr.getStackChange());
        }

        @Test
        void getStackChangeWithInvalidItemReturnsZero() {
            Utf8Item utf8 = new Utf8Item();
            utf8.setValue("invalid");
            constPool.getItems().add(utf8);
            int invalidIndex = constPool.getItems().size() - 1;

            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, invalidIndex);

            assertEquals(0, instr.getStackChange());
        }

        @Test
        void getLocalChangeAlwaysZero() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            assertEquals(0, instr.getLocalChange());
        }

        @Test
        void resolveMethodReturnsCorrectString() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            String result = instr.resolveMethod();
            assertTrue(result.contains("apply"));
            assertTrue(result.contains("()Ljava/util/function/Function;"));
        }

        @Test
        void resolveMethodWithZeroIndexReturnsNotInClassPool() {
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, 0);

            assertEquals("NotInClassPool", instr.resolveMethod());
        }

        @Test
        void resolveMethodWithInvalidItemReturnsErrorMessage() {
            Utf8Item utf8 = new Utf8Item();
            utf8.setValue("invalid");
            constPool.getItems().add(utf8);
            int invalidIndex = constPool.getItems().size() - 1;

            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, invalidIndex);

            String result = instr.resolveMethod();
            assertTrue(result.contains("InvalidCPItem"));
            assertTrue(result.contains("Utf8Item"));
        }

        @Test
        void toStringContainsInvokedynamicMnemonic() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            String result = instr.toString();
            assertTrue(result.contains("INVOKEDYNAMIC"));
            assertTrue(result.contains("#" + cpIndex));
        }

        @Test
        void writesCorrectBytecode() throws IOException {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            instr.write(dos);
            dos.flush();

            byte[] bytes = baos.toByteArray();
            assertEquals(5, bytes.length);
            assertEquals((byte) 0xBA, bytes[0]);
            assertEquals((cpIndex >> 8) & 0xFF, bytes[1] & 0xFF);
            assertEquals(cpIndex & 0xFF, bytes[2] & 0xFF);
            assertEquals(0, bytes[3]);
            assertEquals(0, bytes[4]);
        }

        @Test
        void acceptsVisitor() {
            int cpIndex = setupInvokeDynamic("apply", "()Ljava/util/function/Function;", 0);
            InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, 0xBA, 0, cpIndex);
            TestVisitor visitor = new TestVisitor();

            instr.accept(visitor);

            assertTrue(visitor.visitedInvokeDynamic);
        }
    }

    @Nested
    class StackEffectEdgeCasesTests {

        @Test
        void invokeVirtualWithDoubleReturnAddsTwo() {
            int methodIndex = setupMethodRef("java/lang/Math", "random", "()D");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals(1, instr.getStackChange());
        }

        @Test
        void invokeStaticWithLongParamsAndVoidReturn() {
            int methodIndex = setupMethodRef("java/lang/System", "setProperty", "(JJ)V");
            InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, 0xB8, 0, methodIndex);

            assertEquals(-2, instr.getStackChange());
        }

        @Test
        void invokeSpecialWithMixedParams() {
            int methodIndex = setupMethodRef("java/lang/String", "<init>", "([BII)V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals(-4, instr.getStackChange());
        }

        @Test
        void invokeInterfaceWithComplexDescriptor() {
            int methodIndex = setupInterfaceRef("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, 0xB9, 0, methodIndex, 3);

            assertEquals(-2, instr.getStackChange());
        }
    }

    @Nested
    class DescriptorParsingTests {

        @Test
        void parsesSimpleVoidMethod() {
            int methodIndex = setupMethodRef("java/lang/Object", "notify", "()V");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals("()V", instr.getMethodDescriptor());
        }

        @Test
        void parsesMethodWithPrimitiveParams() {
            int methodIndex = setupMethodRef("java/lang/String", "substring", "(II)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals("(II)Ljava/lang/String;", instr.getMethodDescriptor());
        }

        @Test
        void parsesMethodWithObjectParams() {
            int methodIndex = setupMethodRef("java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;");
            InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, 0xB6, 0, methodIndex);

            assertEquals("(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
                         instr.getMethodDescriptor());
        }

        @Test
        void parsesMethodWithArrayParam() {
            int methodIndex = setupMethodRef("java/lang/String", "<init>", "([C)V");
            InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, 0xB7, 0, methodIndex);

            assertEquals("([C)V", instr.getMethodDescriptor());
        }
    }

    private static class TestVisitor extends AbstractBytecodeVisitor {
        boolean visitedInvokeVirtual = false;
        boolean visitedInvokeStatic = false;
        boolean visitedInvokeSpecial = false;
        boolean visitedInvokeInterface = false;
        boolean visitedInvokeDynamic = false;

        @Override
        public void visit(InvokeVirtualInstruction instr) {
            visitedInvokeVirtual = true;
        }

        @Override
        public void visit(InvokeSpecialInstruction instr) {
            visitedInvokeSpecial = true;
        }

        @Override
        public void visit(InvokeInterfaceInstruction instr) {
            visitedInvokeInterface = true;
        }

        @Override
        public void visit(InvokeDynamicInstruction instr) {
            visitedInvokeDynamic = true;
        }
    }
}

package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.InvokeDynamic;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class InvokeDynamicItemTest {

    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        classFile = BytecodeBuilder.forClass("com/test/InvokeDynamicTest")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();
        constPool = classFile.getConstPool();
    }

    @Nested
    class ConstructionTests {

        @Test
        void defaultConstructor() {
            InvokeDynamicItem item = new InvokeDynamicItem();
            assertNotNull(item);
            assertEquals(Item.ITEM_INVOKEDYNAMIC, item.getType());
        }

        @Test
        void constructorWithParameters() {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 5, 10);

            assertNotNull(item);
            assertNotNull(item.getValue());
            assertEquals(5, item.getValue().getBootstrapMethodAttrIndex());
            assertEquals(10, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void constructorSetsConstPool() {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 3, 7);

            assertNotNull(item.getValue());
        }
    }

    @Nested
    class ResolutionTests {

        @Test
        void getNameReturnsNullWhenConstPoolNotSet() {
            InvokeDynamicItem item = new InvokeDynamicItem();

            assertNull(item.getName());
        }

        @Test
        void getDescriptorReturnsNullWhenConstPoolNotSet() {
            InvokeDynamicItem item = new InvokeDynamicItem();

            assertNull(item.getDescriptor());
        }

        @Test
        void getNameWithConstPool() throws IOException {
            int natIndex = constPool.addNameAndType("apply", "()Ljava/util/function/Function;");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            String name = item.getName();
            assertNotNull(name);
            assertEquals("apply", name);
        }

        @Test
        void getDescriptorWithConstPool() throws IOException {
            int natIndex = constPool.addNameAndType("run", "()Ljava/lang/Runnable;");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            String descriptor = item.getDescriptor();
            assertNotNull(descriptor);
            assertEquals("()Ljava/lang/Runnable;", descriptor);
        }

        @Test
        void getDescriptorForComplexSignature() throws IOException {
            int natIndex = constPool.addNameAndType("lambda", "(Ljava/lang/String;I)Ljava/util/function/BiConsumer;");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            String descriptor = item.getDescriptor();
            assertNotNull(descriptor);
            assertTrue(descriptor.startsWith("("));
            assertTrue(descriptor.contains(")"));
        }
    }

    @Nested
    class ParameterAnalysisTests {

        @Test
        void getParameterCountThrowsWhenConstPoolNotSet() {
            InvokeDynamicItem item = new InvokeDynamicItem();

            assertThrows(IllegalStateException.class, item::getParameterCount);
        }

        @Test
        void getParameterCountForNoParameters() throws IOException {
            int natIndex = constPool.addNameAndType("run", "()Ljava/lang/Runnable;");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(0, item.getParameterCount());
        }

        @Test
        void getParameterCountForOneParameter() throws IOException {
            int natIndex = constPool.addNameAndType("accept", "(Ljava/lang/Object;)Ljava/util/function/Consumer;");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(1, item.getParameterCount());
        }

        @Test
        void getParameterCountForMultipleParameters() throws IOException {
            int natIndex = constPool.addNameAndType("lambda", "(ILjava/lang/String;D)Ljava/util/function/Function;");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(3, item.getParameterCount());
        }

        @Test
        void getReturnTypeSlotsThrowsWhenConstPoolNotSet() {
            InvokeDynamicItem item = new InvokeDynamicItem();

            assertThrows(IllegalStateException.class, item::getReturnTypeSlots);
        }

        @Test
        void getReturnTypeSlotsForVoidReturn() throws IOException {
            int natIndex = constPool.addNameAndType("action", "()V");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(0, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForIntReturn() throws IOException {
            int natIndex = constPool.addNameAndType("getValue", "()I");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(1, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForLongReturn() throws IOException {
            int natIndex = constPool.addNameAndType("getLong", "()J");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(2, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForDoubleReturn() throws IOException {
            int natIndex = constPool.addNameAndType("getDouble", "()D");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(2, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForObjectReturn() throws IOException {
            int natIndex = constPool.addNameAndType("get", "()Ljava/lang/Object;");
            int indyIndex = constPool.addInvokeDynamic(0, natIndex);

            InvokeDynamicItem item = (InvokeDynamicItem) constPool.getItem(indyIndex);

            assertEquals(1, item.getReturnTypeSlots());
        }

        }

    @Nested
    class SerializationTests {

        @Test
        void writeInvokeDynamic() throws IOException {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 0x0002, 0x0008);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0x00, bytes[0] & 0xFF);
            assertEquals(0x02, bytes[1] & 0xFF);
            assertEquals(0x00, bytes[2] & 0xFF);
            assertEquals(0x08, bytes[3] & 0xFF);
        }

        @Test
        void writeInvokeDynamicWithZeroIndices() throws IOException {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 0, 0);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            for (byte b : bytes) {
                assertEquals(0x00, b & 0xFF);
            }
        }

        @Test
        void writeInvokeDynamicWithLargeIndices() throws IOException {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 0xABCD, 0x1234);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0xAB, bytes[0] & 0xFF);
            assertEquals(0xCD, bytes[1] & 0xFF);
            assertEquals(0x12, bytes[2] & 0xFF);
            assertEquals(0x34, bytes[3] & 0xFF);
        }
    }

    @Nested
    class TypeTests {

        @Test
        void getTypeReturnsInvokeDynamicConstant() {
            InvokeDynamicItem item = new InvokeDynamicItem();
            assertEquals(Item.ITEM_INVOKEDYNAMIC, item.getType());
            assertEquals(0x12, item.getType());
        }
    }

    @Nested
    class ValueTests {

        @Test
        void getValueReturnsInvokeDynamic() {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 5, 10);

            InvokeDynamic result = item.getValue();
            assertNotNull(result);
            assertEquals(5, result.getBootstrapMethodAttrIndex());
            assertEquals(10, result.getNameAndTypeIndex());
        }

        @Test
        void valueFromConstructorIsAccessible() {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 7, 14);

            InvokeDynamic value = item.getValue();
            assertNotNull(value);
            assertEquals(7, value.getBootstrapMethodAttrIndex());
            assertEquals(14, value.getNameAndTypeIndex());
        }

        @Test
        void invokeDynamicValuesAreImmutable() {
            InvokeDynamicItem item = new InvokeDynamicItem(constPool, 100, 200);

            assertEquals(100, item.getValue().getBootstrapMethodAttrIndex());
            assertEquals(200, item.getValue().getNameAndTypeIndex());
        }
    }
}

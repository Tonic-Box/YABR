package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.ConstantDynamic;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ConstantDynamicItemTest {

    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        classFile = BytecodeBuilder.forClass("com/test/ConstantDynamicTest")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();
        constPool = classFile.getConstPool();
    }

    @Nested
    class ConstructionTests {

        @Test
        void defaultConstructor() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            assertNotNull(item);
            assertEquals(Item.ITEM_DYNAMIC, item.getType());
        }

        @Test
        void constructorWithValue() {
            ConstantDynamic value = new ConstantDynamic(3, 7);
            ConstantDynamicItem item = new ConstantDynamicItem(value);

            assertNotNull(item);
            assertNotNull(item.getValue());
            assertEquals(3, item.getValue().getBootstrapMethodAttrIndex());
            assertEquals(7, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void setValueAfterConstruction() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(5, 10);
            item.setValue(value);

            assertEquals(value, item.getValue());
            assertEquals(5, item.getValue().getBootstrapMethodAttrIndex());
            assertEquals(10, item.getValue().getNameAndTypeIndex());
        }
    }

    @Nested
    class ResolutionTests {

        @Test
        void getNameReturnsNullWhenConstPoolNotSet() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(0, 1);
            item.setValue(value);

            assertNull(item.getName());
        }

        @Test
        void getDescriptorReturnsNullWhenConstPoolNotSet() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(0, 1);
            item.setValue(value);

            assertNull(item.getDescriptor());
        }

        @Test
        void getDescriptorForObjectType() throws IOException {
            ConstantDynamicItem item = createConstantDynamicWithNameAndType("defaultValue", "Ljava/lang/String;");

            String descriptor = item.getDescriptor();
            assertNotNull(descriptor);
            assertEquals("Ljava/lang/String;", descriptor);
        }

        @Test
        void getDescriptorForLongType() throws IOException {
            ConstantDynamicItem item = createConstantDynamicWithNameAndType("timestamp", "J");

            String descriptor = item.getDescriptor();
            assertNotNull(descriptor);
            assertEquals("J", descriptor);
        }

        @Test
        void getDescriptorForDoubleType() throws IOException {
            ConstantDynamicItem item = createConstantDynamicWithNameAndType("epsilon", "D");

            String descriptor = item.getDescriptor();
            assertNotNull(descriptor);
            assertEquals("D", descriptor);
        }

        @Test
        void getNameReturnsCorrectValue() throws IOException {
            ConstantDynamicItem item = createConstantDynamicWithNameAndType("PI", "D");

            String name = item.getName();
            assertNotNull(name);
            assertEquals("PI", name);
        }
    }

    @Nested
    class BootstrapMethodTests {

        @Test
        void getBootstrapMethodAttrIndexFromValue() {
            ConstantDynamic value = new ConstantDynamic(42, 100);
            ConstantDynamicItem item = new ConstantDynamicItem(value);

            assertEquals(42, item.getBootstrapMethodAttrIndex());
        }

        @Test
        void getBootstrapMethodAttrIndexAfterSetValue() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(15, 30);
            item.setValue(value);

            assertEquals(15, item.getBootstrapMethodAttrIndex());
        }

        @Test
        void getBootstrapMethodAttrIndexWithZero() {
            ConstantDynamic value = new ConstantDynamic(0, 5);
            ConstantDynamicItem item = new ConstantDynamicItem(value);

            assertEquals(0, item.getBootstrapMethodAttrIndex());
        }

        @Test
        void getBootstrapMethodAttrIndexWithLargeValue() {
            ConstantDynamic value = new ConstantDynamic(65535, 10);
            ConstantDynamicItem item = new ConstantDynamicItem(value);

            assertEquals(65535, item.getBootstrapMethodAttrIndex());
        }
    }

    @Nested
    class SerializationTests {

        @Test
        void writeConstantDynamic() throws IOException {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(0x0004, 0x000C);
            item.setValue(value);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0x00, bytes[0] & 0xFF);
            assertEquals(0x04, bytes[1] & 0xFF);
            assertEquals(0x00, bytes[2] & 0xFF);
            assertEquals(0x0C, bytes[3] & 0xFF);
        }

        @Test
        void writeConstantDynamicWithZeroIndices() throws IOException {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(0, 0);
            item.setValue(value);

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
        void writeConstantDynamicWithMaxIndices() throws IOException {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(0xFFFF, 0xFFFF);
            item.setValue(value);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0xFF, bytes[0] & 0xFF);
            assertEquals(0xFF, bytes[1] & 0xFF);
            assertEquals(0xFF, bytes[2] & 0xFF);
            assertEquals(0xFF, bytes[3] & 0xFF);
        }

        @Test
        void writeConstantDynamicWithMixedIndices() throws IOException {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(0x1234, 0x5678);
            item.setValue(value);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0x12, bytes[0] & 0xFF);
            assertEquals(0x34, bytes[1] & 0xFF);
            assertEquals(0x56, bytes[2] & 0xFF);
            assertEquals(0x78, bytes[3] & 0xFF);
        }
    }

    @Nested
    class TypeTests {

        @Test
        void getTypeReturnsConstantDynamicConstant() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            assertEquals(Item.ITEM_DYNAMIC, item.getType());
            assertEquals(0x11, item.getType());
        }

        @Test
        void typeIsConsistentAcrossInstances() {
            ConstantDynamicItem item1 = new ConstantDynamicItem();
            ConstantDynamicItem item2 = new ConstantDynamicItem(new ConstantDynamic(1, 2));

            assertEquals(item1.getType(), item2.getType());
        }
    }

    @Nested
    class ValueTests {

        @Test
        void getValueReturnsConstantDynamic() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(8, 16);
            item.setValue(value);

            ConstantDynamic result = item.getValue();
            assertNotNull(result);
            assertEquals(8, result.getBootstrapMethodAttrIndex());
            assertEquals(16, result.getNameAndTypeIndex());
        }

        @Test
        void valueFromConstructorIsAccessible() {
            ConstantDynamic value = new ConstantDynamic(25, 50);
            ConstantDynamicItem item = new ConstantDynamicItem(value);

            ConstantDynamic result = item.getValue();
            assertNotNull(result);
            assertSame(value, result);
        }

        @Test
        void constantDynamicValuesAreImmutable() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            ConstantDynamic value = new ConstantDynamic(100, 200);
            item.setValue(value);

            assertEquals(100, item.getValue().getBootstrapMethodAttrIndex());
            assertEquals(200, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void setValueReplacesExistingValue() {
            ConstantDynamic value1 = new ConstantDynamic(10, 20);
            ConstantDynamic value2 = new ConstantDynamic(30, 40);

            ConstantDynamicItem item = new ConstantDynamicItem(value1);
            assertEquals(10, item.getValue().getBootstrapMethodAttrIndex());

            item.setValue(value2);
            assertEquals(30, item.getValue().getBootstrapMethodAttrIndex());
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void handlesEmptyNameGracefully() throws IOException {
            ConstantDynamicItem item = createConstantDynamicWithNameAndType("", "I");

            String name = item.getName();
            assertNotNull(name);
            assertEquals("", name);
        }

        @Test
        void handlesComplexDescriptor() throws IOException {
            ConstantDynamicItem item = createConstantDynamicWithNameAndType("complexValue", "Ljava/util/List;");

            String descriptor = item.getDescriptor();
            assertNotNull(descriptor);
            assertEquals("Ljava/util/List;", descriptor);
        }

        @Test
        void valueCanBeNull() {
            ConstantDynamicItem item = new ConstantDynamicItem();
            assertNull(item.getValue());
        }
    }

    private ConstantDynamicItem createConstantDynamicWithNameAndType(String name, String descriptor) {
        int natIndex = constPool.addNameAndType(name, descriptor);
        ConstantDynamicItem item = new ConstantDynamicItem();
        ConstantDynamic value = new ConstantDynamic(0, natIndex);
        item.setValue(value);
        item.setConstPool(constPool);
        return item;
    }
}

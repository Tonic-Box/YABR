package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.InterfaceRef;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceRefItemTest {

    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        classFile = BytecodeBuilder.forClass("com/test/InterfaceRefTest")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();
        constPool = classFile.getConstPool();
    }

    @Nested
    class ConstructionTests {

        @Test
        void defaultConstructor() {
            InterfaceRefItem item = new InterfaceRefItem();
            assertNotNull(item);
            assertEquals(Item.ITEM_INTERFACE_REF, item.getType());
        }

        @Test
        void setValueWithInterfaceRef() {
            InterfaceRefItem item = new InterfaceRefItem();
            InterfaceRef ref = new InterfaceRef(4, 9);
            item.setValue(ref);

            assertEquals(ref, item.getValue());
            assertEquals(4, item.getValue().getClassIndex());
            assertEquals(9, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void setConstPool() {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setConstPool(constPool);

            assertEquals(constPool, item.getConstPool());
        }
    }

    @Nested
    class ResolutionTests {

        @Test
        void getOwnerReturnsNullWhenConstPoolNotSet() {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setValue(new InterfaceRef(1, 2));

            assertNull(item.getOwner());
        }

        @Test
        void getNameReturnsNullWhenConstPoolNotSet() {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setValue(new InterfaceRef(1, 2));

            assertNull(item.getName());
        }

        @Test
        void getDescriptorReturnsNullWhenConstPoolNotSet() {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setValue(new InterfaceRef(1, 2));

            assertNull(item.getDescriptor());
        }

        @Test
        void getOwnerWithConstPool() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/Runnable");
            int natIndex = constPool.addNameAndType("run", "()V");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            String owner = item.getOwner();
            assertNotNull(owner);
            assertEquals("java/lang/Runnable", owner);
        }

        @Test
        void getNameWithConstPool() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/List");
            int natIndex = constPool.addNameAndType("size", "()I");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            String name = item.getName();
            assertNotNull(name);
            assertEquals("size", name);
        }

        @Test
        void getDescriptorWithConstPool() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/List");
            int natIndex = constPool.addNameAndType("add", "(Ljava/lang/Object;)Z");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            String descriptor = item.getDescriptor();
            assertNotNull(descriptor);
            assertEquals("(Ljava/lang/Object;)Z", descriptor);
        }

        @Test
        void ownerReturnsInternalName() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/Map");
            int natIndex = constPool.addNameAndType("put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            String owner = item.getOwner();
            assertNotNull(owner);
            assertFalse(owner.contains("."));
            assertTrue(owner.contains("/"));
        }
    }

    @Nested
    class ParameterAnalysisTests {

        @Test
        void getParameterCountThrowsWhenConstPoolNotSet() {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setValue(new InterfaceRef(1, 2));

            assertThrows(IllegalStateException.class, item::getParameterCount);
        }

        @Test
        void getParameterCountForNoParameters() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/Runnable");
            int natIndex = constPool.addNameAndType("run", "()V");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(0, item.getParameterCount());
        }

        @Test
        void getParameterCountForOneParameter() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/function/Consumer");
            int natIndex = constPool.addNameAndType("accept", "(Ljava/lang/Object;)V");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(1, item.getParameterCount());
        }

        @Test
        void getParameterCountForMultipleParameters() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/Map");
            int natIndex = constPool.addNameAndType("put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(2, item.getParameterCount());
        }

        @Test
        void getReturnTypeSlotsThrowsWhenConstPoolNotSet() {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setValue(new InterfaceRef(1, 2));

            assertThrows(IllegalStateException.class, item::getReturnTypeSlots);
        }

        @Test
        void getReturnTypeSlotsForVoidReturn() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/Runnable");
            int natIndex = constPool.addNameAndType("run", "()V");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(0, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForIntReturn() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/List");
            int natIndex = constPool.addNameAndType("size", "()I");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(1, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForLongReturn() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/stream/LongStream");
            int natIndex = constPool.addNameAndType("count", "()J");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(2, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForDoubleReturn() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/function/DoubleSupplier");
            int natIndex = constPool.addNameAndType("getAsDouble", "()D");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(2, item.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForObjectReturn() {
            ClassRefItem classRef = constPool.findOrAddClass("java/util/function/Supplier");
            int natIndex = constPool.addNameAndType("get", "()Ljava/lang/Object;");
            InterfaceRefItem item = constPool.findOrAddInterfaceRef(constPool.getIndexOf(classRef), natIndex);

            assertEquals(1, item.getReturnTypeSlots());
        }

        }

    @Nested
    class SerializationTests {

        @Test
        void writeInterfaceRef() throws IOException {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setValue(new InterfaceRef(0x0003, 0x0007));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0x00, bytes[0] & 0xFF);
            assertEquals(0x03, bytes[1] & 0xFF);
            assertEquals(0x00, bytes[2] & 0xFF);
            assertEquals(0x07, bytes[3] & 0xFF);
        }

        @Test
        void writeInterfaceRefWithLargeIndices() throws IOException {
            InterfaceRefItem item = new InterfaceRefItem();
            item.setValue(new InterfaceRef(0x1234, 0x5678));

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
        void getTypeReturnsInterfaceRefConstant() {
            InterfaceRefItem item = new InterfaceRefItem();
            assertEquals(Item.ITEM_INTERFACE_REF, item.getType());
            assertEquals(0xB, item.getType());
        }
    }

    @Nested
    class ValueTests {

        @Test
        void getValueReturnsInterfaceRef() {
            InterfaceRefItem item = new InterfaceRefItem();
            InterfaceRef ref = new InterfaceRef(7, 14);
            item.setValue(ref);

            InterfaceRef value = item.getValue();
            assertNotNull(value);
            assertSame(ref, value);
        }

        @Test
        void interfaceRefIndicesAreImmutable() {
            InterfaceRefItem item = new InterfaceRefItem();
            InterfaceRef ref = new InterfaceRef(100, 200);
            item.setValue(ref);

            assertEquals(100, item.getValue().getClassIndex());
            assertEquals(200, item.getValue().getNameAndTypeIndex());
        }
    }
}

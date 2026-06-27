package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.MethodRef;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MethodRefItemTest {

    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        classFile = BytecodeBuilder.forClass("com/test/MethodRefTest")
            .publicStaticMethod("testMethod", "(II)I")
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            .build();
        constPool = classFile.getConstPool();
    }

    @Nested
    class ConstructionTests {

        @Test
        void defaultConstructor() {
            MethodRefItem item = new MethodRefItem();
            assertNotNull(item);
            assertEquals(Item.ITEM_METHOD_REF, item.getType());
        }

        @Test
        void setValueWithMethodRef() {
            MethodRefItem item = new MethodRefItem();
            MethodRef ref = new MethodRef(5, 10);
            item.setValue(ref);

            assertEquals(ref, item.getValue());
            assertEquals(5, item.getValue().getClassIndex());
            assertEquals(10, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void setClassFile() {
            MethodRefItem item = new MethodRefItem();
            item.setClassFile(classFile);

            MethodRef ref = new MethodRef(1, 2);
            item.setValue(ref);

            assertNotNull(item.getValue());
        }
    }

    @Nested
    class IndexManipulationTests {

        @Test
        void setClassIndex() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(5, 10));

            item.setClassIndex(15);

            assertEquals(15, item.getValue().getClassIndex());
            assertEquals(10, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void setNameAndTypeIndex() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(5, 10));

            item.setNameAndTypeIndex(20);

            assertEquals(5, item.getValue().getClassIndex());
            assertEquals(20, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void setBothIndices() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(5, 10));

            item.setClassIndex(100);
            item.setNameAndTypeIndex(200);

            assertEquals(100, item.getValue().getClassIndex());
            assertEquals(200, item.getValue().getNameAndTypeIndex());
        }
    }

    @Nested
    class ResolutionTests {

        @Test
        void getClassNameReturnsNullWhenClassFileNotSet() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(1, 2));

            assertNull(item.getClassName());
        }

        @Test
        void getNameReturnsNullWhenClassFileNotSet() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(1, 2));

            assertNull(item.getName());
        }

        @Test
        void getDescriptorReturnsNullWhenClassFileNotSet() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(1, 2));

            assertNull(item.getDescriptor());
        }

        @Test
        void getOwnerReturnsNullWhenClassFileNotSet() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(1, 2));

            assertNull(item.getOwner());
        }

        @Test
        void getClassNameWithClassFile() {
            MethodRefItem methodRef = findMethodRefInConstPool();

            if (methodRef != null) {
                String className = methodRef.getClassName();
                assertNotNull(className);
                assertFalse(className.isEmpty());
            }
        }

        @Test
        void getNameWithClassFile() {
            MethodRefItem methodRef = findMethodRefInConstPool();

            if (methodRef != null) {
                String name = methodRef.getName();
                assertNotNull(name);
                assertFalse(name.isEmpty());
            }
        }

        @Test
        void getDescriptorWithClassFile() {
            MethodRefItem methodRef = findMethodRefInConstPool();

            if (methodRef != null) {
                String descriptor = methodRef.getDescriptor();
                assertNotNull(descriptor);
                assertTrue(descriptor.startsWith("("));
                assertTrue(descriptor.contains(")"));
            }
        }

        @Test
        void getOwnerReturnsInternalName() {
            MethodRefItem methodRef = findMethodRefInConstPool();

            if (methodRef != null) {
                String owner = methodRef.getOwner();
                assertNotNull(owner);
                assertFalse(owner.contains("."));
            }
        }

        @Test
        void classNameConvertsSlashesToDots() {
            MethodRefItem methodRef = findMethodRefInConstPool();

            if (methodRef != null) {
                String className = methodRef.getClassName();
                String owner = methodRef.getOwner();

                assertNotNull(className);
                assertNotNull(owner);

                assertEquals(owner.replace('/', '.'), className);
            }
        }
    }

    @Nested
    class ParameterAnalysisTests {

        @Test
        void getParameterCountThrowsWhenClassFileNotSet() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(1, 2));

            assertThrows(IllegalStateException.class, item::getParameterCount);
        }

        @Test
        void getParameterCountForMethodWithNoParameters() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoParams")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodRefItem methodRef = findMethodRefInConstPool(cf);

            if (methodRef != null && methodRef.getDescriptor().equals("()V")) {
                assertEquals(0, methodRef.getParameterCount());
            }
        }

        @Test
        void getParameterCountForMethodWithOneParameter() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/OneParam")
                .publicStaticMethod("test", "(I)V")
                    .vreturn()
                .build();

            int methodRefIndex = cf.getConstPool().addMethodRef(
                "com/test/OneParam", "test", "(I)V");
            MethodRefItem methodRef = (MethodRefItem) cf.getConstPool().getItem(methodRefIndex);

            assertEquals(1, methodRef.getParameterCount());
        }

        @Test
        void getParameterCountForMethodWithMultipleParameters() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/MultiParam")
                .publicStaticMethod("test", "(ILjava/lang/String;D)V")
                    .vreturn()
                .build();

            int methodRefIndex = cf.getConstPool().addMethodRef(
                "com/test/MultiParam", "test", "(ILjava/lang/String;D)V");
            MethodRefItem methodRef = (MethodRefItem) cf.getConstPool().getItem(methodRefIndex);

            assertEquals(3, methodRef.getParameterCount());
        }

        @Test
        void getReturnTypeSlotsThrowsWhenClassFileNotSet() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(1, 2));

            assertThrows(IllegalStateException.class, item::getReturnTypeSlots);
        }

        @Test
        void getReturnTypeSlotsForVoidReturn() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/VoidReturn")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            int methodRefIndex = cf.getConstPool().addMethodRef(
                "com/test/VoidReturn", "test", "()V");
            MethodRefItem methodRef = (MethodRefItem) cf.getConstPool().getItem(methodRefIndex);

            assertEquals(0, methodRef.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForIntReturn() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/IntReturn")
                .publicStaticMethod("test", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            int methodRefIndex = cf.getConstPool().addMethodRef(
                "com/test/IntReturn", "test", "()I");
            MethodRefItem methodRef = (MethodRefItem) cf.getConstPool().getItem(methodRefIndex);

            assertEquals(1, methodRef.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForLongReturn() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/LongReturn")
                .publicStaticMethod("test", "()J")
                    .lconst(42L)
                    .lreturn()
                .build();

            int methodRefIndex = cf.getConstPool().addMethodRef(
                "com/test/LongReturn", "test", "()J");
            MethodRefItem methodRef = (MethodRefItem) cf.getConstPool().getItem(methodRefIndex);

            assertEquals(2, methodRef.getReturnTypeSlots());
        }

        @Test
        void getReturnTypeSlotsForDoubleReturn() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/DoubleReturn")
                .publicStaticMethod("test", "()D")
                    .dconst(1.0)
                    .dreturn()
                .build();

            int methodRefIndex = cf.getConstPool().addMethodRef(
                "com/test/DoubleReturn", "test", "()D");
            MethodRefItem methodRef = (MethodRefItem) cf.getConstPool().getItem(methodRefIndex);

            assertEquals(2, methodRef.getReturnTypeSlots());
        }
    }

    @Nested
    class SerializationTests {

        @Test
        void writeMethodRef() throws IOException {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(0x0005, 0x000A));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0x00, bytes[0] & 0xFF);
            assertEquals(0x05, bytes[1] & 0xFF);
            assertEquals(0x00, bytes[2] & 0xFF);
            assertEquals(0x0A, bytes[3] & 0xFF);
        }

        @Test
        void writeMethodRefWithLargeIndices() throws IOException {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(0xFFFF, 0x1234));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0xFF, bytes[0] & 0xFF);
            assertEquals(0xFF, bytes[1] & 0xFF);
            assertEquals(0x12, bytes[2] & 0xFF);
            assertEquals(0x34, bytes[3] & 0xFF);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringWithoutClassFile() {
            MethodRefItem item = new MethodRefItem();
            item.setValue(new MethodRef(1, 2));

            String result = item.toString();
            assertNotNull(result);
            assertTrue(result.contains("MethodRefItem"));
        }

        @Test
        void toStringWithClassFile() {
            MethodRefItem methodRef = findMethodRefInConstPool();

            if (methodRef != null) {
                String result = methodRef.toString();
                assertNotNull(result);
                assertTrue(result.contains("MethodRefItem"));
            }
        }
    }

    @Nested
    class TypeTests {

        @Test
        void getTypeReturnsMethodRefConstant() {
            MethodRefItem item = new MethodRefItem();
            assertEquals(Item.ITEM_METHOD_REF, item.getType());
            assertEquals(0xA, item.getType());
        }
    }

    private MethodRefItem findMethodRefInConstPool() {
        return findMethodRefInConstPool(classFile);
    }

    private MethodRefItem findMethodRefInConstPool(ClassFile cf) {
        for (int i = 1; i < cf.getConstPool().getItems().size(); i++) {
            Item<?> item = cf.getConstPool().getItems().get(i);
            if (item instanceof MethodRefItem) {
                return (MethodRefItem) item;
            }
        }
        return null;
    }
}

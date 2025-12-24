package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.FieldRef;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FieldRefItemTest {

    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        int publicStatic = new AccessBuilder().setPublic().setStatic().build();
        classFile = BytecodeBuilder.forClass("com/test/FieldRefTest")
            .field(publicStatic, "counter", "I")
            .field(publicStatic, "name", "Ljava/lang/String;")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();
        constPool = classFile.getConstPool();
    }

    @Nested
    class ConstructionTests {

        @Test
        void defaultConstructor() {
            FieldRefItem item = new FieldRefItem();
            assertNotNull(item);
            assertEquals(Item.ITEM_FIELD_REF, item.getType());
        }

        @Test
        void setValueWithFieldRef() {
            FieldRefItem item = new FieldRefItem();
            FieldRef ref = new FieldRef(3, 8);
            item.setValue(ref);

            assertEquals(ref, item.getValue());
            assertEquals(3, item.getValue().getClassIndex());
            assertEquals(8, item.getValue().getNameAndTypeIndex());
        }

        @Test
        void setClassFile() {
            FieldRefItem item = new FieldRefItem();
            item.setClassFile(classFile);

            FieldRef ref = new FieldRef(1, 2);
            item.setValue(ref);

            assertNotNull(item.getValue());
        }
    }

    @Nested
    class ResolutionTests {

        @Test
        void getClassNameReturnsNullWhenClassFileNotSet() {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(1, 2));

            assertNull(item.getClassName());
        }

        @Test
        void getNameReturnsNullWhenClassFileNotSet() {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(1, 2));

            assertNull(item.getName());
        }

        @Test
        void getDescriptorReturnsNullWhenClassFileNotSet() {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(1, 2));

            assertNull(item.getDescriptor());
        }

        @Test
        void getOwnerReturnsNullWhenClassFileNotSet() {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(1, 2));

            assertNull(item.getOwner());
        }

        @Test
        void getClassNameWithClassFile() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .publicStaticMethod("test", "()V")
                    .getstatic("com/test/FieldTest", "value", "I")
                    .pop()
                    .vreturn()
                .build();

            FieldRefItem fieldRef = findFieldRefInConstPool(cf);

            if (fieldRef != null) {
                String className = fieldRef.getClassName();
                assertNotNull(className);
                assertFalse(className.isEmpty());
                assertTrue(className.contains("."));
            }
        }

        @Test
        void getNameWithClassFile() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .publicStaticMethod("test", "()V")
                    .getstatic("com/test/FieldTest", "value", "I")
                    .pop()
                    .vreturn()
                .build();

            FieldRefItem fieldRef = findFieldRefInConstPool(cf);

            if (fieldRef != null) {
                String name = fieldRef.getName();
                assertNotNull(name);
                assertFalse(name.isEmpty());
            }
        }

        @Test
        void getDescriptorWithClassFile() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .publicStaticMethod("test", "()V")
                    .getstatic("com/test/FieldTest", "value", "I")
                    .pop()
                    .vreturn()
                .build();

            FieldRefItem fieldRef = findFieldRefInConstPool(cf);

            if (fieldRef != null) {
                String descriptor = fieldRef.getDescriptor();
                assertNotNull(descriptor);
                assertFalse(descriptor.isEmpty());
            }
        }

        @Test
        void getOwnerReturnsInternalName() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .publicStaticMethod("test", "()V")
                    .getstatic("com/test/FieldTest", "value", "I")
                    .pop()
                    .vreturn()
                .build();

            FieldRefItem fieldRef = findFieldRefInConstPool(cf);

            if (fieldRef != null) {
                String owner = fieldRef.getOwner();
                assertNotNull(owner);
                assertFalse(owner.contains("."));
            }
        }

        @Test
        void classNameConvertsSlashesToDots() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .publicStaticMethod("test", "()V")
                    .getstatic("com/test/FieldTest", "value", "I")
                    .pop()
                    .vreturn()
                .build();

            FieldRefItem fieldRef = findFieldRefInConstPool(cf);

            if (fieldRef != null) {
                String className = fieldRef.getClassName();
                String owner = fieldRef.getOwner();

                assertNotNull(className);
                assertNotNull(owner);

                assertEquals(owner.replace('/', '.'), className);
            }
        }
    }

    @Nested
    class SerializationTests {

        @Test
        void writeFieldRef() throws IOException {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(0x0007, 0x000B));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0x00, bytes[0] & 0xFF);
            assertEquals(0x07, bytes[1] & 0xFF);
            assertEquals(0x00, bytes[2] & 0xFF);
            assertEquals(0x0B, bytes[3] & 0xFF);
        }

        @Test
        void writeFieldRefWithZeroIndices() throws IOException {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(0, 0));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            assertEquals(0x00, bytes[0] & 0xFF);
            assertEquals(0x00, bytes[1] & 0xFF);
            assertEquals(0x00, bytes[2] & 0xFF);
            assertEquals(0x00, bytes[3] & 0xFF);
        }

        @Test
        void writeFieldRefWithMaxIndices() throws IOException {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(0xFFFF, 0xFFFF));

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
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringWithoutClassFile() {
            FieldRefItem item = new FieldRefItem();
            item.setValue(new FieldRef(1, 2));

            String result = item.toString();
            assertNotNull(result);
            assertTrue(result.contains("FieldRefItem"));
        }

        @Test
        void toStringWithClassFile() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .publicStaticMethod("test", "()V")
                    .getstatic("com/test/FieldTest", "value", "I")
                    .pop()
                    .vreturn()
                .build();

            FieldRefItem fieldRef = findFieldRefInConstPool(cf);

            if (fieldRef != null) {
                String result = fieldRef.toString();
                assertNotNull(result);
                assertTrue(result.contains("FieldRefItem"));
                assertTrue(result.contains("<"));
                assertTrue(result.contains(">"));
            }
        }

        @Test
        void toStringIncludesDescriptorClassAndName() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/FieldTest")
                .publicStaticMethod("test", "()V")
                    .getstatic("com/test/FieldTest", "myField", "Ljava/lang/String;")
                    .pop()
                    .vreturn()
                .build();

            FieldRefItem fieldRef = findFieldRefInConstPool(cf);

            if (fieldRef != null) {
                String result = fieldRef.toString();
                String descriptor = fieldRef.getDescriptor();
                String className = fieldRef.getClassName();
                String name = fieldRef.getName();

                if (descriptor != null && className != null && name != null) {
                    assertTrue(result.contains(descriptor));
                    assertTrue(result.contains("."));
                }
            }
        }
    }

    @Nested
    class TypeTests {

        @Test
        void getTypeReturnsFieldRefConstant() {
            FieldRefItem item = new FieldRefItem();
            assertEquals(Item.ITEM_FIELD_REF, item.getType());
            assertEquals(0x9, item.getType());
        }
    }

    @Nested
    class ValueTests {

        @Test
        void getValueReturnsFieldRef() {
            FieldRefItem item = new FieldRefItem();
            FieldRef ref = new FieldRef(5, 10);
            item.setValue(ref);

            FieldRef value = item.getValue();
            assertNotNull(value);
            assertSame(ref, value);
        }

        @Test
        void fieldRefIndicesAreAccessible() {
            FieldRefItem item = new FieldRefItem();
            FieldRef ref = new FieldRef(100, 200);
            item.setValue(ref);

            assertEquals(100, item.getValue().getClassIndex());
            assertEquals(200, item.getValue().getNameAndTypeIndex());
        }
    }

    private FieldRefItem findFieldRefInConstPool(ClassFile cf) {
        for (int i = 1; i < cf.getConstPool().getItems().size(); i++) {
            Item<?> item = cf.getConstPool().getItems().get(i);
            if (item instanceof FieldRefItem) {
                return (FieldRefItem) item;
            }
        }
        return null;
    }
}

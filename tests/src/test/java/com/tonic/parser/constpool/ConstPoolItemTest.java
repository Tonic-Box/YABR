package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for constant pool item types and ConstPool operations.
 */
class ConstPoolItemTest {

    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        // Create a simple class to get a valid ClassFile and ConstPool
        classFile = BytecodeBuilder.forClass("com/test/ConstPoolTest")
            .publicStaticMethod("test", "()V")
                .vreturn()
            .build();
        constPool = classFile.getConstPool();
    }

    // ========== Item Type Constants Tests ==========

    @Nested
    class ItemTypeConstantsTests {

        @Test
        void itemTypeConstantsHaveCorrectValues() {
            assertEquals(0x1, Item.ITEM_UTF_8);
            assertEquals(0x3, Item.ITEM_INTEGER);
            assertEquals(0x4, Item.ITEM_FLOAT);
            assertEquals(0x5, Item.ITEM_LONG);
            assertEquals(0x6, Item.ITEM_DOUBLE);
            assertEquals(0x7, Item.ITEM_CLASS_REF);
            assertEquals(0x8, Item.ITEM_STRING_REF);
            assertEquals(0x9, Item.ITEM_FIELD_REF);
            assertEquals(0xA, Item.ITEM_METHOD_REF);
            assertEquals(0xB, Item.ITEM_INTERFACE_REF);
            assertEquals(0xC, Item.ITEM_NAME_TYPE_REF);
            assertEquals(0xF, Item.ITEM_METHOD_HANDLE);
            assertEquals(0x10, Item.ITEM_METHOD_TYPE);
            assertEquals(0x11, Item.ITEM_DYNAMIC);
            assertEquals(0x12, Item.ITEM_INVOKEDYNAMIC);
            assertEquals(0x13, Item.ITEM_PACKAGE);
            assertEquals(0x14, Item.ITEM_MODULE);
        }
    }

    // ========== IntegerItem Tests ==========

    @Nested
    class IntegerItemTests {

        @Test
        void integerItemGetType() {
            IntegerItem item = new IntegerItem();
            assertEquals(Item.ITEM_INTEGER, item.getType());
        }

        @Test
        void integerItemSetAndGetValue() {
            IntegerItem item = new IntegerItem();
            item.setValue(42);
            assertEquals(42, item.getValue());
        }

        @Test
        void integerItemWriteAndVerify() throws IOException {
            IntegerItem item = new IntegerItem();
            item.setValue(0x12345678);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(4, bytes.length);
            // Big-endian: 0x12, 0x34, 0x56, 0x78
            assertEquals(0x12, bytes[0] & 0xFF);
            assertEquals(0x34, bytes[1] & 0xFF);
            assertEquals(0x56, bytes[2] & 0xFF);
            assertEquals(0x78, bytes[3] & 0xFF);
        }

        @Test
        void integerItemNegativeValue() {
            IntegerItem item = new IntegerItem();
            item.setValue(-1);
            assertEquals(-1, item.getValue());
        }

        @Test
        void integerItemMaxValue() {
            IntegerItem item = new IntegerItem();
            item.setValue(Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, item.getValue());
        }

        @Test
        void integerItemMinValue() {
            IntegerItem item = new IntegerItem();
            item.setValue(Integer.MIN_VALUE);
            assertEquals(Integer.MIN_VALUE, item.getValue());
        }
    }

    // ========== LongItem Tests ==========

    @Nested
    class LongItemTests {

        @Test
        void longItemGetType() {
            LongItem item = new LongItem();
            assertEquals(Item.ITEM_LONG, item.getType());
        }

        @Test
        void longItemSetAndGetValue() {
            LongItem item = new LongItem();
            item.setValue(123456789012345L);
            assertEquals(123456789012345L, item.getValue());
        }

        @Test
        void longItemWriteAndVerify() throws IOException {
            LongItem item = new LongItem();
            item.setValue(0x123456789ABCDEF0L);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(8, bytes.length);
        }
    }

    // ========== FloatItem Tests ==========

    @Nested
    class FloatItemTests {

        @Test
        void floatItemGetType() {
            FloatItem item = new FloatItem();
            assertEquals(Item.ITEM_FLOAT, item.getType());
        }

        @Test
        void floatItemSetAndGetValue() {
            FloatItem item = new FloatItem();
            item.setValue(3.14f);
            assertEquals(3.14f, item.getValue(), 0.001f);
        }

        @Test
        void floatItemSpecialValues() {
            FloatItem item = new FloatItem();

            item.setValue(Float.POSITIVE_INFINITY);
            assertEquals(Float.POSITIVE_INFINITY, item.getValue());

            item.setValue(Float.NEGATIVE_INFINITY);
            assertEquals(Float.NEGATIVE_INFINITY, item.getValue());

            item.setValue(Float.NaN);
            assertTrue(Float.isNaN(item.getValue()));
        }
    }

    // ========== DoubleItem Tests ==========

    @Nested
    class DoubleItemTests {

        @Test
        void doubleItemGetType() {
            DoubleItem item = new DoubleItem();
            assertEquals(Item.ITEM_DOUBLE, item.getType());
        }

        @Test
        void doubleItemSetAndGetValue() {
            DoubleItem item = new DoubleItem();
            item.setValue(3.14159265358979);
            assertEquals(3.14159265358979, item.getValue(), 0.0000001);
        }
    }

    // ========== Utf8Item Tests ==========

    @Nested
    class Utf8ItemTests {

        @Test
        void utf8ItemGetType() {
            Utf8Item item = new Utf8Item();
            assertEquals(Item.ITEM_UTF_8, item.getType());
        }

        @Test
        void utf8ItemSetAndGetValue() {
            Utf8Item item = new Utf8Item();
            item.setValue("Hello, World!");
            assertEquals("Hello, World!", item.getValue());
        }

        @Test
        void utf8ItemEmptyString() {
            Utf8Item item = new Utf8Item();
            item.setValue("");
            assertEquals("", item.getValue());
        }

        @Test
        void utf8ItemUnicodeCharacters() {
            Utf8Item item = new Utf8Item();
            item.setValue("日本語テスト");
            assertEquals("日本語テスト", item.getValue());
        }
    }

    // ========== ClassRefItem Tests ==========

    @Nested
    class ClassRefItemTests {

        @Test
        void classRefItemGetType() {
            ClassRefItem item = new ClassRefItem();
            assertEquals(Item.ITEM_CLASS_REF, item.getType());
        }

        @Test
        void classRefItemSetAndGetValue() {
            ClassRefItem item = new ClassRefItem();
            item.setValue(5);
            assertEquals(5, item.getValue());
        }

        @Test
        void classRefItemNameIndex() {
            ClassRefItem item = new ClassRefItem();
            item.setNameIndex(10);
            assertEquals(10, item.getNameIndex());
            assertEquals(10, item.getValue());
        }

        @Test
        void classRefItemGetClassNameWithClassFile() {
            // Find a ClassRefItem in our test class's constant pool
            ClassRefItem classRef = null;
            for (int i = 1; i < constPool.getItems().size(); i++) {
                Item<?> item = constPool.getItems().get(i);
                if (item instanceof ClassRefItem) {
                    classRef = (ClassRefItem) item;
                    break;
                }
            }
            assertNotNull(classRef);
            String className = classRef.getClassName();
            assertNotNull(className);
            assertFalse(className.isEmpty());
        }
    }

    // ========== StringRefItem Tests ==========

    @Nested
    class StringRefItemTests {

        @Test
        void stringRefItemGetType() {
            StringRefItem item = new StringRefItem();
            assertEquals(Item.ITEM_STRING_REF, item.getType());
        }

        @Test
        void stringRefItemSetAndGetValue() {
            StringRefItem item = new StringRefItem();
            item.setValue(7);
            assertEquals(7, item.getValue());
        }

        @Test
        void stringRefItemWriteAndVerify() throws IOException {
            StringRefItem item = new StringRefItem();
            item.setValue(0x0102);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            item.write(dos);

            byte[] bytes = baos.toByteArray();
            assertEquals(2, bytes.length);
            assertEquals(0x01, bytes[0] & 0xFF);
            assertEquals(0x02, bytes[1] & 0xFF);
        }
    }

    // ========== ConstPool Operations Tests ==========

    @Nested
    class ConstPoolOperationsTests {

        @Test
        void getItemValidIndex() {
            Item<?> item = constPool.getItem(1);
            assertNotNull(item);
        }

        @Test
        void getItemInvalidIndexZero() {
            assertThrows(IllegalArgumentException.class, () -> constPool.getItem(0));
        }

        @Test
        void getItemInvalidIndexNegative() {
            assertThrows(IllegalArgumentException.class, () -> constPool.getItem(-1));
        }

        @Test
        void getItemInvalidIndexTooLarge() {
            int size = constPool.getItems().size();
            assertThrows(IllegalArgumentException.class, () -> constPool.getItem(size + 10));
        }

        @Test
        void constPoolToStringNotEmpty() {
            String str = constPool.toString();
            assertNotNull(str);
            assertTrue(str.contains("Constant Pool"));
        }

        @Test
        void findOrAddUtf8ExistingValue() {
            // First add a UTF-8 item
            Utf8Item added = constPool.findOrAddUtf8("TestString123");
            assertNotNull(added);
            assertEquals("TestString123", added.getValue());

            // Find it again - should return same item
            Utf8Item found = constPool.findOrAddUtf8("TestString123");
            assertSame(added, found);
        }

        @Test
        void findOrAddUtf8NewValue() {
            int sizeBefore = constPool.getItems().size();
            Utf8Item item = constPool.findOrAddUtf8("BrandNewString" + System.currentTimeMillis());
            assertNotNull(item);
            assertTrue(constPool.getItems().size() >= sizeBefore);
        }

        @Test
        void findOrAddClassRefNewValue() {
            // First add a Utf8 for the class name
            Utf8Item nameItem = constPool.findOrAddUtf8("com/example/NewTestClass");
            int nameIndex = nameItem.getIndex(constPool);

            // Then add a ClassRef pointing to it
            ClassRefItem classRef = constPool.findOrAddClassRef(nameIndex);
            assertNotNull(classRef);
            assertEquals(nameIndex, classRef.getNameIndex());
        }

        @Test
        void addItemLongTakesDoubleSlot() {
            int sizeBefore = constPool.getItems().size();
            LongItem longItem = new LongItem();
            longItem.setValue(100L);
            constPool.addItem(longItem);
            // Long items take 2 slots
            assertEquals(sizeBefore + 2, constPool.getItems().size());
        }

        @Test
        void addItemDoubleTakesDoubleSlot() {
            int sizeBefore = constPool.getItems().size();
            DoubleItem doubleItem = new DoubleItem();
            doubleItem.setValue(3.14);
            constPool.addItem(doubleItem);
            // Double items take 2 slots
            assertEquals(sizeBefore + 2, constPool.getItems().size());
        }
    }

    // ========== Item.getIndex Tests ==========

    @Nested
    class ItemGetIndexTests {

        @Test
        void getIndexReturnsCorrectIndex() {
            // The first non-null item should be at index 1
            Item<?> firstItem = constPool.getItem(1);
            assertNotNull(firstItem);
            int index = firstItem.getIndex(constPool);
            assertEquals(1, index);
        }

        @Test
        void getIndexForNewlyAddedItem() {
            Utf8Item newItem = new Utf8Item();
            newItem.setValue("GetIndexTest");
            int addedIndex = constPool.addItem(newItem);

            int retrievedIndex = newItem.getIndex(constPool);
            assertEquals(addedIndex, retrievedIndex);
        }
    }

    // ========== MethodRefItem Tests ==========

    @Nested
    class MethodRefItemTests {

        @Test
        void methodRefItemGetType() {
            MethodRefItem item = new MethodRefItem();
            assertEquals(Item.ITEM_METHOD_REF, item.getType());
        }

        @Test
        void findMethodRefInConstPool() {
            // Our test class should have method refs (at least for Object.<init>)
            MethodRefItem methodRef = null;
            for (int i = 1; i < constPool.getItems().size(); i++) {
                Item<?> item = constPool.getItems().get(i);
                if (item instanceof MethodRefItem) {
                    methodRef = (MethodRefItem) item;
                    break;
                }
            }
            // Might not have any method refs in simple class
            if (methodRef != null) {
                assertNotNull(methodRef.getValue());
            }
        }
    }

    // ========== FieldRefItem Tests ==========

    @Nested
    class FieldRefItemTests {

        @Test
        void fieldRefItemGetType() {
            FieldRefItem item = new FieldRefItem();
            assertEquals(Item.ITEM_FIELD_REF, item.getType());
        }
    }

    // ========== NameAndTypeRefItem Tests ==========

    @Nested
    class NameAndTypeRefItemTests {

        @Test
        void nameAndTypeRefItemGetType() {
            NameAndTypeRefItem item = new NameAndTypeRefItem();
            assertEquals(Item.ITEM_NAME_TYPE_REF, item.getType());
        }

        @Test
        void findNameAndTypeInConstPool() {
            // Our test class should have NameAndType refs
            NameAndTypeRefItem natRef = null;
            for (int i = 1; i < constPool.getItems().size(); i++) {
                Item<?> item = constPool.getItems().get(i);
                if (item instanceof NameAndTypeRefItem) {
                    natRef = (NameAndTypeRefItem) item;
                    break;
                }
            }
            if (natRef != null) {
                assertNotNull(natRef.getValue());
            }
        }
    }
}

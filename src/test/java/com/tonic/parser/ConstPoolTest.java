package com.tonic.parser;

import com.tonic.parser.constpool.*;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConstPool functionality.
 * Covers constant pool item management, find-or-add operations, and retrieval.
 */
class ConstPoolTest {

    private ClassPool pool;
    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/TestClass", access);
        constPool = classFile.getConstPool();
    }

    // ========== Basic Operations Tests ==========

    @Test
    void constPoolIsNotNull() {
        assertNotNull(constPool);
    }

    @Test
    void constPoolHasInitialItems() {
        // New class has constant pool entries for class name, superclass, etc.
        assertTrue(constPool.getItems().size() > 1);
    }

    @Test
    void getItemAtIndexZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> constPool.getItem(0));
    }

    @Test
    void getItemAtNegativeIndexThrows() {
        assertThrows(IllegalArgumentException.class, () -> constPool.getItem(-1));
    }

    @Test
    void getItemAtTooLargeIndexThrows() {
        int size = constPool.getItems().size();
        assertThrows(IllegalArgumentException.class, () -> constPool.getItem(size + 100));
    }

    // ========== Utf8Item Tests ==========

    @Test
    void findOrAddUtf8CreatesNewItem() {
        Utf8Item item = constPool.findOrAddUtf8("newString");
        assertNotNull(item);
        assertEquals("newString", item.getValue());
    }

    @Test
    void findOrAddUtf8ReturnsExistingItem() {
        Utf8Item first = constPool.findOrAddUtf8("duplicateString");
        Utf8Item second = constPool.findOrAddUtf8("duplicateString");
        assertSame(first, second);
    }

    @Test
    void findOrAddUtf8HandlesEmptyString() {
        Utf8Item item = constPool.findOrAddUtf8("");
        assertNotNull(item);
        assertEquals("", item.getValue());
    }

    @Test
    void findOrAddUtf8HandlesUnicodeString() {
        Utf8Item item = constPool.findOrAddUtf8("日本語テスト");
        assertNotNull(item);
        assertEquals("日本語テスト", item.getValue());
    }

    // ========== IntegerItem Tests ==========

    @Test
    void findOrAddIntegerCreatesNewItem() {
        IntegerItem item = constPool.findOrAddInteger(42);
        assertNotNull(item);
        assertEquals(42, item.getValue());
    }

    @Test
    void findOrAddIntegerReturnsExistingItem() {
        IntegerItem first = constPool.findOrAddInteger(100);
        IntegerItem second = constPool.findOrAddInteger(100);
        assertSame(first, second);
    }

    @Test
    void findOrAddIntegerHandlesNegative() {
        IntegerItem item = constPool.findOrAddInteger(-999);
        assertEquals(-999, item.getValue());
    }

    @Test
    void findOrAddIntegerHandlesMaxValue() {
        IntegerItem item = constPool.findOrAddInteger(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, item.getValue());
    }

    @Test
    void findOrAddIntegerHandlesMinValue() {
        IntegerItem item = constPool.findOrAddInteger(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, item.getValue());
    }

    // ========== LongItem Tests ==========

    @Test
    void findOrAddLongCreatesNewItem() {
        LongItem item = constPool.findOrAddLong(123456789L);
        assertNotNull(item);
        assertEquals(123456789L, item.getValue());
    }

    @Test
    void findOrAddLongReturnsExistingItem() {
        LongItem first = constPool.findOrAddLong(987654321L);
        LongItem second = constPool.findOrAddLong(987654321L);
        assertSame(first, second);
    }

    @Test
    void findOrAddLongHandlesMaxValue() {
        LongItem item = constPool.findOrAddLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, item.getValue());
    }

    // ========== FloatItem Tests ==========

    @Test
    void findOrAddFloatCreatesNewItem() {
        FloatItem item = constPool.findOrAddFloat(3.14f);
        assertNotNull(item);
        assertEquals(3.14f, item.getValue(), 0.001f);
    }

    @Test
    void findOrAddFloatReturnsExistingItem() {
        FloatItem first = constPool.findOrAddFloat(2.718f);
        FloatItem second = constPool.findOrAddFloat(2.718f);
        assertSame(first, second);
    }

    // ========== DoubleItem Tests ==========

    @Test
    void findOrAddDoubleCreatesNewItem() {
        DoubleItem item = constPool.findOrAddDouble(3.14159265359);
        assertNotNull(item);
        assertEquals(3.14159265359, item.getValue(), 0.0000000001);
    }

    @Test
    void findOrAddDoubleReturnsExistingItem() {
        DoubleItem first = constPool.findOrAddDouble(2.718281828);
        DoubleItem second = constPool.findOrAddDouble(2.718281828);
        assertSame(first, second);
    }

    // ========== StringRefItem Tests ==========

    @Test
    void findOrAddStringCreatesNewItem() {
        StringRefItem item = constPool.findOrAddString("Hello, World!");
        assertNotNull(item);
    }

    @Test
    void findOrAddStringReturnsExistingItem() {
        StringRefItem first = constPool.findOrAddString("duplicate");
        StringRefItem second = constPool.findOrAddString("duplicate");
        assertSame(first, second);
    }

    // ========== ClassRefItem Tests ==========

    @Test
    void findOrAddClassCreatesNewItem() {
        ClassRefItem item = constPool.findOrAddClass("java/util/ArrayList");
        assertNotNull(item);
    }

    @Test
    void findOrAddClassReturnsExistingItem() {
        ClassRefItem first = constPool.findOrAddClass("java/util/HashMap");
        ClassRefItem second = constPool.findOrAddClass("java/util/HashMap");
        assertSame(first, second);
    }

    @Test
    void getClassNameReturnsClassName() {
        ClassRefItem classRef = constPool.findOrAddClass("java/lang/String");
        int index = constPool.getIndexOf(classRef);
        String className = constPool.getClassName(index);
        assertEquals("java/lang/String", className);
    }

    @Test
    void getClassNameReturnsNullForIndexZero() {
        String className = constPool.getClassName(0);
        assertNull(className);
    }

    // ========== MethodRefItem Tests ==========

    @Test
    void findOrAddMethodRefCreatesNewItem() {
        MethodRefItem item = constPool.findOrAddMethodRef("java/lang/Object", "<init>", "()V");
        assertNotNull(item);
    }

    @Test
    void findOrAddMethodRefReturnsExistingItem() {
        MethodRefItem first = constPool.findOrAddMethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
        MethodRefItem second = constPool.findOrAddMethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
        assertSame(first, second);
    }

    // ========== FieldRefItem Tests ==========

    @Test
    void findOrAddFieldRefCreatesNewItem() {
        FieldRefItem item = constPool.findOrAddFieldRef("java/lang/System", "out", "Ljava/io/PrintStream;");
        assertNotNull(item);
    }

    @Test
    void findOrAddFieldRefReturnsExistingItem() {
        FieldRefItem first = constPool.findOrAddFieldRef("java/lang/System", "err", "Ljava/io/PrintStream;");
        FieldRefItem second = constPool.findOrAddFieldRef("java/lang/System", "err", "Ljava/io/PrintStream;");
        assertSame(first, second);
    }

    @Test
    void findOrAddFieldAlias() {
        // findOrAddField is an alias for findOrAddFieldRef
        FieldRefItem item = constPool.findOrAddField("com/test/MyClass", "value", "I");
        assertNotNull(item);
    }

    // ========== InterfaceRefItem Tests ==========

    @Test
    void findOrAddInterfaceRefCreatesNewItem() {
        InterfaceRefItem item = constPool.findOrAddInterfaceRef("java/util/List", "size", "()I");
        assertNotNull(item);
    }

    @Test
    void findOrAddInterfaceRefReturnsExistingItem() {
        InterfaceRefItem first = constPool.findOrAddInterfaceRef("java/util/Collection", "isEmpty", "()Z");
        InterfaceRefItem second = constPool.findOrAddInterfaceRef("java/util/Collection", "isEmpty", "()Z");
        assertSame(first, second);
    }

    // ========== NameAndTypeRefItem Tests ==========

    @Test
    void findOrAddNameAndTypeByIndices() {
        Utf8Item nameUtf8 = constPool.findOrAddUtf8("testMethod");
        Utf8Item descUtf8 = constPool.findOrAddUtf8("()V");
        int nameIndex = constPool.getIndexOf(nameUtf8);
        int descIndex = constPool.getIndexOf(descUtf8);

        NameAndTypeRefItem item = constPool.findOrAddNameAndType(nameIndex, descIndex);
        assertNotNull(item);
    }

    @Test
    void findOrAddNameAndTypeByStrings() {
        NameAndTypeRefItem item = constPool.findOrAddNameAndType("getValue", "()I");
        assertNotNull(item);
    }

    @Test
    void findOrAddNameAndTypeReturnsExistingItem() {
        NameAndTypeRefItem first = constPool.findOrAddNameAndType("setValue", "(I)V");
        NameAndTypeRefItem second = constPool.findOrAddNameAndType("setValue", "(I)V");
        assertSame(first, second);
    }

    // ========== MethodHandleItem Tests ==========

    @Test
    void findOrAddMethodHandleForMethod() {
        // REF_invokeVirtual = 5
        MethodHandleItem item = constPool.findOrAddMethodHandle(5, "java/lang/String", "length", "()I");
        assertNotNull(item);
    }

    @Test
    void findOrAddMethodHandleForField() {
        // REF_getField = 1
        MethodHandleItem item = constPool.findOrAddMethodHandle(1, "com/test/MyClass", "value", "I");
        assertNotNull(item);
    }

    @Test
    void findOrAddMethodHandleForInterface() {
        // REF_invokeInterface = 9
        MethodHandleItem item = constPool.findOrAddMethodHandle(9, "java/util/List", "size", "()I");
        assertNotNull(item);
    }

    // ========== MethodTypeItem Tests ==========

    @Test
    void findOrAddMethodTypeCreatesNewItem() {
        MethodTypeItem item = constPool.findOrAddMethodType("(II)I");
        assertNotNull(item);
    }

    @Test
    void findOrAddMethodTypeReturnsExistingItem() {
        MethodTypeItem first = constPool.findOrAddMethodType("(Ljava/lang/String;)V");
        MethodTypeItem second = constPool.findOrAddMethodType("(Ljava/lang/String;)V");
        assertSame(first, second);
    }

    // ========== Index Operations Tests ==========

    @Test
    void getIndexOfReturnsCorrectIndex() {
        Utf8Item utf8 = constPool.findOrAddUtf8("testIndex");
        int index = constPool.getIndexOf(utf8);
        assertTrue(index > 0);
        assertSame(utf8, constPool.getItem(index));
    }

    @Test
    void getIndexOfThrowsForMissingItem() {
        Utf8Item orphan = new Utf8Item();
        orphan.setValue("orphan");
        assertThrows(IllegalArgumentException.class, () -> constPool.getIndexOf(orphan));
    }

    // ========== Long/Double Slot Tests ==========

    @Test
    void longItemTakesTwoSlots() {
        int sizeBefore = constPool.getItems().size();
        constPool.findOrAddLong(12345L);
        int sizeAfter = constPool.getItems().size();
        // Long takes 2 slots
        assertEquals(2, sizeAfter - sizeBefore);
    }

    @Test
    void doubleItemTakesTwoSlots() {
        int sizeBefore = constPool.getItems().size();
        constPool.findOrAddDouble(3.14);
        int sizeAfter = constPool.getItems().size();
        // Double takes 2 slots
        assertEquals(2, sizeAfter - sizeBefore);
    }

    // ========== toString Tests ==========

    @Test
    void toStringIncludesConstantPool() {
        String str = constPool.toString();
        assertTrue(str.contains("Constant Pool"));
    }

    // ========== Round-Trip Tests ==========

    @Test
    void roundTripPreservesUtf8Items() throws IOException {
        constPool.findOrAddUtf8("preservedString");
        ClassFile reloaded = TestUtils.roundTrip(classFile);

        boolean found = false;
        for (Item<?> item : reloaded.getConstPool().getItems()) {
            if (item instanceof Utf8Item && "preservedString".equals(((Utf8Item) item).getValue())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "UTF8 item should be preserved after round-trip");
    }

    @Test
    void roundTripPreservesIntegerItems() throws IOException {
        constPool.findOrAddInteger(12345);
        ClassFile reloaded = TestUtils.roundTrip(classFile);

        boolean found = false;
        for (Item<?> item : reloaded.getConstPool().getItems()) {
            if (item instanceof IntegerItem && ((IntegerItem) item).getValue() == 12345) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Integer item should be preserved after round-trip");
    }

    @Test
    void roundTripPreservesLongItems() throws IOException {
        constPool.findOrAddLong(9876543210L);
        ClassFile reloaded = TestUtils.roundTrip(classFile);

        boolean found = false;
        for (Item<?> item : reloaded.getConstPool().getItems()) {
            if (item instanceof LongItem && ((LongItem) item).getValue() == 9876543210L) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Long item should be preserved after round-trip");
    }
}

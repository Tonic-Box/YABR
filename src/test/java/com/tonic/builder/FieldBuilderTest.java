package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.attribute.DeprecatedAttribute;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class FieldBuilderTest {

    @Nested
    class BasicFieldCreation {

        @Test
        void addFieldCreatesFieldEntry() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "value", "I")
                .end()
                .build();

            FieldEntry field = findField(cf, "value");
            assertNotNull(field);
            assertEquals("I", field.getDesc());
        }

        @Test
        void addFieldWithObjectType() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "name", "Ljava/lang/String;")
                .end()
                .build();

            FieldEntry field = findField(cf, "name");
            assertNotNull(field);
            assertEquals("Ljava/lang/String;", field.getDesc());
        }

        @Test
        void addFieldWithArrayType() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "data", "[B")
                .end()
                .build();

            FieldEntry field = findField(cf, "data");
            assertNotNull(field);
            assertEquals("[B", field.getDesc());
        }
    }

    @Nested
    class FieldAccessFlags {

        @Test
        void privateField() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "privateField", "I")
                .end()
                .build();

            FieldEntry field = findField(cf, "privateField");
            assertTrue((field.getAccess() & AccessFlags.ACC_PRIVATE) != 0);
        }

        @Test
        void publicStaticField() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "staticField", "I")
                .end()
                .build();

            FieldEntry field = findField(cf, "staticField");
            assertTrue((field.getAccess() & AccessFlags.ACC_PUBLIC) != 0);
            assertTrue((field.getAccess() & AccessFlags.ACC_STATIC) != 0);
        }

        @Test
        void syntheticField() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "syntheticField", "I")
                .synthetic()
                .end()
                .build();

            FieldEntry field = findField(cf, "syntheticField");
            assertTrue((field.getAccess() & AccessFlags.ACC_SYNTHETIC) != 0);
        }
    }

    @Nested
    class MultipleFields {

        @Test
        void addMultipleFields() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "field1", "I")
                .end()
                .addField(AccessFlags.ACC_PRIVATE, "field2", "J")
                .end()
                .addField(AccessFlags.ACC_PRIVATE, "field3", "Ljava/lang/String;")
                .end()
                .build();

            assertNotNull(findField(cf, "field1"));
            assertNotNull(findField(cf, "field2"));
            assertNotNull(findField(cf, "field3"));
        }
    }

    @Nested
    class EndMethod {

        @Test
        void endReturnsClassBuilder() {
            ClassBuilder cb = ClassBuilder.create("com/test/FieldTest");
            FieldBuilder fb = cb.addField(AccessFlags.ACC_PRIVATE, "field", "I");
            ClassBuilder returned = fb.end();

            assertSame(cb, returned);
        }
    }

    @Nested
    class DeprecatedTests {

        @Test
        void deprecatedAddsDeprecatedAttribute() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "oldField", "I")
                .deprecated()
                .end()
                .build();

            FieldEntry field = findField(cf, "oldField");
            assertNotNull(field);
            boolean hasDeprecated = field.getAttributes().stream()
                .anyMatch(attr -> attr instanceof DeprecatedAttribute);
            assertTrue(hasDeprecated);
        }

        @Test
        void deprecatedFieldHasCorrectAccess() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PUBLIC, "deprecatedField", "I")
                .deprecated()
                .end()
                .build();

            FieldEntry field = findField(cf, "deprecatedField");
            assertTrue((field.getAccess() & AccessFlags.ACC_PUBLIC) != 0);
        }
    }

    @Nested
    class ConstantValueTests {

        @Test
        void constantValueWithInteger() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_FINAL, "MAX_VALUE", "I")
                .constantValue(100)
                .end()
                .build();

            FieldEntry field = findField(cf, "MAX_VALUE");
            assertNotNull(field);
        }

        @Test
        void constantValueWithString() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_FINAL, "NAME", "Ljava/lang/String;")
                .constantValue("TestValue")
                .end()
                .build();

            FieldEntry field = findField(cf, "NAME");
            assertNotNull(field);
        }

        @Test
        void constantValueWithLong() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_FINAL, "LONG_VALUE", "J")
                .constantValue(999999999L)
                .end()
                .build();

            FieldEntry field = findField(cf, "LONG_VALUE");
            assertNotNull(field);
        }
    }

    @Nested
    class SyntheticTests {

        @Test
        void syntheticAddsSyntheticFlag() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "syntheticField", "I")
                .synthetic()
                .end()
                .build();

            FieldEntry field = findField(cf, "syntheticField");
            assertTrue((field.getAccess() & AccessFlags.ACC_SYNTHETIC) != 0);
        }

        @Test
        void getAccessReturnsSyntheticFlag() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PUBLIC, "syntheticPublic", "I")
                .synthetic()
                .end()
                .build();

            FieldEntry field = findField(cf, "syntheticPublic");
            int access = field.getAccess();
            assertTrue((access & AccessFlags.ACC_SYNTHETIC) != 0);
            assertTrue((access & AccessFlags.ACC_PUBLIC) != 0);
        }
    }

    @Nested
    class CombinedAttributesTests {

        @Test
        void fieldCanBeBothSyntheticAndDeprecated() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "syntheticDeprecated", "I")
                .synthetic()
                .deprecated()
                .end()
                .build();

            FieldEntry field = findField(cf, "syntheticDeprecated");
            assertTrue((field.getAccess() & AccessFlags.ACC_SYNTHETIC) != 0);
            boolean hasDeprecated = field.getAttributes().stream()
                .anyMatch(attr -> attr instanceof DeprecatedAttribute);
            assertTrue(hasDeprecated);
        }
    }

    private FieldEntry findField(ClassFile cf, String name) {
        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }
}

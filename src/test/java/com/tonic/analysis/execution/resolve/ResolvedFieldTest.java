package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedFieldTest {

    @Test
    void constructor_shouldInitializeAllFields() throws IOException {
        int fieldAccess = new AccessBuilder().setPublic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Test")
            .field(fieldAccess, "counter", "I")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertNotNull(resolved);
        assertEquals(field, resolved.getField());
        assertEquals(classFile, resolved.getDeclaringClass());
    }

    @Test
    void getField_shouldReturnCorrectFieldEntry() throws IOException {
        int fieldAccess = new AccessBuilder().setPrivate().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Bean")
            .field(fieldAccess, "value", "Ljava/lang/String;")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertEquals(field, resolved.getField());
        assertEquals("value", field.getName());
    }

    @Test
    void getDeclaringClass_shouldReturnCorrectClassFile() throws IOException {
        int fieldAccess = new AccessBuilder().setPublic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Data")
            .field(fieldAccess, "items", "[I")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertEquals(classFile, resolved.getDeclaringClass());
        assertEquals("com/example/Data", classFile.getClassName());
    }

    @Test
    void isStatic_shouldReturnTrueForStaticField() throws IOException {
        int staticAccess = new AccessBuilder().setPublic().setStatic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Config")
            .field(staticAccess, "DEFAULT_SIZE", "I")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertTrue(resolved.isStatic());
    }

    @Test
    void isStatic_shouldReturnFalseForInstanceField() throws IOException {
        int instanceAccess = new AccessBuilder().setPrivate().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Point")
            .field(instanceAccess, "x", "D")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertFalse(resolved.isStatic());
    }

    @Test
    void isStatic_shouldCheckCorrectAccessFlagBit() throws IOException {
        int access = 0x0008;
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Test")
            .field(access, "staticField", "J")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertTrue(resolved.isStatic());
        assertEquals(0x0008, field.getAccess() & 0x0008);
    }

    @Test
    void toString_shouldIncludeFieldInfo() throws IOException {
        int fieldAccess = new AccessBuilder().setPublic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Container")
            .field(fieldAccess, "data", "Ljava/util/List;")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        String result = resolved.toString();

        assertTrue(result.contains("ResolvedField{"));
        assertTrue(result.contains("com/example/Container"));
        assertTrue(result.contains("data"));
        assertTrue(result.contains("Ljava/util/List;"));
    }

    @Test
    void shouldHandleMultipleFieldsInClass() throws IOException {
        int publicAccess = new AccessBuilder().setPublic().build();
        int privateAccess = new AccessBuilder().setPrivate().build();
        int staticAccess = new AccessBuilder().setPublic().setStatic().build();

        ClassFile classFile = BytecodeBuilder.forClass("com/example/MultiField")
            .field(privateAccess, "instanceField", "I")
            .field(staticAccess, "staticField", "J")
            .field(publicAccess, "publicField", "Ljava/lang/String;")
            .build();

        List<FieldEntry> fields = classFile.getFields();
        assertEquals(3, fields.size());

        ResolvedField resolved1 = new ResolvedField(fields.get(0), classFile);
        ResolvedField resolved2 = new ResolvedField(fields.get(1), classFile);
        ResolvedField resolved3 = new ResolvedField(fields.get(2), classFile);

        assertFalse(resolved1.isStatic());
        assertTrue(resolved2.isStatic());
        assertFalse(resolved3.isStatic());
    }

    @Test
    void shouldHandlePrimitiveFieldDescriptors() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Primitives")
            .field(access, "boolField", "Z")
            .field(access, "byteField", "B")
            .field(access, "charField", "C")
            .field(access, "shortField", "S")
            .field(access, "intField", "I")
            .field(access, "longField", "J")
            .field(access, "floatField", "F")
            .field(access, "doubleField", "D")
            .build();

        List<FieldEntry> fields = classFile.getFields();
        assertEquals(8, fields.size());

        ResolvedField resolved = new ResolvedField(fields.get(4), classFile);
        assertEquals("intField", resolved.getField().getName());
        assertEquals("I", resolved.getField().getDesc());
    }

    @Test
    void shouldHandleArrayFieldDescriptors() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Arrays")
            .field(access, "intArray", "[I")
            .field(access, "stringArray", "[Ljava/lang/String;")
            .field(access, "matrix", "[[D")
            .build();

        List<FieldEntry> fields = classFile.getFields();

        ResolvedField resolved1 = new ResolvedField(fields.get(0), classFile);
        ResolvedField resolved2 = new ResolvedField(fields.get(1), classFile);
        ResolvedField resolved3 = new ResolvedField(fields.get(2), classFile);

        assertEquals("[I", resolved1.getField().getDesc());
        assertEquals("[Ljava/lang/String;", resolved2.getField().getDesc());
        assertEquals("[[D", resolved3.getField().getDesc());
    }

    @Test
    void shouldHandleComplexObjectFieldDescriptors() throws IOException {
        int access = new AccessBuilder().setPrivate().setStatic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Complex")
            .field(access, "map", "Ljava/util/Map;")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertEquals("Ljava/util/Map;", resolved.getField().getDesc());
        assertTrue(resolved.isStatic());
    }

    @Test
    void accessFlagCombinations_shouldWorkCorrectly() throws IOException {
        int combinedAccess = new AccessBuilder()
            .setPublic()
            .setStatic()
            .setFinal()
            .build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Constants")
            .field(combinedAccess, "PI", "D")
            .build();
        FieldEntry field = classFile.getFields().get(0);

        ResolvedField resolved = new ResolvedField(field, classFile);

        assertTrue(resolved.isStatic());
        assertTrue((field.getAccess() & 0x0010) != 0);
        assertTrue((field.getAccess() & 0x0001) != 0);
    }
}

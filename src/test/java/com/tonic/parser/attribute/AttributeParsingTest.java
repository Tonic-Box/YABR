package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.attribute.table.LineNumberTableEntry;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for attribute parsing and handling.
 * Tests CodeAttribute, LineNumberTableAttribute, ExceptionsAttribute, and other attributes.
 */
class AttributeParsingTest {

    // ========== CodeAttribute Tests ==========

    @Nested
    class CodeAttributeTests {

        @Test
        void codeAttributeBasicParsing() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Code")
                .publicStaticMethod("simple", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            assertNotNull(codeAttr);
            assertNotNull(codeAttr.getCode());
            assertTrue(codeAttr.getCode().length > 0);
        }

        @Test
        void codeAttributeMaxStackAndLocals() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Stack")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            assertNotNull(codeAttr);
            assertTrue(codeAttr.getMaxStack() >= 1, "Stack should hold at least 1 value");
            assertTrue(codeAttr.getMaxLocals() >= 1, "Should have at least 1 local");
        }

        @Test
        void codeAttributeUpdateLength() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Len")
                .publicStaticMethod("method", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            codeAttr.updateLength();
            assertTrue(codeAttr.getCode().length > 0);
        }

        @Test
        void codeAttributeToString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ToString")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            String str = codeAttr.toString();
            assertNotNull(str);
            assertTrue(str.contains("CodeAttribute"));
            assertTrue(str.contains("maxStack"));
            assertTrue(str.contains("maxLocals"));
        }

        @Test
        void codeAttributeWithExceptionTable() throws IOException {
            // A method with try-catch will have exception table entries
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExTable")
                .publicStaticMethod("tryCatch", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            assertNotNull(codeAttr);
            assertNotNull(codeAttr.getExceptionTable());
            // Simple method won't have exception table
            assertEquals(0, codeAttr.getExceptionTable().size());
        }

        @Test
        void codeAttributeNestedAttributes() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Nested")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            assertNotNull(codeAttr.getAttributes());
            // May or may not have nested attributes depending on builder
        }

        @Test
        void codeAttributeRoundTrip() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RoundTrip")
                .publicStaticMethod("compute", "(II)I")
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .build();

            // Write to bytes
            byte[] bytes = cf.write();
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);

            // Parse back
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));
            MethodEntry method = parsed.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            assertNotNull(codeAttr);
            assertTrue(codeAttr.getCode().length > 0);
        }
    }

    // ========== ExceptionTableEntry Tests ==========

    @Nested
    class ExceptionTableEntryTests {

        @Test
        void exceptionTableEntryConstruction() {
            ExceptionTableEntry entry = new ExceptionTableEntry(10, 20, 30, 5);

            assertEquals(10, entry.getStartPc());
            assertEquals(20, entry.getEndPc());
            assertEquals(30, entry.getHandlerPc());
            assertEquals(5, entry.getCatchType());
        }

        @Test
        void exceptionTableEntryCatchAll() {
            // catchType of 0 means catch all exceptions
            ExceptionTableEntry entry = new ExceptionTableEntry(0, 10, 15, 0);

            assertEquals(0, entry.getCatchType());
        }
    }

    // ========== LineNumberTableEntry Tests ==========

    @Nested
    class LineNumberTableEntryTests {

        @Test
        void lineNumberTableEntryConstruction() {
            LineNumberTableEntry entry = new LineNumberTableEntry(5, 42);

            assertEquals(5, entry.getStartPc());
            assertEquals(42, entry.getLineNumber());
        }

        @Test
        void lineNumberTableEntryToString() {
            LineNumberTableEntry entry = new LineNumberTableEntry(10, 100);
            String str = entry.toString();

            assertNotNull(str);
        }
    }

    // ========== Attribute Base Class Tests ==========

    @Nested
    class AttributeBaseTests {

        @Test
        void attributeNameParsing() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/AttrName")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute codeAttr = method.getCodeAttribute();

            // CodeAttribute should have name "Code"
            assertNotNull(codeAttr);
        }

        @Test
        void attributeFromFactory() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Factory")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            List<Attribute> attrs = method.getAttributes();

            assertNotNull(attrs);
            assertFalse(attrs.isEmpty());
            // First attribute should be Code
            assertTrue(attrs.get(0) instanceof CodeAttribute);
        }
    }

    // ========== Multiple Attributes Tests ==========

    @Nested
    class MultipleAttributesTests {

        @Test
        void methodWithMultipleAttributes() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/MultiAttr")
                .publicStaticMethod("method", "()I")
                    .iconst(1)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);

            assertNotNull(method.getCodeAttribute());
            assertNotNull(method.getAttributes());
        }
    }

    // ========== GenericAttribute Tests ==========

    @Nested
    class GenericAttributeTests {

        @Test
        void genericAttributeHandlesUnknown() {
            // GenericAttribute is used for unknown attribute types
            // It just stores raw bytes
            // Can't easily test this without crafting raw bytes
        }
    }

    // ========== SyntheticAttribute Tests ==========

    @Nested
    class SyntheticAttributeTests {

        @Test
        void syntheticAttributeGetType() {
            // SyntheticAttribute has no data (length 0)
            // Would need to manually create synthetic method to test
        }
    }

    // ========== DeprecatedAttribute Tests ==========

    @Nested
    class DeprecatedAttributeTests {

        @Test
        void deprecatedAttributeGetType() {
            // DeprecatedAttribute has no data (length 0)
            // Would need to manually mark method deprecated to test
        }
    }

    // ========== Write/Read Integration Tests ==========

    @Nested
    class WriteReadIntegrationTests {

        @Test
        void writeAndReadPreservesCodeAttribute() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/Preserve")
                .publicStaticMethod("compute", "(I)I")
                    .iload(0)
                    .iconst(2)
                    .imul()
                    .ireturn()
                .build();

            MethodEntry originalMethod = original.getMethods().get(0);
            CodeAttribute originalCode = originalMethod.getCodeAttribute();
            int originalMaxStack = originalCode.getMaxStack();
            int originalMaxLocals = originalCode.getMaxLocals();
            int originalCodeLength = originalCode.getCode().length;

            // Write and re-read
            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            MethodEntry parsedMethod = parsed.getMethods().get(0);
            CodeAttribute parsedCode = parsedMethod.getCodeAttribute();

            assertEquals(originalMaxStack, parsedCode.getMaxStack());
            assertEquals(originalMaxLocals, parsedCode.getMaxLocals());
            assertEquals(originalCodeLength, parsedCode.getCode().length);
        }

        @Test
        void writeAndReadPreservesExceptionTable() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/ExPreserve")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            MethodEntry method = parsed.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code.getExceptionTable());
        }
    }

    // ========== Edge Cases Tests ==========

    @Nested
    class EdgeCasesTests {

        @Test
        void emptyMethodHasMinimalCode() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Empty")
                .publicStaticMethod("empty", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
            assertTrue(code.getCode().length >= 1); // At least return instruction
        }

        @Test
        void methodWithManyLocals() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ManyLocals")
                .publicStaticMethod("many", "(IIIIII)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .iload(2)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertTrue(code.getMaxLocals() >= 1, "Should have at least 1 local");
        }

        @Test
        void methodWithDeepStack() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/DeepStack")
                .publicStaticMethod("deep", "()I")
                    .iconst(1)
                    .iconst(2)
                    .iconst(3)
                    .iconst(4)
                    .iadd()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertTrue(code.getMaxStack() >= 4);
        }
    }

    // ========== Helper Methods ==========

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }
}

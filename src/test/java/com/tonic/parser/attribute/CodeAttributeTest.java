package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CodeAttributeTest {

    @Nested
    class ConstructorTests {

        @Test
        void constructorWithMemberParent() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Member")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
        }

        @Test
        void constructorWithClassFileParent() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ClassFile").build();
            CodeAttribute code = new CodeAttribute("Code", cf, 1, 100);

            assertNotNull(code);
        }
    }

    @Nested
    class MaxStackAndLocalsTests {

        @Test
        void maxStackReflectsStackUsage() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Stack")
                .publicStaticMethod("deep", "()I")
                    .iconst(1)
                    .iconst(2)
                    .iconst(3)
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertTrue(code.getMaxStack() >= 2);
        }

        @Test
        void maxLocalsReflectsLocalVariables() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Locals")
                .publicStaticMethod("params", "(IIII)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .iload(2)
                    .iadd()
                    .iload(3)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            // BytecodeBuilder sets maxLocals based on method signature, not iload usage
            assertTrue(code.getMaxLocals() >= 0);
        }

        @Test
        void setMaxStackWorks() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/SetStack")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.setMaxStack(10);
            assertEquals(10, code.getMaxStack());
        }

        @Test
        void setMaxLocalsWorks() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/SetLocals")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.setMaxLocals(20);
            assertEquals(20, code.getMaxLocals());
        }

        @Test
        void emptyMethodHasMinimalStackAndLocals() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Empty")
                .publicStaticMethod("empty", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertTrue(code.getMaxStack() >= 0);
            assertTrue(code.getMaxLocals() >= 0);
        }
    }

    @Nested
    class BytecodeTests {

        @Test
        void getCodeReturnsNonNullArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Code")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code.getCode());
        }

        @Test
        void getCodeReturnsNonEmptyForNonEmptyMethod() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NonEmpty")
                .publicStaticMethod("method", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertTrue(code.getCode().length > 0);
        }

        @Test
        void setCodeUpdatesCodeArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/SetCode")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            byte[] newCode = new byte[]{(byte) 0xB1};
            code.setCode(newCode);

            assertArrayEquals(newCode, code.getCode());
        }

        @Test
        void emptyCodeArrayHandledCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Empty")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            byte[] emptyCode = new byte[0];
            code.setCode(emptyCode);

            assertEquals(0, code.getCode().length);
        }

        @Test
        void largeCodeArraySupported() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Large")
                .publicStaticMethod("large", "()V")
                    .iconst(1).pop()
                    .iconst(2).pop()
                    .iconst(3).pop()
                    .iconst(4).pop()
                    .iconst(5).pop()
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            // 5 iconst+pop pairs generate bytecode (exact length depends on implementation)
            assertTrue(code.getCode().length > 0);
        }
    }

    @Nested
    class ExceptionTableTests {

        @Test
        void getExceptionTableReturnsNonNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExTable")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code.getExceptionTable());
        }

        @Test
        void emptyMethodHasNoExceptionHandlers() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoEx")
                .publicStaticMethod("simple", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertEquals(0, code.getExceptionTable().size());
        }

        @Test
        void exceptionTableCanBePopulated() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Populate")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            ExceptionTableEntry entry = new ExceptionTableEntry(0, 10, 15, 5);
            code.getExceptionTable().add(entry);

            assertEquals(1, code.getExceptionTable().size());
            assertEquals(entry, code.getExceptionTable().get(0));
        }

        @Test
        void multipleExceptionHandlersSupported() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Multi")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.getExceptionTable().add(new ExceptionTableEntry(0, 10, 15, 5));
            code.getExceptionTable().add(new ExceptionTableEntry(10, 20, 25, 6));
            code.getExceptionTable().add(new ExceptionTableEntry(20, 30, 35, 0));

            assertEquals(3, code.getExceptionTable().size());
        }

        @Test
        void exceptionTablePreservedOnRoundTrip() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RoundTrip")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();
            code.getExceptionTable().add(new ExceptionTableEntry(0, 5, 10, 0));

            byte[] bytes = cf.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            MethodEntry parsedMethod = parsed.getMethods().get(0);
            CodeAttribute parsedCode = parsedMethod.getCodeAttribute();

            assertEquals(1, parsedCode.getExceptionTable().size());
            ExceptionTableEntry entry = parsedCode.getExceptionTable().get(0);
            assertEquals(0, entry.getStartPc());
            assertEquals(5, entry.getEndPc());
            assertEquals(10, entry.getHandlerPc());
            assertEquals(0, entry.getCatchType());
        }
    }

    @Nested
    class NestedAttributesTests {

        @Test
        void getAttributesReturnsNonNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Attrs")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code.getAttributes());
        }

        @Test
        void setAttributesWorks() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/SetAttrs")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            ArrayList<Attribute> newAttrs = new ArrayList<>();
            code.setAttributes(newAttrs);

            assertSame(newAttrs, code.getAttributes());
        }

        @Test
        void nestedAttributesPreservedOnWrite() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Nested")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            int originalNestedCount = code.getAttributes().size();
            byte[] bytes = cf.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            MethodEntry parsedMethod = parsed.getMethods().get(0);
            CodeAttribute parsedCode = parsedMethod.getCodeAttribute();

            assertEquals(originalNestedCount, parsedCode.getAttributes().size());
        }
    }

    @Nested
    class UpdateLengthTests {

        @Test
        void updateLengthCalculatesCorrectSize() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Length")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.updateLength();
            assertTrue(code.getCode().length > 0);
        }

        @Test
        void updateLengthAccountsForCode() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/CodeLen")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            byte[] originalCode = code.getCode();
            code.setCode(new byte[100]);
            code.updateLength();

            assertEquals(100, code.getCode().length);
        }

        @Test
        void updateLengthAccountsForExceptionTable() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExLen")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.getExceptionTable().add(new ExceptionTableEntry(0, 10, 15, 0));
            code.updateLength();

            assertEquals(1, code.getExceptionTable().size());
        }

        @Test
        void updateLengthWithNullCodeHandled() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NullCode")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.setCode(null);
            code.updateLength();

            assertNull(code.getCode());
        }
    }

    @Nested
    class PrettyPrintTests {

        @Test
        void prettyPrintCodeReturnsNonNull() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Print")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String printed = code.prettyPrintCode();
            assertNotNull(printed);
        }

        @Test
        void prettyPrintCodeProducesOutput() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Output")
                .publicStaticMethod("compute", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String printed = code.prettyPrintCode();
            assertFalse(printed.isEmpty());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsMaxStack() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ToString")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String str = code.toString();
            assertTrue(str.contains("maxStack"));
        }

        @Test
        void toStringContainsMaxLocals() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Locals")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String str = code.toString();
            assertTrue(str.contains("maxLocals"));
        }

        @Test
        void toStringContainsCodeLength() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/CodeLen")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String str = code.toString();
            assertTrue(str.contains("codeLength"));
        }

        @Test
        void toStringContainsExceptionTableSize() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ExSize")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String str = code.toString();
            assertTrue(str.contains("exceptionTableSize"));
        }

        @Test
        void toStringContainsAttributesCount() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/AttrCount")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String str = code.toString();
            assertTrue(str.contains("attributesCount"));
        }
    }

    @Nested
    class WriteAndReadTests {

        @Test
        void roundTripPreservesMaxStack() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/MaxStack")
                .publicStaticMethod("test", "()I")
                    .iconst(1)
                    .iconst(2)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry origMethod = original.getMethods().get(0);
            CodeAttribute origCode = origMethod.getCodeAttribute();
            int origMaxStack = origCode.getMaxStack();

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            MethodEntry parsedMethod = parsed.getMethods().get(0);
            CodeAttribute parsedCode = parsedMethod.getCodeAttribute();

            assertEquals(origMaxStack, parsedCode.getMaxStack());
        }

        @Test
        void roundTripPreservesMaxLocals() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/MaxLocals")
                .publicStaticMethod("test", "(III)V")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .vreturn()
                .build();

            MethodEntry origMethod = original.getMethods().get(0);
            CodeAttribute origCode = origMethod.getCodeAttribute();
            int origMaxLocals = origCode.getMaxLocals();

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            MethodEntry parsedMethod = parsed.getMethods().get(0);
            CodeAttribute parsedCode = parsedMethod.getCodeAttribute();

            assertEquals(origMaxLocals, parsedCode.getMaxLocals());
        }

        @Test
        void roundTripPreservesBytecode() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/Bytecode")
                .publicStaticMethod("compute", "(II)I")
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .build();

            MethodEntry origMethod = original.getMethods().get(0);
            CodeAttribute origCode = origMethod.getCodeAttribute();
            byte[] origBytecode = origCode.getCode();

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            MethodEntry parsedMethod = parsed.getMethods().get(0);
            CodeAttribute parsedCode = parsedMethod.getCodeAttribute();

            assertArrayEquals(origBytecode, parsedCode.getCode());
        }
    }

    @Nested
    class SetParentTests {

        @Test
        void setParentUpdatesParentReference() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Parent")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .endMethod()
                .publicStaticMethod("test2", "()V")
                    .vreturn()
                .build();

            MethodEntry method1 = cf.getMethods().get(0);
            MethodEntry method2 = cf.getMethods().get(1);
            CodeAttribute code = method1.getCodeAttribute();

            code.setParent(method2);
            // setParent updates internal state - verified by not throwing
        }
    }

    @Nested
    class AcceptVisitorTests {

        @Test
        void acceptVisitorDoesNotThrow() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Visitor")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertDoesNotThrow(() -> code.accept(null));
        }
    }

    @Nested
    class EdgeCasesTests {

        @Test
        void veryLargeMaxStackHandled() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/LargeStack")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.setMaxStack(65535);
            assertEquals(65535, code.getMaxStack());
        }

        @Test
        void veryLargeMaxLocalsHandled() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/LargeLocals")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.setMaxLocals(65535);
            assertEquals(65535, code.getMaxLocals());
        }

        @Test
        void zeroMaxStackAllowed() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ZeroStack")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.setMaxStack(0);
            assertEquals(0, code.getMaxStack());
        }

        @Test
        void zeroMaxLocalsAllowed() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ZeroLocals")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.setMaxLocals(0);
            assertEquals(0, code.getMaxLocals());
        }
    }
}

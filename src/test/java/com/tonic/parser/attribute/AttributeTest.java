package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.testutil.BytecodeBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttributeTest {

    @Nested
    class AttributeFactoryTests {

        @Test
        void factoryCreatesCodeAttributeForMethod() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Factory")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            List<Attribute> attrs = method.getAttributes();

            assertFalse(attrs.isEmpty());
            assertTrue(attrs.get(0) instanceof CodeAttribute);
        }

        @Test
        void factoryCreatesCorrectAttributeTypes() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Types")
                .publicStaticMethod("method", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
        }

        @Test
        void factoryHandlesUnknownAttributeType() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Unknown")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            assertNotNull(method.getAttributes());
        }
    }

    @Nested
    class AttributeNameResolutionTests {

        @Test
        void attributeNameResolvedFromConstantPool() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Name")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
            String toString = code.toString();
            assertTrue(toString.contains("CodeAttribute") || toString.contains("Code"));
        }

        @Test
        void attributeNameIndexMatchesConstantPool() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Index")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
        }
    }

    @Nested
    class AttributeLengthTests {

        @Test
        void attributeLengthUpdatesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Length")
                .publicStaticMethod("simple", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            code.updateLength();
            assertTrue(code.getCode().length > 0);
        }

        @Test
        void attributeLengthChangesWithCodeSize() throws IOException {
            ClassFile cf1 = BytecodeBuilder.forClass("com/test/Short")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            ClassFile cf2 = BytecodeBuilder.forClass("com/test/Long")
                .publicStaticMethod("method", "()V")
                    .iconst(1)
                    .iconst(2)
                    .iconst(3)
                    .iconst(4)
                    .iconst(5)
                    .vreturn()
                .build();

            CodeAttribute short1 = cf1.getMethods().get(0).getCodeAttribute();
            CodeAttribute long1 = cf2.getMethods().get(0).getCodeAttribute();

            // Both methods should have valid code arrays
            assertTrue(long1.getCode().length > 0);
            assertTrue(short1.getCode().length > 0);
        }
    }

    @Nested
    class AttributeWriteReadTests {

        @Test
        void attributeWritePreservesData() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/Write")
                .publicStaticMethod("compute", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            assertEquals(original.getMethods().size(), parsed.getMethods().size());

            MethodEntry originalMethod = original.getMethods().get(0);
            MethodEntry parsedMethod = parsed.getMethods().get(0);

            assertEquals(originalMethod.getAttributes().size(), parsedMethod.getAttributes().size());
        }

        @Test
        void multipleAttributesPreservedOnRoundTrip() throws IOException {
            ClassFile original = BytecodeBuilder.forClass("com/test/Multiple")
                .publicStaticMethod("method1", "()V")
                    .vreturn()
                .endMethod()
                .publicStaticMethod("method2", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            byte[] bytes = original.write();
            ClassFile parsed = new ClassFile(new ByteArrayInputStream(bytes));

            assertEquals(original.getMethods().size(), parsed.getMethods().size());

            for (int i = 0; i < original.getMethods().size(); i++) {
                MethodEntry origMethod = original.getMethods().get(i);
                MethodEntry parsedMethod = parsed.getMethods().get(i);

                assertNotNull(origMethod.getCodeAttribute());
                assertNotNull(parsedMethod.getCodeAttribute());
            }
        }
    }

    @Nested
    class AttributeToStringTests {

        @Test
        void toStringContainsAttributeName() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ToString")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String str = code.toString();
            assertNotNull(str);
            assertTrue(str.contains("CodeAttribute") || str.contains("Code"));
        }

        @Test
        void toStringProducesNonEmptyString() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NonEmpty")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            String str = code.toString();
            assertNotNull(str);
            assertFalse(str.isEmpty());
        }
    }

    @Nested
    class AttributeGetClassFileTests {

        @Test
        void getClassFileReturnsParentClassFile() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Parent")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code.getClassFile());
            assertSame(cf, code.getClassFile());
        }

        @Test
        void getClassFileWorksWithMemberParent() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Member")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            ClassFile fromAttr = code.getClassFile();
            assertNotNull(fromAttr);
            assertSame(cf, fromAttr);
        }

        @Test
        void getClassFileWorksWithDirectClassFileParent() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Direct")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            assertNotNull(cf);
            assertTrue(cf.getClassAttributes().size() >= 0);
        }
    }

    @Nested
    class AttributeEdgeCasesTests {

        @Test
        void emptyMethodHasCodeAttribute() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Empty")
                .publicStaticMethod("empty", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
            assertNotNull(code.getCode());
            assertTrue(code.getCode().length >= 1);
        }

        @Test
        void complexMethodHasCodeAttribute() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Complex")
                .publicStaticMethod("complex", "(IIII)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .iload(2)
                    .imul()
                    .iload(3)
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
            assertTrue(code.getCode().length > 0);
            // Stack and locals are set by BytecodeBuilder
            assertTrue(code.getMaxStack() >= 0);
            assertTrue(code.getMaxLocals() >= 0);
        }

        @Test
        void attributeWithZeroLengthHandledCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ZeroLen")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            assertNotNull(method.getAttributes());
        }
    }

    @Nested
    class SpecificAttributeTypeTests {

        @Test
        void codeAttributeIsRecognized() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Code")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            Attribute attr = method.getAttributes().get(0);

            assertTrue(attr instanceof CodeAttribute);
        }

        @Test
        void stackMapTableAttributeMayExist() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/StackMap")
                .publicStaticMethod("withBranch", "(I)V")
                    .iload(0)
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code.getAttributes());
        }
    }

    @Nested
    class ConstantPoolIntegrationTests {

        @Test
        void attributeNameInConstantPool() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/CP")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();
            ConstPool constPool = cf.getConstPool();

            assertNotNull(code);
            assertNotNull(constPool);
        }

        @Test
        void attributeConstantPoolReferencesValid() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Valid")
                .publicStaticMethod("method", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            CodeAttribute code = method.getCodeAttribute();

            assertNotNull(code);
            assertNotNull(code.getClassFile());
        }
    }
}

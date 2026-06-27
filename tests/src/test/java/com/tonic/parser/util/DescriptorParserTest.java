package com.tonic.parser.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DescriptorParserTest {

    @Nested
    class GetArgumentTypesTests {

        @Test
        void noArguments() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("()V");
            assertTrue(args.isEmpty());
        }

        @Test
        void singlePrimitiveArgument() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(I)V");
            assertEquals(1, args.size());
            assertEquals(TypeInfo.INT, args.get(0));
        }

        @Test
        void multiplePrimitiveArguments() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(IJF)V");
            assertEquals(3, args.size());
            assertEquals(TypeInfo.INT, args.get(0));
            assertEquals(TypeInfo.LONG, args.get(1));
            assertEquals(TypeInfo.FLOAT, args.get(2));
        }

        @Test
        void allPrimitiveTypes() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(ZBCSIJFD)V");
            assertEquals(8, args.size());
            assertEquals(TypeInfo.BOOLEAN, args.get(0));
            assertEquals(TypeInfo.BYTE, args.get(1));
            assertEquals(TypeInfo.CHAR, args.get(2));
            assertEquals(TypeInfo.SHORT, args.get(3));
            assertEquals(TypeInfo.INT, args.get(4));
            assertEquals(TypeInfo.LONG, args.get(5));
            assertEquals(TypeInfo.FLOAT, args.get(6));
            assertEquals(TypeInfo.DOUBLE, args.get(7));
        }

        @Test
        void singleObjectArgument() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(Ljava/lang/String;)V");
            assertEquals(1, args.size());
            assertEquals("Ljava/lang/String;", args.get(0).getDescriptor());
        }

        @Test
        void multipleObjectArguments() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(Ljava/lang/String;Ljava/lang/Object;)V");
            assertEquals(2, args.size());
            assertEquals("Ljava/lang/String;", args.get(0).getDescriptor());
            assertEquals("Ljava/lang/Object;", args.get(1).getDescriptor());
        }

        @Test
        void mixedPrimitiveAndObjectArguments() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(ILjava/lang/String;J)V");
            assertEquals(3, args.size());
            assertEquals(TypeInfo.INT, args.get(0));
            assertEquals("Ljava/lang/String;", args.get(1).getDescriptor());
            assertEquals(TypeInfo.LONG, args.get(2));
        }

        @Test
        void singleDimensionArrayArgument() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("([I)V");
            assertEquals(1, args.size());
            assertEquals("[I", args.get(0).getDescriptor());
            assertTrue(args.get(0).isArray());
        }

        @Test
        void multiDimensionArrayArgument() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("([[I)V");
            assertEquals(1, args.size());
            assertEquals("[[I", args.get(0).getDescriptor());
        }

        @Test
        void objectArrayArgument() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("([Ljava/lang/String;)V");
            assertEquals(1, args.size());
            assertEquals("[Ljava/lang/String;", args.get(0).getDescriptor());
        }

        @Test
        void multiDimensionObjectArray() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("([[Ljava/lang/Object;)V");
            assertEquals(1, args.size());
            assertEquals("[[Ljava/lang/Object;", args.get(0).getDescriptor());
        }

        @Test
        void complexMixedArguments() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(I[ILjava/lang/String;[[Ljava/lang/Object;JD)V");
            assertEquals(6, args.size());
            assertEquals(TypeInfo.INT, args.get(0));
            assertEquals("[I", args.get(1).getDescriptor());
            assertEquals("Ljava/lang/String;", args.get(2).getDescriptor());
            assertEquals("[[Ljava/lang/Object;", args.get(3).getDescriptor());
            assertEquals(TypeInfo.LONG, args.get(4));
            assertEquals(TypeInfo.DOUBLE, args.get(5));
        }

        @Test
        void nullDescriptorThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.getArgumentTypes(null));
        }

        @Test
        void missingOpenParenThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.getArgumentTypes("I)V"));
        }

        @Test
        void missingCloseParenThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.getArgumentTypes("(IV"));
        }

        @Test
        void invalidArgumentTypeThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.getArgumentTypes("(X)V"));
        }
    }

    @Nested
    class GetReturnTypeTests {

        @Test
        void voidReturnType() {
            TypeInfo returnType = DescriptorParser.getReturnType("()V");
            assertEquals(TypeInfo.VOID, returnType);
        }

        @Test
        void primitiveReturnType() {
            TypeInfo returnType = DescriptorParser.getReturnType("()I");
            assertEquals(TypeInfo.INT, returnType);
        }

        @Test
        void longReturnType() {
            TypeInfo returnType = DescriptorParser.getReturnType("()J");
            assertEquals(TypeInfo.LONG, returnType);
        }

        @Test
        void objectReturnType() {
            TypeInfo returnType = DescriptorParser.getReturnType("()Ljava/lang/String;");
            assertEquals("Ljava/lang/String;", returnType.getDescriptor());
        }

        @Test
        void arrayReturnType() {
            TypeInfo returnType = DescriptorParser.getReturnType("()[I");
            assertEquals("[I", returnType.getDescriptor());
        }

        @Test
        void objectArrayReturnType() {
            TypeInfo returnType = DescriptorParser.getReturnType("()[Ljava/lang/Object;");
            assertEquals("[Ljava/lang/Object;", returnType.getDescriptor());
        }

        @Test
        void returnTypeWithArguments() {
            TypeInfo returnType = DescriptorParser.getReturnType("(ILjava/lang/String;)Z");
            assertEquals(TypeInfo.BOOLEAN, returnType);
        }

        @Test
        void nullDescriptorThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.getReturnType(null));
        }

        @Test
        void missingCloseParenThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.getReturnType("(I"));
        }

        @Test
        void missingReturnTypeThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.getReturnType("()"));
        }
    }

    @Nested
    class GetArgumentsSizeTests {

        @Test
        void noArgumentsSize() {
            assertEquals(0, DescriptorParser.getArgumentsSize("()V"));
        }

        @Test
        void singleSlotArguments() {
            assertEquals(1, DescriptorParser.getArgumentsSize("(I)V"));
            assertEquals(2, DescriptorParser.getArgumentsSize("(II)V"));
            assertEquals(3, DescriptorParser.getArgumentsSize("(III)V"));
        }

        @Test
        void doubleSlotArguments() {
            assertEquals(2, DescriptorParser.getArgumentsSize("(J)V"));
            assertEquals(2, DescriptorParser.getArgumentsSize("(D)V"));
            assertEquals(4, DescriptorParser.getArgumentsSize("(JD)V"));
        }

        @Test
        void mixedSizeArguments() {
            assertEquals(3, DescriptorParser.getArgumentsSize("(IJ)V"));
            assertEquals(4, DescriptorParser.getArgumentsSize("(IJI)V"));
            assertEquals(6, DescriptorParser.getArgumentsSize("(IDJZ)V"));
        }

        @Test
        void objectArgumentsSize() {
            assertEquals(1, DescriptorParser.getArgumentsSize("(Ljava/lang/String;)V"));
            assertEquals(2, DescriptorParser.getArgumentsSize("(Ljava/lang/String;Ljava/lang/Object;)V"));
        }

        @Test
        void arrayArgumentsSize() {
            assertEquals(1, DescriptorParser.getArgumentsSize("([I)V"));
            assertEquals(1, DescriptorParser.getArgumentsSize("([[Ljava/lang/Object;)V"));
        }
    }

    @Nested
    class GetArgumentsAndReturnSizeTests {

        @Test
        void noArgsVoidReturn() {
            assertEquals(0, DescriptorParser.getArgumentsAndReturnSize("()V"));
        }

        @Test
        void noArgsPrimitiveReturn() {
            assertEquals(1, DescriptorParser.getArgumentsAndReturnSize("()I"));
            assertEquals(2, DescriptorParser.getArgumentsAndReturnSize("()J"));
        }

        @Test
        void argsAndVoidReturn() {
            assertEquals(2, DescriptorParser.getArgumentsAndReturnSize("(II)V"));
        }

        @Test
        void argsAndPrimitiveReturn() {
            assertEquals(3, DescriptorParser.getArgumentsAndReturnSize("(II)I"));
            assertEquals(4, DescriptorParser.getArgumentsAndReturnSize("(II)J"));
        }

        @Test
        void complexDescriptor() {
            assertEquals(6, DescriptorParser.getArgumentsAndReturnSize("(IJLjava/lang/String;)D"));
        }
    }

    @Nested
    class GetSizeTests {

        @Test
        void getSizePrimitives() {
            assertEquals(1, DescriptorParser.getSize("I"));
            assertEquals(1, DescriptorParser.getSize("Z"));
            assertEquals(2, DescriptorParser.getSize("J"));
            assertEquals(2, DescriptorParser.getSize("D"));
            assertEquals(0, DescriptorParser.getSize("V"));
        }

        @Test
        void getSizeObject() {
            assertEquals(1, DescriptorParser.getSize("Ljava/lang/String;"));
        }

        @Test
        void getSizeArray() {
            assertEquals(1, DescriptorParser.getSize("[I"));
            assertEquals(1, DescriptorParser.getSize("[[Ljava/lang/Object;"));
        }
    }

    @Nested
    class ParseTypeAtTests {

        @Test
        void parseTypeAtPrimitives() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("I", 0);
            assertEquals(TypeInfo.INT, result.type);
            assertEquals(1, result.length);
        }

        @Test
        void parseTypeAtVoid() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("V", 0);
            assertEquals(TypeInfo.VOID, result.type);
            assertEquals(1, result.length);
        }

        @Test
        void parseTypeAtLong() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("J", 0);
            assertEquals(TypeInfo.LONG, result.type);
            assertEquals(1, result.length);
        }

        @Test
        void parseTypeAtObject() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("Ljava/lang/String;", 0);
            assertEquals("Ljava/lang/String;", result.type.getDescriptor());
            assertEquals(18, result.length);
        }

        @Test
        void parseTypeAtObjectWithOffset() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("ILjava/lang/String;", 1);
            assertEquals("Ljava/lang/String;", result.type.getDescriptor());
            assertEquals(18, result.length);
        }

        @Test
        void parseTypeAtSingleDimensionArray() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("[I", 0);
            assertEquals("[I", result.type.getDescriptor());
            assertEquals(2, result.length);
        }

        @Test
        void parseTypeAtMultiDimensionArray() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("[[I", 0);
            assertEquals("[[I", result.type.getDescriptor());
            assertEquals(3, result.length);
        }

        @Test
        void parseTypeAtObjectArray() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("[Ljava/lang/Object;", 0);
            assertEquals("[Ljava/lang/Object;", result.type.getDescriptor());
            assertEquals(19, result.length);
        }

        @Test
        void parseTypeAtMultiDimensionObjectArray() {
            DescriptorParser.ParseResult result = DescriptorParser.parseTypeAt("[[Ljava/lang/Object;", 0);
            assertEquals("[[Ljava/lang/Object;", result.type.getDescriptor());
            assertEquals(20, result.length);
        }

        @Test
        void parseTypeAtOffsetOutOfBoundsThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.parseTypeAt("I", 5));
        }

        @Test
        void parseTypeAtInvalidCharacterThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.parseTypeAt("X", 0));
        }

        @Test
        void parseTypeAtMissingSemicolonThrows() {
            assertThrows(IllegalArgumentException.class, () -> DescriptorParser.parseTypeAt("Ljava/lang/String", 0));
        }
    }

    @Nested
    class GetMethodDescriptorTests {

        @Test
        void getMethodDescriptorNoArgs() {
            String descriptor = DescriptorParser.getMethodDescriptor(TypeInfo.VOID);
            assertEquals("()V", descriptor);
        }

        @Test
        void getMethodDescriptorSingleArg() {
            String descriptor = DescriptorParser.getMethodDescriptor(TypeInfo.VOID, TypeInfo.INT);
            assertEquals("(I)V", descriptor);
        }

        @Test
        void getMethodDescriptorMultipleArgs() {
            String descriptor = DescriptorParser.getMethodDescriptor(TypeInfo.INT, TypeInfo.INT, TypeInfo.LONG);
            assertEquals("(IJ)I", descriptor);
        }

        @Test
        void getMethodDescriptorWithObjectArgs() {
            TypeInfo stringType = TypeInfo.forClassName("java/lang/String");
            String descriptor = DescriptorParser.getMethodDescriptor(TypeInfo.VOID, TypeInfo.INT, stringType);
            assertEquals("(ILjava/lang/String;)V", descriptor);
        }

        @Test
        void getMethodDescriptorWithArrayArgs() {
            TypeInfo arrayType = TypeInfo.of("[I");
            String descriptor = DescriptorParser.getMethodDescriptor(TypeInfo.VOID, arrayType);
            assertEquals("([I)V", descriptor);
        }

        @Test
        void getMethodDescriptorFromList() {
            List<TypeInfo> params = Arrays.asList(TypeInfo.INT, TypeInfo.LONG, TypeInfo.FLOAT);
            String descriptor = DescriptorParser.getMethodDescriptor(TypeInfo.DOUBLE, params);
            assertEquals("(IJF)D", descriptor);
        }

        @Test
        void getMethodDescriptorFromEmptyList() {
            List<TypeInfo> params = Arrays.asList();
            String descriptor = DescriptorParser.getMethodDescriptor(TypeInfo.VOID, params);
            assertEquals("()V", descriptor);
        }
    }

    @Nested
    class IsMethodDescriptorTests {

        @Test
        void isMethodDescriptorValid() {
            assertTrue(DescriptorParser.isMethodDescriptor("()V"));
            assertTrue(DescriptorParser.isMethodDescriptor("(I)V"));
            assertTrue(DescriptorParser.isMethodDescriptor("(IJ)Ljava/lang/String;"));
            assertTrue(DescriptorParser.isMethodDescriptor("([I[[Ljava/lang/Object;)V"));
        }

        @Test
        void isMethodDescriptorInvalid() {
            assertFalse(DescriptorParser.isMethodDescriptor("I"));
            assertFalse(DescriptorParser.isMethodDescriptor("Ljava/lang/String;"));
            assertFalse(DescriptorParser.isMethodDescriptor("[I"));
            assertFalse(DescriptorParser.isMethodDescriptor(""));
            assertFalse(DescriptorParser.isMethodDescriptor(null));
        }

        @Test
        void isMethodDescriptorMalformed() {
            assertFalse(DescriptorParser.isMethodDescriptor("IV"));
            assertFalse(DescriptorParser.isMethodDescriptor("(I"));
        }
    }

    @Nested
    class IsFieldDescriptorTests {

        @Test
        void isFieldDescriptorPrimitives() {
            assertTrue(DescriptorParser.isFieldDescriptor("I"));
            assertTrue(DescriptorParser.isFieldDescriptor("Z"));
            assertTrue(DescriptorParser.isFieldDescriptor("B"));
            assertTrue(DescriptorParser.isFieldDescriptor("C"));
            assertTrue(DescriptorParser.isFieldDescriptor("S"));
            assertTrue(DescriptorParser.isFieldDescriptor("J"));
            assertTrue(DescriptorParser.isFieldDescriptor("F"));
            assertTrue(DescriptorParser.isFieldDescriptor("D"));
        }

        @Test
        void isFieldDescriptorObject() {
            assertTrue(DescriptorParser.isFieldDescriptor("Ljava/lang/String;"));
            assertTrue(DescriptorParser.isFieldDescriptor("Lcom/example/MyClass;"));
        }

        @Test
        void isFieldDescriptorArray() {
            assertTrue(DescriptorParser.isFieldDescriptor("[I"));
            assertTrue(DescriptorParser.isFieldDescriptor("[[Ljava/lang/Object;"));
        }

        @Test
        void isFieldDescriptorInvalid() {
            assertFalse(DescriptorParser.isFieldDescriptor("()V"));
            assertFalse(DescriptorParser.isFieldDescriptor("(I)V"));
            assertFalse(DescriptorParser.isFieldDescriptor(""));
            assertFalse(DescriptorParser.isFieldDescriptor(null));
            assertFalse(DescriptorParser.isFieldDescriptor("X"));
        }

        @Test
        void isFieldDescriptorVoid() {
            assertFalse(DescriptorParser.isFieldDescriptor("V"));
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void nestedClassDescriptor() {
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(Lcom/example/Outer$Inner;)V");
            assertEquals(1, args.size());
            assertEquals("Lcom/example/Outer$Inner;", args.get(0).getDescriptor());
        }

        @Test
        void veryLongClassName() {
            String longClassName = "L" + "a/".repeat(100) + "ClassName;";
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(" + longClassName + ")V");
            assertEquals(1, args.size());
            assertEquals(longClassName, args.get(0).getDescriptor());
        }

        @Test
        void manyDimensionsArray() {
            String arrayDesc = "[".repeat(10) + "I";
            List<TypeInfo> args = DescriptorParser.getArgumentTypes("(" + arrayDesc + ")V");
            assertEquals(1, args.size());
            assertEquals(arrayDesc, args.get(0).getDescriptor());
            assertEquals(10, args.get(0).getArrayDimensions());
        }

        @Test
        void manyParameters() {
            StringBuilder desc = new StringBuilder("(");
            for (int i = 0; i < 50; i++) {
                desc.append("I");
            }
            desc.append(")V");
            List<TypeInfo> args = DescriptorParser.getArgumentTypes(desc.toString());
            assertEquals(50, args.size());
        }
    }
}

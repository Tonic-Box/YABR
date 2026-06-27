package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.dispatch.InvokeDynamicInfo;
import com.tonic.analysis.execution.state.ConcreteValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class StringConcatHandlerTest {

    private StringConcatHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StringConcatHandler();
    }

    @Nested
    class TagConstantsTests {
        @Test
        void shouldHaveCorrectTagArgValue() {
            assertEquals('\u0001', StringConcatHandler.TAG_ARG);
        }

        @Test
        void shouldHaveCorrectTagConstValue() {
            assertEquals('\u0002', StringConcatHandler.TAG_CONST);
        }
    }

    @Nested
    class IsStringConcatTests {
        @Test
        void shouldIdentifyMakeConcatWithConstants() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(I)Ljava/lang/String;", 1);
            assertTrue(handler.isStringConcat(info));
        }

        @Test
        void shouldIdentifyMakeConcat() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(II)Ljava/lang/String;", 1);
            assertTrue(handler.isStringConcat(info));
        }

        @Test
        void shouldNotIdentifyMetafactory() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "metafactory", "()Ljava/util/function/Function;", 1);
            assertFalse(handler.isStringConcat(info));
        }

        @Test
        void shouldHandleNull() {
            assertFalse(handler.isStringConcat(null));
        }
    }

    @Nested
    class HasRecipeTests {
        @Test
        void shouldReturnTrueForMakeConcatWithConstants() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(I)Ljava/lang/String;", 1);
            assertTrue(handler.hasRecipe(info));
        }

        @Test
        void shouldReturnFalseForMakeConcat() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(II)Ljava/lang/String;", 1);
            assertFalse(handler.hasRecipe(info));
        }
    }

    @Nested
    class SimpleConcatTests {
        @Test
        void shouldConcatIntegers() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(II)Ljava/lang/String;", 1);
            ConcreteValue[] args = {
                ConcreteValue.intValue(1),
                ConcreteValue.intValue(2)
            };

            String result = handler.executeConcat(info, args);
            assertEquals("12", result);
        }

        @Test
        void shouldConcatLongs() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(JJ)Ljava/lang/String;", 1);
            ConcreteValue[] args = {
                ConcreteValue.longValue(100L),
                ConcreteValue.longValue(200L)
            };

            String result = handler.executeConcat(info, args);
            assertEquals("100200", result);
        }

        @Test
        void shouldConcatFloats() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(F)Ljava/lang/String;", 1);
            ConcreteValue[] args = {
                ConcreteValue.floatValue(3.14f)
            };

            String result = handler.executeConcat(info, args);
            assertTrue(result.startsWith("3.14"));
        }

        @Test
        void shouldConcatDoubles() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(D)Ljava/lang/String;", 1);
            ConcreteValue[] args = {
                ConcreteValue.doubleValue(2.718)
            };

            String result = handler.executeConcat(info, args);
            assertTrue(result.startsWith("2.718"));
        }

        @Test
        void shouldHandleNullValue() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "(Ljava/lang/Object;)Ljava/lang/String;", 1);
            ConcreteValue[] args = {
                ConcreteValue.nullRef()
            };

            String result = handler.executeConcat(info, args);
            assertEquals("null", result);
        }

        @Test
        void shouldHandleEmptyArgs() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcat", "()Ljava/lang/String;", 1);
            ConcreteValue[] args = {};

            String result = handler.executeConcat(info, args);
            assertEquals("", result);
        }
    }

    @Nested
    class RecipeConcatTests {
        @Test
        void shouldConcatWithSimpleRecipe() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(I)Ljava/lang/String;", 1);
            ConcreteValue[] args = { ConcreteValue.intValue(42) };
            String recipe = "Value: \u0001";
            Object[] constants = {};

            String result = handler.executeConcat(info, args, recipe, constants);
            assertEquals("Value: 42", result);
        }

        @Test
        void shouldConcatWithMultipleArgs() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(II)Ljava/lang/String;", 1);
            ConcreteValue[] args = {
                ConcreteValue.intValue(1),
                ConcreteValue.intValue(2)
            };
            String recipe = "\u0001 + \u0001 = 3";
            Object[] constants = {};

            String result = handler.executeConcat(info, args, recipe, constants);
            assertEquals("1 + 2 = 3", result);
        }

        @Test
        void shouldConcatWithConstants() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(I)Ljava/lang/String;", 1);
            ConcreteValue[] args = { ConcreteValue.intValue(5) };
            String recipe = "\u0002\u0001\u0002";
            Object[] constants = { "<<", ">>" };

            String result = handler.executeConcat(info, args, recipe, constants);
            assertEquals("<<5>>", result);
        }

        @Test
        void shouldHandleNullRecipe() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(II)Ljava/lang/String;", 1);
            ConcreteValue[] args = {
                ConcreteValue.intValue(1),
                ConcreteValue.intValue(2)
            };

            String result = handler.executeConcat(info, args, null, null);
            assertEquals("12", result);
        }

        @Test
        void shouldHandleEmptyRecipe() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "(I)Ljava/lang/String;", 1);
            ConcreteValue[] args = { ConcreteValue.intValue(42) };

            String result = handler.executeConcat(info, args, "", null);
            assertEquals("42", result);
        }

        @Test
        void shouldHandleLiteralOnlyRecipe() {
            InvokeDynamicInfo info = new InvokeDynamicInfo(0, "makeConcatWithConstants", "()Ljava/lang/String;", 1);
            ConcreteValue[] args = {};
            String recipe = "Hello World";
            Object[] constants = {};

            String result = handler.executeConcat(info, args, recipe, constants);
            assertEquals("Hello World", result);
        }
    }

    @Nested
    class ExpectedArgCountTests {
        @Test
        void shouldReturnZeroForNoParams() {
            assertEquals(0, handler.getExpectedArgCount("()Ljava/lang/String;"));
        }

        @Test
        void shouldCountPrimitiveParams() {
            assertEquals(4, handler.getExpectedArgCount("(IJFD)Ljava/lang/String;"));
        }

        @Test
        void shouldCountObjectParams() {
            assertEquals(2, handler.getExpectedArgCount("(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;"));
        }

        @Test
        void shouldCountArrayParams() {
            assertEquals(2, handler.getExpectedArgCount("([I[Ljava/lang/String;)Ljava/lang/String;"));
        }

        @Test
        void shouldHandleNullDescriptor() {
            assertEquals(0, handler.getExpectedArgCount(null));
        }

        @Test
        void shouldHandleMalformedDescriptor() {
            assertEquals(0, handler.getExpectedArgCount("invalid"));
        }
    }

    @Nested
    class ParseRecipeTests {
        @Test
        void shouldParseTagArg() {
            String result = handler.parseRecipe("\u0001");
            assertEquals("{arg}", result);
        }

        @Test
        void shouldParseTagConst() {
            String result = handler.parseRecipe("\u0002");
            assertEquals("{const}", result);
        }

        @Test
        void shouldParseMixedRecipe() {
            String result = handler.parseRecipe("Hello \u0001, \u0002!");
            assertEquals("Hello {arg}, {const}!", result);
        }

        @Test
        void shouldParseNoTags() {
            String result = handler.parseRecipe("Hello World");
            assertEquals("Hello World", result);
        }

        @Test
        void shouldHandleNullRecipe() {
            String result = handler.parseRecipe(null);
            assertEquals("", result);
        }
    }

    @Nested
    class CountDynamicArgsTests {
        @Test
        void shouldCountZeroArgs() {
            assertEquals(0, handler.countDynamicArgs("Hello World"));
        }

        @Test
        void shouldCountOneArg() {
            assertEquals(1, handler.countDynamicArgs("Value: \u0001"));
        }

        @Test
        void shouldCountMultipleArgs() {
            assertEquals(3, handler.countDynamicArgs("\u0001 + \u0001 = \u0001"));
        }

        @Test
        void shouldIgnoreConstants() {
            assertEquals(1, handler.countDynamicArgs("\u0002\u0001\u0002"));
        }

        @Test
        void shouldHandleNull() {
            assertEquals(0, handler.countDynamicArgs(null));
        }
    }

    @Nested
    class CountConstantsTests {
        @Test
        void shouldCountZeroConstants() {
            assertEquals(0, handler.countConstants("Hello \u0001"));
        }

        @Test
        void shouldCountOneConstant() {
            assertEquals(1, handler.countConstants("\u0002suffix"));
        }

        @Test
        void shouldCountMultipleConstants() {
            assertEquals(3, handler.countConstants("\u0002\u0001\u0002text\u0002"));
        }

        @Test
        void shouldHandleNull() {
            assertEquals(0, handler.countConstants(null));
        }
    }
}
